// /home/djx/game/core/src/main/java/com/game/cardgame/model/GameState.java
package com.game.cardgame.model;

import java.util.ArrayList;

/**
 * 一整场战斗的完整状态（现在的规模是 3v3）。
 *
 * 目的：把"战斗现在是什么样"和"战斗怎么算"分开。
 *   GameState —— 只有数据（谁、多少血、手牌是什么、轮到谁）
 *   BattleController / BattleSystem —— 只有规则
 *
 * 【和之前 1v1 版本的差别】
 *   原来只有 player / enemy 两个 Fighter，现在改成两队各 3 个。
 *   另外提供一堆按队查询的小工具，免得各处都写"这人在哪一队"的判断。
 */
public final class GameState {

    /** 胜负状态。用枚举而不是两个 boolean，免得出现"没打完但已经算玩家赢了"这种矛盾状态 */
    public enum Result {
        /** 进行中 */
        ONGOING,
        PLAYER_WIN,
        ENEMY_WIN
    }

    /** 玩家队伍（3 个） */
    public final ArrayList<Fighter> playerTeam = new ArrayList<Fighter>();

    /** 敌方队伍（3 个） */
    public final ArrayList<Fighter> enemyTeam = new ArrayList<Fighter>();

    /** 牌库（抽牌堆） */
    public final ArrayList<Card> drawPile = new ArrayList<Card>();

    /** 手牌（双方共用一手牌） */
    public final ArrayList<Card> hand = new ArrayList<Card>();

    /** 弃牌堆 */
    public final ArrayList<Card> discardPile = new ArrayList<Card>();

    /** 战技点：双方共用，出牌消耗它 */
    public int skillPoints;

    /** 当前该谁行动 */
    public Fighter currentActor;

    /** 第几次行动，从 1 开始 */
    public int turnCount;

    /** 胜负 */
    public Result result = Result.ONGOING;

    public GameState(ArrayList<Fighter> playerTeam, ArrayList<Fighter> enemyTeam) {
        this.playerTeam.addAll(playerTeam);
        this.enemyTeam.addAll(enemyTeam);
        this.skillPoints = 0;
        this.currentActor = null;
        this.turnCount = 1;
    }

    public boolean isFinished() {
        return result != Result.ONGOING;
    }

    // =======================================================================
    // 队伍相关的小工具
    // =======================================================================

    /** 这个人是不是玩家这边的 */
    public boolean isPlayerSide(Fighter f) {
        return playerTeam.contains(f);
    }

    /** 这个人在哪一队 */
    public ArrayList<Fighter> teamOf(Fighter f) {
        return isPlayerSide(f) ? playerTeam : enemyTeam;
    }

    /** 这个人的对面那一队 */
    public ArrayList<Fighter> opposingTeamOf(Fighter f) {
        return isPlayerSide(f) ? enemyTeam : playerTeam;
    }

    /** 两队合起来。行动条要按这个名单找下一个行动者 */
    public ArrayList<Fighter> allUnits() {
        ArrayList<Fighter> all = new ArrayList<Fighter>(playerTeam.size() + enemyTeam.size());
        all.addAll(playerTeam);
        all.addAll(enemyTeam);
        return all;
    }

    /** 队伍里还活着的人 */
    public static ArrayList<Fighter> aliveOf(ArrayList<Fighter> team) {
        ArrayList<Fighter> alive = new ArrayList<Fighter>();
        for (Fighter f : team) {
            if (!f.isDead()) {
                alive.add(f);
            }
        }
        return alive;
    }

    /** 这一队是不是全倒了 */
    public static boolean isTeamDefeated(ArrayList<Fighter> team) {
        for (Fighter f : team) {
            if (!f.isDead()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 队伍里"血最少的那个"（按血量百分比算）。
     * 治疗/护盾打给自己队最危险的人，伤害/减益打给对面最危险的人 —— 都用它。
     * 全倒了就返回 null。
     */
    public static Fighter lowestHpRatio(ArrayList<Fighter> team) {
        Fighter best = null;
        float bestRatio = Float.MAX_VALUE;
        for (Fighter f : team) {
            if (f.isDead()) {
                continue;
            }
            float ratio = f.hp / (float) f.getMaxHp();
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = f;
            }
        }
        return best;
    }

    /** 队伍总血量百分比，用来判断"哪边快撑不住了" */
    public static float teamHpRatio(ArrayList<Fighter> team) {
        int hp = 0;
        int max = 0;
        for (Fighter f : team) {
            hp += Math.max(0, f.hp);
            max += f.getMaxHp();
        }
        return (max == 0) ? 0f : hp / (float) max;
    }

    @Override
    public String toString() {
        return "回合 " + turnCount
                + " | 行动者=" + (currentActor == null ? "未定" : currentActor.config.name)
                + " | 战技点=" + skillPoints
                + " | 牌库=" + drawPile.size()
                + " 手牌=" + hand.size()
                + " 弃牌堆=" + discardPile.size()
                + " | 胜负=" + result
                + "\n  我方: " + teamLine(playerTeam)
                + "\n  敌方: " + teamLine(enemyTeam);
    }

    private static String teamLine(ArrayList<Fighter> team) {
        StringBuilder sb = new StringBuilder();
        for (Fighter f : team) {
            if (sb.length() > 0) {
                sb.append("　|　");
            }
            sb.append(f.config.name).append(" ").append(Math.max(0, f.hp)).append("/")
                    .append(f.getMaxHp());
            if (f.isDead()) {
                sb.append("(倒)");
            }
        }
        return sb.toString();
    }
}
