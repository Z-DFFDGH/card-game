// /home/djx/game/core/src/main/java/com/game/cardgame/model/StatusEffect.java
package com.game.cardgame.model;

/**
 * 一个状态效果（buff/debuff 条目）。只包含 类型 / 值 / 持续时间 三样。
 *
 * 按你的规格，类型目前【只有灼烧和冻结】两种。
 * 注意后果：这两个本质都是减益，所以 Fighter 里的「增益」列表现在恒为空，
 * 是留给以后扩展的坑位。要真正支持增益（攻击力提升之类），
 * 得给下面的 Type 加类型，或者另建一个 Buff 类。
 */
public final class StatusEffect {

    /** 状态类型。JSON 里没有对应的数据，由战斗规则在运行期创建 */
    public enum Type {
        /** 灼烧：每回合按 value 掉血 */
        BURN("灼烧"),
        /** 冻结：行动受阻（具体规则阶段 4 定） */
        FREEZE("冻结");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 状态类型，创建后不变 */
    public final Type type;

    /** 数值：灼烧=每回合伤害；冻结=强度（具体含义阶段 4 定）。创建后不变 */
    public final int value;

    /** 剩余持续回合数。每次 Fighter.tick() 减 1，减到 0 就被移除，所以是可变的 */
    public int duration;

    public StatusEffect(Type type, int value, int duration) {
        this.type = type;
        this.value = value;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return type.label + "(" + value + ", 剩 " + duration + " 回合)";
    }
}
