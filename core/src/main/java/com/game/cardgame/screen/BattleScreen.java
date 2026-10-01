// /home/djx/game/core/src/main/java/com/game/cardgame/screen/BattleScreen.java
package com.game.cardgame.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import com.game.cardgame.GameMain;
import com.game.cardgame.battle.ActionOrderManager;
import com.game.cardgame.battle.BattleController;
import com.game.cardgame.battle.Difficulty;
import com.game.cardgame.battle.EnergyManager;
import com.game.cardgame.battle.LocalBattleController;
import com.game.cardgame.battle.SkillPointManager;
import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Fighter;
import com.game.cardgame.model.GameState;
import com.game.cardgame.model.StatusEffect;
import com.game.cardgame.util.Res;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 战斗界面（3v3）。
 *
 * ★ 硬性约束：本类【只通过 BattleController 接口】操作战斗，
 *   不 import、不 new、也不 instanceOf BattleSystem。
 *
 * 【布局】除了手牌区，每行都不写死高度，由内容自己撑 —— 写死高度会被内容压掉。
 *   手牌区用 grow() 吃掉剩下的空间，四边留出 Android 安全区。
 *
 * 【玩家也能放大招】终结技不能当普通牌打，必须走 useUltimate。
 *   我方任意一人能量满，手里的终结技就能点（对应"满可随时插队"），
 *   而且不占用每回合 1 张牌的名额。
 */
public class BattleScreen extends ScreenAdapter {

    private static final String TAG = "BattleScreen";

    /** 敌方思考时间，纯粹为了让人看清，不影响规则 */
    private static final float ENEMY_THINK_DELAY = 0.75f;

    /** 卡牌面板尺寸 */
    private static final float CARD_W = 210f;
    private static final float CARD_H = 280f;

    /**
     * 队伍面板里每个单位的头像格子尺寸。
     *
     * 【为什么不再用"居中裁切填满"】
     *   素材的宽高比从 0.40 到 2.20 都有，其中 14 个角色是方图(≈1.00)。
     *   把方图裁进一个 56:98 的竖格子，只能露出 57%；那张 2.20 的超宽图只露 26%。
     *   也就是"部分角色被遮住了"。
     *   现在改成：格子尺寸固定（每行高度一致、整齐），图片在里面【完整显示】——
     *   宁可小一点、留点空白，也不让角色被裁掉。
     */
    private static final float UNIT_PORTRAIT_W = 104f;
    private static final float UNIT_PORTRAIT_H = 112f;

    /**
     * 横屏用的队伍头像尺寸。
     *
     * 横屏高度只有 720，比竖屏的 1280 少一半，两排队伍 + 手牌纵向挤不下。
     * 所以横屏把"状态文字"塞进头像右边的信息列里（竖屏是放在头像下面的），
     * 省下来的高度全部给头像 —— 横屏的头像反而比竖屏大。
     */
    private static final float LAND_UNIT_PORTRAIT_W = 130f;
    private static final float LAND_UNIT_PORTRAIT_H = 124f;

    /** 手牌区最小高度 */
    private static final float HAND_MIN_H = 280f;

    private enum Phase { PLAYER, ENEMY, DONE }

    /** 一个单位在界面上对应的一组控件，刷新时按人找 */
    private static class UnitWidgets {
        Image background;
        Bar hpBar;
        Bar energyBar;
        Bar toughBar;
        Label nameLabel;
        Label statusLabel;
    }

    private final GameMain game;
    private final Res res;
    private final Stage stage;
    private final BattleController controller;
    private final ArrayList<Character> playerConfigs;
    private final ArrayList<Character> enemyConfigs;

    private Phase phase = Phase.PLAYER;
    private float thinkTimer;
    private boolean leaving;

    private Table root;
    private boolean layoutChecked;

    private final HashMap<Fighter, UnitWidgets> unitWidgets = new HashMap<Fighter, UnitWidgets>();
    private final HashMap<Fighter, Bar> actionBars = new HashMap<Fighter, Bar>();

    private Label skillPointLabel, actorLabel, hintLabel, teamHpLabel;
    private Table handTable;
    private TextButton endTurnButton;

