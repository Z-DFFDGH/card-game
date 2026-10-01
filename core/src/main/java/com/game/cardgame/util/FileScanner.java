// /home/djx/game/core/src/main/java/com/game/cardgame/util/FileScanner.java
package com.game.cardgame.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * 扫描素材目录，把"文件名（去掉后缀） → 相对路径"整理成一张表。
 *
 * 用途：立绘是按「角色名.png」命名的，所以要先知道目录里到底有哪些图，
 * 再去和 characters.json 里的角色名对。
 *
 * 关键点：
 *   1. 后缀大小写不敏感（.PNG / .Jpg 都认）。
 *   2. 目录不存在、或者目录是空的、或者读取过程抛异常 —— 一律打警告 + 返回空表，
 *      【绝对不抛异常】。素材缺失不该让游戏起不来，界面上会用占位图顶着。
 *   3. Gdx.files.internal 的 list() 在目录不存在时是会抛 GdxRuntimeException 的，
 *      所以这里必须先 exists() 判断，再 try/catch 兜一层。
 */
public final class FileScanner {

    private static final String TAG = "FileScanner";

    /** 认的图片后缀（小写） */
    public static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private FileScanner() {
    }

    /**
     * 扫描某个素材目录。
     *
     * @param dirInternalPath internal 路径，不带 assets/ 前缀，例如 "images/characters"
     * @return 去后缀的文件名 → 相对路径（如 "星野" → "images/characters/星野.png"）。
     *         目录不存在或没有图片时返回空表。
     */
    public static HashMap<String, String> scanImages(String dirInternalPath) {
        HashMap<String, String> result = new HashMap<String, String>();

        FileHandle dir = Gdx.files.internal(dirInternalPath);
        if (!dir.exists()) {
            warn("素材目录不存在：" + dirInternalPath
                    + "\n（Android 上它应该在 android/assets/" + dirInternalPath + " 下）");
            return result;
        }
        if (!dir.isDirectory()) {
            warn("这不是一个目录：" + dirInternalPath);
            return result;
        }

        FileHandle[] children;
        try {
            children = dir.list();
        } catch (RuntimeException e) {
            warn("读取目录失败：" + dirInternalPath + " —— " + e.getMessage());
            return result;
        }

        if (children == null || children.length == 0) {
            warn("素材目录是空的：" + dirInternalPath);
            return result;
        }

        int skipped = 0;
        for (FileHandle child : children) {
            if (child == null || child.isDirectory()) {
                continue;
            }
            String fileName = child.name();
            if (!hasImageExtension(fileName)) {
                skipped++;
                continue;
            }
            String stem = stripExtension(fileName);
            if (stem.isEmpty()) {
                skipped++;
                continue;
            }
            String previous = result.get(stem);
            if (previous != null) {
                // 同名但不同后缀（星野.png 和 星野.jpg），保留先扫到的那个，只提醒一下
                warn("同名图片重复，忽略 " + child.path() + "（已经在用 " + previous + "）");
                continue;
            }
            result.put(stem, child.path());
        }

        if (result.isEmpty()) {
            warn("素材目录里一张图片都没有：" + dirInternalPath);
        } else {
            info("扫描 " + dirInternalPath + " → 找到 " + result.size() + " 张图片"
                    + (skipped > 0 ? "（跳过 " + skipped + " 个非图片文件）" : ""));
        }
        return result;
    }

    /**
     * 直接问"某个名字的图片存不存在"（不用列目录）。
     *
     * 【为什么必须有它 —— 打包成 exe/可执行文件时踩到的坑】
     *   Gdx.files.internal(dir).list() 列的是【文件系统】上的目录。
     *   一旦素材被打进 jar（打包后就是这样），这个目录在磁盘上并不存在，
     *   list() 返回空 → 所有角色都被判成"无立绘" → 整个游戏全是占位图。
     *   实测：不带素材目录直接双击打包好的程序，23 个角色全部显示占位图。
     *   而"某个具体文件在不在"是可以穿透 jar 查的（classpath 回退），所以加这条兜底路。
     */
    public static boolean imageExists(String dirInternalPath, String stem) {
        if (stem == null || stem.isEmpty()) {
            return false;
        }
        for (String ext : IMAGE_EXTENSIONS) {
            String path = dirInternalPath + "/" + stem + ext;
            try {
                if (Gdx.files.classpath(path).exists()) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // classpath 查询在某些后端可能抛异常，忽略，继续试文件系统
            }
            if (Gdx.files.internal(path).exists()) {
                return true;
            }
        }
        return false;
    }

    /** 后缀是不是图片（大小写不敏感） */
    public static boolean hasImageExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 去掉最后一个后缀。"a.b.png" → "a.b"；没有后缀就原样返回 */
    public static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    /** 把表里的键排好序列出来，只用于打印日志（HashMap 的遍历顺序不稳定） */
    public static String keysForLog(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "（无）";
        }
        TreeSet<String> sorted = new TreeSet<String>(map.keySet());
        StringBuilder sb = new StringBuilder();
        for (String k : sorted) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k);
        }
        return sb.toString();
    }

    private static void warn(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(TAG, message);
        } else {
            System.err.println("[" + TAG + "] " + message);
        }
    }

    private static void info(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] " + message);
        }
    }
}
