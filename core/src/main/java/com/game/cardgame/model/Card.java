// /home/djx/game/core/src/main/java/com/game/cardgame/model/Card.java
package com.game.cardgame.model;

import java.util.ArrayList;

/**
 * 一张卡牌的【只读配置】，对应 android/assets/data/cards.json 里的一条。
 *
 * 设计原则：
 *   1. 所有字段都是 public final —— 配置数据，运行期不许改。
 *      战斗中要变的量（生命、手牌…）在 Fighter / GameState 里，不在这里。
 *   2. 不写 getter/setter。字段名就是语义，少一层包装，读代码更快。
 *   3. 对象由 JsonLoader 用全参数构造器创建，自身不负责解析 JSON。
 *      （libGDX 的 Json 是按字段名反射映射的，而我们 JSON 里的字段名是中文，
 *        所以走显式映射，顺便还能给出「第几个、哪个字段错了」的报错。）
 */
public final class Card {

    /** 稀有度。JSON: rarity */
    public enum Rarity {
        N, R, SR
    }

    /** 卡牌类型。JSON: type */
    public enum Type {
        /** 普攻 */
        NORMAL,
        /** 战技 */
        SKILL,
        /** 终结技（cost 仅作平衡参考，实际能不能放由能量决定） */
        ULTIMATE
    }

    /** 效果类型。JSON: effectType。每张卡只有 1 个效果，不做复合 */
    public enum EffectType {
        DAMAGE,
        HEAL,
        SHIELD,
        DRAW,
        BUFF,
        DEBUFF
    }

    /** 唯一 id，如 n001 / s003 / u002 */
    public final String id;

    /** 显示名，如「燎原一击」 */
    public final String name;

    public final Rarity rarity;

    public final Type type;

    /** 消耗的战技点。终结技的这个值只是平衡参考 */
    public final int cost;

    /** 卡牌属性，也是造成伤害时的属性（打弱点要用它） */
    public final Element attribute;

    public final EffectType effectType;

    /**
     * 效果数值，单位随 effectType 变：
     *   DAMAGE / HEAL / SHIELD —— 点数
     *   DRAW                  —— 张数
     *   BUFF / DEBUFF         —— 百分比（25 表示 25%）
     */
    public final int effectValue;

    /** 卡面描述文本 */
    public final String description;

    /** 预留标签位，当前恒为空数组 */
    public final ArrayList<String> tags;

    public Card(String id, String name, Rarity rarity, Type type, int cost,
                Element attribute, EffectType effectType, int effectValue,
                String description, ArrayList<String> tags) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.type = type;
        this.cost = cost;
        this.attribute = attribute;
        this.effectType = effectType;
        this.effectValue = effectValue;
        this.description = description;
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "[" + id + "] " + name
                + " 稀有=" + rarity
                + " 类型=" + type
                + " 费用=" + cost
                + " 属性=" + attribute.label
                + " 效果=" + effectType + "(" + effectValue + ")"
                + " tags=" + tags;
    }
}