    public BattleScreen(GameMain game, ArrayList<Character> playerTeam,
                        ArrayList<Character> enemyTeam, Difficulty difficulty) {
        this.game = game;
        this.res = game.getRes();
        this.playerConfigs = new ArrayList<Character>(playerTeam);
        this.enemyConfigs = new ArrayList<Character>(enemyTeam);
        this.stage = new Stage(game.getViewport(), game.getBatch());

        // 只有这一处创建具体实现。界面其余部分只认 BattleController 这个类型。
        LocalBattleController local = new LocalBattleController(difficulty);
        local.addListener(new BattleController.Listener() {
            @Override
            public void onLog(String message) {
                Gdx.app.log(TAG, message);
            }

            @Override
            public void onStateChanged(GameState state) {
            }

            @Override
            public void onBattleEnd(GameState.Result result) {
                Gdx.app.log(TAG, "战斗结束：" + result);
            }
        });
        this.controller = local;

        controller.initialize(playerConfigs, enemyConfigs, game.buildDeck());

        buildUi();
        refreshAll();
        resolvePhase();
    }

    // =======================================================================
    // 搭界面
    // =======================================================================

    private void buildUi() {
        root = new Table();
        root.setFillParent(true);

        float[] inset = Res.safeInsets(game.getViewport());
        float base = game.isLandscape() ? 8f : 10f;
        root.pad(base + inset[1], base + inset[0], base + inset[3], base + inset[2]);
        stage.addActor(root);

        GameState s = controller.getState();

        if (game.isLandscape()) {
            // 竖屏那套竖着排需要 1072 高，横屏只有 720，硬排会被挤掉。
            // 横屏改成：行动条 / 敌方 / 我方 横着铺满，最下面一行是"战技点 | 手牌 | 结束回合"。
            root.add(buildActionBar()).growX().padBottom(4f).row();
            root.add(buildTeamPanel(s.enemyTeam, false)).growX().padBottom(4f).row();
            root.add(buildTeamPanel(s.playerTeam, true)).growX().padBottom(4f).row();
            root.add(buildLandscapeBottomRow()).growX().minHeight(HAND_MIN_H);
            return;
        }

        root.add(buildActionBar()).growX().padBottom(6f).row();
        root.add(buildTeamPanel(s.enemyTeam, false)).growX().padBottom(6f).row();
        root.add(buildCenterPanel()).growX().padBottom(6f).row();
        root.add(buildTeamPanel(s.playerTeam, true)).growX().padBottom(6f).row();
        root.add(buildHandPanel()).grow().minHeight(HAND_MIN_H).padBottom(6f).row();
        root.add(buildBottomBar()).growX();
    }

    /** 顶部行动条：6 个单位各一个小头像 + "离行动还有多远"的进度条 */
    private Table buildActionBar() {
        Table bar = new Table();
        bar.setBackground(res.rect(new Color(0.09f, 0.10f, 0.14f, 1f)));
        bar.pad(6f);

        GameState s = controller.getState();
        boolean land = game.isLandscape();
        for (Fighter f : s.allUnits()) {
            Table slot = new Table();

            // 顶部小头像：居中裁切填满，保证 6 个大小一致
            Image avatar = new Image(new TextureRegionDrawable(
                    res.coverRegion(res.portrait(f.config.portraitFile),
                            land ? 26f : 34f, land ? 38f : 58f)));
            avatar.setScaling(Scaling.fit);

            Bar progress = new Bar(res.rect(new Color(0.16f, 0.18f, 0.24f, 1f)),
                    res.rect(s.isPlayerSide(f) ? Res.COLOR_ENERGY : Res.COLOR_HP));
            actionBars.put(f, progress);

            if (land) {
                // 横屏：头像在左、进度条在右，一整条只要 38 高（竖着放要 58+）
                slot.add(avatar).size(26f, 38f).padRight(6f);
                slot.add(progress).height(8f).growX();
            } else {
                slot.add(avatar).size(30f, 52f).row();
                slot.add(progress).height(6f).growX().padTop(3f);
            }

            bar.add(slot).growX().padRight(4f);
        }
        return bar;
    }

    /** 一队 3 个人的面板 */
    private Table buildTeamPanel(ArrayList<Fighter> team, boolean isPlayerSide) {
        Table panel = new Table();
        panel.setBackground(res.rect(new Color(0.09f, 0.10f, 0.14f, 1f)));
        panel.pad(6f);

        for (Fighter f : team) {
            panel.add(buildUnitColumn(f, isPlayerSide)).growX().padRight(4f);
        }
        return panel;
    }

