// /home/djx/game/core/src/main/java/com/game/cardgame/screen/CharacterSelectScreen.java
package com.game.cardgame.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import com.game.cardgame.GameMain;
import com.game.cardgame.model.Character;
import com.game.cardgame.util.Res;

import java.util.ArrayList;

/**
 * 选择出战角色。纵向滚动列表。
 *
 * 列表内容来自 characters.json（唯一权威），有几个角色就列几个。
 * 立绘缺失的角色不会被藏起来，而是照常出现在列表里、显示占位图，
 * 并在那一行末尾标一个「［缺立绘］」—— 藏起来的话你会以为角色丢了。
 *
 * 【为什么每行是用 Stack + Image 拼的，而不是给 Table 设背景】
 *   实测过：给行 Table 用 setBackground() 之后，点在文字和立绘上能选中，
 *   但点在行内的空白处会被 ScrollPane 吃掉 —— 因为 Table（Group）不会把
 *   自己当作 hit 的返回值，"背景"只是画出来的，不是可点的区域。
 *   对手游来说"整行可点"是硬性要求，所以改成：底下铺一张铺满整行的 Image
 *   （Image 是 Widget，能被 hit 到），内容 Table 叠在上面（Stack），
 *   点击监听挂在 Stack 上，事件从 Image 冒泡上去。
 */
public class CharacterSelectScreen extends ScreenAdapter {

    private static final String TAG = "CharacterSelectScreen";

    /**
     * 立绘最多占的框（虚拟坐标）。实际显示尺寸按【图片真实宽高比】算 ——
     * 素材的宽高比从 0.40 到 2.20 都有，硬套一个比例会让宽图变成一条细缝。
     */
    private static final float PORTRAIT_BOX_W = 150f;
    private static final float PORTRAIT_BOX_H = 210f;

    /** 横屏网格里一格的立绘框（固定尺寸，保证每格一样大） */
    private static final float CELL_PORTRAIT_W = 130f;
    private static final float CELL_PORTRAIT_H = 150f;

    /** 横屏网格一行放几格 */
    private static final int LANDSCAPE_COLUMNS = 5;

    /** 横屏时界面四周的留白（和 buildUi 里那个 basePad 必须是同一个数） */
    private static final float LANDSCAPE_BASE_PAD = 16f;

    private final GameMain game;
    private final Res res;
    private final Stage stage;
    private final ArrayList<Character> roster;

    /** 一队几个角色 */
    public static final int TEAM_SIZE = 3;

    /** 每行的背景 Image，切换选中态时要改它的 Drawable。下标和 roster 一一对应 */
    private final ArrayList<Image> rowBackgrounds = new ArrayList<Image>();

    /** 已经挑中的角色（最多 TEAM_SIZE 个，按点击顺序） */
    private final ArrayList<Character> picked = new ArrayList<Character>();
    private TextButton confirmButton;
    private Label hintLabel;

    public CharacterSelectScreen(GameMain game) {
        this.game = game;
        this.res = game.getRes();
        this.roster = game.getCharacters();
        this.stage = new Stage(game.getViewport(), game.getBatch());
        buildUi();
    }

    // =======================================================================
    // 界面
    // =======================================================================

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        // 避开 Android 安全区（状态栏 / 刘海 / 手势条）
        float[] inset = Res.safeInsets(game.getViewport());
        float basePad = game.isLandscape() ? LANDSCAPE_BASE_PAD : 24f;
        root.pad(basePad + inset[1], basePad + inset[0], basePad + inset[3], basePad + inset[2]);
        stage.addActor(root);

