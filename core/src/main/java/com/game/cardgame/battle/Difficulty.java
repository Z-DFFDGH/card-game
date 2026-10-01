// /home/djx/game/core/src/main/java/com/game/cardgame/battle/Difficulty.java
package com.game.cardgame.battle;

/**
 * 难度。三档，分别对应：
 *   低 —— 菜鸟也能跟它打个平手（敌方偏弱、AI 不会放大招、打人也不集中火力，还会后手）
 *   中 —— 普通人能跟它打个平手（敌方实力接近、AI 正常）
 *   高 —— 基本只有大佬才打得赢（敌方偏强、AI 正常且【先手】）
 *
 * 每个参数都是"能真正改胜负"的旋钮，不是摆设：
 *   enemyStrengthBias —— 敌方队伍从强度排序的哪个位置挑（<1 偏弱，>1 偏强）
 *   aiUsesUltimate    —— 敌方会不会放终结技。这条影响【极大】：
 *                        实测同一批对局，AI 不放 vs 放，玩家胜率是从 4% 到 70% 的差距。
 *                        但正因为太猛，低难度最后【没有】关掉它，而是改用了
 *                        "敌人偏弱 + 乱打目标 + 后手"这三个温和一点的旋钮
 *                        （关掉大招会让低难度胜率直接飙到 99.5%，那就不叫难度了）
 *   aiTargetsWeakest  —— 敌方打人是否集中火力打血最少的。关掉就变成随机打
 *   enemyActionScale  —— 敌方开局行动值倍率。<1 表示它行动值更小 → 【它先手】
 */
public enum Difficulty {

    EASY("低", 0.96f, true, false, 1.06f),
    NORMAL("中", 1.00f, true, true, 1.00f),
    HARD("高", 1.02f, true, true, 0.96f);

    /** 界面上显示的名字 */
    public final String label;

    /** 敌方队伍的强度目标 = 我方平均强度 × 这个系数 */
    public final float enemyStrengthBias;

    /** 敌方 AI 会不会放终结技 */
    public final boolean aiUsesUltimate;

    /** 敌方 AI 是否集中火力打血最少的（false = 随机挑目标） */
    public final boolean aiTargetsWeakest;

    /** 敌方开局行动值倍率。<1 表示敌方行动值更小 → 敌方先手 */
    public final float enemyActionScale;

    Difficulty(String label, float enemyStrengthBias, boolean aiUsesUltimate,
               boolean aiTargetsWeakest, float enemyActionScale) {
        this.label = label;
        this.enemyStrengthBias = enemyStrengthBias;
        this.aiUsesUltimate = aiUsesUltimate;
        this.aiTargetsWeakest = aiTargetsWeakest;
        this.enemyActionScale = enemyActionScale;
    }

    /** 一句话说明，显示在界面上 */
    public String describe() {
        switch (this) {
            case EASY:
                return "敌方偏弱、乱打目标、还后手，适合刚上手";
            case NORMAL:
                return "双方实力接近，AI 会放大招";
            default:
                return "敌方偏强、集中火力、而且通常先手，硬仗";
        }
    }

    public static Difficulty fromLabel(String label) {
        for (Difficulty d : values()) {
            if (d.label.equals(label)) {
                return d;
            }
        }
        return NORMAL;
    }
}
