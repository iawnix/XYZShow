package io.iaw.molview;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;

final class AppTheme {
    static final int ACCENT = 0xff0a84ff;

    private static final String PREFS = "xyzshow_ui";
    private static final String KEY_LIGHT = "light_background";

    private AppTheme() {
    }

    static boolean isLight(Context context) {
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_LIGHT)) {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LIGHT, false);
        }
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }

    static void setLight(Context context, boolean light) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIGHT, light)
                .apply();
    }

    static void applySystemBars(Activity activity, boolean light) {
        Window window = activity.getWindow();
        window.setStatusBarColor(panel(light));
        window.setNavigationBarColor(panel(light));
        window.getDecorView().setSystemUiVisibility(light
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0);
    }

    static int bg(boolean light) {
        return light ? 0xfff6f6f7 : 0xff1c1c1e;
    }

    static int panel(boolean light) {
        return light ? 0xffe9e9eb : 0xff242426;
    }

    static int button(boolean light) {
        return light ? 0xfffdfdfd : 0xff2c2c2e;
    }

    static int border(boolean light) {
        return light ? 0xffc7c7cc : 0xff545458;
    }

    static int text(boolean light) {
        return light ? 0xff1d1d1f : 0xfff2f2f7;
    }

    static int secondary(boolean light) {
        return light ? 0xff4f4f55 : 0xffc7c7cc;
    }

    static int gutter(boolean light) {
        return light ? 0xff7a7a80 : 0xff8e8e93;
    }
}