        // ---- 顶栏：返回 + 标题 ----
        TextButton backButton = new TextButton("返回", res.buttonStyle(res.fontSmall));
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.showMenu();
            }
        });

        Label title = new Label("选择出战角色（选 " + TEAM_SIZE + " 个）",
                res.labelStyle(res.fontBody, Res.COLOR_ACCENT));
        title.setAlignment(Align.center);

        // ---- 确认按钮 + 提示（两种排版的文案一样，先建好再决定放哪儿）----
        confirmButton = new TextButton("确认出战", res.buttonStyle(res.fontBody));
        confirmButton.setDisabled(true);
        confirmButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onConfirm();
            }
        });

        hintLabel = new Label(
                roster.isEmpty() ? "没有可用角色，请看控制台的报错"
                        : "点角色选中／取消，共要选 " + TEAM_SIZE + " 个",
                res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM));
        hintLabel.setAlignment(Align.center);
        hintLabel.setWrap(true);

        Table header = new Table();
        header.add(backButton).width(160f).height(80f);
        header.add(title).expandX().center();
        if (game.isLandscape()) {
            // 横屏：确认按钮直接放进顶栏。
            // 放在右边单开一栏的话网格只剩 852 宽、一列 170，
            // 而格子里的文字比 170 还宽，5 列会互相压在一起。
            header.add(confirmButton).width(260f).height(80f);
        } else {
            // 右边补一个和返回按钮等宽的占位，标题才会真正居中
            header.add().width(160f);
        }
        root.add(header).growX().padBottom(game.isLandscape() ? 8f : 18f).row();

        if (game.isLandscape()) {
            // 提示单独一行，剩下的宽度和高度全给角色网格
            root.add(hintLabel).growX().padBottom(8f).row();
            root.add(roster.isEmpty() ? buildEmptyNotice() : buildGrid()).grow();
            return;
        }

        // ---- 角色列表 ----
        if (roster.isEmpty()) {
            root.add(buildEmptyNotice()).grow().row();
        } else {
            root.add(buildList()).grow().row();
        }

        root.add(confirmButton).width(420f).height(140f).padTop(18f).row();
        root.add(hintLabel).padTop(12f);
    }

    /** 横屏的角色网格：一行 LANDSCAPE_COLUMNS 格，超出的部分纵向滚动 */
    private ScrollPane buildGrid() {
        Table grid = new Table();
        grid.top();

        float boxW = landscapeCellWidth();
        int column = 0;
        for (Character c : roster) {
            grid.add(buildCell(c, boxW)).width(boxW).padBottom(10f).padRight(6f);
            column++;
            if (column % LANDSCAPE_COLUMNS == 0) {
                grid.row();
            }
        }
        // 最后一行不满的话补几个空位，不然剩的那几格会被拉得特别宽
        if (column % LANDSCAPE_COLUMNS != 0) {
            for (int i = column % LANDSCAPE_COLUMNS; i < LANDSCAPE_COLUMNS; i++) {
                grid.add().width(boxW).padRight(6f);
            }
            grid.row();
        }

        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.background = res.rect(new Color(0.08f, 0.09f, 0.12f, 1f));
        style.vScroll = res.rect(new Color(0.10f, 0.11f, 0.14f, 1f));
        style.vScrollKnob = res.rect(new Color(0.36f, 0.40f, 0.48f, 1f));

        ScrollPane scroll = new ScrollPane(grid, style);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setScrollbarsOnTop(true);
        scroll.setForceScroll(false, true);
        return scroll;
    }

    private Label buildEmptyNotice() {
        Label label = new Label(
                "characters.json 里一个角色都没有。\n"
                        + "请检查 android/assets/data/characters.json 是否存在、格式是否正确。\n"
                        + "详细报错已经打到控制台（logcat / 终端）。",
                res.labelStyle(res.fontSmall, Res.COLOR_HP));
        label.setAlignment(Align.center);
        return label;
    }

    private ScrollPane buildList() {
        Table list = new Table();
        list.top();

        for (Character c : roster) {
            list.add(buildRow(c)).growX().padBottom(12f).row();
        }

        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.background = res.rect(new Color(0.08f, 0.09f, 0.12f, 1f));
        style.vScroll = res.rect(new Color(0.10f, 0.11f, 0.14f, 1f));
        style.vScrollKnob = res.rect(new Color(0.36f, 0.40f, 0.48f, 1f));

        ScrollPane scroll = new ScrollPane(list, style);
        scroll.setFadeScrollBars(false);          // 滚动条一直显示，不然不知道能滚
        scroll.setScrollingDisabled(true, false); // 只允许纵向滚
        scroll.setOverscroll(false, false);       // 关掉回弹，省得还要配 overscroll 贴图
        scroll.setScrollbarsOnTop(true);
        scroll.setForceScroll(false, true);
        return scroll;
    }

    private Stack buildRow(final Character c) {
        // 底层：铺满整行的可点背景
        final Image background = new Image(res.rect(Res.COLOR_PANEL));
        rowBackgrounds.add(background);

        // 上层：立绘 + 文字
        Table content = new Table();
        content.pad(12f);

        // 立绘：缺图时 Res 会自动返回占位图，这里不用管
        // 尺寸按图片真实比例算，保证整张立绘都露出来（缺图时 Res 会给占位图）
        Texture tex = res.portrait(c.portraitFile);
        Image portrait = new Image(tex);
        portrait.setScaling(Scaling.fit);
        float[] psize = res.fitSize(tex, PORTRAIT_BOX_W, PORTRAIT_BOX_H);

        Label name = new Label(c.name, res.labelStyle(res.fontBody, Res.COLOR_TEXT));

        Label line1 = new Label(
                roleLabel(c.role) + "　hp " + c.hp + "　速度 " + c.speed,
                res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM));

        StringBuilder sb = new StringBuilder();
        sb.append("属性 ").append(c.element.label)
                .append("　弱点 ").append(c.weakness.label)
                .append("　坚韧 ").append(c.toughnessMax);
        if (c.portraitFile == null) {
            sb.append("　［缺立绘］");
        }
        Label line2 = new Label(sb.toString(), res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));

        Table texts = new Table();
        texts.left();
        texts.add(name).left().row();
        texts.add(line1).left().padTop(8f).row();
        texts.add(line2).left().padTop(8f).row();

        // minHeight：宽图那一行的高度只有 ~68，不加下限的话行会忽高忽低很难看。
        // 立绘本身仍按真实比例完整显示，只是在行里居中。
        content.add(portrait).size(psize[0], psize[1]).minHeight(PORTRAIT_BOX_H).padRight(22f);
        content.add(texts).growX().left();

        Stack row = new Stack();
        row.add(background);
        row.add(content);

        attachSelectListener(row, c);
        return row;
    }

    /**
     * 横屏用的一格：立绘在上，名字和数值在下。
     *
     * 竖屏是一行一个角色（立绘在左、文字在右），横屏要是一行还放一个，
     * 一屏只能看见 1~2 个角色，23 个角色得滚半天。所以横屏改成网格，一屏能看见 10 个。
     */
    private Stack buildCell(final Character c, float boxW) {
        final Image background = new Image(res.rect(Res.COLOR_PANEL));
        rowBackgrounds.add(background);

        Table content = new Table();
        content.pad(10f);

        // 固定格子 + 等比缩放 = 每格一样大，而且立绘完整不裁切
        Image portrait = new Image(res.portrait(c.portraitFile));
        portrait.setScaling(Scaling.fit);

        // 文字宽度定死 = 格子宽 - 内边距。放不下就换行，绝不会横着压到隔壁格子
        float textW = boxW - 20f;

        Label name = new Label(c.name, res.labelStyle(res.fontSmall, Res.COLOR_TEXT));
        Label line1 = new Label(roleLabel(c.role) + "　hp " + c.hp,
                res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        Label line2 = new Label("速度 " + c.speed + "　" + c.element.label + "/弱" + c.weakness.label,
                res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        name.setWrap(true);
        line1.setWrap(true);
        line2.setWrap(true);

        content.add(portrait).size(CELL_PORTRAIT_W, CELL_PORTRAIT_H).row();
        content.add(name).width(textW).left().padTop(6f).row();
        content.add(line1).width(textW).left().padTop(4f).row();
        content.add(line2).width(textW).left().padTop(2f).row();
        if (c.portraitFile == null) {
            Label miss = new Label("［缺立绘］", res.labelStyle(res.fontTiny, Res.COLOR_HP));
            miss.setWrap(true);
            content.add(miss).width(textW).left().padTop(2f).row();
        }

        Stack cell = new Stack();
        cell.add(background);
        cell.add(content);

        attachSelectListener(cell, c);
        return cell;
    }

    /** 横屏网格一格的宽度。定死宽度，文字才不会把列撑歪 */
    private float landscapeCellWidth() {
        float worldW = game.getViewport().getWorldWidth();
        return (worldW - LANDSCAPE_BASE_PAD * 2f) / LANDSCAPE_COLUMNS - 8f;
    }

    /** 点整格都能选中：监听挂在 Stack 上，点背景、点立绘、点文字都会冒泡到这里 */
    private void attachSelectListener(Stack row, final Character c) {
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggle(c);
            }
        });
    }

    private static String roleLabel(Character.Role role) {
        switch (role) {
            case ATTACK:
                return "攻击型";
            case DEFENSE:
                return "防御型";
            default:
                return "均衡型";
        }
    }

    // =======================================================================
    // 交互
    // =======================================================================

    /** 点一下选中，再点一下取消。满 TEAM_SIZE 个之后不能再加，只能先取消一个 */
    private void toggle(Character c) {
        if (picked.contains(c)) {
            picked.remove(c);
            Gdx.app.log(TAG, "取消选择 " + c.id + " " + c.name);
        } else if (picked.size() >= TEAM_SIZE) {
            hintLabel.setText("最多 " + TEAM_SIZE + " 个，先点掉一个再选");
            return;
        } else {
            picked.add(c);
            Gdx.app.log(TAG, "选中 " + c.id + " " + c.name
                    + (c.portraitFile == null ? "（无立绘，用占位图）" : ""));
        }
        refreshSelectionUi();
    }

    private void refreshSelectionUi() {
        for (int i = 0; i < rowBackgrounds.size(); i++) {
            boolean isThis = picked.contains(roster.get(i));
            rowBackgrounds.get(i).setDrawable(
                    res.rect(isThis ? Res.COLOR_PANEL_SEL : Res.COLOR_PANEL));
        }

        boolean full = (picked.size() == TEAM_SIZE);
        confirmButton.setDisabled(!full);

        StringBuilder sb = new StringBuilder();
        sb.append("已选 ").append(picked.size()).append("/").append(TEAM_SIZE).append("：");
        if (picked.isEmpty()) {
            sb.append("（还没选）");
        } else {
            for (int i = 0; i < picked.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(picked.get(i).name);
            }
        }
        if (!full && !roster.isEmpty()) {
            sb.append("　—— 还差 ").append(TEAM_SIZE - picked.size()).append(" 个");
        }
        hintLabel.setText(sb.toString());
    }

    private void onConfirm() {
        if (picked.size() != TEAM_SIZE) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Character c : picked) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.id).append(" ").append(c.name);
        }
        Gdx.app.log(TAG, "确认出战（" + picked.size() + " 人）：" + sb);
        game.startBattle(new ArrayList<Character>(picked));
    }

    // =======================================================================
    // Screen 生命周期
    // =======================================================================

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(Res.COLOR_BG.r, Res.COLOR_BG.g, Res.COLOR_BG.b, Res.COLOR_BG.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
