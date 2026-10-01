// /home/djx/game/core/src/main/java/com/game/cardgame/model/Character.java
package com.game.cardgame.model;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 一个角色的【只读配置】，对应 android/assets/data/characters.json 里的一条。
 *
 * 注意类名：这个 Character 会和 java.lang.Character 撞名。
 * 凡是 import 了本类的文件，就不能再用 java.lang.Character 这个短名字了
 * （真要用的地方得写 java.lang.Character）。目前本工程用不到它，但改代码时要心里有数。
 *
 * 所有字段 public final：这是配置，运行期不许改。
 * 战斗里会变的量（当前生命、护盾、能量…）在 Fighter 里。
 */
public final class Character {

    /** 角色定位。JSON: 角色 */
    public enum Role {
        /** 攻击型：hp 80~90，攻击 15 */
        ATTACK,
        /** 防御型：hp 110~120，攻击 10 */
        DEFENSE,
        /** 均衡型：hp≈100，攻击 12 */
        BALANCE
    }

    /** 唯一 id，如 c001 */
    public final String id;

    /** 显示名。必须和 images/characters/ 下的图片文件名（去后缀）一致 */
    public final String name;

    /** 生命值上限。JSON: hp */
    public final int hp;

    /** 速度，决定行动顺序。JSON: 速度 */
    public final int speed;

    /** 攻击力。JSON: 攻击 */
    public final int attack;

    /** 自身属性。JSON: 属性 */
    public final Element element;

    /** 定位。JSON: 角色 */
    public final Role role;

    /** 被动技能文本，格式「名称：描述」。JSON: 被动技能 —— 目前只是给人看的文本 */
    public final String passiveSkill;

    /** 终极技能文本，格式「名称：描述」。JSON: 终极技能 */
    public final String ultimateSkill;

    /** 弱点属性，单选。JSON: 弱点 —— 用这个属性打它，才削韧性 */
    public final Element weakness;

    /** 坚韧极限（韧性上限）。JSON: 坚韧极限 */
    public final int toughnessMax;

    /**
     * 立绘文件名（不带后缀），如 "星野"。
     * JSON: portraitFile —— 实际文件是 images/characters/星野.png 或 .jpg
     */
    public final String portraitFile;

    /** 预留扩展属性，当前恒为空对象 {}。JSON: extraAttributes */
    public final HashMap<String, String> extraAttributes;

    /** 预留装备槽位，当前恒为空数组 []。JSON: equipmentSlots */
    public final ArrayList<String> equipmentSlots;

    /** 预留状态列表，当前恒为空数组 []。JSON: statusEffects */
    public final ArrayList<String> statusEffects;

    public Character(String id, String name, int hp, int speed, int attack,
                     Element element, Role role, String passiveSkill, String ultimateSkill,
                     Element weakness, int toughnessMax, String portraitFile,
                     HashMap<String, String> extraAttributes,
                     ArrayList<String> equipmentSlots,
                     ArrayList<String> statusEffects) {
        this.id = id;
        this.name = name;
        this.hp = hp;
        this.speed = speed;
        this.attack = attack;
        this.element = element;
        this.role = role;
        this.passiveSkill = passiveSkill;
        this.ultimateSkill = ultimateSkill;
        this.weakness = weakness;
        this.toughnessMax = toughnessMax;
        this.portraitFile = portraitFile;
        this.extraAttributes = extraAttributes;
        this.equipmentSlots = equipmentSlots;
        this.statusEffects = statusEffects;
    }

    /**
     * 强度评分（0~10）。和阶段 2 校验器里用的是同一套公式：
     *   10 × [ 攻击/15 × 0.30  +  hp/120 × 0.45  +  速度/120 × 0.25 ]
     * 三个属性各自按"同类上限"归一化再加权。
     *
     * 用途：挑对手时判断谁和谁"实力相当"。当前全部 23 个角色的评分都在 7.79~8.88 之间。
     */
    public float strengthScore() {
        return 10f * (0.30f * (attack / 15f)
                + 0.45f * (hp / 120f)
                + 0.25f * (speed / 120f));
    }

    /**
     * 复制一份，只把 portraitFile 换成新的。
     *
     * 为什么要它：Character 的字段全是 final（只读配置），而立绘匹配是"加载时才知道"的结果，
     * 所以 CharacterGenerator 需要拿 JSON 读出来的角色再造一份、只换立绘名。
     * 放在这里而不是让 Generator 自己拼，是为了让字段清单只有一份，以后加字段不会漏。
     */
    public Character withPortraitFile(String newPortraitFile) {
        return new Character(id, name, hp, speed, attack, element, role,
                passiveSkill, ultimateSkill, weakness, toughnessMax,
                newPortraitFile, extraAttributes, equipmentSlots, statusEffects);
    }

    @Override
    public String toString() {
        return "[" + id + "] " + name
                + " 定位=" + role
                + " 属性=" + element.label
                + " 弱点=" + weakness.label
                + " hp=" + hp
                + " 速度=" + speed
                + " 攻击=" + attack
                + " 坚韧极限=" + toughnessMax
                + " 立绘=" + portraitFile;
    }
}
