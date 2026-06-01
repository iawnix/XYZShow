package io.iaw.molview;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class DetailsActivity extends Activity {
    static final String EXTRA_TITLE = "io.iaw.molview.extra.TITLE";
    static final String EXTRA_SUMMARY = "io.iaw.molview.extra.SUMMARY";

    private static final int COLOR_BG = 0xff1c1c1e;
    private static final int COLOR_PANEL = 0xff242426;
    private static final int COLOR_BUTTON = 0xff2c2c2e;
    private static final int COLOR_BORDER = 0xff545458;
    private static final int COLOR_TEXT = 0xfff2f2f7;
    private static final int COLOR_TEXT_SECONDARY = 0xffc7c7cc;

    private LinearLayout root;
    private LinearLayout header;
    private TextView titleView;
    private TextView bodyView;
    private Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_PANEL);
        window.setNavigationBarColor(COLOR_PANEL);
        buildLayout();
        titleView.setText(getIntent().getStringExtra(EXTRA_TITLE));
        bodyView.setText(getIntent().getStringExtra(EXTRA_SUMMARY));
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(COLOR_PANEL);

        titleView = new TextView(this);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        header.addView(titleView, new LinearLayout.LayoutParams(0, dp(40), 1f));

        closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(COLOR_TEXT);
        closeButton.setTextSize(13);
        closeButton.setBackgroundColor(COLOR_BUTTON);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(78), dp(40)));

        ScrollView scroll = new ScrollView(this);
        bodyView = new TextView(this);
        bodyView.setTextColor(COLOR_TEXT_SECONDARY);
        bodyView.setTextSize(14);
        bodyView.setLineSpacing(dp(2), 1.08f);
        bodyView.setPadding(dp(16), dp(14), dp(16), dp(24));
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

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                header.setPadding(dp(12) + insets.getSystemWindowInsetLeft(),
                        dp(8) + insets.getSystemWindowInsetTop(),
                        dp(12) + insets.getSystemWindowInsetRight(),
                        dp(8));
                bodyView.setPadding(dp(16) + insets.getSystemWindowInsetLeft(),
                        dp(14),
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
