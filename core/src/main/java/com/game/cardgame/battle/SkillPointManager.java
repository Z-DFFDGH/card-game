// /home/djx/game/core/src/main/java/com/game/cardgame/battle/SkillPointManager.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.GameState;

/**
 * 战技点（双方共用一套）。
 *
 * 规则：
 *   上限 5，初始 3；
 *   打出普攻  +1；
 *   打出战技  -1；
 *   打出终结技 0（不消耗也不产生）。
 *
 * 注意：这里【不】看卡牌 JSON 里的 cost 字段。按你的规则，消耗只由卡牌类型决定
 * （普攻 +1 / 战技 -1 / 终结技 0），而 cards.json 里的 cost 是 1/2/3/5 这样的
 * 平衡参考值。也就是说 cost 字段目前在战斗结算里完全用不到。
 */
public final class SkillPointManager {

    /** 战技点上限 */
    public static final int MAX = 5;

    /** 开局战技点 */
    public static final int INITIAL = 3;

    /** 普攻产生的战技点 */
    public static final int NORMAL_GAIN = 1;

    /** 战技消耗的战技点 */
    public static final int SKILL_COST = 1;

    /** 终结技的战技点消耗（0 = 不消耗不产生） */
    public static final int ULTIMATE_COST = 0;

    /** 开局初始化 */
    public void init(GameState state) {
        state.skillPoints = INITIAL;
    }

    /** 当前战技点够不够打这张牌 */
    public boolean canAfford(GameState state, Card card) {
        if (card.type == Card.Type.SKILL) {
            return state.skillPoints >= SKILL_COST;
        }
        return true;   // 普攻会加，终结技不变，都不需要检查
    }

    /**
     * 结算一次出牌的战技点变化。
     *
     * @return 实际的变化量（正数是增加），调用方可以拿去打日志
     */
    public int applyCardCost(GameState state, Card card) {
        int before = state.skillPoints;
        int delta;

        if (card.type == Card.Type.SKILL) {
            delta = -SKILL_COST;
        } else if (card.type == Card.Type.NORMAL) {
            delta = NORMAL_GAIN;
        } else {
            delta = -ULTIMATE_COST;   // 终结技固定 0
        }

        state.skillPoints = clamp(before + delta);
        return state.skillPoints - before;
    }

    /** 把战技点夹在 [0, MAX] 内 */
    public int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > MAX) {
            return MAX;
        }
        return value;
    }
}
