// /home/djx/game/core/src/main/java/com/game/cardgame/battle/MainTest.java
package com.game.cardgame.battle;

import com.badlogic.gdx.files.FileHandle;

import com.game.cardgame.model.Card;
import com.game.cardgame.model.Character;
import com.game.cardgame.model.Fighter;
import com.game.cardgame.model.GameState;
import com.game.cardgame.model.StatusEffect;
import com.game.cardgame.util.JsonLoader;

import java.io.File;
import java.util.ArrayList;

/**
 * 战斗逻辑自检（纯逻辑，不需要图形界面 / Android / 网络）。
 *
 * 跑法（在工程根目录）：
 *     ./gradlew battleTest
 *
 * 三段：
 *   1. 3v3 自动对局（AI 替双方打）
 *   2. 受控公式验证（用 1v1 摆局面，核对 护盾抵扣 / 弱点削韧 / 击破 ×1.1 / 延后 2500）
 *   3. 灼烧 / 冻结 规则演示
 */
public final class MainTest {

    public static final String DATA_DIR = "android/assets/data";
    public static final int MAX_ACTIONS = 60;

    private MainTest() {
    }

    public static void main(String[] args) {
        File dataDir = (args.length > 0) ? new File(args[0]) : new File(DATA_DIR);

        ArrayList<Card> deck = JsonLoader.loadCards(
                new FileHandle(new File(dataDir, "cards.json")));
        ArrayList<Character> roster = JsonLoader.loadCharacters(
                new FileHandle(new File(dataDir, "characters.json")));

        printRules();

        // 取 3 个当玩家队，另外 3 个当敌方队
        ArrayList<Character> mine = new ArrayList<Character>();
        ArrayList<Character> foes = new ArrayList<Character>();
        for (int i = 0; i < 3 && i < roster.size(); i++) {
            mine.add(roster.get(i));
        }
        for (int i = 3; i < 6 && i < roster.size(); i++) {
            foes.add(roster.get(i));
        }

        runTeamBattle(mine, foes, deck);
        // 受控场景要用弱点是火的对手：n001 是火(全额)、n002 是冰(减半)，正好对照
        Character fireWeak = findWeakToFire(roster);
        demoFormulas(roster.get(0), fireWeak, deck);
        demoStatusRules(roster.get(0), fireWeak, deck);
    }

    private static void printRules() {
        System.out.println("================ 战斗逻辑自检（3v3）================");
        System.out.println("  伤害     = 攻击力 + 卡牌 effectValue，然后【护盾先扣】；");
        System.out.println("             若目标处于击破状态，得到的基础伤害再 ×1.1");
        System.out.println("  削韧     = 卡牌 effectValue × (命中弱点 ? 1.0 全额 : 0.5 减半)");
        System.out.println("  击破延后 = 行动值 + 2500（= 行动条全长 10000 的 25%）");
        System.out.println("  行动条   = 全长 10000，开局满格，每刻减【速度】，归零者行动，行动完重置");
        System.out.println("  战技点   = 上限 5 / 初始 3 / 普攻 +1 / 战技 −1 / 终结技 0");
        System.out.println("  能量     = 上限 100 / 出牌 +20 / 受击 +10 / 满可插队 / 释放清零");
        System.out.println("  胜负     = 一边 3 个全倒就结束");
    }

    // =======================================================================
    // 第一段：3v3 自动对局
    // =======================================================================

    private static void runTeamBattle(ArrayList<Character> mine, ArrayList<Character> foes,
                                      ArrayList<Card> deck) {
        System.out.println();
        System.out.println("==================== 第一段：3v3 自动对局 ====================");

        LocalBattleController controller = new LocalBattleController();
        controller.addListener(new BattleController.Listener() {
            @Override
            public void onLog(String message) {
                System.out.println(message);
            }

            @Override
            public void onStateChanged(GameState state) {
            }

            @Override
            public void onBattleEnd(GameState.Result result) {
                System.out.println("  ★ 战斗结束：" + result);
            }
        });

        controller.initialize(mine, foes, new ArrayList<Card>(deck));

        for (int i = 0; i < MAX_ACTIONS; i++) {
            GameState state = controller.getState();
            if (state.isFinished()) {
                break;
            }
            controller.autoPlayTurn();      // 谁行动就替谁打（AI）
            if (controller.getState().isFinished()) {
                break;
            }
            controller.advanceToNextTurn();
        }

        GameState finalState = controller.getState();
        System.out.println();
        System.out.println("-------------------- 对局结束 --------------------");
        System.out.println("结果：" + finalState.result + "（" + describeResult(finalState.result) + "）");
        System.out.println("总行动次数：" + finalState.turnCount);
        System.out.println("我方 " + GameState.teamHpRatio(finalState.playerTeam) * 100 + "% 血量剩余");
        System.out.println("敌方 " + GameState.teamHpRatio(finalState.enemyTeam) * 100 + "% 血量剩余");
        controller.dispose();
    }

