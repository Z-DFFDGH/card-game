// /home/djx/game/core/src/main/java/com/game/cardgame/util/Res.java
package com.game.cardgame.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 资源统一管理：字体 + 立绘（缺图兜底）/ 占位图。
 *
 * 两条硬性要求：
 *   1. 【缺资源不许崩】找不到立绘就返回占位图；找不到字体就退回 libGDX 内置的 ASCII 字体，
 *      只打警告。
 *   2. 【只在应用启动后创建】纹理和字体都要 GL 上下文，所以 new Res() 必须放在
 *      GameMain.create() 里，不能放在静态代码块或命令行 main 里。
 *
 * 生命周期：谁创建谁销毁。在 GameMain.dispose() 里调 res.dispose()。
 */
public final class Res implements Disposable {

    private static final String TAG = "Res";

    /** 中文字体。注意是 .ttc（字体集合），不是 .ttf，详见 android/assets/fonts/README.txt */
    public static final String FONT_PATH = "fonts/NotoSansCJK-Regular.ttc";

    /**
     * .ttc 里的 face 序号。实测 NotoSansCJK-Regular.ttc 有 10 个 face：
     * 0=JP 1=KR 2=SC 3=TC 4=HK 5~9 = 对应的 Mono 版本。简体中文用 2。
     */
    public static final int FONT_FACE_INDEX = 2;

    /** 缺图时用的占位图 */
    public static final String PLACEHOLDER_PATH = "images/placeholder.png";

    /** 立绘目录 */
    public static final String PORTRAIT_DIR = "images/characters";

    /** 立绘允许的后缀，按顺序找，找到就用 */
    public static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg"};

    /** 字体自检要确认存在的字形：汉字 + 数字 + 字母 + 标点 */
    private static final char[] REQUIRED_GLYPHS = {'回', '战', '0', '9', 'x', 'A', '.'};

    public final BitmapFont fontTitle;
    public final BitmapFont fontBody;
    public final BitmapFont fontSmall;
    /** 更小的字：卡牌描述、状态标签这种挤得没地方的地方用 */
    public final BitmapFont fontTiny;

    // ---- 界面配色（各 Screen 统一用这几支颜色，别各写各的）----
    public static final Color COLOR_BG = new Color(0.06f, 0.07f, 0.10f, 1f);
    public static final Color COLOR_PANEL = new Color(0.11f, 0.12f, 0.16f, 1f);
    public static final Color COLOR_PANEL_SEL = new Color(0.20f, 0.23f, 0.30f, 1f);
    public static final Color COLOR_ACCENT = new Color(0.98f, 0.86f, 0.45f, 1f);
    public static final Color COLOR_TEXT = new Color(0.92f, 0.93f, 0.96f, 1f);
    public static final Color COLOR_TEXT_DIM = new Color(0.62f, 0.66f, 0.74f, 1f);
    public static final Color COLOR_DISABLED = new Color(0.34f, 0.36f, 0.42f, 1f);
    public static final Color COLOR_HP = new Color(0.85f, 0.28f, 0.32f, 1f);
    public static final Color COLOR_TOUGH = new Color(0.95f, 0.96f, 0.98f, 1f);
    public static final Color COLOR_ENERGY = new Color(0.36f, 0.72f, 0.95f, 1f);
    public static final Color COLOR_SKILLPOINT = new Color(0.55f, 0.85f, 0.60f, 1f);

    /** 1x1 白色贴图。所有纯色面板、按钮、进度条都是拿它染色出来的，省掉一套皮肤文件 */
    private final Texture whitePixel;

    private final Texture placeholder;
    private final HashMap<String, Texture> portraitCache = new HashMap<String, Texture>();
    private final HashSet<String> reportedMissing = new HashSet<String>();
    private final ArrayList<BitmapFont> ownedFonts = new ArrayList<BitmapFont>();

    /** 可能为 null：字体文件缺失时退回内置字体 */
    private final FreeTypeFontGenerator generator;

    public Res() {
        whitePixel = createWhitePixel();
        placeholder = loadPlaceholder();
        generator = openGenerator();
        // 字号按"手机竖屏可读性"定：虚拟画布宽 720，所以 36 号字在 1080 宽的手机上约等于 54 真实像素
        fontTitle = createFont(104);
        fontBody = createFont(56);
        fontSmall = createFont(36);
        fontTiny = createFont(30);
    }

    // =======================================================================
    // 界面基础件（不引入皮肤文件，全部用 1x1 白贴图染色凑出来）
    // =======================================================================

