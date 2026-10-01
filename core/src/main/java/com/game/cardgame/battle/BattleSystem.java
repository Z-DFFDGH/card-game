// /home/djx/game/core/src/main/java/com/game/cardgame/battle/BattleSystem.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Fighter;
import com.game.cardgame.model.GameState;
import com.game.cardgame.model.StatusEffect;

import java.util.ArrayList;

/**
 * 战斗结算调度。纯逻辑：不碰图形、不碰 Android、不碰网络。
 *
 * 流程：
 *   setup()                建两队、发初始手牌、行动条重置
 *   advanceToNextActor()   推进行动条，返回下一个行动者（跳过倒下的）
 *   beginTurn(actor)       回合开始处理（击破回满 / 灼烧掉血 / 冻结跳过）
 *   playCard(card, target) 出一张牌并结算
 *   endTurn()              回合结束处理（状态倒计时、行动条重置、判定胜负）
 *
 * 【阶段 6：1v1 → 3v3】
 *   - 行动条现在在一个 6 人名单上跑，倒下的自动跳过。
 *   - 胜负按【队伍】判定：一边全倒就结束。
 *   - 出牌的目标由调用方（LocalBattleController.pickTarget）决定。
 */
public final class BattleSystem {

    /** 日志回调。不需要日志就不设置，内部会判空 */
    public interface Log {
        void line(String message);
    }

    /** 开局手牌张数 */
    public static final int INITIAL_HAND = 5;

    /** 手牌上限，超出的弃掉最早抽到的 */
    public static final int HAND_LIMIT = 8;

    /** 一手牌打空后从牌库重抽的张数 */
    public static final int HAND_REFILL = 5;

    /** 每回合最多出几张牌 */
    public static final int CARDS_PER_TURN = 1;

    /** 击破状态下受到的伤害倍率 */
    public static final float BREAK_DAMAGE_MULTIPLIER = 1.1f;

    /** 灼烧：每次发作掉多少血 */
    public static final int BURN_DAMAGE_PER_TICK = 3;

    /** 灼烧：持续几回合 */
    public static final int BURN_DURATION = 2;

    /** 冻结：持续几回合（也就是跳过几个回合） */
    public static final int FREEZE_DURATION = 1;

    private final ActionOrderManager actionOrder = new ActionOrderManager();
    private final SkillPointManager skillPoints = new SkillPointManager();
    private final EnergyManager energy = new EnergyManager();
    private final ToughnessManager toughness = new ToughnessManager();

    private GameState state;
    private Log log;

    /** 当前回合已经出了几张牌 */
    private int cardsPlayedThisTurn;

    /**
     * 当前行动者在【本回合开始时】身上是否已经带着临时攻击加成。
     * 用来实现"±攻击力 buff 持续 1 回合"：
     *   - 如果回合开始时就有，说明这个回合用掉了它 → 回合结束时清零；
     *   - 如果是本回合内刚加上的，说明还没用过 → 留到下一个回合，等那时再清。
     */
    private boolean tempBuffActiveAtTurnStart;

    // =======================================================================
    // 基础设施
    // =======================================================================

    public void setLog(Log log) {
        this.log = log;
    }

    public GameState getState() {
        return state;
    }

    /** 联机用：把外部（对面传来的）状态直接装进来 */
    public void setState(GameState state) {
        this.state = state;
    }

    public ActionOrderManager getActionOrder() {
        return actionOrder;
    }

    public SkillPointManager getSkillPoints() {
        return skillPoints;
    }

    public EnergyManager getEnergy() {
        return energy;
    }

    public ToughnessManager getToughness() {
        return toughness;
    }

    public boolean canPlayAnotherCard() {
        return cardsPlayedThisTurn < CARDS_PER_TURN;
    }

    private void say(String message) {
        if (log != null) {
            log.line(message);
        }
    }

    // =======================================================================
    // 开局
    // =======================================================================

    /**
     * 建战斗。
     *
     * @param deck 牌库。按传进来的顺序抽牌，【不洗牌】——洗牌是上面那层的事。
     */
    public void setup(ArrayList<Character> playerConfigs, ArrayList<Character> enemyConfigs,
                      ArrayList<Card> deck) {
        setup(playerConfigs, enemyConfigs, deck, 1.0f);
    }

