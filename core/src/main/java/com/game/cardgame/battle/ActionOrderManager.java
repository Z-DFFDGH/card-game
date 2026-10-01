// /home/djx/game/core/src/main/java/com/game/cardgame/battle/ActionOrderManager.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Fighter;

import java.util.ArrayList;

/**
 * 行动顺序（行动条）。
 *
 * 模型：行动值是 [0, 10000] 的【倒计时】。
 *   开局每人满格 10000；
 *   每推进一段时间，每个人的行动值减去「速度 × 这段时间」；
 *   谁先归零谁行动，行动完重置回 10000。
 *
 * 所以走完一整条要 10000/速度 的时间，也就是"速度 × 所需时间 = 10000"。
 *
 * 击破延后：被击破的单位行动值 +2500（全长的 25%），也就是多等 1/4 条。
 * 这里【不做封顶】—— 如果它当前剩余已经超过 7500，加上 2500 会超过全长，
 * 那就再多等一点，这样"延后 25%"在任何时刻都是实打实的 25%。
 *
 * 阶段 6 变化：从"固定两个人"改成"一个名单里任意多个人"（现在是 6 个），
 * 并且会自动跳过已经倒下的单位。
 */
public final class ActionOrderManager {

    /** 行动条全长 */
    public static final float BAR_LENGTH = Fighter.ACTION_BAR_LENGTH;

    /** 击破延后量 = 全长的 25% = 2500 */
    public static final float BREAK_DELAY = BAR_LENGTH * 0.25f;

    /** 浮点误差容忍度。贴到 0 就算归零 */
    private static final float EPSILON = 0.0001f;   // 浮点容差，只用于比较"谁更快"

    /** 把所有人的行动值都置为满格。战斗开始时调用一次 */
    public void resetAll(ArrayList<Fighter> units) {
        for (Fighter f : units) {
            f.actionValue = BAR_LENGTH;
        }
    }

    /** 某个单位行动完了：行动值重置回满格 */
    public void afterAction(Fighter f) {
        f.actionValue = BAR_LENGTH;
    }

    /** 击破延后：行动值 +2500 */
    public void delayAfterBreak(Fighter f) {
        f.actionValue += BREAK_DELAY;
    }

    /**
     * 推进时间，返回下一个该行动的人（已经倒下的人会被跳过）。
     *
     * 做法：在【还活着的人】里算出各自还需要多少时间（剩余行动值 ÷ 速度），取最小值当作推进量，
     * 所有人一起减掉各自这段时间能走的量。这样推完之后，至少有一个人的行动值正好归零。
     *
     * 调用方必须在单位行动结束后调用 afterAction() 把它重置回满格，
     * 否则它还是 0，下一次推进会推不动。
     *
     * @return 下一个行动者；如果所有人都倒下了返回 null
     */
    public Fighter nextActor(ArrayList<Fighter> units) {
        // 第一步：先选出"最快归零"的那个人。
        // 注意必须【先选人、再推进】，不能"推完再看谁变成 0" —— 见下面的注释。
        Fighter soonest = null;
        float advance = Float.MAX_VALUE;
        for (Fighter f : units) {
            if (f.isDead()) {
                continue;
            }
            float wait = waitTime(f);
            if (wait < advance - EPSILON) {
                advance = wait;
                soonest = f;      // 时间相同时保留名单靠前的那个，行为可预测
            }
        }
        if (soonest == null) {
            return null;          // 全倒了
        }

        // 第二步：所有人按各自速度推进同样长的时间
        for (Fighter f : units) {
            if (f.isDead()) {
                continue;
            }
            f.actionValue -= speedOf(f) * advance;
            if (f.actionValue < 0f) {
                f.actionValue = 0f;
            }
        }

        // 第三步：把选中的那个人直接归零。
        //
        // 【为什么必须显式归零 —— 这里踩过一个会让战斗永久卡死的坑】
        //   推进量就是"最小等待时间"，理论上减完那个人正好是 0。
        //   但浮点乘法有误差：10000 − 112 × (10000/112) 可能等于 0.00047 而不是 0，
        //   0.00047 比容差 EPSILON(0.0001) 大，于是"贴 0"判断不成立，
        //   结果没有任何人被判定为归零 → 返回 null → 战斗从此再也推不动（界面看着像卡死）。
        //   所以这里不靠浮点碰运气，直接把它置 0。
        soonest.actionValue = 0f;
        return soonest;
    }

    /** 这个单位还要等多久才行动（时间单位） */
    public static float waitTime(Fighter f) {
        return f.actionValue / speedOf(f);
    }

    /** 速度保护：配置里速度不可能是 0，但除以 0 会直接崩，兜一下 */
    private static int speedOf(Fighter f) {
        return Math.max(1, f.config.speed);
    }
}