    /** 一个单位一列：头像 + 名字 + 韧性条 + 血条 + 能量条 + 状态 */
    private Stack buildUnitColumn(Fighter f, boolean isPlayerSide) {
        UnitWidgets w = new UnitWidgets();
        boolean land = game.isLandscape();

        // 底层铺一张 Image 当背景（用 Image 而不是 Table 的 background，
        // 因为 Table 不会把自己作为命中目标返回）
        w.background = new Image(res.rect(Res.COLOR_PANEL));

        // 队伍里的头像：整张完整显示，不裁切 —— 格子大小固定，所以行高还是整齐的
        Image avatar = new Image(res.portrait(f.config.portraitFile));
        avatar.setScaling(Scaling.fit);

        Table info = new Table();
        info.left();

        w.nameLabel = new Label(f.config.name, res.labelStyle(res.fontTiny, Res.COLOR_TEXT));

        w.toughBar = new Bar(res.rect(new Color(0.16f, 0.18f, 0.24f, 1f)),
                res.rect(Res.COLOR_TOUGH));
        w.hpBar = new Bar(res.rect(new Color(0.16f, 0.18f, 0.24f, 1f)), res.rect(Res.COLOR_HP));
        w.energyBar = new Bar(res.rect(new Color(0.16f, 0.18f, 0.24f, 1f)),
                res.rect(Res.COLOR_ENERGY));

        w.statusLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));

        info.add(w.nameLabel).left().row();
        if (!isPlayerSide) {
            // 韧性条只画在敌方（玩家的弱点打击对象是敌人），放在血条上方
            info.add(w.toughBar).height(8f).growX().padTop(3f).row();
        }
        info.add(w.hpBar).height(12f).growX().padTop(3f).row();
        info.add(w.energyBar).height(7f).growX().padTop(3f).row();
        if (land) {
            // 横屏把状态挪到头像右边，省下一整行高度给头像
            info.add(w.statusLabel).left().padTop(4f).row();
        }

        Table top = new Table();
        top.add(avatar).size(land ? LAND_UNIT_PORTRAIT_W : UNIT_PORTRAIT_W,
                land ? LAND_UNIT_PORTRAIT_H : UNIT_PORTRAIT_H).padRight(8f);
        top.add(info).growX();

        Table content = new Table();
        content.pad(4f);
        content.add(top).growX();
        if (!land) {
            content.row();
            content.add(w.statusLabel).left().padTop(4f);
        }

        Stack stack = new Stack();
        stack.add(w.background);
        stack.add(content);

        unitWidgets.put(f, w);
        return stack;
    }

    /**
     * 横屏最下面一行：左边战技点/行动者/双方血量，中间手牌，右边结束回合。
     *
     * 竖屏这三样是上中下三块竖着排的（战技点在中间、结束回合在最底下），
     * 横屏把它们并成一排，一行只要 280 高，正好是一张牌的高度。
     */
    private Table buildLandscapeBottomRow() {
        Table row = new Table();

        skillPointLabel = new Label("", res.labelStyle(res.fontTitle, Res.COLOR_SKILLPOINT));
        skillPointLabel.setAlignment(Align.center);
        actorLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_ACCENT));
        actorLabel.setAlignment(Align.center);
        teamHpLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        teamHpLabel.setAlignment(Align.center);

        Table left = new Table();
        left.setBackground(res.rect(new Color(0.08f, 0.09f, 0.12f, 1f)));
        left.pad(8f);
        left.top();
        left.add(skillPointLabel).row();
        left.add(actorLabel).padTop(4f).row();
        left.add(teamHpLabel).padTop(4f).row();
        left.add().grow();

        endTurnButton = createEndTurnButton();
        hintLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        hintLabel.setAlignment(Align.center);
        hintLabel.setWrap(true);

        Table right = new Table();
        right.setBackground(res.rect(new Color(0.08f, 0.09f, 0.12f, 1f)));
        right.pad(8f);
        right.top();
        right.add(endTurnButton).width(220f).height(90f).row();
        right.add(hintLabel).width(200f).padTop(8f).row();
        right.add().grow();

        row.add(left).width(230f).growY();
        row.add(buildHandPanel()).grow().padLeft(6f).padRight(6f);
        row.add(right).width(230f).growY();
        return row;
    }

    /** 中间：战技点大数字 + 当前行动者 + 双方血量 */
    private Table buildCenterPanel() {
        Table panel = new Table();
        panel.setBackground(res.rect(new Color(0.08f, 0.09f, 0.12f, 1f)));
        panel.pad(6f);

        skillPointLabel = new Label("", res.labelStyle(res.fontTitle, Res.COLOR_SKILLPOINT));
        skillPointLabel.setAlignment(Align.center);

        actorLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_ACCENT));
        actorLabel.setAlignment(Align.center);

        teamHpLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        teamHpLabel.setAlignment(Align.center);

        panel.add(skillPointLabel).row();
        panel.add(actorLabel).padTop(2f).row();
        panel.add(teamHpLabel).padTop(2f);
        return panel;
    }

    private ScrollPane buildHandPanel() {
        handTable = new Table();

        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.background = res.rect(new Color(0.08f, 0.09f, 0.12f, 1f));
        style.hScroll = res.rect(new Color(0.10f, 0.11f, 0.14f, 1f));
        style.hScrollKnob = res.rect(new Color(0.36f, 0.40f, 0.48f, 1f));

        ScrollPane scroll = new ScrollPane(handTable, style);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(false, true);
        scroll.setOverscroll(false, false);
        scroll.setScrollbarsOnTop(true);
        return scroll;
    }

    private Table buildBottomBar() {
        Table bar = new Table();

        endTurnButton = createEndTurnButton();

        hintLabel = new Label("", res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        hintLabel.setAlignment(Align.center);

        bar.add(endTurnButton).width(210f).height(84f).padRight(16f);
        bar.add(hintLabel).growX().left();
        return bar;
    }

    /** 结束回合按钮。竖屏在底部横条里，横屏在右下角，按钮本身是同一个 */
    private TextButton createEndTurnButton() {
        TextButton button = new TextButton("结束回合", res.buttonStyle(res.fontSmall));
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onEndTurnClicked();
            }
        });
        return button;
    }

    // =======================================================================
    // 刷新
    // =======================================================================

    private void refreshAll() {
        rebuildHand();
        refreshDynamic();
    }

    private void refreshDynamic() {
        GameState s = controller.getState();
        if (s == null) {
            return;
        }

        for (Fighter f : s.allUnits()) {
            UnitWidgets w = unitWidgets.get(f);
            if (w == null) {
                continue;
            }

            boolean actor = (f == s.currentActor);

            // 当前行动者：整列背景换成亮色，一眼看出轮到谁
            Color bg;
            if (f.isDead()) {
                bg = new Color(0.06f, 0.06f, 0.08f, 1f);
            } else if (actor) {
                bg = Res.COLOR_PANEL_SEL;
            } else {
                bg = Res.COLOR_PANEL;
            }
            w.background.setDrawable(res.rect(bg));

            w.nameLabel.setText(f.config.name + (f.isDead() ? "（倒）" : ""));
            w.nameLabel.setColor(f.isDead() ? Res.COLOR_DISABLED : Res.COLOR_TEXT);

            w.hpBar.setRatio(f.hp / (float) f.getMaxHp());
            w.energyBar.setRatio(f.energy / (float) EnergyManager.MAX);
            if (w.toughBar != null) {
                w.toughBar.setRatio(f.toughness / (float) f.getMaxToughness());
            }

            String status;
            if (f.isDead()) {
                status = "已倒下";
            } else if (f.debuffs.isEmpty()) {
                status = "hp " + f.hp + "/" + f.getMaxHp() + "　能量 " + f.energy;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("hp ").append(f.hp).append("　");
                for (StatusEffect e : f.debuffs) {
                    sb.append(e.type.label).append("(").append(e.duration).append(") ");
                }
                status = sb.toString().trim();
            }
            w.statusLabel.setText(status);
            w.statusLabel.setColor(f.isDead() ? Res.COLOR_HP : Res.COLOR_TEXT_DIM);

            Bar ab = actionBars.get(f);
            if (ab != null) {
                ab.setRatio(actionRatio(f));
            }
        }

        skillPointLabel.setText(String.valueOf(s.skillPoints));

        if (s.currentActor != null) {
            actorLabel.setText("第 " + s.turnCount + " 次行动　→　"
                    + (s.isPlayerSide(s.currentActor) ? "我方" : "敌方")
                    + "　" + s.currentActor.config.name);
        }
        teamHpLabel.setText("我方 " + pct(GameState.teamHpRatio(s.playerTeam))
                + "　敌方 " + pct(GameState.teamHpRatio(s.enemyTeam))
                + "　战技点上限 " + SkillPointManager.MAX);

        endTurnButton.setDisabled(phase != Phase.PLAYER);
        endTurnButton.setText(phase == Phase.PLAYER ? "结束回合" : "敌方行动中");
    }

    private static String pct(float r) {
        return (int) Math.round(r * 100) + "%";
    }

    private static float actionRatio(Fighter f) {
        return 1f - f.actionValue / ActionOrderManager.BAR_LENGTH;
    }

    private void rebuildHand() {
        handTable.clear();
        GameState s = controller.getState();
        ArrayList<Card> hand = s.hand;

        if (hand.isEmpty()) {
            handTable.add(new Label("（手牌为空）", res.labelStyle(res.fontSmall, Res.COLOR_TEXT_DIM)));
            return;
        }
        for (Card c : hand) {
            handTable.add(buildCardPanel(c)).width(CARD_W).height(CARD_H).padRight(10f);
        }
    }

    private Actor buildCardPanel(final Card c) {
        boolean ultimate = (c.type == Card.Type.ULTIMATE);
        boolean usable = ultimate
                ? controller.canUseUltimate(c)
                : ((phase == Phase.PLAYER) && controller.canPlay(c));

        Color bgColor;
        if (usable && ultimate) {
            bgColor = new Color(0.26f, 0.22f, 0.10f, 1f);   // 大招可用：偏金色
        } else if (usable) {
            bgColor = Res.COLOR_PANEL;
        } else {
            bgColor = new Color(0.09f, 0.10f, 0.12f, 1f);
        }
        Image background = new Image(res.rect(bgColor));

        Table content = new Table();
        content.pad(8f);
        content.top();

        Color nameColor = usable ? Res.COLOR_TEXT : Res.COLOR_DISABLED;
        Color mainColor = usable ? Res.COLOR_ACCENT : Res.COLOR_DISABLED;

        Label name = new Label(c.name, res.labelStyle(res.fontTiny, nameColor));
        name.setWrap(true);
        name.setAlignment(Align.center);

        Label cost = new Label((ultimate ? "★" : "") + typeLabel(c.type) + "　费用 " + c.cost,
                res.labelStyle(res.fontTiny, mainColor));
        cost.setAlignment(Align.center);

        Label effect = new Label(c.effectType + " " + c.effectValue + "　" + c.attribute.label,
                res.labelStyle(res.fontTiny, mainColor));
        effect.setAlignment(Align.center);

        Label desc = new Label(c.description, res.labelStyle(res.fontTiny, Res.COLOR_TEXT_DIM));
        desc.setWrap(true);
        desc.setAlignment(Align.center);

        content.add(name).width(CARD_W - 24f).row();
        content.add(cost).padTop(6f).row();
        content.add(effect).padTop(3f).row();
        content.add(desc).width(CARD_W - 24f).padTop(6f).row();

        if (!usable) {
            content.add(new Label(blockReason(c), res.labelStyle(res.fontTiny, Res.COLOR_HP)))
                    .padTop(6f).row();
        } else if (ultimate) {
            content.add(new Label("点它就能放", res.labelStyle(res.fontTiny, Res.COLOR_ACCENT)))
                    .padTop(6f).row();
        }

        Stack stack = new Stack();
        stack.add(background);
        stack.add(content);

        if (usable) {
            stack.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onCardClicked(c);
                }
            });
        } else {
            stack.setTouchable(Touchable.disabled);
        }
        return stack;
    }

    private String blockReason(Card c) {
        GameState s = controller.getState();
        if (c.type == Card.Type.ULTIMATE) {
            return "（我方没人能量满）";
        }
        if (phase != Phase.PLAYER) {
            return "（不是你的回合）";
        }
        if (c.type == Card.Type.SKILL && s.skillPoints < SkillPointManager.SKILL_COST) {
            return "（战技点不足）";
        }
        return "（本回合已出过牌）";
    }

    private static String typeLabel(Card.Type type) {
        switch (type) {
            case NORMAL:
                return "普攻";
            case SKILL:
                return "战技";
            default:
                return "终结技";
        }
    }

    // =======================================================================
    // 回合流程
    // =======================================================================

    private void resolvePhase() {
        GameState s = controller.getState();
        if (s.isFinished()) {
            leaveToResult();
            return;
        }
        if (controller.isMyTurn()) {
            phase = Phase.PLAYER;
            hintLabel.setText("轮到 " + s.currentActor.config.name + "：点一张牌出牌"
                    + "（我方能量满时终结技随时可点）");
        } else {
            phase = Phase.ENEMY;
            thinkTimer = 0f;
            hintLabel.setText("敌方 " + s.currentActor.config.name + " 行动中…");
        }
        refreshAll();
    }

    private void onCardClicked(Card card) {
        // 终结技：随时可放，不结束回合、不占出牌名额
        if (card.type == Card.Type.ULTIMATE) {
            if (!controller.canUseUltimate(card)) {
                return;
            }
            controller.useUltimate(card, controller.pickTarget(card));
            if (controller.getState().isFinished()) {
                leaveToResult();
                return;
            }
            refreshAll();
            return;
        }

        if (phase != Phase.PLAYER) {
            return;
        }
        if (!controller.canPlay(card)) {
            hintLabel.setText("这张牌现在打不出去");
            return;
        }
        controller.playCard(card, controller.pickTarget(card));

        if (controller.getState().isFinished()) {
            leaveToResult();
            return;
        }
        // 每回合最多 1 张牌，出完就自动过回合
        controller.endTurn();
        resolvePhase();
    }

    private void onEndTurnClicked() {
        if (phase != Phase.PLAYER) {
            return;
        }
        controller.endTurn();
        resolvePhase();
    }

    private void runEnemyTurn() {
        controller.playOpponentTurn();
        resolvePhase();
    }

    private void leaveToResult() {
        if (leaving) {
            return;
        }
        leaving = true;
        phase = Phase.DONE;
        GameState s = controller.getState();
        game.switchScreen(new ResultScreen(game, playerConfigs, enemyConfigs, s.result));
    }

    // =======================================================================
    // Screen 生命周期
    // =======================================================================

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(Res.COLOR_BG.r, Res.COLOR_BG.g, Res.COLOR_BG.b, Res.COLOR_BG.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (phase == Phase.ENEMY && !leaving) {
            thinkTimer += delta;
            if (thinkTimer >= ENEMY_THINK_DELAY) {
                thinkTimer = 0f;
                runEnemyTurn();
            }
        }
        if (leaving) {
            return;
        }

        refreshDynamic();
        stage.act(delta);
        stage.draw();

        checkLayoutOnce();
    }

    private void checkLayoutOnce() {
        if (layoutChecked || root.getHeight() <= 0f) {
            return;
        }
        layoutChecked = true;
        float need = root.getPrefHeight();
        float have = root.getHeight();
        if (need > have + 1f) {
            Gdx.app.error(TAG, "布局警告：内容需要 " + (int) need + " 世界单位，"
                    + "但只有 " + (int) have + "，会有内容被挤掉。");
        } else {
            Gdx.app.log(TAG, "布局自检通过：内容需要 " + (int) need
                    + "，可用 " + (int) have + "（留白 " + (int) (have - need) + "）");
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        // 尺寸变了要重新自检一次：手机横竖比差别很大，换个窗口尺寸内容就可能塞不下
        layoutChecked = false;
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
        controller.dispose();
        stage.dispose();
    }

    // =======================================================================
    // 一根进度条。libGDX 没有皮肤文件就没有 ProgressBar，所以自己画
    // =======================================================================
    private static class Bar extends Actor {

        private final Drawable background;
        private final Drawable fill;
        private float ratio = 1f;

        Bar(Drawable background, Drawable fill) {
            this.background = background;
            this.fill = fill;
        }

        void setRatio(float r) {
            this.ratio = Math.max(0f, Math.min(1f, r));
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color c = getColor();
            batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
            background.draw(batch, getX(), getY(), getWidth(), getHeight());
            if (ratio > 0f) {
                fill.draw(batch, getX(), getY(), getWidth() * ratio, getHeight());
            }
        }
    }
}