    /**
     * @param enemyActionScale 敌方开局行动值倍率。<1 表示敌方行动值更小 → 敌方先手（高难度用）
     */
    public void setup(ArrayList<Character> playerConfigs, ArrayList<Character> enemyConfigs,
                      ArrayList<Card> deck, float enemyActionScale) {
        ArrayList<Fighter> playerTeam = new ArrayList<Fighter>();
        for (Character c : playerConfigs) {
            playerTeam.add(new Fighter(c));
        }
        ArrayList<Fighter> enemyTeam = new ArrayList<Fighter>();
        for (Character c : enemyConfigs) {
            enemyTeam.add(new Fighter(c));
        }

        state = new GameState(playerTeam, enemyTeam);
        state.turnCount = 0;              // advanceToNextActor 里会 +1，第一次行动就是第 1 回
        state.drawPile.addAll(deck);
        state.hand.clear();
        state.discardPile.clear();

        skillPoints.init(state);
        actionOrder.resetAll(state.allUnits());

        // 难度旋钮：敌方开局行动值 × 系数。<1 就是让它先手
        if (enemyActionScale != 1.0f) {
            for (Fighter f : state.enemyTeam) {
                f.actionValue *= enemyActionScale;
            }
            say("难度参数：敌方开局行动值 × " + enemyActionScale
                    + "（" + (enemyActionScale < 1f ? "敌方先手" : "敌方后手") + "）");
        }
        cardsPlayedThisTurn = 0;
        tempBuffActiveAtTurnStart = false;

        say("牌库装载 " + deck.size() + " 张牌");
        say("我方：" + names(playerTeam) + "　敌方：" + names(enemyTeam));
        drawCards(INITIAL_HAND);
    }

