// /home/djx/game/core/src/main/java/com/game/cardgame/GameMain.java
package com.game.cardgame;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.game.cardgame.battle.Difficulty;
import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.screen.BattleScreen;
import com.game.cardgame.screen.CharacterSelectScreen;
import com.game.cardgame.screen.MenuScreen;
import com.game.cardgame.util.CharacterGenerator;
import com.game.cardgame.util.FileScanner;
import com.game.cardgame.util.JsonLoader;
import com.game.cardgame.util.Res;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 游戏入口。desktop 和 android 两个启动器都只是 new 一个它，然后交给平台跑。
 *
 * 它是全局"资源持有者"：
 *   Res          字体 / 立绘 / 占位图 / 界面基础件
 *   cards        cards.json 读出来的卡（全工程共用一份，只读）
 *   characters   characters.json + 立绘扫描对完号之后的角色表（只读）
 *
 * 【统一 Viewport】
 *   整个游戏只创建这一个 Viewport，在 create() 里建好，之后每个 Screen 都复用同一个。
 *   好处：切换界面时世界坐标系不变，不会出现"这个屏幕上字大、那个屏幕上字小"。
 *
 * 【虚拟分辨率 720x1280，为什么用 ExtendViewport 而不是 FitViewport】
 *   FitViewport   —— 严格保持比例，屏幕比例不同就留黑边。
 *   ExtendViewport—— 保证"至少"能看到 720x1280，屏幕更长/更宽时把世界延伸出去填满，不留黑边。
 *   注意：用了 ExtendViewport 之后，世界的宽高【不一定】正好是 720x1280，
 *         所以写 UI 时要用 viewport.getWorldWidth()/getWorldHeight() 来算，
 *         这就是"禁止硬编码像素"的意思。
 */
public class GameMain extends Game {

    private static final String TAG = "GameMain";

    // 虚拟分辨率。整套 UI 的"设计尺子"，全工程唯一允许出现这两个数字的地方。
    public static final float VIRTUAL_WIDTH = 720f;
    public static final float VIRTUAL_HEIGHT = 1280f;

    // 横屏（安卓版）用的虚拟分辨率，和上面那两个是一对。
    public static final float LANDSCAPE_WIDTH = 1280f;
    public static final float LANDSCAPE_HEIGHT = 720f;

    /**
     * 本次运行是不是横屏。
     *
     * 规则：安卓走横屏（AndroidManifest 里锁死了 landscape），其它平台保持竖屏不动。
     * 额外认一个 -Dgame.landscape=true 开关，只是为了能在电脑上跑出横屏来验证布局，
     * 不传这个参数时行为和以前一模一样。
     */
    private boolean landscape;

    /** 全局唯一的 Viewport，创建后传给各个 Screen */
    private Viewport viewport;

    /** 全局共用的画笔 */
    private SpriteBatch batch;

    /** 全局资源 */
    private Res res;

    /** 全部卡牌（只读） */
    private ArrayList<Card> cards = new ArrayList<Card>();

    /** 全部角色（只读，立绘已经对过号） */
    private ArrayList<Character> characters = new ArrayList<Character>();

    /** 当前选择的难度 */
    private Difficulty difficulty = Difficulty.NORMAL;

    /** 本次角色生成的统计，给界面显示用 */
    private CharacterGenerator.Result characterStats;

    @Override
    public void create() {
        batch = new SpriteBatch();

        landscape = detectLandscape();
        if (landscape) {
            // 横屏：宽 1280 保底，高 720 保底，更宽的屏幕把世界往两边延伸
            viewport = new ExtendViewport(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT);
        } else {
            viewport = new ExtendViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        }
        Gdx.app.log(TAG, "界面方向：" + (landscape ? "横屏 1280x720" : "竖屏 720x1280"));

        res = new Res();

        loadGameData();

        setScreen(new MenuScreen(this));
    }

    /** 安卓 = 横屏；其它平台 = 竖屏。额外的 -Dgame.landscape 只用于在电脑上验证横屏布局 */
    private static boolean detectLandscape() {
        if (Boolean.getBoolean("game.landscape")) {
            return true;
        }
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }

    /** 当前是不是横屏。各个 Screen 用它决定排版 */
    public boolean isLandscape() {
        return landscape;
    }

    // =======================================================================
    // 数据加载
    // =======================================================================

    private void loadGameData() {
        loadCards();
        loadCharacters();
    }

    private void loadCards() {
        try {
            cards = JsonLoader.loadCards();
            log("卡牌加载完成：" + cards.size() + " 张");
        } catch (RuntimeException e) {
            // 卡牌读不出来是致命问题（战斗没法打），但也不能让游戏起不来：
            // 记下来，界面上会提示"没有卡牌"。
            cards = new ArrayList<Card>();
            error("cards.json 加载失败：" + e.getMessage());
        }
    }

    private void loadCharacters() {
        // 1) 先扫立绘目录，拿到「去后缀文件名 → 路径」
        HashMap<String, String> portraits = FileScanner.scanImages(Res.PORTRAIT_DIR);

        // 2) 再读 characters.json。它才是角色的唯一权威
        ArrayList<Character> fromJson = null;
        FileHandle jsonFile = Gdx.files.internal(JsonLoader.CHARACTERS_INTERNAL_PATH);

        if (!jsonFile.exists()) {
            // 只有"文件根本不存在"这一种情况才走文件名兜底
            error("characters.json 不存在（应该是 "
                    + JsonLoader.CHARACTERS_INTERNAL_PATH + "），改用立绘文件名兜底");
        } else {
            try {
                fromJson = JsonLoader.loadCharacters();
            } catch (RuntimeException e) {
                // 文件在、但读不出来（格式错/字段错）→ 不兜底，把错误暴露出来，
                // 不然你会以为自己的配置生效了。
                error("characters.json 读取失败，角色列表为空。请按上面的报错修 JSON："
                        + e.getMessage());
            }
        }

        CharacterGenerator.Result result;
        if (fromJson == null) {
            result = CharacterGenerator.generateFromFilenames(portraits);
        } else {
            result = CharacterGenerator.generate(fromJson, portraits);
        }

        characters = result.characters;
        characterStats = result;

        log("角色加载完成：" + result.summary());
    }

