// /home/djx/game/core/src/main/java/com/game/cardgame/util/JsonLoader.java
package com.game.cardgame.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 把 android/assets/data/*.json 读成 Java 对象。
 *
 * 为什么不用 libGDX 自带的 Json.fromJson() 反射映射：
 *   1. 我们的 JSON 字段名是【中文】（速度、攻击、属性…），
 *      而 libGDX 的 Json 是拿 Java 字段名去对的，没有 @SerializedName 这类改名的注解
 *      （我在 gdx-1.14.2.jar 里查过，只有 Json/JsonValue/JsonReader/JsonWriter 这几个类，
 *        没有任何重命名注解）。
 *      硬要用就得把 Java 字段也写成中文标识符，可读性太差。
 *   2. 显式映射可以给出「cards.json 第 7 条 [s001] 的字段 cost 不是数字」这种能直接照着改的报错，
 *      而不是 libGDX 反射那种一句 "Unable to convert value"。
 *   3. 不用反射，以后开代码混淆（minifyEnabled true）也不会出问题。
 *
 * 出错时的行为：先用 Gdx.app.error（没有图形环境时退回 System.err）打日志，
 * 然后抛 IllegalStateException，message 里带上文件、第几条、哪个字段、实际值。
 */
public final class JsonLoader {

    private static final String TAG = "JsonLoader";

    /** 运行期（libGDX 应用内）用的 internal 路径，不带 assets/ 前缀 */
    public static final String CARDS_INTERNAL_PATH = "data/cards.json";
    public static final String CHARACTERS_INTERNAL_PATH = "data/characters.json";

    /** 命令行验证时的默认数据目录，相对于工程根目录 */
    public static final String DEFAULT_DATA_DIR = "android/assets/data";

    private JsonLoader() {
        // 工具类，不实例化
    }

    // =======================================================================
    // 运行期入口：必须在 libGDX 应用启动之后调用（依赖 Gdx.files）
    // =======================================================================

    public static ArrayList<Card> loadCards() {
        return loadCards(requireInternal(CARDS_INTERNAL_PATH));
    }

    public static ArrayList<Character> loadCharacters() {
        return loadCharacters(requireInternal(CHARACTERS_INTERNAL_PATH));
    }

    private static FileHandle requireInternal(String internalPath) {
        if (Gdx.files == null) {
            throw fail("Gdx.files 还没初始化。无参的 loadCards()/loadCharacters() "
                    + "只能在 libGDX 应用启动后调用；\n命令行验证请用 loadCards(FileHandle)。");
        }
        return Gdx.files.internal(internalPath);
    }

    // =======================================================================
    // 核心：从任意 FileHandle 读（运行期和命令行验证共用这一条路径）
    // =======================================================================

    /** 读 cards.json */
    public static ArrayList<Card> loadCards(FileHandle file) {
        JsonValue root = parseArray(file, "卡牌");
        ArrayList<Card> result = new ArrayList<Card>();

        int index = 0;
        for (JsonValue item = root.child; item != null; item = item.next) {
            index++;
            String where = file.name() + " 第 " + index + " 条";

            // id 单独先读出来，后面报错就能带上 id，比"第几条"好定位
            String id = requireString(item, "id", where);
            where = file.name() + " [" + id + "]";

            Card card = new Card(
                    id,
                    requireString(item, "name", where),
                    parseEnum(Card.Rarity.class,
                            requireString(item, "rarity", where), "rarity", where),
                    parseEnum(Card.Type.class,
                            requireString(item, "type", where), "type", where),
                    requireInt(item, "cost", where),
                    parseElement(requireString(item, "attribute", where), "attribute", where),
                    parseEnum(Card.EffectType.class,
                            requireString(item, "effectType", where), "effectType", where),
                    requireInt(item, "effectValue", where),
                    requireString(item, "description", where),
                    requireStringArray(item, "tags", where));
            result.add(card);
        }

        if (result.isEmpty()) {
            throw fail(file.name() + " 里一条数据都没有");
        }
        return result;
    }

    /** 读 characters.json */
    public static ArrayList<Character> loadCharacters(FileHandle file) {
        JsonValue root = parseArray(file, "角色");
        ArrayList<Character> result = new ArrayList<Character>();

        int index = 0;
        for (JsonValue item = root.child; item != null; item = item.next) {
            index++;
            String where = file.name() + " 第 " + index + " 条";

            String id = requireString(item, "id", where);
            where = file.name() + " [" + id + "]";

            Character ch = new Character(
                    id,
                    requireString(item, "name", where),
                    requireInt(item, "hp", where),
                    requireInt(item, "速度", where),
                    requireInt(item, "攻击", where),
                    parseElement(requireString(item, "属性", where), "属性", where),
                    parseEnum(Character.Role.class,
                            requireString(item, "角色", where), "角色", where),
                    requireString(item, "被动技能", where),
                    requireString(item, "终极技能", where),
                    parseElement(requireString(item, "弱点", where), "弱点", where),
                    requireInt(item, "坚韧极限", where),
                    requireString(item, "portraitFile", where),
                    requireStringMap(item, "extraAttributes", where),
                    requireStringArray(item, "equipmentSlots", where),
                    requireStringArray(item, "statusEffects", where));
            result.add(ch);
        }

        if (result.isEmpty()) {
            throw fail(file.name() + " 里一条数据都没有");
        }
        return result;
    }

