// /home/djx/game/core/src/main/java/com/game/cardgame/util/CharacterGenerator.java
package com.game.cardgame.util;

import com.badlogic.gdx.Gdx;

import com.game.cardgame.model.Character;
import com.game.cardgame.model.Element;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

/**
 * 把 characters.json 和立绘目录对上号。
 *
 * ★ 最重要的一条规则：characters.json 是【唯一权威】。
 *   本类只做一件事 —— 给每个角色补上 portraitFile。它【绝不】根据文件名反向生成
 *   角色属性（hp / 攻击 / 速度 / 属性 / 弱点 …）。
 *   理由：如果属性可以从文件名推出来，那目录里多一张图就会凭空多出一个角色，
 *   数值还会和 JSON 里写的对不上，出问题时根本查不出来是哪儿来的。
 *   唯一的例外是"characters.json 整个读不出来"，那种情况下游戏总得能起来，
 *   才会用文件名造一批【明显标注为占位】的默认角色，并且打醒目警告。
 *
 * 匹配规则：角色的 name 和文件名去后缀后【完全一致】才算命中。
 *   大小写、全角半角、空格都算不同字符 —— 这是故意的，宁可报"没找到"也不要瞎猜。
 */
public final class CharacterGenerator {

    private static final String TAG = "CharacterGenerator";

    /** 默认角色用到的占位数值（只在 JSON 缺失时用） */
    public static final int DEFAULT_HP = 100;
    public static final int DEFAULT_ATTACK = 12;
    public static final int DEFAULT_SPEED = 100;
    public static final int DEFAULT_TOUGHNESS = 30;

    /** 生成结果，附带统计，方便启动时打印 */
    public static final class Result {

        public final ArrayList<Character> characters;

        /** 立绘匹配成功的数量 */
        public final int matched;

        /** 没找到立绘、portraitFile 置 null 的数量 */
        public final int missing;

        /** 没找到立绘的角色名（排好序，只为了好读） */
        public final ArrayList<String> missingNames;

        /** 是不是走了"JSON 缺失、用文件名兜底"的特殊路径 */
        public final boolean usedFilenameFallback;

        Result(ArrayList<Character> characters, ArrayList<String> missingNames,
               boolean usedFilenameFallback) {
            this.characters = characters;
            this.missingNames = missingNames;
            this.matched = characters.size() - missingNames.size();
            this.missing = missingNames.size();
            this.usedFilenameFallback = usedFilenameFallback;
        }

        /** 一行摘要，给控制台用 */
        public String summary() {
            return "共 " + characters.size() + " 个角色：立绘匹配成功 " + matched
                    + " 个，未找到 " + missing + " 个"
                    + (usedFilenameFallback ? "（⚠ 用的是文件名兜底生成的默认角色）" : "");
        }
    }

    private CharacterGenerator() {
    }

    // =======================================================================
    // 正常路径：以 characters.json 为准
    // =======================================================================

    /**
     * 以 JSON 里的角色为准，按 name 去立绘表里找同名的图。
     *
     * @param fromJson  JsonLoader 读出来的角色（权威数据，属性一个字都不改）
     * @param portraits FileScanner 扫出来的「去后缀文件名 → 相对路径」
     */
    public static Result generate(ArrayList<Character> fromJson, Map<String, String> portraits) {
        ArrayList<Character> out = new ArrayList<Character>();
        ArrayList<String> missing = new ArrayList<String>();

        int viaDirectCheck = 0;
        for (Character c : fromJson) {
            // 先查目录扫描的结果；查不到再【直接问这个文件在不在】——
            // 打包后素材在 jar 里，目录列不出来，只能靠后一条路。
            boolean found = (portraits != null && portraits.containsKey(c.name));
            if (!found && FileScanner.imageExists(Res.PORTRAIT_DIR, c.name)) {
                found = true;
                viaDirectCheck++;
            }
            if (found) {
                // 命中：portraitFile 就用文件名（去后缀），属性全部保持 JSON 原样
                out.add(c.withPortraitFile(c.name));
            } else {
                // 未命中：置 null，让 Res.portrait(null) 去走占位图那一条路
                out.add(c.withPortraitFile(null));
                missing.add(c.name);
                warn("角色「" + c.name + "」无立绘，用占位图。"
                        + "\n  应该在 " + Res.PORTRAIT_DIR + "/ 下放 " + c.name + ".png 或 .jpg"
                        + "（文件名必须和 characters.json 里的 name 完全一致）");
            }
        }

        Result result = new Result(out, missing, false);
        info("[角色] " + result.summary()
                + (viaDirectCheck > 0 ? "（其中 " + viaDirectCheck + " 个是靠逐个文件查询找到的）" : ""));
        if (result.missing > 0) {
            info("[角色] 缺立绘的角色：" + join(result.missingNames));
        }
        return result;
    }

    // =======================================================================
    // 兜底路径：characters.json 读不出来
    // =======================================================================

    /**
     * 【只有 characters.json 完全缺失/读不出来时才会走到这里】
     * 用立绘文件名造一批默认角色，好让游戏还能起来。
     *
     * 造出来的角色属性全是写死的占位值，不是从文件名推出来的（文件名里也没有这些信息），
     * 而且会打醒目警告，免得你以为自己配的角色生效了。
     */
    public static Result generateFromFilenames(Map<String, String> portraits) {
        warn("⚠ characters.json 读不出来，正在用立绘文件名生成【默认占位角色】。"
                + "\n  这些角色的 hp/攻击/速度/属性/弱点 全是写死的默认值，不是你的配置！"
                + "\n  请检查 android/assets/data/characters.json 是否存在、格式是否正确。");

        ArrayList<Character> out = new ArrayList<Character>();
        TreeSet<String> sorted = new TreeSet<String>(
                portraits == null ? java.util.Collections.<String>emptySet() : portraits.keySet());

        int index = 0;
        for (String stem : sorted) {
            index++;
            out.add(new Character(
                    "auto" + String.format("%03d", index),
                    stem,
                    DEFAULT_HP,
                    DEFAULT_SPEED,
                    DEFAULT_ATTACK,
                    Element.PHYSICAL,
                    Character.Role.BALANCE,
                    "（默认被动：characters.json 缺失，占位）",
                    "（默认终结技：characters.json 缺失，占位）",
                    Element.QUANTUM,
                    DEFAULT_TOUGHNESS,
                    stem,
                    new java.util.HashMap<String, String>(),
                    new ArrayList<String>(),
                    new ArrayList<String>()));
        }

        Result result = new Result(out, new ArrayList<String>(), true);
        info("[角色] " + result.summary());
        return result;
    }

    // =======================================================================
    // 工具
    // =======================================================================

    private static String join(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static void warn(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message);
        } else {
            System.err.println("[" + TAG + "] " + message);
        }
    }

    private static void info(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] " + message);
        }
    }
}
