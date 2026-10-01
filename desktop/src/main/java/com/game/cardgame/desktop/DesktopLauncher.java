// /home/djx/game/desktop/src/main/java/com/game/cardgame/desktop/DesktopLauncher.java
package com.game.cardgame.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import com.game.cardgame.GameMain;

/**
 * 桌面启动器。
 *
 * 运行方式（在 /home/djx/game 目录下）：
 *   ./gradlew desktop:run
 *
 * 窗口 360x640，正好是虚拟分辨率 720x1280 的一半，保持竖屏比例，
 * 电脑上看到的就是手机上按比例缩小的效果。
 */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("卡牌对战 · 桌面调试");
        config.setWindowedMode(360, 640);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new GameMain(), config);
    }
}
