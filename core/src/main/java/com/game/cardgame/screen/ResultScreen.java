// /home/djx/game/core/src/main/java/com/game/cardgame/screen/ResultScreen.java
package com.game.cardgame.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import com.game.cardgame.GameMain;
import com.game.cardgame.model.Character;

import java.util.ArrayList;
import com.game.cardgame.model.GameState;
import com.game.cardgame.util.Res;

/**
 * 战斗结算。
 *
 * 只有三件事：显示胜负、再来一局、回主菜单。
 *
 * "再来一局"用的是【同一对角色】重开，牌库会重新洗（GameMain.buildDeck() 每次都是新随机），
 * 所以每局抽牌顺序都不一样。
 * 想换角色就点"返回主菜单"重新选。
 */
public class ResultScreen extends ScreenAdapter {

    private static final String TAG = "ResultScreen";

    private final GameMain game;
    private final Res res;
    private final Stage stage;
    private final ArrayList<Character> playerConfigs;
    private final ArrayList<Character> enemyConfigs;
    private final GameState.Result result;

    public ResultScreen(GameMain game, ArrayList<Character> playerTeam,
                        ArrayList<Character> enemyTeam, GameState.Result result) {
        this.game = game;
        this.res = game.getRes();
        this.playerConfigs = new ArrayList<Character>(playerTeam);
        this.enemyConfigs = new ArrayList<Character>(enemyTeam);
        this.result = result;
        this.stage = new Stage(game.getViewport(), game.getBatch());
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        // 避开 Android 安全区（状态栏 / 刘海 / 手势条）
        float[] inset = Res.safeInsets(game.getViewport());
        float p = game.isLandscape() ? 24f : 40f;
        root.pad(p + inset[1], p + inset[0], p + inset[3], p + inset[2]);
        stage.addActor(root);

        boolean win = (result == GameState.Result.PLAYER_WIN);
        String titleText;
        String subText;
        if (win) {
            titleText = "胜  利";
            subText = "敌方 " + names(enemyConfigs) + " 全部倒下";
        } else if (result == GameState.Result.ENEMY_WIN) {
            titleText = "败  北";
            subText = "我方 " + names(playerConfigs) + " 全部倒下";
        } else {
            titleText = "未 分 胜 负";
            subText = "（战斗没有结束就被中断了）";
        }

        Label title = new Label(titleText, res.labelStyle(res.fontTitle,
                win ? Res.COLOR_ACCENT : Res.COLOR_HP));
        title.setAlignment(Align.center);

        Label sub = new Label(subText, res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM));
        sub.setAlignment(Align.center);

        // 两边各 3 个头像，一眼看出谁打谁
        Table avatars = new Table();
        avatars.add(buildAvatarRow(playerConfigs)).padRight(24f);
        avatars.add(new Label("VS", res.labelStyle(res.fontSmall, Res.COLOR_ACCENT)));
        avatars.add(buildAvatarRow(enemyConfigs)).padLeft(24f);

        Label names = new Label(names(playerConfigs) + "　VS　" + names(enemyConfigs),
                res.labelStyle(res.fontTiny, Res.COLOR_TEXT));
        names.setAlignment(Align.center);

        TextButton againButton = new TextButton("再来一局", res.buttonStyle(res.fontBody));
        againButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.log(TAG, "再来一局：" + names(playerConfigs)
                        + " vs " + names(enemyConfigs));
                game.startBattle(new ArrayList<Character>(playerConfigs));
            }
        });

        TextButton menuButton = new TextButton("返回主菜单", res.buttonStyle(res.fontBody));
        menuButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.showMenu();
            }
        });

        if (game.isLandscape()) {
            // 横屏：竖排下来要 858 高，只有 720，所以压紧间距并把两个按钮并排放
            Table buttons = new Table();
            buttons.add(againButton).width(380f).height(140f).padRight(20f);
            buttons.add(menuButton).width(380f).height(140f);

            root.add(title).padBottom(8f).row();
            root.add(sub).padBottom(20f).row();
            root.add(avatars).padBottom(12f).row();
            root.add(names).padBottom(24f).row();
            root.add(buttons);
            return;
        }

        root.add(title).padBottom(12f).row();
        root.add(sub).padBottom(40f).row();
        root.add(avatars).padBottom(16f).row();
        root.add(names).padBottom(60f).row();
        root.add(againButton).width(420f).height(140f).padBottom(30f).row();
        root.add(menuButton).width(420f).height(140f);
    }

    private Table buildAvatarRow(ArrayList<Character> team) {
        Table row = new Table();
        for (Character c : team) {
            // 完整显示，不裁切
            Image img = new Image(res.portrait(c.portraitFile));
            img.setScaling(Scaling.fit);
            row.add(img).size(110f, 130f).padRight(8f);
        }
        return row;
    }

    private static String names(ArrayList<Character> team) {
        StringBuilder sb = new StringBuilder();
        for (Character c : team) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(c.name);
        }
        return sb.toString();
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