    private static String describeResult(GameState.Result result) {
        switch (result) {
            case PLAYER_WIN:
                return "我方获胜";
            case ENEMY_WIN:
                return "我方失败";
            default:
                return "还没打完";
        }
    }

    // =======================================================================
    // 第二段：受控公式验证（用 1v1 摆局面）
    // =======================================================================

    private static void demoFormulas(Character playerCfg, Character enemyCfg, ArrayList<Card> deck) {
        System.out.println();
        System.out.println("============ 第二段：受控公式验证（1v1 手工摆局面）============");
        System.out.println("注意：这一段只用 1 个角色对 1 个角色，是为了把数字摆得干净好核对。");

        ArrayList<Card> controlled = new ArrayList<Card>();
        controlled.add(findCard(deck, "n001"));
        controlled.add(findCard(deck, "n002"));
        controlled.add(findCard(deck, "u001"));
        controlled.add(findCard(deck, "n003"));
        controlled.add(findCard(deck, "n004"));

        BattleSystem sys = new BattleSystem();
        sys.setLog(new BattleSystem.Log() {
            @Override
            public void line(String message) {
                System.out.println("    " + message);
            }
        });

        ArrayList<Character> one = new ArrayList<Character>();
        one.add(playerCfg);
        ArrayList<Character> two = new ArrayList<Character>();
        two.add(enemyCfg);
        sys.setup(one, two, controlled);

        Fighter player = sys.getState().playerTeam.get(0);
        Fighter target = sys.getState().enemyTeam.get(0);

        System.out.println("玩家 " + player.config.name + "：攻击 " + player.config.attack
                + "，属性 " + player.config.element.label);
        System.out.println("对手 " + target.config.name + "：hp " + target.getMaxHp()
                + "，韧性上限 " + target.getMaxToughness()
                + "，弱点 " + target.config.weakness.label);

        // ---- 场景 A：非弱点减半 ----
        System.out.println();
        System.out.println("── 场景 A：属性不是弱点 → 韧性减半 ──");
        sys.advanceToNextActor();
        sys.beginTurn(player);
        target.toughness = target.getMaxToughness();
        target.shield = 0;
        player.tempAtkBuff = 0;

        Card cardA = findCard(controlled, "n002");
        int toughBeforeA = target.toughness;
        System.out.println("  摆局面：对手韧性 " + toughBeforeA + "/" + target.getMaxToughness()
                + "，护盾 0；玩家攻击力 " + player.getAttack());
        System.out.println("  出牌：" + cardA.id + " " + cardA.name + "（属性 "
                + cardA.attribute.label + "，DAMAGE " + cardA.effectValue
                + "），对手弱点 = " + target.config.weakness.label);
        sys.playCard(cardA, target);
        int cutA = (int) Math.floor(cardA.effectValue * 0.5f);
        System.out.println("  【核对-削韧】非弱点 → 削减 = " + cardA.effectValue + " × 0.5 = " + cutA
                + "；韧性 " + toughBeforeA + " − " + cutA + " = " + (toughBeforeA - cutA)
                + "，实际 " + target.toughness
                + (target.toughness == toughBeforeA - cutA ? "   ✔" : "   ✘ 对不上"));
        sys.endTurn();

        // ---- 场景 B + C ----
        System.out.println();
        System.out.println("── 场景 B+C：护盾抵扣 → 弱点全额削韧 → 击破延后 2500 → 击破状态下 ×1.1 ──");
        System.out.println("  说明：B 和 C 必须放在【同一个回合】里，因为对手一旦轮到自己行动，");
        System.out.println("        韧性就会回满、击破状态就没了。");

        // 推到玩家回合
        for (int i = 0; i < 10; i++) {
            Fighter a = sys.advanceToNextActor();
            boolean canAct = sys.beginTurn(a);
            if (a == player && canAct) {
                break;
            }
            sys.endTurn();
        }

        target.shield = 20;
        target.toughness = 9;
        player.tempAtkBuff = 0;

        Card cardB = findCard(controlled, "n001");
        int hpBeforeB = target.hp;
        int shieldBeforeB = target.shield;
        int toughBeforeB = target.toughness;
        float avBeforeB = target.actionValue;
        int attackB = player.getAttack();

        System.out.println();
        System.out.println("  摆局面：对手 护盾 " + shieldBeforeB + "、韧性 " + toughBeforeB
                + "/" + target.getMaxToughness() + "（弱点 " + target.config.weakness.label
                + "）、hp " + hpBeforeB + "、行动值 " + String.format("%.1f", avBeforeB)
                + "；玩家攻击力 " + attackB);
        System.out.println("  出牌：" + cardB.id + " " + cardB.name + "（属性 "
                + cardB.attribute.label + "，DAMAGE " + cardB.effectValue + "）");
        sys.playCard(cardB, target);

        int rawB = attackB + cardB.effectValue;
        int absorbedB = Math.min(shieldBeforeB, rawB);
        int baseB = rawB - absorbedB;
        int expectHpB = hpBeforeB - baseB;
        int expectToughB = toughBeforeB - cardB.effectValue;
        float expectAvB = avBeforeB + ActionOrderManager.BREAK_DELAY;

        System.out.println("  【核对-伤害】攻击 " + attackB + " + 卡牌 " + cardB.effectValue
                + " = " + rawB + "；护盾 " + shieldBeforeB + " 先扣 " + absorbedB
                + " → 基础 " + baseB + "；hp " + hpBeforeB + " − " + baseB + " = " + expectHpB
                + "，实际 " + target.hp + (target.hp == expectHpB ? "   ✔" : "   ✘ 对不上"));
        System.out.println("  【核对-削韧】命中弱点 → 全额 " + cardB.effectValue
                + "；韧性 " + toughBeforeB + " − " + cardB.effectValue + " = " + expectToughB
                + "，实际 " + target.toughness
                + (target.toughness == expectToughB ? "   ✔" : "   ✘ 对不上"));
        System.out.println("  【核对-击破】韧性归零 → 击破=" + target.broken + "（应为 true）"
                + (target.broken ? "   ✔" : "   ✘ 对不上"));
        System.out.println("  【核对-延后】行动值 " + String.format("%.1f", avBeforeB)
                + " + " + String.format("%.0f", ActionOrderManager.BREAK_DELAY)
                + " = " + String.format("%.1f", expectAvB)
                + "，实际 " + String.format("%.1f", target.actionValue)
                + (Math.abs(target.actionValue - expectAvB) < 0.01f ? "   ✔" : "   ✘ 对不上"));

        System.out.println();
        System.out.println("  【场景 C】同一回合内插队放终结技（能量满可随时插队）");
        player.energy = EnergyManager.MAX;
        Card cardC = findCard(controlled, "u001");
        int hpBeforeC = target.hp;
        int shieldBeforeC = target.shield;
        int attackC = player.getAttack();
        System.out.println("  摆局面：把玩家能量充满 → " + player.energy + "/" + EnergyManager.MAX
                + "；对手 击破=" + target.broken + "，护盾 " + shieldBeforeC + "，hp " + hpBeforeC);
        System.out.println("  放终结技：" + cardC.id + " " + cardC.name + "（属性 "
                + cardC.attribute.label + "，DAMAGE " + cardC.effectValue + "）");
        sys.useUltimate(player, cardC, target);

        int rawC = attackC + cardC.effectValue;
        int absorbedC = Math.min(shieldBeforeC, rawC);
        int baseC = rawC - absorbedC;
        int finalC = Math.round(baseC * BattleSystem.BREAK_DAMAGE_MULTIPLIER);
        int expectHpC = Math.max(0, hpBeforeC - finalC);   // 血量不会变负，打死了就封 0

        System.out.println("  【核对-伤害】攻击 " + attackC + " + 卡牌 " + cardC.effectValue
                + " = " + rawC + "；护盾 " + shieldBeforeC + " 先扣 " + absorbedC
                + " → 基础 " + baseC);
        System.out.println("  【核对-×1.1】目标处于击破状态 → " + baseC + " × "
                + BattleSystem.BREAK_DAMAGE_MULTIPLIER + " = " + finalC
                + "；hp " + hpBeforeC + " − " + finalC + " = " + expectHpC
                + "，实际 " + target.hp + (target.hp == expectHpC ? "   ✔" : "   ✘ 对不上"));
        System.out.println("  【核对-能量】释放后应清零，实际 " + player.energy + "/"
                + EnergyManager.MAX + (player.energy == 0 ? "   ✔" : "   ✘ 对不上"));
        sys.endTurn();
    }

