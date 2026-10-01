// /home/djx/game/core/src/main/java/com/game/cardgame/screen/MenuScreen.java
package com.game.cardgame.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

import com.game.cardgame.GameMain;
import com.game.cardgame.battle.Difficulty;
import com.game.cardgame.util.CharacterGenerator;
import com.game.cardgame.util.Res;

import java.util.ArrayList;

/**
 * 主菜单。
 *
 * 只有一个真按钮：人机对战。
 * 联机对战按钮故意做成【不能点】的置灰状态 —— 它是 v2 的入口，
 * 现在放一个能点的假按钮只会让人以为坏了。
 */
public class MenuScreen extends ScreenAdapter {

    private static final String TAG = "MenuScreen";

    private final GameMain game;
    private final Res res;
    private final Stage stage;

    private final ArrayList<TextButton> difficultyButtons = new ArrayList<TextButton>();
    private Label difficultyDesc;

    public MenuScreen(GameMain game) {
        this.game = game;
        this.res = game.getRes();
        // 复用 GameMain 的统一 Viewport 和共用 SpriteBatch
        this.stage = new Stage(game.getViewport(), game.getBatch());
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        // 避开 Android 安全区（状态栏 / 刘海 / 手势条）
        float[] inset = Res.safeInsets(game.getViewport());
        root.pad(40f + inset[1], 40f + inset[0], 40f + inset[3], 40f + inset[2]);
        stage.addActor(root);

        Label title = new Label("回合制卡牌对战", res.labelStyle(res.fontTitle, Res.COLOR_ACCENT));
        title.setAlignment(Align.center);

        Label subtitle = new Label((game.isLandscape() ? "横屏" : "竖屏")
                + " · 回合制 · 战技点 / 能量 / 韧性击破",
                res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM));
        subtitle.setAlignment(Align.center);

        TextButton pveButton = new TextButton("人机对战", res.buttonStyle(res.fontBody));
        pveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.log(TAG, "进入角色选择");
                game.showCharacterSelect();
            }
        });

        TextButton pvpButton = new TextButton("联机对战", res.buttonStyle(res.fontBody));
        pvpButton.setDisabled(true);
        pvpButton.setTouchable(Touchable.disabled);   // 连按下的反馈都不给，彻底置灰

        Label pvpHint = new Label("（v2 预留，还没做）", res.labelStyle(res.fontTiny, Res.COLOR_DISABLED));
        pvpHint.setAlignment(Align.center);

        if (game.isLandscape()) {
            buildLandscapeUi(root, title, subtitle, pveButton, pvpButton, pvpHint);
            return;
        }

        root.add(title).padBottom(16f).row();
        root.add(subtitle).padBottom(56f).row();
        root.add(buildDifficultyRow()).padBottom(10f).row();
        difficultyDesc = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        difficultyDesc.setAlignment(Align.center);
        root.add(difficultyDesc).padBottom(40f).row();
        root.add(pveButton).width(460f).height(150f).padBottom(30f).row();
        root.add(pvpButton).width(460f).height(150f).row();
        root.add(pvpHint).padTop(10f).padBottom(50f).row();
        root.add(buildStatsLabel()).row();
    }

    /**
     * 横屏排版。
     *
     * 竖着排下来要 962 个世界单位高，横屏只有 720，内容会被挤掉。
     * 所以改成左右两栏 —— 左边是"这游戏是干嘛的"，右边是"点哪个按钮"。
     */
    private void buildLandscapeUi(Table root, Label title, Label subtitle,
                                  TextButton pveButton, TextButton pvpButton, Label pvpHint) {
        Table left = new Table();
        left.add(title).padBottom(10f).row();
        left.add(subtitle).padBottom(30f).row();
        left.add(buildStatsLabel()).row();

        difficultyDesc = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        difficultyDesc.setAlignment(Align.center);

        Table right = new Table();
        right.add(buildDifficultyRow()).padBottom(6f).row();
        right.add(difficultyDesc).padBottom(24f).row();
        right.add(pveButton).width(420f).height(140f).padBottom(18f).row();
        right.add(pvpButton).width(420f).height(140f).row();
        right.add(pvpHint).padTop(8f);

        root.add(left).growX().top().padRight(20f);
        root.add(right).width(560f).top();
    }

    /** 难度三选一 */
    private Table buildDifficultyRow() {
        Table row = new Table();
        row.add(new Label("难度", res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM)))
                .padRight(14f);

        for (Difficulty d : Difficulty.values()) {
            final Difficulty picked = d;
            TextButton b = new TextButton(d.label, res.buttonStyle(res.fontSmall));
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    game.setDifficulty(picked);
                    refreshDifficultyButtons();
                }
            });
            difficultyButtons.add(b);
            row.add(b).width(150f).height(96f).padRight(8f);
        }

        // 先按当前难度摆一次
        int index = game.getDifficulty().ordinal();
        for (int i = 0; i < difficultyButtons.size(); i++) {
            difficultyButtons.get(i).setText(
                    (i == index ? "● " : "○ ") + Difficulty.values()[i].label);
        }
        if (difficultyDesc != null) {
            difficultyDesc.setText(game.getDifficulty().describe());
        }
        return row;
    }

    private void refreshDifficultyButtons() {
        int index = game.getDifficulty().ordinal();
        for (int i = 0; i < difficultyButtons.size(); i++) {
            difficultyButtons.get(i).setText(
                    (i == index ? "● " : "○ ") + Difficulty.values()[i].label);
        }
        difficultyDesc.setText(game.getDifficulty().describe());
    }

    /** 把"加载到了多少东西"直接写在菜单上，这样不接控制台也能一眼看到缺图情况 */
    private Label buildStatsLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("卡牌 ").append(game.getCards().size()).append(" 张");

        CharacterGenerator.Result stats = game.getCharacterStats();
        if (stats == null) {
            sb.append("　角色 ").append(game.getCharacters().size()).append(" 个");
        } else {
            sb.append("　角色 ").append(stats.characters.size()).append(" 个")
                    .append("（立绘匹配 ").append(stats.matched)
                    .append(" / 缺 ").append(stats.missing).append("）");
            if (stats.usedFilenameFallback) {
                sb.append("\n⚠ characters.json 读不出来，用的是文件名兜底的默认角色");
            }
        }

        Label label = new Label(sb.toString(),
                res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        label.setAlignment(Align.center);
        return label;
    }

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
