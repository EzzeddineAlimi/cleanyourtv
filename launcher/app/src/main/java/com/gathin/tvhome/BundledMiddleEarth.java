package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Installs the optional artwork and theme into the separate Middle-earth test variant.
 * Does not alter system settings, stock launchers, or another CleanYourTV installation.
 * Existing user-selected theme files always win.
 */
final class BundledMiddleEarth {
    private static final String TEST_PACKAGE = "com.ezzeddine.middleearth";
    private static final String TAG = "MiddleEarth";
    private BundledMiddleEarth() {}

    static void installIfNeeded(Context context) {
        if (!TEST_PACKAGE.equals(context.getPackageName())) return;
        File dir = context.getExternalFilesDir(null);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            Log.w(TAG, "Theme directory unavailable");
            return;
        }
        copyIfAbsent(context, "middle_earth_config.json", new File(dir, "config.json"));
        copyIfAbsent(context, "middle_earth_wallpaper.jpg", new File(dir, "wallpaper.jpg"));
    }

    private static void copyIfAbsent(Context context, String asset, File target) {
        if (target.exists()) return;
        try (InputStream input = context.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = input.read(buf)) != -1) output.write(buf, 0, n);
        } catch (IOException error) {
            target.delete();
            Log.w(TAG, "Could not install optional theme asset " + asset, error);
        }
    }
}