    // =======================================================================
    // 字段读取与校验
    // =======================================================================

    private static JsonValue parseArray(FileHandle file, String what) {
        if (file == null) {
            throw fail("要读的" + what + "文件是 null");
        }
        if (!file.exists()) {
            throw fail("找不到" + what + "文件：" + file.path()
                    + "\nAndroid 上它应该在 android/assets/ 下；"
                    + "桌面版注意工作目录是 android/assets。");
        }

        JsonValue root;
        try {
            root = new JsonReader().parse(file);
        } catch (RuntimeException e) {
            // JSON 语法错误（少个逗号、多个括号之类）都会走到这里
            throw fail("解析" + what + "文件失败，JSON 格式有错：" + file.path()
                    + "\n" + e.getMessage()
                    + "\n提示：用编辑器或 `python3 -m json.tool 文件路径` 检查一下。");
        }

        if (root == null || !root.isArray()) {
            throw fail(what + "文件的顶层必须是数组 []，实际不是：" + file.path());
        }
        return root;
    }

    private static String requireString(JsonValue item, String field, String where) {
        JsonValue v = requireField(item, field, where);
        if (v.isNull()) {
            throw fail(where + " 的字段「" + field + "」是 null");
        }
        String s = v.asString();
        if (s == null || s.trim().isEmpty()) {
            throw fail(where + " 的字段「" + field + "」是空字符串");
        }
        return s;
    }

    private static int requireInt(JsonValue item, String field, String where) {
        JsonValue v = requireField(item, field, where);
        if (!v.isNumber()) {
            throw fail(where + " 的字段「" + field + "」必须是数字，实际是 " + describe(v));
        }
        return v.asInt();
    }

    private static ArrayList<String> requireStringArray(JsonValue item, String field, String where) {
        JsonValue v = requireField(item, field, where);
        if (!v.isArray()) {
            throw fail(where + " 的字段「" + field + "」必须是数组 []，实际是 " + describe(v));
        }
        ArrayList<String> out = new ArrayList<String>();
        for (JsonValue c = v.child; c != null; c = c.next) {
            out.add(c.asString());
        }
        return out;
    }

    private static HashMap<String, String> requireStringMap(JsonValue item, String field, String where) {
        JsonValue v = requireField(item, field, where);
        if (!v.isObject()) {
            throw fail(where + " 的字段「" + field + "」必须是对象 {}，实际是 " + describe(v));
        }
        HashMap<String, String> out = new HashMap<String, String>();
        for (JsonValue c = v.child; c != null; c = c.next) {
            out.put(c.name, c.asString());
        }
        return out;
    }

    private static JsonValue requireField(JsonValue item, String field, String where) {
        if (!item.has(field)) {
            throw fail(where + " 缺少字段「" + field + "」"
                    + "\n该条现有的字段：" + fieldNames(item));
        }
        JsonValue v = item.get(field);
        if (v == null) {
            throw fail(where + " 的字段「" + field + "」读出来是 null");
        }
        return v;
    }

    private static Element parseElement(String raw, String field, String where) {
        try {
            return Element.fromLabel(raw);
        } catch (IllegalArgumentException e) {
            throw fail(where + " 的字段「" + field + "」" + e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw,
                                                   String field, String where) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw fail(where + " 的字段「" + field + "」值非法：" + raw
                    + "\n合法值只有：" + enumValues(type));
        }
    }

