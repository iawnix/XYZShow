package io.iaw.molview;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class DetailsActivity extends Activity {
    static final String EXTRA_TITLE = "io.iaw.molview.extra.TITLE";
    static final String EXTRA_SUMMARY = "io.iaw.molview.extra.SUMMARY";

    private LinearLayout root;
    private LinearLayout header;
    private TextView titleView;
    private TextView bodyView;
    private ImageButton closeButton;
    private boolean light;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        light = AppTheme.isLight(this);
        AppTheme.applySystemBars(this, light);
        buildLayout();
        titleView.setText(getIntent().getStringExtra(EXTRA_TITLE));
        bodyView.setText(getIntent().getStringExtra(EXTRA_SUMMARY));
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(light));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackgroundColor(AppTheme.panel(light));

        titleView = new TextView(this);
        titleView.setTextColor(AppTheme.text(light));
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        titleParams.setMarginEnd(dp(12));
        header.addView(titleView, titleParams);

        closeButton = iconButton("Close", R.drawable.ic_close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        header.addView(closeButton, iconLayout(false));

        ScrollView scroll = new ScrollView(this);
        bodyView = new TextView(this);
        bodyView.setTextColor(AppTheme.secondary(light));
        bodyView.setTextSize(14);
        bodyView.setLineSpacing(dp(2), 1.08f);
        bodyView.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(bodyView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        applyInsets();
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        setContentView(root);
    }

    private ImageButton iconButton(String description, int imageResId) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResId);
        button.setImageTintList(iconTint());
        button.setBackground(buttonBackground());
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        return button;
    }

    private LinearLayout.LayoutParams iconLayout(boolean withMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
        if (withMargin) {
            params.setMarginEnd(dp(8));
        }
        return params;
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, round(AppTheme.ACCENT));
        states.addState(new int[]{}, round(AppTheme.button(light)));
        return states;
    }

    private ColorStateList iconTint() {
        return new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        AppTheme.gutter(light),
                        AppTheme.text(light)
                });
    }

    private GradientDrawable round(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(21));
        drawable.setStroke(dp(1), AppTheme.border(light));
        return drawable;
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                header.setPadding(dp(14) + insets.getSystemWindowInsetLeft(),
                        dp(10) + insets.getSystemWindowInsetTop(),
                        dp(14) + insets.getSystemWindowInsetRight(),
                        dp(10));
                bodyView.setPadding(dp(16) + insets.getSystemWindowInsetLeft(),
                        dp(16),
                        dp(16) + insets.getSystemWindowInsetRight(),
                        dp(24) + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
