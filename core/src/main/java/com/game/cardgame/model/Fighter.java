// /home/djx/game/core/src/main/java/com/game/cardgame/model/Fighter.java
package com.game.cardgame.model;

import java.util.ArrayList;

/**
 * 战斗中的【可变状态】。一个参战单位。
 *
 * 和 Character 的分工：
 *   Character —— 只读配置（上限值：hp 上限、攻击、速度、韧性上限…），来自 JSON
 *   Fighter   —— 这一场里会变的量（当前生命、护盾、能量、韧性、状态…），由 Character 造出来
 *
 * 一个 Character 可以造出多个 Fighter（同一角色打多场、或两边都有同一个角色）。
 *
 * 阶段 4 相对阶段 3 的变化（三处，都是战斗规则要求的）：
 *   1. 新增 toughness（当前韧性）和 broken（是否已击破）—— 阶段 3 就没有这两个字段，
 *      而"弱点击破"必须要有当前韧性，Character 里只有"坚韧极限"（上限）。
 *   2. actionValue 的初始值从 10000/速度 改成满格 10000。
 *      因为"归零者行动 + 击破延后 2500 = 全长的 25%"说明行动值是【倒计时条】，
 *      而不是"所需时间"：开局满格，每刻减速度，归零行动，行动完重置回 10000。
 *   3. 新增 applyHpDamage()：只扣血不碰护盾，给 BattleSystem 用（伤害要走
 *      "先扣护盾 → 得基础伤害 → 再乘击破 ×1.1"这个顺序，没法交给 applyDamage 一次做完）。
 */
public final class Fighter {

    /**
     * 行动条全长。战斗里的"行动值"就是这个 [0, 10000] 的倒计时：
     * 开局满格 10000，每经过 1 单位时间减掉【速度】点，谁先归零谁先行动。
     * 所以走完整条需要 10000/速度 单位时间，也就是"速度 × 所需时间 = 10000"。
     */
    public static final float ACTION_BAR_LENGTH = 10000f;

    /** 造出这个 Fighter 的配置。只读，不会被改 */
    public final Character config;

    /** 当前生命值。上限是 config.hp */
    public int hp;

    /** 当前护盾。受伤时先扣护盾，护盾扣完才扣生命 */
    public int shield;

    /**
     * 当前能量。攒满 100 才能放终结技。
     * 上限是战斗规则定的常量（battle/EnergyManager.MAX），不是角色配置。
     */
    public int energy;

    /**
     * 当前耐力。
     * 注意：你的需求给了这个字段但始终没定义它的用途（和「韧性」「战技点」是什么关系？）。
     * 阶段 4 的战斗规则里也用不到它，所以目前一直保持 0。
     */
    public int stamina;

    /**
     * 当前韧性（0 ~ config.toughnessMax）。
     * 被自己的弱点属性打会【全额】削减，其它属性【减半】；归零就是"击破"。
     * 击破状态下该单位受到的伤害 ×1.1，并且它的行动值会被延后 2500；
     * 到它自己下一个回合开始时韧性回满、击破解除。
     */
    public int toughness;

    /** 是否处于"击破"状态。韧性归零时置 true，回满时置 false */
    public boolean broken;

    /**
     * 行动值：倒计时，越小越接近行动，归零就行动。
     * 初始满格 10000，行动完重置回 10000。
     * 击破延后会在此基础上 +2500，允许超过全长（相当于再多等 25% 的条）。
     */
    public float actionValue;

    /** 增益列表。按当前 StatusEffect 的定义（只有灼烧/冻结，都是减益），这个列表恒为空，是预留位 */
    public final ArrayList<StatusEffect> buffs = new ArrayList<StatusEffect>();

    /** 减益列表（灼烧、冻结放这里） */
    public final ArrayList<StatusEffect> debuffs = new ArrayList<StatusEffect>();

    /**
     * 临时攻击增益（直接加在攻击力上的点数）。
     * 战斗规则：增益/减益卡的效果是"攻击力 ±X%，持续 1 回合"，
     * BattleSystem 会把这个百分比换算成点数加到这里，在该单位的回合结束时清零。
     */
    public int tempAtkBuff;

    /** 由只读配置造出一个满状态的战斗单位 */
    public Fighter(Character config) {
        this.config = config;
        this.hp = config.hp;
        this.shield = 0;
        this.energy = 0;
        this.stamina = 0;
        this.tempAtkBuff = 0;
        this.toughness = config.toughnessMax;
        this.broken = false;
        this.actionValue = ACTION_BAR_LENGTH;
    }

    /** 生命上限 */
    public int getMaxHp() {
        return config.hp;
    }

