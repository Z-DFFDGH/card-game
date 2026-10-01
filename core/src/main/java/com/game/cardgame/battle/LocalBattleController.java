// /home/djx/game/core/src/main/java/com/game/cardgame/battle/LocalBattleController.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Fighter;
import com.game.cardgame.model.GameState;

import java.util.ArrayList;

/**
 * 单机（人机对战）的战斗控制器。持有 BattleSystem，并在这里实现 AI。
 *
 * 【3v3】两边各 3 个角色：
 *   - 行动条在一个 6 人名单上跑，轮到谁谁动；
 *   - 轮到【我方】的人 → 界面交给玩家点牌（isMyTurn() 为 true）；
 *   - 轮到【敌方】的人 → 界面调 playOpponentTurn() 让 AI 打。
 *
 * AI 决策顺序：
 *   1. 本方有人能量满 且 手里有终结技 → 插队放
 *   2. 当前行动者 HP 低于 40% 且 手里有治疗牌 → 治疗
 *   3. 否则攻击（优先伤害牌，其次减益牌）
 *   4. 上面都不成立 → 打一张能打的牌（兜底，避免明明手里有牌却空过回合）
 *   5. 真的没有能打的牌 → 结束回合
 *
 * 目标选择：
 *   伤害 / 减益 → 对面【血量百分比最低】的人（集中火力，先秒一个）
 *   治疗 / 护盾 → 我队【血量百分比最低】的人
 *   增益       → 当前行动者自己
 */
public class LocalBattleController implements BattleController {

    /** HP 低于这个比例就优先治疗 */
    public static final float HEAL_HP_THRESHOLD = 0.40f;

    /** 死循环保护：连续这么多次找不到能行动的人就停手 */
    private static final int MAX_SKIP_LOOP = 200;

    private final BattleSystem battle = new BattleSystem();
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();
    private final Difficulty difficulty;
    private final java.util.Random random = new java.util.Random();

    private boolean disposed;

    public LocalBattleController() {
        this(Difficulty.NORMAL);
    }

    public LocalBattleController(Difficulty difficulty) {
        this.difficulty = (difficulty == null) ? Difficulty.NORMAL : difficulty;
        battle.setLog(new BattleSystem.Log() {
            @Override
            public void line(String message) {
                emitLog(message);
            }
        });
    }

    // =======================================================================
    // BattleController 实现
    // =======================================================================

    @Override
    public void initialize(ArrayList<Character> playerTeam, ArrayList<Character> enemyTeam,
                           ArrayList<Card> deck) {
        battle.setup(playerTeam, enemyTeam, deck, difficulty.enemyActionScale);
        beginNextTurn();
    }

    @Override
    public boolean canPlay(Card card) {
        return battle.canPlay(card);
    }

    @Override
    public boolean playCard(Card card, Fighter target) {
        boolean ok = battle.playCard(card, target);
        if (ok) {
            notifyStateChanged();
        }
        return ok;
    }

    @Override
    public Fighter pickTarget(Card card) {
        GameState state = battle.getState();
        if (state == null || state.currentActor == null || card == null) {
            return null;
        }
        return targetFor(state.currentActor, card);
    }

    @Override
    public boolean canUseUltimate(Card card) {
        GameState state = battle.getState();
        if (state == null || card == null || state.isFinished()) {
            return false;
        }
        if (card.type != Card.Type.ULTIMATE || !state.hand.contains(card)) {
            return false;
        }
        // "能量满可随时插队"：只要【我方】有人能量满就能放，不限于当前行动者
        return battle.findReadyUltimateCaster(state.playerTeam) != null;
    }

    @Override
    public boolean useUltimate(Card card, Fighter target) {
        GameState state = battle.getState();
        if (state == null) {
            return false;
        }
        // 释放者：优先当前行动者（如果它能量满），否则我方第一个能量满的
        Fighter caster = state.currentActor;
        if (caster == null || caster.isDead() || !state.isPlayerSide(caster)
                || state.playerTeam == null || caster.energy < EnergyManager.MAX) {
            caster = battle.findReadyUltimateCaster(state.playerTeam);
        }
        boolean ok = battle.useUltimate(caster, card, target);
        if (ok) {
            notifyStateChanged();
        }
        return ok;
    }