    // =======================================================================
    // 第三段：灼烧 / 冻结
    // =======================================================================

    private static void demoStatusRules(Character playerCfg, Character enemyCfg,
                                        ArrayList<Card> deck) {
        System.out.println();
        System.out.println("============ 第三段：灼烧 / 冻结 规则演示（手工施加）============");
        System.out.println("cards.json 里 6 种 effectType 没有状态类，所以没有任何卡能产生它们。");

        BattleSystem sys = new BattleSystem();
        sys.setLog(new BattleSystem.Log() {
            @Override
            public void line(String message) {
                System.out.println("    " + message);
            }
        });

        ArrayList<Character> one = new ArrayList<Character>();
        one.add(playerCfg);
        ArrayList<Character> two = new ArrayList<Character>();
        two.add(enemyCfg);
        sys.setup(one, two, new ArrayList<Card>(deck));

        Fighter player = sys.getState().playerTeam.get(0);
        Fighter enemy = sys.getState().enemyTeam.get(0);

        player.addDebuff(BattleSystem.makeBurn(
                BattleSystem.BURN_DAMAGE_PER_TICK, BattleSystem.BURN_DURATION));
        enemy.addDebuff(BattleSystem.makeFreeze(BattleSystem.FREEZE_DURATION));

        System.out.println("  施加 → " + player.config.name + " 减益=" + player.debuffs
                + "（灼烧：每回合开始掉 " + BattleSystem.BURN_DAMAGE_PER_TICK
                + " 血，共 " + BattleSystem.BURN_DURATION + " 个回合）");
        System.out.println("  施加 → " + enemy.config.name + " 减益=" + enemy.debuffs
                + "（冻结：跳过下 " + BattleSystem.FREEZE_DURATION + " 个回合）");

        int hpStart = player.hp;
        int burnTicks = 0;
        int freezeSkips = 0;
        for (int i = 0; i < 5; i++) {
            Fighter actor = sys.advanceToNextActor();
            if (actor == player && actor.hasDebuff(StatusEffect.Type.BURN)) {
                burnTicks++;
            }
            boolean canAct = sys.beginTurn(actor);
            if (!canAct && actor == enemy) {
                freezeSkips++;
            }
            System.out.println("  >>> " + actor.config.name + " 本回合："
                    + (canAct ? "正常行动" : "被跳过")
                    + "　hp " + actor.hp + "/" + actor.getMaxHp()
                    + "　减益=" + actor.debuffs);
            sys.endTurn();
        }

        int expectedBurnTotal = BattleSystem.BURN_DAMAGE_PER_TICK * BattleSystem.BURN_DURATION;
        int actualBurnTotal = hpStart - player.hp;
        System.out.println("  【核对-灼烧】应为 " + BattleSystem.BURN_DURATION + " 次 × "
                + BattleSystem.BURN_DAMAGE_PER_TICK + " 血 = " + expectedBurnTotal
                + "；实际发作 " + burnTicks + " 次、共掉 " + actualBurnTotal + " 血"
                + (burnTicks == BattleSystem.BURN_DURATION && actualBurnTotal == expectedBurnTotal
                ? "   ✔" : "   ✘ 对不上"));
        System.out.println("  【核对-冻结】应只跳过 " + BattleSystem.FREEZE_DURATION
                + " 个回合；实际跳过 " + freezeSkips + " 次"
                + (freezeSkips == BattleSystem.FREEZE_DURATION ? "   ✔" : "   ✘ 对不上"));
    }

    // =======================================================================
    // 工具
    // =======================================================================

    /** 找一个弱点是火的角色，受控场景要用它 */
    private static Character findWeakToFire(ArrayList<Character> roster) {
        for (Character c : roster) {
            if (c.weakness == com.game.cardgame.model.Element.FIRE) {
                return c;
            }
        }
        return roster.get(0);
    }

    private static Card findCard(ArrayList<Card> deck, String id) {
        for (Card c : deck) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        throw new IllegalStateException("cards.json 里找不到 id=" + id + " 的卡");
    }
}