    /** 实际攻击力 = 配置攻击 + 临时攻击增益 */
    public int getAttack() {
        return config.attack + tempAtkBuff;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    /** 韧性上限 */
    public int getMaxToughness() {
        return config.toughnessMax;
    }

    /**
     * 受到伤害。护盾先扛，扛不住的才对生命生效。
     * 注意：阶段 4 的 BattleSystem 【不】用这个方法（它要自己控制护盾和击破加成的先后），
     * 保留它是为了别处（比如 DOT、环境伤害）需要"护盾优先"时还能直接用。
     *
     * @return 实际掉的生命值（0 表示全被护盾挡了）。
     */
    public int applyDamage(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remain = amount;

        if (shield > 0) {
            int absorbed = Math.min(shield, remain);
            shield -= absorbed;
            remain -= absorbed;
        }
        if (remain <= 0) {
            return 0;
        }
        return applyHpDamage(remain);
    }

    /**
     * 直接扣血，【不碰护盾】。
     *
     * 为什么需要它：伤害要走"先扣护盾 → 得基础伤害 → 再乘击破 ×1.1"这个顺序。
     * 这个顺序没法交给 applyDamage() 一次做完（它内部会再扣一次护盾，等于重复抵扣），
     * 所以由 BattleSystem 自己算好护盾和加成，最后用这个方法落到生命上。
     *
     * @return 实际掉的生命值
     */
    public int applyHpDamage(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = hp;
        hp -= amount;
        if (hp < 0) {
            hp = 0;               // 不做负生命值，免得后面到处判断
        }
        return before - hp;
    }

    /**
     * 治疗。不会超过生命上限。
     *
     * @return 实际恢复的生命值（满血时返回 0）
     */
    public int heal(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = hp;
        hp = Math.min(getMaxHp(), hp + amount);
        return hp - before;
    }

    /**
     * 加护盾（叠加）。护盾上限没定义，所以不封顶。
     * 注意：阶段 4 的规则是"护盾不叠加，新值 > 旧值才替换"，那条规则写在 BattleSystem 里，
     * 没用这个方法。保留它是为了别处真的需要叠加时还能用。
     */
    public void addShield(int amount) {
        if (amount > 0) {
            shield += amount;
        }
    }

    /**
     * 加一个减益。同类型的已经有了就【覆盖刷新】（取新的 value 和 duration），
     * 不叠加层数——这样最简单，也避免同一状态算两次。
     */
    public void addDebuff(StatusEffect effect) {
        putEffect(debuffs, effect);
    }

    /** 加一个增益。含义同上（当前 StatusEffect 只有减益类型，所以实际很少用到） */
    public void addBuff(StatusEffect effect) {
        putEffect(buffs, effect);
    }

    /** 这个单位身上有没有指定类型的减益 */
    public boolean hasDebuff(StatusEffect.Type type) {
        for (StatusEffect e : debuffs) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    /** 取指定类型的减益，没有就返回 null */
    public StatusEffect getDebuff(StatusEffect.Type type) {
        for (StatusEffect e : debuffs) {
            if (e.type == type) {
                return e;
            }
        }
        return null;
    }

    private static void putEffect(ArrayList<StatusEffect> list, StatusEffect effect) {
        if (effect == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).type == effect.type) {
                list.set(i, effect);    // 覆盖刷新
                return;
            }
        }
        list.add(effect);
    }

    /** 加临时攻击增益（叠加到已有的上面） */
    public void addTempAtkBuff(int amount) {
        tempAtkBuff += amount;
    }

    /**
     * 过一个回合。在【该单位自己的】回合结束时调用一次。
     * 做两件事：
     *   1. 所有增益/减益的剩余回合 -1，减到 0 的移除
     *   2. 清空临时攻击增益（所以"±攻击力 buff"正好是持续 1 回合）
     *
     * 注意：这里【不】处理灼烧掉血、冻结跳回合——那些在【回合开始时】由 BattleSystem 处理。
     * 这个分工很关键：如果减持续时间放在"施加者的回合结束"，那么刚施加的 1 回合状态
     * 会立刻被减掉，等于没生效。
     */
    /**
     * 过一个回合。在【该单位自己的】回合结束时调用一次。
     * 只做一件事：所有增益/减益的剩余回合 -1，减到 0 的移除。
     *
     * 注意两点，都是踩过的坑：
     *   1. 这里【不】处理灼烧掉血、冻结跳回合——那些在【回合开始时】由 BattleSystem 处理。
     *      如果减持续时间放在"施加者的回合结束"，那么刚施加的 1 回合状态会被立刻减掉，
     *      等于根本没生效。
     *   2. 这里【不】清 tempAtkBuff。因为"自己给自己加攻击力"如果在本回合结束就清掉，
     *      也等于永远用不上（本回合的牌已经出完了）。它的生命周期由 BattleSystem 控制：
     *      在"真正用到了它的那个回合"结束时清零，这样才叫真的"持续 1 回合"。
     */
    public void tick() {
        tickList(buffs);
        tickList(debuffs);
    }

    private static void tickList(ArrayList<StatusEffect> list) {
        // 倒着遍历，这样删除元素不会打乱后面的下标
        for (int i = list.size() - 1; i >= 0; i--) {
            StatusEffect e = list.get(i);
            e.duration--;
            if (e.duration <= 0) {
                list.remove(i);
            }
        }
    }

    @Override
    public String toString() {
        return config.name + " hp=" + hp + "/" + getMaxHp()
                + " 护盾=" + shield
                + " 能量=" + energy
                + " 韧性=" + toughness + "/" + getMaxToughness()
                + (broken ? "(已击破)" : "")
                + " 行动值=" + String.format("%.1f", actionValue)
                + " 临时攻击+" + tempAtkBuff
                + " 减益=" + debuffs;
    }
}