    @Override
    public void endTurn() {
        battle.endTurn();
        notifyStateChanged();

        if (battle.getState().isFinished()) {
            notifyBattleEnd();
            return;
        }
        beginNextTurn();
    }

    @Override
    public GameState getState() {
        return battle.getState();
    }

    @Override
    public void setState(GameState state) {
        battle.setState(state);
        notifyStateChanged();
    }

    @Override
    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public boolean isMyTurn() {
        GameState state = battle.getState();
        return state != null && !state.isFinished()
                && state.currentActor != null
                && state.isPlayerSide(state.currentActor);
    }

    @Override
    public boolean isHost() {
        return true;   // 单机永远是"主机"
    }

    @Override
    public void dispose() {
        listeners.clear();
        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /** 给测试和调试用：直接拿到规则层 */
    public BattleSystem getBattleSystem() {
        return battle;
    }

    // =======================================================================
    // 回合推进
    // =======================================================================

    /**
     * 推进到"下一位能正常行动的人"。
     * 被冻结的、以及回合开始就倒下的，都会被自动跳过（跳过也算消耗掉了那一回合）。
     */
    private void beginNextTurn() {
        for (int guard = 0; guard < MAX_SKIP_LOOP; guard++) {
            Fighter actor = battle.advanceToNextActor();
            if (actor == null) {
                battle.checkEnd();
                notifyStateChanged();
                if (battle.getState().isFinished()) {
                    notifyBattleEnd();
                }
                return;
            }
            emitLog("");
            emitLog("【第 " + battle.getState().turnCount + " 次行动】"
                    + (battle.getState().isPlayerSide(actor) ? "我方" : "敌方")
                    + " " + actor.config.name + " 行动（行动值归零）");
            notifyStateChanged();

            boolean canAct = battle.beginTurn(actor);
            notifyStateChanged();

            if (battle.getState().isFinished()) {
                notifyBattleEnd();
                return;
            }
            if (canAct) {
                return;
            }
            battle.endTurn();
            notifyStateChanged();
            if (battle.getState().isFinished()) {
                notifyBattleEnd();
                return;
            }
        }
        emitLog("异常：连续 " + MAX_SKIP_LOOP + " 次都没找到能行动的人，强制停手");
    }

    // =======================================================================
    // AI
    // =======================================================================

    /**
     * 让 AI 替【当前行动者】打完这个回合，并结算"回合结束"。
     * 【不】推进行动条 —— 推进由 advanceToNextTurn() 负责。
     */
    public void autoPlayTurn() {
        GameState state = battle.getState();
        if (state == null || state.isFinished() || state.currentActor == null) {
            return;
        }
        Fighter actor = state.currentActor;
        if (actor.isDead()) {
            return;
        }

        // 1) 本方有人能量满 + 手里有终结技 → 插队放（低难度不会放，这是最大的难度旋钮）
        Card ultimate = findUltimate(state.hand);
        Fighter caster = battle.findReadyUltimateCaster(state.teamOf(actor));
        if (difficulty.aiUsesUltimate && ultimate != null && caster != null) {
            emitLog("  AI：能量已满（" + caster.energy + "/" + EnergyManager.MAX
                    + "，" + caster.config.name + "）且手里有终结技 → 插队释放");
            battle.useUltimate(caster, ultimate, aiTargetFor(caster, ultimate));
            notifyStateChanged();
            if (state.isFinished()) {
                notifyBattleEnd();
                return;
            }
        }

        // 2/3/4) 选一张牌打
        Card choice = chooseCard(actor, state.hand);
        if (choice == null && battle.refreshHandIfStuck(actor, difficulty.aiUsesUltimate)) {
            notifyStateChanged();
            choice = chooseCard(actor, state.hand);
        }
        if (choice == null) {
            emitLog("  AI：手里没有能打的牌（" + battle.handNames() + "）→ 直接结束回合");
        } else {
            battle.playCard(choice, aiTargetFor(actor, choice));
            notifyStateChanged();
        }

        battle.endTurn();
        notifyStateChanged();
        if (state.isFinished()) {
            notifyBattleEnd();
        }
    }

    /** 推进行动条，把回合交给下一位能正常行动的人 */
    public void advanceToNextTurn() {
        GameState state = battle.getState();
        if (state == null || state.isFinished()) {
            return;
        }
        beginNextTurn();
    }

    @Override
    public void playOpponentTurn() {
        autoPlayTurn();
        GameState state = battle.getState();
        if (state == null || state.isFinished()) {
            return;
        }
        advanceToNextTurn();
    }

    /** AI 选牌 */
    public Card chooseCard(Fighter actor, ArrayList<Card> hand) {
        boolean lowHp = actor.hp * 1.0f / actor.getMaxHp() < HEAL_HP_THRESHOLD;

        if (lowHp) {
            Card heal = firstPlayable(hand, Card.EffectType.HEAL);
            if (heal != null) {
                emitLog("  AI：" + actor.config.name + " hp " + actor.hp + "/"
                        + actor.getMaxHp() + " 低于 "
                        + (int) (HEAL_HP_THRESHOLD * 100) + "% → 优先治疗");
                return heal;
            }
        }

        Card damage = firstPlayable(hand, Card.EffectType.DAMAGE);
        if (damage != null) {
            return damage;
        }
        Card debuff = firstPlayable(hand, Card.EffectType.DEBUFF);
        if (debuff != null) {
            return debuff;
        }

        Card.EffectType[] fallback = {
                Card.EffectType.SHIELD, Card.EffectType.BUFF,
                Card.EffectType.DRAW, Card.EffectType.HEAL};
        for (Card.EffectType type : fallback) {
            Card c = firstPlayable(hand, type);
            if (c != null) {
                emitLog("  AI：没有攻击牌 → 退而求其次打 " + type);
                return c;
            }
        }
        return null;
    }

    /**
     * 目标选择。
     * 伤害/减益 → 对面血最少的（集中火力）；治疗/护盾 → 我队血最少的；增益 → 自己。
     */
    public Fighter targetFor(Fighter actor, Card card) {
        GameState state = battle.getState();
        if (state == null || actor == null || card == null) {
            return null;
        }
        switch (card.effectType) {
            case HEAL:
            case SHIELD: {
                Fighter ally = GameState.lowestHpRatio(state.teamOf(actor));
                return (ally != null) ? ally : actor;
            }
            case BUFF:
            case DRAW:
                return actor;
            default: {
                Fighter foe = GameState.lowestHpRatio(state.opposingTeamOf(actor));
                return (foe != null) ? foe : actor;
            }
        }
    }

    /**
     * AI 自己选目标。和 pickTarget（给玩家用的、永远最优）不同，
     * 低难度会随机挑，高难度才集中火力。
     */
    private Fighter aiTargetFor(Fighter actor, Card card) {
        if (difficulty.aiTargetsWeakest) {
            return targetFor(actor, card);
        }
        GameState state = battle.getState();
        ArrayList<Fighter> pool;
        switch (card.effectType) {
            case HEAL:
            case SHIELD:
            case BUFF:
            case DRAW:
                pool = GameState.aliveOf(state.teamOf(actor));
                break;
            default:
                pool = GameState.aliveOf(state.opposingTeamOf(actor));
                break;
        }
        if (pool.isEmpty()) {
            return targetFor(actor, card);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private Card firstPlayable(ArrayList<Card> hand, Card.EffectType type) {
        for (Card c : hand) {
            if (c.effectType == type && battle.canPlay(c)) {
                return c;
            }
        }
        return null;
    }

    private static Card findUltimate(ArrayList<Card> hand) {
        for (Card c : hand) {
            if (c.type == Card.Type.ULTIMATE) {
                return c;
            }
        }
        return null;
    }

    // =======================================================================
    // 事件广播
    // =======================================================================

    private void emitLog(String message) {
        for (Listener l : listeners) {
            l.onLog(message);
        }
    }

    private void notifyStateChanged() {
        GameState state = battle.getState();
        for (Listener l : listeners) {
            l.onStateChanged(state);
        }
    }

    private void notifyBattleEnd() {
        GameState state = battle.getState();
        for (Listener l : listeners) {
            l.onBattleEnd(state.result);
        }
    }
}