    private static <E extends Enum<E>> String enumValues(Class<E> type) {
        StringBuilder sb = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(e.name());
        }
        return sb.toString();
    }

    /** 把这一条实际有哪些字段拼出来，字段名写错时特别有用 */
    private static String fieldNames(JsonValue item) {
        StringBuilder sb = new StringBuilder();
        for (JsonValue c = item.child; c != null; c = c.next) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.name);
        }
        return sb.toString();
    }

    private static String describe(JsonValue v) {
        if (v == null) {
            return "null";
        }
        if (v.isArray()) {
            return "数组";
        }
        if (v.isObject()) {
            return "对象";
        }
        if (v.isBoolean()) {
            return "布尔值 " + v.asBoolean();
        }
        return "「" + v.asString() + "」";
    }

    // =======================================================================
    // 日志与异常
    // =======================================================================

    /** 有 libGDX 环境就打到 logcat/控制台，没有（命令行 main）就打到 System.err */
    private static void logError(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message);
        } else {
            System.err.println("[" + TAG + "] " + message);
        }
    }

    /**
     * 造一个可读的异常。故意做成"返回"而不是"抛出"，
     * 这样调用处写 throw fail(...) 时，堆栈指向的是真正出错的那一行。
     */
    private static IllegalStateException fail(String message) {
        String full = "读取游戏数据失败：" + message;
        logError(full);
        return new IllegalStateException(full);
    }

    // =======================================================================
    // 阶段 3 验收：命令行加载两个 JSON，打印条数与字段
    // =======================================================================

    public static void main(String[] args) {
        File dataDir = (args.length > 0)
                ? new File(args[0])
                : new File(DEFAULT_DATA_DIR);

        System.out.println("数据目录：" + dataDir.getAbsolutePath());

        ArrayList<Card> cards;
        ArrayList<Character> characters;
        try {
            cards = loadCards(new FileHandle(new File(dataDir, "cards.json")));
            characters = loadCharacters(new FileHandle(new File(dataDir, "characters.json")));
        } catch (RuntimeException e) {
            // fail() 抛出来的异常在抛之前已经打过日志了，这里不要重复打。
            // 只有"不是我们自己的校验异常"（比如空指针）才补打堆栈，免得悄无声息地退出。
            if (!(e instanceof IllegalStateException)
                    || e.getMessage() == null
                    || !e.getMessage().startsWith("读取游戏数据失败")) {
                e.printStackTrace();
            }
            System.exit(1);
            return;
        }

        printCards(cards);
        printCharacters(characters);
    }

    private static void printCards(ArrayList<Card> cards) {
        System.out.println();
        System.out.println("==================== cards.json ====================");
        System.out.println("条数：" + cards.size());
        for (Card c : cards) {
            System.out.println("  " + c);
        }

        System.out.println();
        System.out.println("  按 type：");
        for (Card.Type t : Card.Type.values()) {
            System.out.println("    " + pad(t.name(), 10) + countByType(cards, t));
        }
        System.out.println("  按 rarity：");
        for (Card.Rarity r : Card.Rarity.values()) {
            System.out.println("    " + pad(r.name(), 10) + countByRarity(cards, r));
        }
        System.out.println("  按 effectType：");
        for (Card.EffectType e : Card.EffectType.values()) {
            System.out.println("    " + pad(e.name(), 10) + countByEffect(cards, e));
        }
    }

    private static void printCharacters(ArrayList<Character> characters) {
        System.out.println();
        System.out.println("================= characters.json =================");
        System.out.println("条数：" + characters.size());
        for (Character c : characters) {
            System.out.println("  " + c);
            System.out.println("      被动：" + c.passiveSkill);
            System.out.println("      终结：" + c.ultimateSkill);
        }

        System.out.println();
        System.out.println("  按角色定位：");
        for (Character.Role r : Character.Role.values()) {
            System.out.println("    " + pad(r.name(), 10) + countByRole(characters, r));
        }
        System.out.println("  按属性：");
        for (Element e : Element.values()) {
            System.out.println("    " + pad(e.label, 10) + countByElement(characters, e));
        }
        System.out.println("  按弱点：");
        for (Element e : Element.values()) {
            System.out.println("    " + pad(e.label, 10) + countByWeakness(characters, e));
        }
    }

    private static int countByType(ArrayList<Card> list, Card.Type t) {
        int n = 0;
        for (Card c : list) {
            if (c.type == t) {
                n++;
            }
        }
        return n;
    }

    private static int countByRarity(ArrayList<Card> list, Card.Rarity r) {
        int n = 0;
        for (Card c : list) {
            if (c.rarity == r) {
                n++;
            }
        }
        return n;
    }

    private static int countByEffect(ArrayList<Card> list, Card.EffectType e) {
        int n = 0;
        for (Card c : list) {
            if (c.effectType == e) {
                n++;
            }
        }
        return n;
    }

    private static int countByRole(ArrayList<Character> list, Character.Role r) {
        int n = 0;
        for (Character c : list) {
            if (c.role == r) {
                n++;
            }
        }
        return n;
    }

    private static int countByElement(ArrayList<Character> list, Element e) {
        int n = 0;
        for (Character c : list) {
            if (c.element == e) {
                n++;
            }
        }
        return n;
    }

    private static int countByWeakness(ArrayList<Character> list, Element e) {
        int n = 0;
        for (Character c : list) {
            if (c.weakness == e) {
                n++;
            }
        }
        return n;
    }

    /** 中文字符在终端里占两格，所以不能用 String.format 的 %-10s 对齐，得自己按显示宽度补 */
    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        int visual = 0;
        for (int i = 0; i < s.length(); i++) {
            visual += (s.charAt(i) > 0x2E80) ? 2 : 1;
        }
        while (visual < width) {
            sb.append(' ');
            visual++;
        }
        return sb.toString();
    }
}
