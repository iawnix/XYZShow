package io.iaw.molview;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MainActivity extends Activity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private static final int REQUEST_OPEN = 41;
    private static final int COLOR_BG = 0xff1c1c1e;
    private static final int COLOR_PANEL = 0xff242426;
    private static final int COLOR_BUTTON = 0xff2c2c2e;
    private static final int COLOR_BUTTON_PRESSED = 0xff3a3a3c;
    private static final int COLOR_BUTTON_FOCUSED = 0xff33363a;
    private static final int COLOR_BUTTON_DISABLED = 0xff252527;
    private static final int COLOR_BORDER = 0xff545458;
    private static final int COLOR_BORDER_SOFT = 0xff3a3a3c;
    private static final int COLOR_TEXT = 0xfff2f2f7;
    private static final int COLOR_TEXT_SECONDARY = 0xffaeaeb2;
    private static final int COLOR_TEXT_DISABLED = 0xff6e6e73;
    private static final int COLOR_ACCENT = 0xff0a84ff;
    private static final int COLOR_LIGHT_BG = 0xfff6f6f7;
    private static final int COLOR_LIGHT_PANEL = 0xffe9e9eb;
    private static final int COLOR_LIGHT_BUTTON = 0xfffdfdfd;
    private static final int COLOR_LIGHT_BUTTON_PRESSED = 0xffd8d8dc;
    private static final int COLOR_LIGHT_BUTTON_FOCUSED = 0xffeef5ff;
    private static final int COLOR_LIGHT_BUTTON_DISABLED = 0xffededf0;
    private static final int COLOR_LIGHT_BORDER = 0xffc7c7cc;
    private static final int COLOR_LIGHT_BORDER_SOFT = 0xffd1d1d6;
    private static final int COLOR_LIGHT_TEXT = 0xff1d1d1f;
    private static final int COLOR_LIGHT_TEXT_SECONDARY = 0xff6e6e73;
    private static final int COLOR_LIGHT_TEXT_DISABLED = 0xffa1a1a6;
    private static final String DEFAULT_ASSET = "samples/dopamine.xyz";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MoleculeView moleculeView;
    private TextView titleView;
    private TextView infoView;
    private TextView statusView;
    private TextView frameView;
    private Button playButton;
    private Button modeButton;
    private ImageButton infoButton;
    private ImageButton backgroundButton;
    private LinearLayout rootLayout;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private SeekBar frameSeek;
    private Molecule current;
    private String currentSourceLabel = "";
    private boolean playing;
    private boolean lightBackground;
    private boolean infoVisible;
    private int vibrationIndex;
    private int vibrationTick;

    private final Runnable playTick = new PlaybackTick();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        buildLayout();
        loadDefaultMolecule();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (moleculeView != null) {
            moleculeView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (moleculeView != null) {
            moleculeView.onResume();
        }
    }

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_BG);

        toolbar = row();
        toolbar.setBackgroundColor(COLOR_PANEL);
        toolbar.setPadding(dp(8), dp(8), dp(8), dp(6));
        Button openButton = button("Open");
        openButton.setId(1);
        openButton.setOnClickListener(this);
        modeButton = button(styleButtonText());
        modeButton.setId(3);
        modeButton.setOnClickListener(this);
        Button resetButton = button("Reset");
        resetButton.setId(7);
        resetButton.setOnClickListener(this);
        infoButton = iconButton(R.drawable.ic_info);
        infoButton.setId(9);
        infoButton.setContentDescription("Info");
        infoButton.setOnClickListener(this);
        backgroundButton = iconButton(R.drawable.ic_bg_dark);
        backgroundButton.setId(8);
        backgroundButton.setOnClickListener(this);
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(36), 1f));
        toolbar.addView(openButton, buttonLayout(dp(58)));
        toolbar.addView(modeButton, buttonLayout(dp(104)));
        toolbar.addView(resetButton, buttonLayout(dp(58)));
        toolbar.addView(infoButton, buttonLayout(dp(38)));
        toolbar.addView(backgroundButton, buttonLayoutLast(dp(38)));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(36), 1f));

        titleView = label("", 15, COLOR_TEXT);
        titleView.setGravity(Gravity.CENTER);
        titleView.setBackgroundColor(COLOR_PANEL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        infoView = label("", 12, COLOR_TEXT_SECONDARY);
        infoView.setGravity(Gravity.CENTER);
        infoView.setBackgroundColor(COLOR_PANEL);
        infoView.setPadding(dp(12), dp(4), dp(12), dp(6));
        infoView.setMaxLines(3);
        infoView.setEllipsize(TextUtils.TruncateAt.END);
        infoView.setLineSpacing(0f, 1.05f);
        infoView.setVisibility(View.GONE);

        moleculeView = new MoleculeView(this);
        modeButton.setText(styleButtonText());
        rootLayout.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rootLayout.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
        ));
        rootLayout.addView(infoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));
        rootLayout.addView(moleculeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        controls = row();
        controls.setBackgroundColor(COLOR_PANEL);
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        Button prevButton = button("Prev");
        prevButton.setId(4);
        prevButton.setOnClickListener(this);
        playButton = button("Play");
        playButton.setId(5);
        playButton.setOnClickListener(this);
        Button nextButton = button("Next");
        nextButton.setId(6);
        nextButton.setOnClickListener(this);
        frameSeek = new SeekBar(this);
        frameSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(frameSeek);
        frameView = label("1/1", 13, COLOR_TEXT_SECONDARY);
        frameView.setGravity(Gravity.CENTER);
        controls.addView(prevButton, buttonLayout(dp(62)));
        controls.addView(playButton, buttonLayout(dp(62)));
        controls.addView(nextButton, buttonLayout(dp(62)));
        controls.addView(frameSeek, new LinearLayout.LayoutParams(0, dp(42), 1f));
        controls.addView(frameView, new LinearLayout.LayoutParams(dp(64), dp(42)));

        statusView = label("", 12, COLOR_TEXT_SECONDARY);
        statusView.setBackgroundColor(COLOR_PANEL);
        statusView.setPadding(dp(10), 0, dp(10), dp(8));
        statusView.setSingleLine(true);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        applySystemInsets(rootLayout, toolbar, titleView, infoView, controls, statusView);

        rootLayout.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rootLayout.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        applyChromeTheme();
        setContentView(rootLayout);
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(lightBackground ? COLOR_LIGHT_PANEL : COLOR_PANEL);
        window.setNavigationBarColor(lightBackground ? COLOR_LIGHT_PANEL : COLOR_PANEL);
        window.getDecorView().setSystemUiVisibility(lightBackground
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0);
    }

    private void applySystemInsets(
            final View root,
            final LinearLayout toolbar,
            final TextView title,
            final TextView info,
            final LinearLayout controls,
            final TextView status
    ) {
        final int toolbarLeft = dp(8);
        final int toolbarTop = dp(8);
        final int toolbarRight = dp(8);
        final int toolbarBottom = dp(6);
        final int titleLeft = dp(12);
        final int titleTop = 0;
        final int titleRight = dp(12);
        final int titleBottom = dp(6);
        final int infoLeft = dp(12);
        final int infoTop = dp(4);
        final int infoRight = dp(12);
        final int infoBottom = dp(6);
        final int controlsLeft = dp(8);
        final int controlsTop = dp(6);
        final int controlsRight = dp(8);
        final int controlsBottom = dp(8);
        final int statusLeft = dp(10);
        final int statusTop = 0;
        final int statusRight = dp(10);
        final int statusBottom = dp(8);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int left = insets.getSystemWindowInsetLeft();
                int top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight();
                int bottom = insets.getSystemWindowInsetBottom();
                toolbar.setPadding(toolbarLeft + left, toolbarTop + top, toolbarRight + right, toolbarBottom);
                title.setPadding(titleLeft + left, titleTop, titleRight + right, titleBottom);
                info.setPadding(infoLeft + left, infoTop, infoRight + right, infoBottom);
                controls.setPadding(controlsLeft + left, controlsTop, controlsRight + right, controlsBottom);
                status.setPadding(statusLeft + left, statusTop, statusRight + right, statusBottom + bottom);
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    @Override
    public void onClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        int id = view.getId();
        if (id == 1) {
            openFile();
        } else if (id == 3) {
            moleculeView.cycleMode();
            modeButton.setText(styleButtonText());
            updateStatus();
        } else if (id == 4) {
            setFrame(navigationIndex() - 1);
        } else if (id == 5) {
            togglePlayback();
        } else if (id == 6) {
            setFrame(navigationIndex() + 1);
        } else if (id == 7) {
            moleculeView.resetView();
        } else if (id == 8) {
            lightBackground = !lightBackground;
            applyChromeTheme();
        } else if (id == 9) {
            infoVisible = !infoVisible;
            updateInfoArea();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            setFrame(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        stopPlayback();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void openFile() {
        stopPlayback();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "chemical/x-xyz",
                "text/plain",
                "text/x-gaussian-output",
                "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException ignored) {
            }
        }
        try {
            String text = readUri(uri);
            String name = displayName(uri);
            displayMolecule(parseMoleculeFile(text, name), "File");
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadDefaultMolecule() {
        stopPlayback();
        try (InputStream input = getAssets().open(DEFAULT_ASSET)) {
            String text = readAll(input);
            displayMolecule(parseXyzFile(text, DEFAULT_ASSET), "Default");
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Molecule parseXyzFile(String text, String sourceName) throws IOException {
        try {
            return MoleculeParser.parseXyzOnly(text, sourceName);
        } catch (IOException ex) {
            throw new IOException("Only XYZ files are supported in this version. " + ex.getMessage());
        }
    }

    private Molecule parseMoleculeFile(String text, String sourceName) throws IOException {
        if (MoleculeParser.looksLikeGaussianOutput(text, sourceName)) {
            try {
                return MoleculeParser.parseGaussianOutput(text, sourceName);
            } catch (IOException ex) {
                throw new IOException("Cannot read Gaussian out/log file. " + ex.getMessage());
            }
        }
        try {
            return MoleculeParser.parseXyzOnly(text, sourceName);
        } catch (IOException ex) {
            throw new IOException("Only XYZ and Gaussian out/log files are supported. " + ex.getMessage());
        }
    }

    private void displayMolecule(Molecule molecule, String sourceLabel) {
        current = molecule;
        currentSourceLabel = sourceLabel;
        vibrationIndex = 0;
        vibrationTick = 0;
        moleculeView.setMolecule(molecule);
        titleView.setText(molecule.title);
        updateInfoArea();
        updateStatus();
        if (molecule.hasVibrations()) {
            frameSeek.setMax(Math.max(0, molecule.vibrationCount() - 1));
            frameSeek.setEnabled(molecule.vibrationCount() > 1);
        } else {
            frameSeek.setMax(Math.max(0, molecule.frameCount() - 1));
            frameSeek.setEnabled(molecule.frameCount() > 1);
        }
        setFrame(0);
        updatePlaybackButton();
    }

    private void setFrame(int frame) {
        if (current == null) {
            return;
        }
        if (current.hasVibrations()) {
            int count = current.vibrationCount();
            int safe = count == 0 ? 0 : (frame % count + count) % count;
            vibrationIndex = safe;
            vibrationTick = 0;
            moleculeView.setVibrationMode(safe);
            moleculeView.setVibrationPhase(0f);
            frameSeek.setProgress(safe);
            frameView.setText("F " + (safe + 1) + "/" + count);
            updateInfoArea();
            updateStatus();
            updatePlaybackButton();
            return;
        }
        int safe = current.frameCount() == 0 ? 0 : (frame % current.frameCount() + current.frameCount()) % current.frameCount();
        moleculeView.setFrameIndex(safe);
        frameSeek.setProgress(safe);
        frameView.setText((safe + 1) + "/" + current.frameCount());
    }

    private void togglePlayback() {
        if (current == null) {
            return;
        }
        if (current.hasVibrations()) {
            Molecule.VibrationMode vibration = current.vibrationAt(vibrationIndex);
            if (vibration == null || !vibration.hasDisplacement()) {
                return;
            }
        } else if (current.frameCount() <= 1) {
            return;
        }
        playing = !playing;
        updatePlaybackButton();
        handler.removeCallbacks(playTick);
        if (playing) {
            handler.post(playTick);
        }
    }

    private void stopPlayback() {
        playing = false;
        handler.removeCallbacks(playTick);
        updatePlaybackButton();
    }

    private void updatePlaybackButton() {
        if (playButton == null) {
            return;
        }
        boolean canPlay = current != null && (
                current.hasVibrations()
                        ? current.vibrationAt(vibrationIndex) != null && current.vibrationAt(vibrationIndex).hasDisplacement()
                        : current.frameCount() > 1
        );
        playButton.setEnabled(canPlay);
        playButton.setText(playing ? "Pause" : "Play");
    }

    private int navigationIndex() {
        if (current != null && current.hasVibrations()) {
            return vibrationIndex;
        }
        return moleculeView == null ? 0 : moleculeView.getFrameIndex();
    }

    private void updateStatus() {
        if (current == null || statusView == null) {
            return;
        }
        String prefix = current.hasVibrations() ? vibrationStatusText() + "  " : "";
        statusView.setText(
                prefix +
                currentSourceLabel + "  " +
                current.sourceType + "  " +
                current.summary() + "  " +
                "Style: " + moleculeView.modeName() + "  " +
                backgroundModeText()
        );
    }

    private void updateInfoArea() {
        if (current == null || infoView == null) {
            return;
        }
        String base = current.infoText();
        if (current.hasVibrations()) {
            String mode = selectedVibrationInfo();
            if (!mode.isEmpty()) {
                base = base + "\n" + mode;
            }
        }
        infoView.setText(base);
        infoView.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
    }

    private String selectedVibrationInfo() {
        Molecule.VibrationMode vibration = current == null ? null : current.vibrationAt(vibrationIndex);
        if (vibration == null) {
            return "";
        }
        String text = String.format(Locale.US,
                "Mode %d: %.1f cm^-1",
                vibrationIndex + 1,
                vibration.frequency);
        if (vibration.frequency < 0f) {
            text += " imag";
        }
        if (!Float.isNaN(vibration.irIntensity)) {
            text += String.format(Locale.US, " | IR %.2f", vibration.irIntensity);
        }
        if (!Float.isNaN(vibration.reducedMass)) {
            text += String.format(Locale.US, " | RM %.3f", vibration.reducedMass);
        }
        if (!Float.isNaN(vibration.forceConstant)) {
            text += String.format(Locale.US, " | FC %.4f", vibration.forceConstant);
        }
        return text;
    }

    private String vibrationStatusText() {
        Molecule.VibrationMode vibration = current == null ? null : current.vibrationAt(vibrationIndex);
        if (vibration == null) {
            return "";
        }
        String text = String.format(Locale.US,
                "Freq %d/%d %.1f cm^-1",
                vibrationIndex + 1,
                current.vibrationCount(),
                vibration.frequency);
        if (vibration.frequency < 0f) {
            text += " imag";
        }
        if (!Float.isNaN(vibration.irIntensity)) {
            text += String.format(Locale.US, " IR %.2f", vibration.irIntensity);
        }
        return text;
    }

    private String styleButtonText() {
        if (moleculeView == null) {
            return "Style";
        }
        return "Style: " + moleculeView.modeName();
    }

    private String backgroundModeText() {
        return lightBackground ? "BG: White" : "BG: Black";
    }

    private String backgroundDescription() {
        return lightBackground ? "Background: White" : "Background: Black";
    }

    private void applyChromeTheme() {
        int bg = lightBackground ? COLOR_LIGHT_BG : COLOR_BG;
        int panel = lightBackground ? COLOR_LIGHT_PANEL : COLOR_PANEL;
        int title = lightBackground ? COLOR_LIGHT_TEXT : COLOR_TEXT;
        int secondary = lightBackground ? COLOR_LIGHT_TEXT_SECONDARY : COLOR_TEXT_SECONDARY;

        if (rootLayout != null) {
            rootLayout.setBackgroundColor(bg);
        }
        if (toolbar != null) {
            toolbar.setBackgroundColor(panel);
        }
        if (controls != null) {
            controls.setBackgroundColor(panel);
        }
        if (titleView != null) {
            titleView.setTextColor(title);
            titleView.setBackgroundColor(panel);
        }
        if (infoView != null) {
            infoView.setTextColor(secondary);
            infoView.setBackgroundColor(panel);
        }
        if (statusView != null) {
            statusView.setTextColor(secondary);
            statusView.setBackgroundColor(panel);
        }
        if (frameView != null) {
            frameView.setTextColor(secondary);
        }
        updateBackgroundButton();
        refreshButtonStyles(rootLayout);
        styleSeekBar(frameSeek);
        configureSystemBars();
        if (moleculeView != null) {
            moleculeView.setBackgroundMode(lightBackground ? MoleculeView.BACKGROUND_LIGHT : MoleculeView.BACKGROUND_DARK);
        }
        updateStatus();
    }

    private void updateBackgroundButton() {
        if (backgroundButton == null) {
            return;
        }
        backgroundButton.setImageResource(lightBackground ? R.drawable.ic_bg_light : R.drawable.ic_bg_dark);
        backgroundButton.setImageTintList(buttonIconColors());
        backgroundButton.setContentDescription(backgroundDescription());
    }

    private void refreshButtonStyles(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setTextColor(buttonTextColors());
            button.setBackground(buttonBackground());
        }
        if (view instanceof ImageButton) {
            ImageButton button = (ImageButton) view;
            button.setImageTintList(buttonIconColors());
            button.setBackground(buttonBackground());
        }
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshButtonStyles(group.getChildAt(i));
            }
        }
    }

    private String readUri(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open file");
            }
            return readAll(input);
        }
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "Molecule" : path;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(buttonTextColors());
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setHapticFeedbackEnabled(true);
        button.setBackground(buttonBackground());
        return button;
    }

    private ImageButton iconButton(int imageResId) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResId);
        button.setImageTintList(buttonIconColors());
        button.setBackground(buttonBackground());
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setHapticFeedbackEnabled(true);
        button.setContentDescription(backgroundDescription());
        return button;
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout.LayoutParams buttonLayout(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(36));
        params.setMarginEnd(dp(6));
        return params;
    }

    private LinearLayout.LayoutParams buttonLayoutLast(int width) {
        return new LinearLayout.LayoutParams(width, dp(36));
    }

    private void styleSeekBar(SeekBar seekBar) {
        if (seekBar == null) {
            return;
        }
        seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(lightBackground ? COLOR_LIGHT_BORDER_SOFT : COLOR_BORDER_SOFT));
        seekBar.setSplitTrack(false);
    }

    private ColorStateList buttonTextColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{
                        lightBackground ? COLOR_LIGHT_TEXT_DISABLED : COLOR_TEXT_DISABLED,
                        lightBackground ? COLOR_LIGHT_TEXT : COLOR_TEXT,
                        lightBackground ? COLOR_LIGHT_TEXT : COLOR_TEXT,
                        lightBackground ? COLOR_LIGHT_TEXT : COLOR_TEXT
                }
        );
    }

    private ColorStateList buttonIconColors() {
        return buttonTextColors();
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_DISABLED : COLOR_BUTTON_DISABLED,
                lightBackground ? COLOR_LIGHT_BORDER_SOFT : COLOR_BORDER_SOFT
        ));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_PRESSED : COLOR_BUTTON_PRESSED,
                COLOR_ACCENT
        ));
        states.addState(new int[]{android.R.attr.state_focused}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_FOCUSED : COLOR_BUTTON_FOCUSED,
                COLOR_ACCENT
        ));
        states.addState(new int[]{}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON : COLOR_BUTTON,
                lightBackground ? COLOR_LIGHT_BORDER : COLOR_BORDER
        ));
        return states;
    }

    private GradientDrawable roundedRect(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PlaybackTick implements Runnable {
        @Override
        public void run() {
            if (!playing || current == null || current.frameCount() <= 1) {
                if (current != null && current.hasVibrations()) {
                    Molecule.VibrationMode vibration = current.vibrationAt(vibrationIndex);
                    if (playing && vibration != null && vibration.hasDisplacement()) {
                        vibrationTick = (vibrationTick + 1) % 48;
                        float phase = (float) (vibrationTick * Math.PI * 2.0 / 48.0);
                        moleculeView.setVibrationPhase(phase);
                        handler.postDelayed(this, 55);
                        return;
                    }
                }
                playing = false;
                updatePlaybackButton();
                return;
            }
            int next = moleculeView.getFrameIndex() + 1;
            if (next >= current.frameCount()) {
                next = 0;
            }
            setFrame(next);
            handler.postDelayed(this, 180);
        }
    }
}