    // =======================================================================
    // 给各个 Screen 用
    // =======================================================================

    public Viewport getViewport() {
        return viewport;
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public Res getRes() {
        return res;
    }

    public ArrayList<Card> getCards() {
        return cards;
    }

    public ArrayList<Character> getCharacters() {
        return characters;
    }

    public CharacterGenerator.Result getCharacterStats() {
        return characterStats;
    }

    public void showMenu() {
        switchScreen(new MenuScreen(this));
    }

    public void showCharacterSelect() {
        switchScreen(new CharacterSelectScreen(this));
    }

    /**
     * 开一局人机对战。
     *
     * 对手的选择：取角色表里排在玩家后面一位的那个（循环）。
     * 你没指定敌方怎么选，我挑了这种"确定、且会随玩家选择变化"的做法。
     * 想改成随机、或者让玩家自己挑对手，改这里就行。
     */
    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = (difficulty == null) ? Difficulty.NORMAL : difficulty;
        log("难度设为：" + this.difficulty.label + " —— " + this.difficulty.describe());
    }

    public void startBattle(ArrayList<Character> playerTeam) {
        if (playerTeam == null || playerTeam.isEmpty()) {
            error("没有可用角色，进不了战斗");
            return;
        }
        if (cards.isEmpty()) {
            error("没有卡牌（cards.json 没读出来），进不了战斗");
            return;
        }
        ArrayList<Character> enemyTeam = pickEnemyTeam(playerTeam);
        log("出战（难度 " + difficulty.label + "）：我方 " + teamNames(playerTeam)
                + "　VS　敌方 " + teamNames(enemyTeam));
        switchScreen(new BattleScreen(this, playerTeam, enemyTeam, difficulty));
    }

    private static String teamNames(ArrayList<Character> team) {
        StringBuilder sb = new StringBuilder();
        for (Character c : team) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(c.name);
        }
        return sb.toString();
    }

    /**
     * 随机组一支敌方队伍（3 个不同的角色）。
     *
     * 【"随机"但不乱来】
     *   直接从全部 23 个里纯随机的话，很可能随出一队强的或者一队弱的，
     *   而之前量化测过：一局很短、先手和数值差直接决定胜负，所以那样会非常看运气。
     *   做法：先按"强度评分接近我方队伍平均水平"排序，取最接近的 8 个当候选池，
     *   再从候选池里【真随机】抽 3 个不同的。
     *   这样每局对手都不一样，但不会出现一边倒的碾压局。
     *
     * 想要纯随机（不管强度），把 CANDIDATE_POOL_SIZE 改成 characters.size() 即可。
     */
    private static final int CANDIDATE_POOL_SIZE = 8;

    private ArrayList<Character> pickEnemyTeam(ArrayList<Character> playerTeam) {
        float sum = 0f;
        for (Character c : playerTeam) {
            sum += c.strengthScore();
        }
        // 难度旋钮：把"目标强度"整体压低或抬高，敌方队伍就从弱的一端或强的一端里挑
        final float target = (sum / playerTeam.size()) * difficulty.enemyStrengthBias;

        ArrayList<Character> pool = new ArrayList<Character>(characters);
        pool.sort(new java.util.Comparator<Character>() {
            @Override
            public int compare(Character a, Character b) {
                return Float.compare(Math.abs(a.strengthScore() - target),
                        Math.abs(b.strengthScore() - target));
            }
        });

        int poolSize = Math.min(pool.size(), CANDIDATE_POOL_SIZE);
        ArrayList<Character> candidates = new ArrayList<Character>(pool.subList(0, poolSize));
        java.util.Collections.shuffle(candidates, new java.util.Random());

        ArrayList<Character> team = new ArrayList<Character>();
        for (int i = 0; i < 3 && i < candidates.size(); i++) {
            team.add(candidates.get(i));
        }
        return team;
    }

    /**
     * 本局的牌库：全部卡牌打乱。
     * 用真随机（不是固定种子）—— 每局抽牌顺序不同才是正常的卡牌游戏。
     * 牌库抽空会把弃牌堆收回，手牌全废时有防死锁兜底，所以不会卡死。
     */
    public ArrayList<Card> buildDeck() {
        ArrayList<Card> deck = new ArrayList<Card>(cards);
        java.util.Collections.shuffle(deck, new java.util.Random());
        return deck;
    }

    /**
     * 切界面时把旧界面 dispose 掉。
     * 注意：Game.setScreen() 只会调旧界面的 hide()，【不会】调 dispose()，
     * 所以旧 Stage 里的监听器、Timer 之类要自己收干净。
     * Stage 里如果没自建 SpriteBatch（我们是共用 GameMain 的 batch），
     * dispose 不会把共用 batch 销毁掉。
     */
    public void switchScreen(Screen next) {
        Screen old = getScreen();
        setScreen(next);
        if (old != null) {
            old.dispose();
        }
    }

    @Override
    public void dispose() {
        // 顺序：先销毁当前 Screen，再销毁它用到的资源
        if (getScreen() != null) {
            getScreen().dispose();
        }
        if (res != null) {
            res.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] " + message);
        }
    }

    private static void error(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message);
        } else {
            System.err.println("[" + TAG + "] " + message);
        }
    }
}
