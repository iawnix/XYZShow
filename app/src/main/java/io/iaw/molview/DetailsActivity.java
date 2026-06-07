package io.iaw.molview;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public final class DetailsActivity extends Activity {
    static final String EXTRA_TITLE = "io.iaw.molview.extra.TITLE";
    static final String EXTRA_SUMMARY = "io.iaw.molview.extra.SUMMARY";
    static final String EXTRA_ELEMENTS = "io.iaw.molview.extra.ELEMENTS";
    static final String EXTRA_ELEMENT_COLORS = "io.iaw.molview.extra.ELEMENT_COLORS";
    static final String EXTRA_FREQUENCIES = "io.iaw.molview.extra.FREQUENCIES";
    static final String EXTRA_IR_INTENSITIES = "io.iaw.molview.extra.IR_INTENSITIES";
    static final String EXTRA_SELECTED_MODE = "io.iaw.molview.extra.SELECTED_MODE";

    private ConstraintLayout root;
    private LinearLayout header;
    private LinearLayout content;
    private LinearLayout legendView;
    private TextView titleView;
    private TextView bodyView;
    private FrameLayout spectrumHost;
    private SpectrumView spectrumView;
    private MaterialButton closeButton;
    private boolean light;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.bind(this);
        light = AppTheme.isLight(this);
        AppTheme.applySystemBars(this, light);
        buildLayout();
        titleView.setText(getIntent().getStringExtra(EXTRA_TITLE));
        bodyView.setText(getIntent().getStringExtra(EXTRA_SUMMARY));
        buildElementLegend(
                getIntent().getStringArrayExtra(EXTRA_ELEMENTS),
                getIntent().getIntArrayExtra(EXTRA_ELEMENT_COLORS));
        buildSpectrum(
                getIntent().getFloatArrayExtra(EXTRA_FREQUENCIES),
                getIntent().getFloatArrayExtra(EXTRA_IR_INTENSITIES),
                getIntent().getIntExtra(EXTRA_SELECTED_MODE, -1));
    }

    private void buildLayout() {
        setContentView(R.layout.activity_details);
        root = findViewById(R.id.details_root);
        header = findViewById(R.id.details_header);
        titleView = findViewById(R.id.details_title);
        closeButton = findViewById(R.id.details_close);
        content = findViewById(R.id.details_content);
        bodyView = findViewById(R.id.details_body);
        legendView = findViewById(R.id.details_legend);
        spectrumHost = findViewById(R.id.details_spectrum_host);

        root.setBackgroundColor(AppTheme.bg(light));
        header.setBackgroundColor(AppTheme.panel(light));
        titleView.setTextColor(AppTheme.text(light));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        configureIconButton(closeButton, "Close", R.drawable.ic_close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        bodyView.setTextColor(AppTheme.secondary(light));
        spectrumView = new SpectrumView(this);
        spectrumHost.addView(spectrumView, new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        spectrumHost.setVisibility(View.GONE);
        applyInsets();
    }

    private void configureIconButton(MaterialButton button, String description, int imageResId) {
        button.setIconResource(imageResId);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setTextColor(iconTint());
        button.setIconTint(iconTint());
        button.setBackgroundTintList(ColorStateList.valueOf(AppTheme.button(light)));
        button.setStrokeColor(ColorStateList.valueOf(AppTheme.border(light)));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(20));
        button.setRippleColor(ColorStateList.valueOf(AppTheme.accent(this)));
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
        return round(color, dp(21));
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), AppTheme.border(light));
        return drawable;
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                WindowInsetsCompat compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, view);
                Insets bars = compatInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                header.setPadding(dp(14) + bars.left,
                        dp(10) + bars.top,
                        dp(14) + bars.right,
                        dp(10));
                content.setPadding(dp(16) + bars.left,
                        dp(16),
                        dp(16) + bars.right,
                        dp(24) + bars.bottom);
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private void buildElementLegend(String[] elements, int[] colors) {
        legendView.removeAllViews();
        if (elements == null || colors == null || elements.length == 0) {
            legendView.setVisibility(View.GONE);
            return;
        }
        int count = Math.min(elements.length, colors.length);
        for (int i = 0; i < count; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            View swatch = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setColor(colors[i]);
            shape.setCornerRadius(dp(4));
            shape.setStroke(dp(1), AppTheme.border(light));
            swatch.setBackground(shape);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(28), dp(20));
            swatchParams.setMarginEnd(dp(10));
            row.addView(swatch, swatchParams);

            TextView symbol = new TextView(this);
            symbol.setText(elements[i]);
            symbol.setTextColor(AppTheme.text(light));
            symbol.setTextSize(14);
            symbol.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(symbol, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            legendView.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        legendView.setVisibility(View.VISIBLE);
    }

    private void buildSpectrum(float[] frequencies, float[] intensities, int selectedMode) {
        if (frequencies == null || frequencies.length == 0) {
            spectrumHost.setVisibility(View.GONE);
            return;
        }
        spectrumView.setData(frequencies, intensities, selectedMode);
        spectrumView.setModeListener(new SpectrumView.ModeListener() {
            @Override
            public void onModeSelected(int index) {
                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_MODE, index);
                setResult(RESULT_OK, data);
                finish();
            }
        });
        spectrumHost.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SpectrumView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private float[] frequencies = new float[0];
        private float[] intensities = new float[0];
        private int selectedIndex = -1;
        private ModeListener listener;

        SpectrumView(Activity activity) {
            super(activity);
            setBackground(round(AppTheme.spectrumBg(light), dp(10)));
            setPadding(dp(12), dp(12), dp(12), dp(14));
        }

        void setData(float[] frequencies, float[] intensities, int selectedIndex) {
            this.frequencies = frequencies == null ? new float[0] : frequencies;
            this.intensities = intensities == null ? new float[0] : intensities;
            this.selectedIndex = selectedIndex;
            invalidate();
        }

        void setModeListener(ModeListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (frequencies.length == 0) {
                return;
            }
            float left = getPaddingLeft();
            float top = getPaddingTop();
            float right = getWidth() - getPaddingRight();
            float bottom = getHeight() - getPaddingBottom();
            float height = Math.max(1f, bottom - top);
            paint.setStrokeWidth(dp(1));
            paint.setColor(AppTheme.gutter(light));
            canvas.drawLine(left, bottom, right, bottom, paint);

            float maxIntensity = maxIntensity();
            float step = (right - left) / Math.max(1, frequencies.length);
            float barWidth = Math.max(dp(2), step * 0.62f);
            for (int i = 0; i < frequencies.length; i++) {
                float value = intensityAt(i, maxIntensity);
                float barHeight = Math.max(dp(4), value * height * 0.86f);
                float cx = left + step * i + step * 0.5f;
                bar.set(cx - barWidth * 0.5f, bottom - barHeight, cx + barWidth * 0.5f, bottom);
                if (i == selectedIndex) {
                    paint.setColor(AppTheme.accent(DetailsActivity.this));
                } else if (frequencies[i] < 0f) {
                    paint.setColor(AppTheme.spectrumImaginary(light));
                } else {
                    paint.setColor(AppTheme.spectrumBar(light));
                }
                canvas.drawRoundRect(bar, dp(2), dp(2), paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP || frequencies.length == 0) {
                return true;
            }
            int index = modeAt(event.getX());
            if (index >= 0) {
                selectedIndex = index;
                invalidate();
                if (listener != null) {
                    listener.onModeSelected(index);
                }
            }
            return true;
        }

        private int modeAt(float x) {
            float left = getPaddingLeft();
            float right = getWidth() - getPaddingRight();
            float step = (right - left) / Math.max(1, frequencies.length);
            int index = (int) ((x - left) / Math.max(1f, step));
            return Math.max(0, Math.min(index, frequencies.length - 1));
        }

        private float maxIntensity() {
            float max = 0f;
            for (int i = 0; i < frequencies.length; i++) {
                float value = i < intensities.length ? intensities[i] : Float.NaN;
                if (!Float.isNaN(value) && value > 0f) {
                    max = Math.max(max, value);
                }
            }
            return max <= 0f ? 1f : max;
        }

        private float intensityAt(int index, float maxIntensity) {
            float value = index < intensities.length ? intensities[index] : Float.NaN;
            if (Float.isNaN(value) || value <= 0f) {
                return 0.45f;
            }
            return Math.max(0.08f, Math.min(1f, value / maxIntensity));
        }

        interface ModeListener {
            void onModeSelected(int index);
        }
    }
}
