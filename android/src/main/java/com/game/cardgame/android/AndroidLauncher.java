// /home/djx/game/android/src/main/java/com/game/cardgame/android/AndroidLauncher.java
package com.game.cardgame.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import com.game.cardgame.GameMain;

/**
 * Android 启动器。它只做两件事：关掉用不到的传感器，然后把 GameMain 交出去。
 *
 * 竖屏锁定不在这里，而在 android/AndroidManifest.xml 的
 * android:screenOrientation="portrait" 上。
 */
public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // 纯触摸操作，不需要这些传感器。关掉省电，也少一类输入干扰。
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;

        initialize(new GameMain(), config);
    }
}
