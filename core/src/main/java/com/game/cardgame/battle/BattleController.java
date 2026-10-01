// /home/djx/game/core/src/main/java/com/game/cardgame/battle/BattleController.java
package com.game.cardgame.battle;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Fighter;
import com.game.cardgame.model.GameState;

import java.util.ArrayList;

/**
 * 战斗控制器接口。
 *
 * 为什么要有这一层（而不是直接用 BattleSystem）：
 *   BattleSystem 只懂规则，不知道"谁在操作"。
 *   这一层负责"这一手是谁下的、状态怎么同步出去"，以后加联机就再写一个
 *   RemoteBattleController 实现同样的接口，UI 代码一行都不用改。
 *
 * 单机（本阶段）由 LocalBattleController 实现。
 */
public interface BattleController {

    /** 事件回调。UI 挂它来刷新界面；不挂也能跑 */
    interface Listener {

        /** 战斗过程的一行日志 */
        void onLog(String message);

        /** 状态变了，界面该重画了 */
        void onStateChanged(GameState state);

        /** 战斗结束 */
        void onBattleEnd(GameState.Result result);
    }

    /**
     * 开一局。现在是 3v3，两边各传 3 个角色。
     *
     * @param deck 牌库。按传入顺序抽牌，不洗牌（洗牌由上层做）
     */
    void initialize(ArrayList<Character> playerTeam, ArrayList<Character> enemyTeam,
                    ArrayList<Card> deck);

    /** 当前能不能打这张牌（不看目标） */
    boolean canPlay(Card card);

    /** 出一张牌。终结技不能用这个方法 */
    boolean playCard(Card card, Fighter target);

    /**
     * 这张牌该对谁用。
     *
     * 为什么放在接口上：UI 层只通过接口操作，
     * 而"治疗给血最少的队友、伤害给血最少的敌人"是战斗规则、不是界面逻辑。
     */
    Fighter pickTarget(Card card);

    /** 现在能不能放这张终结技（牌在手里 + 我方有人能量满） */
    boolean canUseUltimate(Card card);

    /**
     * 放终结技。要求能量满，且不占用"每回合 1 张牌"的名额。
     * 释放者由实现内部决定（取我方第一个能量满的人）。
     */
    boolean useUltimate(Card card, Fighter target);

    /** 结束当前回合，轮到下一个行动者 */
    void endTurn();

    /**
     * 让"对面那一方"把当前回合走完，并把回合推进到下一位。
     * 单机实现 = 跑 AI；以后联机实现 = 等/取网络传来的对面操作。
     */
    void playOpponentTurn();

    /** 拿当前完整状态。UI 和联机都靠它 */
    GameState getState();

    /** 联机用：把外部（对面传过来的）状态装进来 */
    void setState(GameState state);

    void addListener(Listener listener);

    /** 现在是不是"我"（本地玩家）的回合，也就是当前行动者是不是我方的人 */
    boolean isMyTurn();

    /** 本地是不是主机。单机固定 true */
    boolean isHost();

    /** 释放资源 */
    void dispose();
}
