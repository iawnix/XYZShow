package io.iaw.molview;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

final class AppTheme {
    static int accent(Context context) {
        return color(context, R.color.accent);
    }

    static int accentActive(Context context) {
        return color(context, R.color.accent_active);
    }

    private static final String PREFS = "xyzshow_ui";
    private static final String KEY_LIGHT = "light_background";

    private AppTheme() {
    }

    static boolean isLight(Context context) {
        bind(context);
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_LIGHT)) {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LIGHT, false);
        }
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }

    static void setLight(Context context, boolean light) {
        bind(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIGHT, light)
                .apply();
    }

    static void applySystemBars(Activity activity, boolean light) {
        bind(activity);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(),
                activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    static int bg(boolean light) {
        return color(light, R.color.app_bg_light, R.color.app_bg_dark);
    }

    static int panel(boolean light) {
        return color(light, R.color.app_panel_light, R.color.app_panel_dark);
    }

    static int button(boolean light) {
        return color(light, R.color.app_button_light, R.color.app_button_dark);
    }

    static int buttonPressed(boolean light) {
        return color(light, R.color.app_button_pressed_light, R.color.app_button_pressed_dark);
    }

    static int buttonFocused(boolean light) {
        return color(light, R.color.app_button_focused_light, R.color.app_button_focused_dark);
    }

    static int buttonDisabled(boolean light) {
        return color(light, R.color.app_button_disabled_light, R.color.app_button_disabled_dark);
    }

    static int border(boolean light) {
        return color(light, R.color.app_border_light, R.color.app_border_dark);
    }

    static int borderSoft(boolean light) {
        return color(light, R.color.app_border_soft_light, R.color.app_border_soft_dark);
    }

    static int text(boolean light) {
        return color(light, R.color.app_text_light, R.color.app_text_dark);
    }

    static int textDisabled(boolean light) {
        return color(light, R.color.app_text_disabled_light, R.color.app_text_disabled_dark);
    }

    static int secondary(boolean light) {
        return color(light, R.color.app_secondary_light, R.color.app_secondary_dark);
    }

    static int gutter(boolean light) {
        return color(light, R.color.app_gutter_light, R.color.app_gutter_dark);
    }

    static int gutterBg(boolean light) {
        return color(light, R.color.app_gutter_bg_light, R.color.app_gutter_bg_dark);
    }

    static int rail(boolean light) {
        return color(light, R.color.app_rail_light, R.color.app_rail_dark);
    }

    static int activePressed(boolean light) {
        return color(light, R.color.app_active_pressed_light, R.color.app_active_pressed_dark);
    }

    static int active(boolean light) {
        return color(light, R.color.app_active_light, R.color.app_active_dark);
    }

    static int spectrumBg(boolean light) {
        return color(light, R.color.app_spectrum_bg_light, R.color.app_spectrum_bg_dark);
    }

    static int spectrumImaginary(boolean light) {
        return color(light, R.color.app_spectrum_imag_light, R.color.app_spectrum_imag_dark);
    }

    static int spectrumBar(boolean light) {
        return color(light, R.color.app_spectrum_bar_light, R.color.app_spectrum_bar_dark);
    }

    private static int color(boolean light, int lightColor, int darkColor) {
        if (AppContextHolder.context == null) {
            throw new IllegalStateException("AppTheme.bind(context) must be called before resolving theme colors");
        }
        return color(AppContextHolder.context, light ? lightColor : darkColor);
    }

    private static int color(Context context, int colorResId) {
        return ContextCompat.getColor(context, colorResId);
    }

    static void bind(Context context) {
        AppContextHolder.context = context.getApplicationContext();
    }

    private static final class AppContextHolder {
        private static Context context;
    }
}
