# /home/djx/game/android/assets/fonts/README.txt

## 这个目录里有什么
NotoSansCJK-Regular.ttc —— 中文字体（19 MB）。

## 为什么必须有它
libGDX 自带的 BitmapFont 只有 ASCII，汉字画出来是空白方块。
要显示中文，必须带一个中文字体文件，用 gdx-freetype 在运行时生成字形。
core/build.gradle 里已经加了 gdx-freetype 依赖。

## 为什么是 .ttc，以及那个 2 是什么意思
.ttc 是"字体集合"（一个文件里装了好几个字体）。系统里只有这个格式的中文全字库。
NotoSansCJK-Regular.ttc 里一共 10 个 face，实测序号是：

    0 = Noto Sans CJK JP      5 = Noto Sans Mono CJK JP
    1 = Noto Sans CJK KR      6 = Noto Sans Mono CJK KR
    2 = Noto Sans CJK SC  ←我们用的（简体中文）
    3 = Noto Sans CJK TC      8 = Noto Sans Mono CJK TC
    4 = Noto Sans CJK HK      9 = Noto Sans Mono CJK HK
                              7 = Noto Sans Mono CJK SC

用法：new FreeTypeFontGenerator(fileHandle, 2)  ← 第二个参数就是 face 序号。

## 【踩过的坑，务必记住】不要用 DroidSansFallbackFull.ttf
这台机器上还有一个"单文件"中文字体：
    /usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf
它【看起来】更省事（不用管 face 序号），但实测它的字符集是：

    20 e3f 1100-1112 ...

意思是：它只有空格 + CJK，【完全没有拉丁字母和数字】。
它本来就是个"给别的字体打补丁"的兜底字体。
用它的话：汉字正常，但 "1080"、"FPS 60"、"1.00" 这些全都画不出来，
而且【不会报任何错】，只是静默地少画字，极难发现。

所以：换字体后，一定要用 FreeTypeFontGenerator.hasGlyph() 检查
汉字和数字/字母是否都在。core 里阶段 1 写字体加载时会带上这道自检。

## 想减肥的话
这个字体占 19 MB，是 APK 体积的大头。当前阶段（纯色画面）其实用不到它。
删掉本目录不影响阶段 0 的构建和运行；阶段 1 需要它时再拷回来即可：

    cp /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc \
       /home/djx/game/android/assets/fonts/

## 许可证
Noto Sans CJK 是 SIL Open Font License 1.1，可以随游戏分发（含商用）。
系统上的许可证全文：/usr/share/doc/fonts-noto-cjk/copyright
