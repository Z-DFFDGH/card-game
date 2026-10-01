// /home/djx/game/core/src/main/java/com/game/cardgame/battle/EnergyManager.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Fighter;

/**
 * 能量（每个单位各自一套）。
 *
 * 规则：
 *   上限 100；
 *   每次出牌 +20（出牌的人加）；
 *   受击 +10（挨打的人加）；
 *   攒满才能放终结技，放完清零；
 *   满了可以"随时插队"放——所以放终结技【不占用】每回合 1 张牌的名额，
 *   也不改变战技点。
 */
public final class EnergyManager {

    /** 能量上限 */
    public static final int MAX = 100;

    /** 每次出牌获得的能量 */
    public static final int GAIN_ON_PLAY = 20;

    /** 每次受击获得的能量 */
    public static final int GAIN_ON_HIT = 10;

    /** 加能量（会自动封顶在 MAX） */
    public int gain(Fighter f, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = f.energy;
        f.energy = Math.min(MAX, f.energy + amount);
        return f.energy - before;
    }

    /** 出牌获得能量 */
    public int gainOnPlay(Fighter f) {
        return gain(f, GAIN_ON_PLAY);
    }

    /** 受击获得能量 */
    public int gainOnHit(Fighter f) {
        return gain(f, GAIN_ON_HIT);
    }

    /** 能量是否已满（可以放终结技） */
    public boolean isFull(Fighter f) {
        return f.energy >= MAX;
    }

    /** 放完终结技：清零 */
    public void consumeAll(Fighter f) {
        f.energy = 0;
    }
}
