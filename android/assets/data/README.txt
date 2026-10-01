# /home/djx/game/android/assets/data/README.txt
# 这个目录 = 游戏数据（JSON 配置）。本阶段是空的，阶段 2 开始往这里放。

## 计划放什么
  characters.json   角色：名称、生命、攻击、速度、韧性、弱点属性、战技/技能列表……
  cards.json        卡牌：名称、费用（战技点）、类型、效果……

## 为什么数据要和代码分开
卡牌数值、角色强度这种东西一定会反复调整。放在 JSON 里改完重开游戏就生效，
不用改 Java 代码、不用重新编译，调平衡会快很多。

## 重要：中文 JSON 的编码
JSON 文件必须存成 UTF-8（不带 BOM）。用 Android Studio 直接存就是 UTF-8。
如果用了别的编辑器存成 GBK，读出来会是乱码。

## 命名对应关系
characters.json 里的角色"名称"，必须和 ../images/characters/ 下的图片文件名一致。
详见 ../images/characters/README.txt。