    private static String names(ArrayList<Fighter> team) {
        StringBuilder sb = new StringBuilder();
        for (Fighter f : team) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(f.config.name);
        }
        return sb.toString();
    }

    // =======================================================================
    // 回合推进
    // =======================================================================

    /** 推进行动条，返回下一个行动者（倒下的会被跳过）。全倒时返回 null */
    public Fighter advanceToNextActor() {
        Fighter actor = actionOrder.nextActor(state.allUnits());
        state.currentActor = actor;
        if (actor != null) {
            state.turnCount++;
        }
        return actor;
    }

    /**
     * 回合开始处理。
     *
     * @return true = 可以正常行动；false = 本回合被跳过（冻结 / 已经倒下）
     */
    public boolean beginTurn(Fighter actor) {
        cardsPlayedThisTurn = 0;
        tempBuffActiveAtTurnStart = (actor.tempAtkBuff != 0);

        if (actor.isDead()) {
            return false;   // 已经倒下了，直接跳过
        }

        say("── 回合开始 " + actor.config.name
                + "（" + sideName(actor) + "，hp " + actor.hp + "/" + actor.getMaxHp()
                + "，能量 " + actor.energy + "）");

        // 1) 击破恢复：自己回合开始时韧性回满、击破解除
        if (actor.broken) {
            int before = actor.toughness;
            toughness.refillAtTurnStart(actor);
            say("   击破恢复：韧性 " + before + " → " + actor.toughness + "（回满），击破状态解除");
        }

        // 2) 灼烧：回合开始掉血
        StatusEffect burn = actor.getDebuff(StatusEffect.Type.BURN);
        if (burn != null) {
            int lost = actor.applyHpDamage(burn.value);
            say("   灼烧发作：掉 " + lost + " 血 → hp " + actor.hp + "/" + actor.getMaxHp());
        }

        if (actor.isDead()) {
            say("   " + actor.config.name + " 倒下了");
            checkEnd();
            return false;
        }

        // 3) 冻结：跳过本回合
        if (actor.hasDebuff(StatusEffect.Type.FREEZE)) {
            say("   被冻结：跳过本回合");
            return false;
        }

        return true;
    }

    private String sideName(Fighter f) {
        return state.isPlayerSide(f) ? "我方" : "敌方";
    }

    /**
     * 回合结束处理。
     * 注意顺序：先判断临时攻击加成该不该清，再 tick() 把状态持续时间 -1。
     */
    public void endTurn() {
        Fighter actor = state.currentActor;
        if (actor != null) {
            if (tempBuffActiveAtTurnStart && actor.tempAtkBuff != 0) {
                say("   临时攻击加成到期：" + actor.tempAtkBuff + " → 0");
                actor.tempAtkBuff = 0;
            }
            actor.tick();                     // 状态持续时间 -1，到期的移除
            actionOrder.afterAction(actor);   // 行动值重置回满格
            say("   回合结束 " + actor.config.name
                    + "（减益 " + actor.debuffs + "）");
        }
        cardsPlayedThisTurn = 0;
        tempBuffActiveAtTurnStart = false;
        checkEnd();
    }

    // =======================================================================
    // 出牌
    // =======================================================================

    /** 能不能打这张牌（不看目标） */
    public boolean canPlay(Card card) {
        if (state == null || state.isFinished() || card == null) {
            return false;
        }
        if (state.currentActor == null || state.currentActor.isDead()) {
            return false;
        }
        if (!canPlayAnotherCard()) {
            return false;
        }
        if (!state.hand.contains(card)) {
            return false;
        }
        if (card.type == Card.Type.ULTIMATE) {
            return false;   // 终结技走 useUltimate()
        }
        return skillPoints.canAfford(state, card);
    }

    /** 出一张牌并结算。目标由调用方挑好 */
    public boolean playCard(Card card, Fighter target) {
        Fighter actor = state.currentActor;

        if (state.isFinished()) {
            say("   战斗已结束，不能再出牌");
            return false;
        }
        if (actor == null || actor.isDead()) {
            say("   当前行动者不合法");
            return false;
        }
        if (!canPlayAnotherCard()) {
            say("   本回合已经出过 " + CARDS_PER_TURN + " 张牌了");
            return false;
        }
        if (!state.hand.contains(card)) {
            say("   这张牌不在手牌里：" + card.name);
            return false;
        }
        if (card.type == Card.Type.ULTIMATE) {
            say("   终结技要用 useUltimate()，不能当普通牌打");
            return false;
        }
        if (!skillPoints.canAfford(state, card)) {
            say("   战技点不够（当前 " + state.skillPoints + "），打不出战技");
            return false;
        }
        if (target == null || target.isDead()) {
            say("   目标不合法");
            return false;
        }

        state.hand.remove(card);
        state.discardPile.add(card);
        cardsPlayedThisTurn++;

        say("   出牌 [" + card.id + "] " + card.name
                + "（" + actor.config.name + " → " + target.config.name
                + "，类型 " + card.type + "，属性 " + card.attribute.label
                + "，效果 " + card.effectType + "=" + card.effectValue + "）");

        applyEffect(actor, target, card);

        int pointDelta = skillPoints.applyCardCost(state, card);
        say("   战技点 " + (pointDelta >= 0 ? "+" : "") + pointDelta
                + " → " + state.skillPoints + "（上限 " + SkillPointManager.MAX + "）");

        int gained = energy.gainOnPlay(actor);
        say("   出牌回能 +" + gained + " → " + actor.config.name
                + " 能量 " + actor.energy + "/" + EnergyManager.MAX);

        if (state.hand.isEmpty()) {
            say("   手牌空了，从牌库重抽 " + HAND_REFILL + " 张");
            drawCards(HAND_REFILL);
        }

        checkEnd();
        return true;
    }

    /**
     * 放终结技。规则上"能量满可以随时插队"，所以：
     *   - 不占用每回合 1 张牌的名额；
     *   - 不改变战技点；
     *   - 只要求释放者能量满 + 牌在手里。
     */
    public boolean useUltimate(Fighter caster, Card card, Fighter target) {
        if (state.isFinished()) {
            say("   战斗已结束");
            return false;
        }
        if (caster == null || caster.isDead()) {
            say("   释放者不合法");
            return false;
        }
        if (card == null || card.type != Card.Type.ULTIMATE) {
            say("   这不是终结技卡");
            return false;
        }
        if (!state.hand.contains(card)) {
            say("   这张终结技不在手牌里：" + card.name);
            return false;
        }
        if (!energy.isFull(caster)) {
            say("   能量不满（" + caster.energy + "/" + EnergyManager.MAX + "），放不了终结技");
            return false;
        }
        if (target == null || target.isDead()) {
            say("   目标不合法");
            return false;
        }

        say("   插队放终结技 [" + card.id + "] " + card.name
                + "（" + caster.config.name + " → " + target.config.name
                + "，属性 " + card.attribute.label
                + "，效果 " + card.effectType + "=" + card.effectValue + "）"
                + " —— 不占本回合出牌名额，战技点不变");

        state.hand.remove(card);
        state.discardPile.add(card);

        applyEffect(caster, target, card);

        energy.consumeAll(caster);
        say("   终结技释放完毕，能量清零 → " + caster.energy + "/" + EnergyManager.MAX);

        if (state.hand.isEmpty()) {
            say("   手牌空了，从牌库重抽 " + HAND_REFILL + " 张");
            drawCards(HAND_REFILL);
        }

        checkEnd();
        return true;
    }

    /** 队伍里有没有能量已经满了、又活着的人（界面用它判断终结技能不能点） */
    public Fighter findReadyUltimateCaster(ArrayList<Fighter> team) {
        for (Fighter f : team) {
            if (!f.isDead() && energy.isFull(f)) {
                return f;
            }
        }
        return null;
    }

    // =======================================================================
    // 效果分发
    // =======================================================================

    private void applyEffect(Fighter actor, Fighter target, Card card) {
        switch (card.effectType) {
            case DAMAGE:
                applyDamage(actor, target, card);
                break;
            case HEAL:
                applyHeal(target, card);
                break;
            case SHIELD:
                applyShield(target, card);
                break;
            case DRAW:
                say("   抽牌 " + card.effectValue + " 张");
                drawCards(card.effectValue);
                break;
            case BUFF:
                applyAttackBuff(target, card, +1);
                break;
            case DEBUFF:
                applyAttackBuff(target, card, -1);
                break;
            default:
                say("   未知效果类型：" + card.effectType);
                break;
        }
    }

    /**
     * 伤害结算。顺序严格按规则来：
     *   1. 原始 = 攻击力 + 卡牌 effectValue
     *   2. 护盾先扣，得到"基础伤害"
     *   3. 目标处于击破状态的话，基础伤害 ×1.1
     *   4. 落到生命上
     *   5. 顺便削韧性，归零就击破 + 延后行动值
     */
    private void applyDamage(Fighter attacker, Fighter target, Card card) {
        int attackPower = attacker.getAttack();
        int raw = attackPower + card.effectValue;
        say("   伤害① ：攻击力 " + attackPower
                + "（基础 " + attacker.config.attack
                + (attacker.tempAtkBuff != 0 ? " + 临时 " + attacker.tempAtkBuff : "")
                + "） + 卡牌 " + card.effectValue + " = " + raw);

        int absorbed = Math.min(target.shield, raw);
        int base = raw - absorbed;
        say("   伤害② ：目标护盾 " + target.shield + " 先扣 " + absorbed + " → 基础伤害 " + base);

        int finalDamage = base;
        if (target.broken) {
            finalDamage = Math.round(base * BREAK_DAMAGE_MULTIPLIER);
            say("   伤害③ ：目标处于【击破】状态 → " + base + " × "
                    + BREAK_DAMAGE_MULTIPLIER + " = " + finalDamage);
        } else {
            say("   伤害③ ：目标未击破，不加成 → " + finalDamage);
        }

        target.shield -= absorbed;
        int lostHp = target.applyHpDamage(finalDamage);
        say("   伤害④ ：护盾剩 " + target.shield + "，实际扣血 " + lostHp
                + (lostHp < finalDamage ? "（应扣 " + finalDamage + "，但剩余生命不够，只扣到 0）" : "")
                + " → " + target.config.name + " hp " + target.hp + "/" + target.getMaxHp());

        // 削韧性：基数用卡牌面值
        int before = target.toughness;
        boolean weak = toughness.isWeaknessHit(target, card.attribute);
        if (before <= 0) {
            say("   韧性  ：" + target.config.name + " 韧性已经是 0（处于击破状态），本次不再削减");
        } else {
            int shouldCut = toughness.amountFor(target, card.attribute, card.effectValue);
            int cut = toughness.reduce(target, card.attribute, card.effectValue);
            say("   韧性  ：" + card.attribute.label + " 打 "
                    + target.config.name + "（弱点 " + target.config.weakness.label + "）→ "
                    + (weak ? "命中弱点，全额" : "非弱点，减半")
                    + " " + card.effectValue + " × " + toughness.rateFor(target, card.attribute)
                    + " = " + shouldCut
                    + (cut < shouldCut ? "（但剩余韧性只有 " + before + "，实际只削掉 " + cut + "）" : "")
                    + " → 韧性 " + before + " → " + target.toughness
                    + "/" + target.getMaxToughness());
        }

        if (toughness.markBreakIfZero(target)) {
            actionOrder.delayAfterBreak(target);
            say("   ★击破★ " + target.config.name + " 韧性归零！"
                    + "行动值延后 +" + String.format("%.0f", ActionOrderManager.BREAK_DELAY)
                    + "（全长的 25%）→ 当前行动值 "
                    + String.format("%.1f", target.actionValue)
                    + "；它下一次行动开始时韧性回满");
        }

        int gained = energy.gainOnHit(target);
        say("   受击回能 +" + gained + " → " + target.config.name
                + " 能量 " + target.energy + "/" + EnergyManager.MAX);

        if (target.isDead()) {
            say("   ☠ " + target.config.name + " 倒下了");
        }
    }

    private void applyHeal(Fighter target, Card card) {
        int healed = target.heal(card.effectValue);
        say("   治疗：" + target.config.name + " 恢复 " + healed
                + "（卡面 " + card.effectValue + "）→ hp "
                + target.hp + "/" + target.getMaxHp());
    }

    /** 护盾不叠加：新值比旧值大才替换，否则保持原样 */
    private void applyShield(Fighter target, Card card) {
        int before = target.shield;
        if (card.effectValue > before) {
            target.shield = card.effectValue;
            say("   护盾：" + target.config.name + " 旧 " + before
                    + " → 新 " + card.effectValue + "（新值更大，替换）");
        } else {
            say("   护盾：" + target.config.name + " 旧 " + before
                    + " ≥ 新 " + card.effectValue + "（护盾不叠加，保持 " + before + "）");
        }
    }

    /** ±攻击力 buff，持续 1 回合。卡面 effectValue 是百分比（25 就是 25%） */
    private void applyAttackBuff(Fighter target, Card card, int sign) {
        int points = Math.round(target.config.attack * card.effectValue / 100f) * sign;
        target.addTempAtkBuff(points);
        say("   攻击力buff：" + target.config.name
                + " 卡面 " + card.effectValue + "% → "
                + (sign > 0 ? "+" : "-") + Math.abs(points) + " 点"
                + " → 当前攻击力 " + target.getAttack()
                + "，持续到它下一次回合结束");
    }

    // =======================================================================
    // 牌库 / 手牌
    // =======================================================================

    /**
     * 从牌库抽牌。牌库空了就把弃牌堆整堆放回牌库（保持原顺序，不洗牌）。
     * 手牌超过上限就把【最早抽到的】弃掉。
     */
    public int drawCards(int count) {
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            if (state.drawPile.isEmpty()) {
                if (state.discardPile.isEmpty()) {
                    say("   牌库和弃牌堆都空了，抽不到牌");
                    break;
                }
                state.drawPile.addAll(state.discardPile);
                say("   牌库空了 → 把弃牌堆 " + state.discardPile.size() + " 张收回牌库");
                state.discardPile.clear();
            }
            state.hand.add(state.drawPile.remove(0));
            drawn++;
        }

        while (state.hand.size() > HAND_LIMIT) {
            Card dropped = state.hand.remove(0);
            state.discardPile.add(dropped);
            say("   手牌超过上限 " + HAND_LIMIT + " 张 → 弃掉最早的 [" + dropped.id + "] "
                    + dropped.name);
        }
        return drawn;
    }

    // =======================================================================
    // 防死锁
    // =======================================================================

    /**
     * 当前行动者手里还有没有"打得出去"的牌。
     * 终结技要单独判断：canPlay() 对它是永远 false（只能走 useUltimate），
     * 所以只有同队有人能量满了才算"能打"。
     */
    public boolean hasPlayableCard(Fighter actor) {
        return hasPlayableCard(actor, true);
    }

    /**
     * @param allowUltimate 这一方【愿不愿意】放终结技。
     *        低难度的敌方 AI 故意不放，所以必须把这个意愿传进来 ——
     *        否则判定会说"有终结技在手、能量也满 = 有牌可打"，于是防死锁兜底不触发，
     *        而 AI 又确实不会打终结技 → 双方永久空过，战斗卡死到天荒地老。
     *        （这是实测出来的：低难度平均一局跑了 491 次行动还没结束。）
     */
    public boolean hasPlayableCard(Fighter actor, boolean allowUltimate) {
        for (Card c : state.hand) {
            if (canPlay(c)) {
                return true;
            }
            if (allowUltimate
                    && c.type == Card.Type.ULTIMATE
                    && findReadyUltimateCaster(state.teamOf(actor)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 防死锁兜底：手里一张能打的牌都没有时，把整手牌弃掉重抽。
     *
     * 【为什么必须有这条规则 —— 这是踩到真死锁之后加的】
     * 手里只剩终结技、而同队又没人能量满时，既打不出去也放不出来；
     * 手牌又不是空的，触发不了"手牌空重抽 5 张" → 双方永远空过，战斗卡死。
     *
     * @return true 表示确实触发了兜底
     */
    public boolean refreshHandIfStuck(Fighter actor) {
        return refreshHandIfStuck(actor, true);
    }

    /** 见 hasPlayableCard 的说明：低难度要传 false 才会正常触发兜底 */
    public boolean refreshHandIfStuck(Fighter actor, boolean allowUltimate) {
        if (hasPlayableCard(actor, allowUltimate)) {
            return false;
        }
        say("   手牌里没有任何能打的牌（" + handNames() + "）→ 整手弃掉，重抽 "
                + HAND_REFILL + " 张（防死锁兜底）");
        state.discardPile.addAll(state.hand);
        state.hand.clear();
        drawCards(HAND_REFILL);
        return true;
    }

    /** 把手牌列成 "id 名字" 的形式，只用于日志 */
    public String handNames() {
        StringBuilder sb = new StringBuilder();
        for (Card c : state.hand) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.id).append(' ').append(c.name);
        }
        return sb.length() == 0 ? "空" : sb.toString();
    }

    // =======================================================================
    // 胜负
    // =======================================================================

    /** 一边全倒就结束。两边同时全倒时算玩家赢（顺序结算，实际不会同时） */
    public void checkEnd() {
        if (state.isFinished()) {
            return;
        }
        if (GameState.isTeamDefeated(state.enemyTeam)) {
            state.result = GameState.Result.PLAYER_WIN;
        } else if (GameState.isTeamDefeated(state.playerTeam)) {
            state.result = GameState.Result.ENEMY_WIN;
        }
    }

    /** 当前局势的一行摘要，给外部打日志用 */
    public String statusLine() {
        return "战技点=" + state.skillPoints
                + " | 我方 " + teamLine(state.playerTeam)
                + " | 敌方 " + teamLine(state.enemyTeam);
    }

    private static String teamLine(ArrayList<Fighter> team) {
        StringBuilder sb = new StringBuilder();
        for (Fighter f : team) {
            if (sb.length() > 0) {
                sb.append("　");
            }
            sb.append(f.config.name).append(" hp").append(Math.max(0, f.hp))
                    .append(" 盾").append(f.shield)
                    .append(" 能量").append(f.energy)
                    .append(" 韧性").append(f.toughness)
                    .append(f.broken ? "(破)" : "")
                    .append(f.isDead() ? "(倒)" : "");
        }
        return sb.toString();
    }

    /** 造一个灼烧减益（当前没有任何卡能产生它，留给以后的卡或技能用） */
    public static StatusEffect makeBurn(int damage, int duration) {
        return new StatusEffect(StatusEffect.Type.BURN, damage, duration);
    }

    /** 造一个冻结减益 */
    public static StatusEffect makeFreeze(int duration) {
        return new StatusEffect(StatusEffect.Type.FREEZE, 1, duration);
    }
}
