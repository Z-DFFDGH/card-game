// /home/djx/game/core/src/main/java/com/game/cardgame/model/Element.java
package com.game.cardgame.model;

/**
 * 属性（元素）。cards.json 的 attribute、characters.json 的 属性/弱点 都用这一套。
 *
 * 为什么值用英文、再挂一个中文 label：
 *   JSON 里是中文（"火"、"物理"…），但 Java 里用中文枚举常量名会很难受
 *   （虽然语法合法），所以常量用英文，用 label 存中文，两边靠 fromLabel() 对上。
 *   这样改显示文案时也只改一处。
 */
public enum Element {

    FIRE("火"),
    ICE("冰"),
    LIGHTNING("雷"),
    WIND("风"),
    PHYSICAL("物理"),
    QUANTUM("量子"),
    IMAGINARY("虚数");

    /** JSON 里出现的中文写法，也是界面上显示的名字 */
    public final String label;

    Element(String label) {
        this.label = label;
    }

    /**
     * 把 JSON 里的中文属性名转成枚举。
     * 找不到就抛异常，异常信息里带上全部合法值，方便你一眼看出是写错了还是漏了。
     */
    public static Element fromLabel(String label) {
        for (Element e : values()) {
            if (e.label.equals(label)) {
                return e;
            }
        }
        throw new IllegalArgumentException(
                "未知属性「" + label + "」，合法值只有：" + allLabels());
    }

    /** 拼出 "火/冰/雷/风/物理/量子/虚数"，只用于报错信息 */
    public static String allLabels() {
        StringBuilder sb = new StringBuilder();
        for (Element e : values()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(e.label);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return label;
    }
}