    private static Texture createWhitePixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture t = new Texture(pixmap);
        pixmap.dispose();
        return t;
    }

    /**
     * 把 Android 的安全区（状态栏 / 刘海 / 手势条）换算成【世界坐标】，返回 {左, 上, 右, 下}。
     *
     * 为什么必须有：targetSdk 35 在 Android 15+ 上【强制 edge-to-edge】，
     * 画面会一直铺到状态栏和导航栏底下。不避让的话，顶部行动条会被状态栏/刘海盖住，
     * 底部按钮会顶到手势条上 —— 这就是"内容被裁掉"的来源之一。
     *
     * 桌面端 getSafeInsetXxx() 一律返回 0，所以对桌面没有任何影响。
     */
    public static float[] safeInsets(Viewport viewport) {
        float scale = 1f;
        if (viewport != null && viewport.getWorldHeight() > 0f) {
            scale = Gdx.graphics.getHeight() / viewport.getWorldHeight();
        }
        if (scale <= 0f) {
            scale = 1f;
        }
        return new float[]{
                Gdx.graphics.getSafeInsetLeft() / scale,
                Gdx.graphics.getSafeInsetTop() / scale,
                Gdx.graphics.getSafeInsetRight() / scale,
                Gdx.graphics.getSafeInsetBottom() / scale,
        };
    }

    /**
     * 按图片【真实宽高比】算出一个正好塞进 boxW×boxH 的尺寸，返回 {宽, 高}。
     *
     * 【为什么必须有它 —— 这是踩过的坑】
     *   素材里 23 张立绘的宽高比从 0.40（很竖）到 2.20（很横）都有，
     *   有方图、有竖图、还有一张 2821×1280 的超横图。
     *   而界面上的格子统一按"竖图比例"(约 0.56) 开。
     *   用 Scaling.fit 把一张 2.20 的横图塞进 56×98 的格子，只会画出 56×25 ——
     *   变成一条细缝，看起来就像"立绘没显示出来"。
     *   所以显示尺寸必须按图片真实比例来算，而不是硬套格子比例。
     */
    public float[] fitSize(Texture tex, float boxW, float boxH) {
        if (tex == null) {
            return new float[]{boxW, boxH};
        }
        float w = tex.getWidth();
        float h = tex.getHeight();
        if (w <= 0f || h <= 0f) {
            return new float[]{boxW, boxH};
        }
        float scale = Math.min(boxW / w, boxH / h);
        return new float[]{Math.max(1f, w * scale), Math.max(1f, h * scale)};
    }

    /**
     * 取一块【居中裁切】后的区域：把图片裁成和 boxW:boxH 一样的比例。
     * 用它之后图片会铺满整个格子（不再有细缝），代价是超出比例的部分被裁掉。
     * 适合按钮上的小头像这种"要统一大小"的地方。
     */
    public TextureRegion coverRegion(Texture tex, float boxW, float boxH) {
        if (tex == null || boxW <= 0f || boxH <= 0f) {
            return new TextureRegion(placeholder);
        }
        int tw = tex.getWidth();
        int th = tex.getHeight();
        float boxAspect = boxW / boxH;
        int cw;
        int ch;
        if (tw / (float) th > boxAspect) {
            ch = th;
            cw = Math.round(th * boxAspect);
        } else {
            cw = tw;
            ch = Math.round(tw / boxAspect);
        }
        cw = Math.max(1, Math.min(cw, tw));
        ch = Math.max(1, Math.min(ch, th));
        TextureRegion region = new TextureRegion(tex);
        region.setRegion((tw - cw) / 2, (th - ch) / 2, cw, ch);
        return region;
    }

    /** 一个纯色矩形 Drawable。不占 GL 资源，可以随手建 */
    public Drawable rect(Color color) {
        return new TextureRegionDrawable(new TextureRegion(whitePixel)).tint(color);
    }

    public Label.LabelStyle labelStyle(BitmapFont font, Color color) {
        return new Label.LabelStyle(font, color);
    }

    /**
     * 统一样式的按钮。size 用虚拟坐标，调用方自己 add(...).width(w).height(h)。
     * 注意：disabled 状态会同时把字变灰、底色变暗，所以"置灰"是能看出来的。
     */
    public TextButton.TextButtonStyle buttonStyle(BitmapFont font) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.fontColor = COLOR_TEXT;
        style.downFontColor = COLOR_ACCENT;
        style.overFontColor = COLOR_ACCENT;
        style.disabledFontColor = COLOR_DISABLED;
        style.up = rect(new Color(0.16f, 0.18f, 0.24f, 1f));
        style.over = rect(new Color(0.22f, 0.25f, 0.33f, 1f));
        style.down = rect(new Color(0.10f, 0.12f, 0.16f, 1f));
        style.disabled = rect(new Color(0.10f, 0.11f, 0.13f, 1f));
        return style;
    }

    // =======================================================================
    // 字体
    // =======================================================================

    /** 打开字体生成器。文件不在、face 序号不对，都只打警告不抛异常 */
    private FreeTypeFontGenerator openGenerator() {
        FileHandle fontFile = Gdx.files.internal(FONT_PATH);
        if (!fontFile.exists()) {
            logWarn("找不到中文字体：" + FONT_PATH
                    + "\n路径应为 android/assets/" + FONT_PATH
                    + "\n将退回内置 ASCII 字体：汉字会显示成空白方块，但不会崩。");
            return null;
        }
        try {
            FreeTypeFontGenerator g = new FreeTypeFontGenerator(fontFile, FONT_FACE_INDEX);
            if (!checkGlyphs(g)) {
                g.dispose();
                return null;
            }
            return g;
        } catch (RuntimeException e) {
            logWarn("打开字体失败（" + FONT_PATH + " face[" + FONT_FACE_INDEX + "]）："
                    + e.getMessage() + "\n将退回内置 ASCII 字体。");
            return null;
        }
    }

    /**
     * 字形自检。这一步不是多余的：
     * 曾经用过 DroidSansFallbackFull.ttf，汉字正常但数字和字母【全部画不出来】，
     * 而且不报任何错，只是静默少画字，靠肉眼在复杂界面上极难发现。
     * 所以这里在启动时就把"汉字 + 数字 + 字母"都确认一遍。
     */
    private boolean checkGlyphs(FreeTypeFontGenerator g) {
        StringBuilder missing = new StringBuilder();
        for (char c : REQUIRED_GLYPHS) {
            if (!g.hasGlyph(c)) {
                if (missing.length() > 0) {
                    missing.append(' ');
                }
                missing.append(c);
            }
        }
        if (missing.length() > 0) {
            logWarn("字体 " + FONT_PATH + " face[" + FONT_FACE_INDEX + "] 缺少这些字形：「"
                    + missing + "」\n换一个同时覆盖 ASCII 和 CJK 的字体（例如 Noto Sans CJK SC）。"
                    + "\n将退回内置 ASCII 字体。");
            return false;
        }
        return true;
    }

    private BitmapFont createFont(int sizeInPixels) {
        if (generator == null) {
            // 兜底：libGDX 自带的 arial-15（ASCII）。难看但不会崩。
            BitmapFont fallback = new BitmapFont();
            ownedFonts.add(fallback);
            return fallback;
        }

        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = sizeInPixels;
        // 汉字按需生成，不用预先列出所有会用到的字
        parameter.incremental = true;
        parameter.color = Color.WHITE;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;

        BitmapFont font = generator.generateFont(parameter);
        font.setUseIntegerPositions(false);
        ownedFonts.add(font);
        return font;
    }

    // =======================================================================
    // 图片
    // =======================================================================

    private Texture loadPlaceholder() {
        FileHandle f = Gdx.files.internal(PLACEHOLDER_PATH);
        if (!f.exists()) {
            // 连占位图都没有：现造一张 1x1 纯色贴图顶上，保证不崩
            logWarn("找不到占位图：" + PLACEHOLDER_PATH + "，改用 1x1 纯色贴图代替。");
            com.badlogic.gdx.graphics.Pixmap pm =
                    new com.badlogic.gdx.graphics.Pixmap(1, 1,
                            com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pm.setColor(0.35f, 0.38f, 0.45f, 1f);
            pm.fill();
            Texture t = new Texture(pm);
            pm.dispose();
            return t;
        }
        return new Texture(f);
    }

    /** 占位图。缺立绘时返回的就是它 */
    public Texture placeholder() {
        return placeholder;
    }

    /**
     * 按 portraitFile 取立绘（传 characters.json 里的 portraitFile，如 "星野"）。
     *
     * 会依次尝试 .png / .jpg / .jpeg。都找不到就打一次警告并返回占位图，【不会抛异常】。
     * 找到的贴图会缓存起来，重复调用不会反复读盘。
     */
    public Texture portrait(String portraitFile) {
        if (portraitFile == null || portraitFile.trim().isEmpty()) {
            return placeholder;
        }

        Texture cached = portraitCache.get(portraitFile);
        if (cached != null) {
            return cached;
        }

        for (String ext : IMAGE_EXTENSIONS) {
            FileHandle f = Gdx.files.internal(PORTRAIT_DIR + "/" + portraitFile + ext);
            if (f.exists()) {
                Texture t = new Texture(f);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                portraitCache.put(portraitFile, t);
                return t;
            }
        }

        // 只报一次，避免每帧刷屏
        if (reportedMissing.add(portraitFile)) {
            logWarn("找不到立绘「" + portraitFile + "」，用占位图代替。"
                    + "\n应该在 " + PORTRAIT_DIR + "/ 下放 " + portraitFile + ".png 或 .jpg");
        }
        return placeholder;
    }

    /** 已经成功加载了几张立绘（占位不算）。调试用 */
    public int loadedPortraitCount() {
        return portraitCache.size();
    }

    private static void logWarn(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] " + message);
        }
    }

    @Override
    public void dispose() {
        for (Texture t : portraitCache.values()) {
            t.dispose();
        }
        portraitCache.clear();
        reportedMissing.clear();

        for (BitmapFont f : ownedFonts) {
            f.dispose();
        }
        ownedFonts.clear();

        // 顺序要紧：先销毁所有 font，再销毁 generator
        if (generator != null) {
            generator.dispose();
        }
        placeholder.dispose();
        whitePixel.dispose();
    }
}
