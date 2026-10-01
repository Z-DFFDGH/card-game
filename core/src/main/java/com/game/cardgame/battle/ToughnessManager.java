// /home/djx/game/core/src/main/java/com/game/cardgame/battle/ToughnessManager.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Element;
import com.game.cardgame.model.Fighter;

/**
 * 韧性 / 弱点击破。
 *
 * 规则：
 *   攻击属性 == 目标弱点   → 全额削减
 *   攻击属性 != 目标弱点   → 减半（向下取整）
 *   韧性归零               → 触发"击破"：目标受到的伤害 ×1.1，行动值 +2500
 *   被击破的单位，到【它自己】下一个回合开始时韧性回满、击破解除
 *
 * 削减量以【卡牌的 effectValue（卡面伤害值）】为基数，而不是最终打出的伤害。
 * 这样做的好处是：护盾挡掉多少、有没有击破加成，都不影响削韧速度，
 * 削韧只跟"用哪张牌、属性对不对"有关，比较好核对。
 */
public final class ToughnessManager {

    /** 命中弱点时的削减倍率：全额 */
    public static final float WEAKNESS_RATE = 1.0f;

    /** 非弱点时的削减倍率：减半 */
    public static final float NORMAL_RATE = 0.5f;

    /** 这一击算不算命中弱点 */
    public boolean isWeaknessHit(Fighter target, Element attackElement) {
        return attackElement == target.config.weakness;
    }

    /** 按属性算这次攻击的削减倍率 */
    public float rateFor(Fighter target, Element attackElement) {
        return isWeaknessHit(target, attackElement) ? WEAKNESS_RATE : NORMAL_RATE;
    }

    /**
     * 削减目标韧性。
     *
     * @param target        被打的人
     * @param attackElement 攻击属性（卡牌的 attribute）
     * @param baseAmount    基数，一般是卡牌的 effectValue
     * @return 实际削减掉的韧性值
     */
    public int reduce(Fighter target, Element attackElement, int baseAmount) {
        int amount = amountFor(target, attackElement, baseAmount);
        if (amount <= 0) {
            return 0;
        }
        int before = target.toughness;
        target.toughness = Math.max(0, before - amount);
        return before - target.toughness;
    }

    /**
     * 这次攻击【应该】削减多少韧性（不考虑"剩余韧性不够扣"的截断）。
     *
     * 为什么要把"应削"和"实削"分开：
     *   打一个只剩 11 点韧性的目标时，45 点的攻击公式算出来是 45，但实际只能削掉 11。
     *   只打印实际值会变成"45 × 1.0 = 11"这种看着像算错了的日志，
     *   所以两个数都要报出来，核对时才知道是公式错还是被截断了。
     */
    public int amountFor(Fighter target, Element attackElement, int baseAmount) {
        if (baseAmount <= 0) {
            return 0;
        }
        return (int) Math.floor(baseAmount * rateFor(target, attackElement));
    }

    /**
     * 韧性是否已经归零。归零的那一瞬间调一次：把 broken 置 true。
     *
     * @return true 表示"刚刚被击破"（本次调用触发的），false 表示没归零或之前就已经击破了。
     *         只有返回 true 的时候才应该做击破延后，否则每次挨打都会延后一次。
     */
    public boolean markBreakIfZero(Fighter target) {
        if (target.toughness <= 0 && !target.broken) {
            target.broken = true;
            return true;
        }
        return false;
    }

    /**
     * 回合开始时的恢复：如果处于击破状态，韧性回满并解除击破。
     *
     * @return true 表示这次真的回满了
     */
    public boolean refillAtTurnStart(Fighter f) {
        if (!f.broken && f.toughness >= f.getMaxToughness()) {
            return false;
        }
        f.toughness = f.getMaxToughness();
        f.broken = false;
        return true;
    }
}
