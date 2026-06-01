package io.iaw.molview;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int COLOR_ACCENT_ACTIVE = 0xff30d158;
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
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private MoleculeView moleculeView;
    private TextView titleView;
    private TextView infoView;
    private TextView statusView;
    private TextView frameView;
    private Button playButton;
    private Button modeButton;
    private Button resetButton;
    private Button menuButton;
    private ProgressBar loadingView;
    private LinearLayout rootLayout;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private FrameLayout viewHost;
    private SeekBar frameSeek;
    private Molecule current;
    private String currentFileName = "";
    private String currentSourceLabel = "";
    private boolean playing;
    private boolean lightBackground;
    private boolean infoVisible;
    private boolean loading;
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
        stopPlayback();
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

    @Override
    protected void onDestroy() {
        stopPlayback();
        loadGeneration.incrementAndGet();
        loadExecutor.shutdownNow();
        super.onDestroy();
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
        menuButton = button("Menu");
        menuButton.setId(10);
        menuButton.setOnClickListener(this);
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(36), 1f));
        toolbar.addView(openButton, buttonLayout(dp(92)));
        toolbar.addView(menuButton, buttonLayoutLast(dp(78)));
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
        infoView.setMaxLines(5);
        infoView.setEllipsize(TextUtils.TruncateAt.END);
        infoView.setLineSpacing(dp(1), 1.05f);
        infoView.setVisibility(View.GONE);

        moleculeView = new MoleculeView(this);
        viewHost = new FrameLayout(this);
        viewHost.addView(moleculeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        modeButton = button(styleButtonText());
        modeButton.setText(styleButtonText());
        modeButton.setId(3);
        modeButton.setOnClickListener(this);
        resetButton = button("Reset");
        resetButton.setId(7);
        resetButton.setOnClickListener(this);
        LinearLayout floatingTools = row();
        floatingTools.setGravity(Gravity.CENTER);
        floatingTools.setPadding(dp(8), dp(8), dp(8), dp(8));
        floatingTools.addView(modeButton, buttonLayout(dp(108)));
        floatingTools.addView(resetButton, buttonLayoutLast(dp(68)));
        FrameLayout.LayoutParams floatingParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        floatingParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        viewHost.addView(floatingTools, floatingParams);
        loadingView = new ProgressBar(this);
        loadingView.setIndeterminate(true);
        loadingView.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        viewHost.addView(loadingView, loadingParams);
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
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rootLayout.addView(viewHost, new LinearLayout.LayoutParams(
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
        Button findButton = button("Find");
        findButton.setId(11);
        findButton.setOnClickListener(this);
        findButton.setContentDescription("Find frequency or mode");
        frameSeek = new SeekBar(this);
        frameSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(frameSeek);
        frameView = label("1/1", 13, COLOR_TEXT_SECONDARY);
        frameView.setGravity(Gravity.CENTER);
        controls.addView(prevButton, buttonLayout(dp(62)));
        controls.addView(playButton, buttonLayout(dp(76)));
        controls.addView(nextButton, buttonLayout(dp(62)));
        controls.addView(frameSeek, new LinearLayout.LayoutParams(0, dp(42), 1f));
        controls.addView(frameView, new LinearLayout.LayoutParams(dp(104), dp(42)));
        controls.addView(findButton, buttonLayoutLast(dp(60)));

        statusView = label("", 12, COLOR_TEXT_SECONDARY);
        statusView.setBackgroundColor(COLOR_PANEL);
        statusView.setPadding(dp(10), dp(4), dp(10), dp(8));
        statusView.setSingleLine(false);
        statusView.setMaxLines(4);
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
        } else if (id == 10) {
            showMainMenu(view);
        } else if (id == 11) {
            showFrequencyJumpDialog();
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

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 9, 0, infoVisible ? "Hide info" : "Show info");
        menu.getMenu().add(0, 8, 1, lightBackground ? "Black background" : "White background");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 9) {
                infoVisible = !infoVisible;
                updateInfoArea();
                return true;
            }
            if (item.getItemId() == 8) {
                lightBackground = !lightBackground;
                applyChromeTheme();
                return true;
            }
            return false;
        });
        menu.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String name = displayName(uri);
        loadMoleculeAsync(new UriLoader(uri, name), "File", name);
    }

    private void loadDefaultMolecule() {
        stopPlayback();
        loadMoleculeAsync(new AssetLoader(DEFAULT_ASSET), "Default", DEFAULT_ASSET);
    }

    private void loadMoleculeAsync(final MoleculeLoader loader, final String sourceLabel, final String fileName) {
        final int generation = loadGeneration.incrementAndGet();
        stopPlayback();
        setLoading(true, "Loading " + fileName + "...");
        loadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Molecule molecule = loader.load();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != loadGeneration.get()) {
                                return;
                            }
                            displayMolecule(molecule, sourceLabel, fileName);
                            setLoading(false, "");
                        }
                    });
                } catch (final IOException | RuntimeException ex) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != loadGeneration.get()) {
                                return;
                            }
                            setLoading(false, "");
                            Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
                            if (statusView != null) {
                                statusView.setText("Load failed\n" + ex.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    private void setLoading(boolean loading, String message) {
        this.loading = loading;
        if (loadingView != null) {
            loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (menuButton != null) {
            menuButton.setEnabled(!loading);
        }
        if (modeButton != null) {
            modeButton.setEnabled(!loading && current != null);
        }
        if (resetButton != null) {
            resetButton.setEnabled(!loading && current != null);
        }
        if (frameSeek != null) {
            frameSeek.setEnabled(!loading && current != null && (current.hasVibrations()
                    ? current.vibrationCount() > 1
                    : current.frameCount() > 1));
        }
        updatePlaybackButton();
        if (statusView != null && loading) {
            statusView.setText(message);
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

    private void displayMolecule(Molecule molecule, String sourceLabel, String fileName) {
        current = molecule;
        currentSourceLabel = sourceLabel;
        currentFileName = fileName == null ? "" : fileName;
        vibrationIndex = 0;
        vibrationTick = 0;
        infoVisible = "Gaussian".equals(molecule.sourceType);
        moleculeView.setMolecule(molecule);
        titleView.setText(titleText());
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

    private String titleText() {
        if (current == null) {
            return "";
        }
        String name = currentFileName == null || currentFileName.trim().isEmpty() ? current.title : currentFileName;
        String detail = current.title == null || current.title.equals(name) ? "" : "  -  " + current.title;
        return current.sourceType + "  -  " + name + detail;
    }

    private void setFrame(int frame) {
        if (current == null) {
            return;
        }
        if (current.hasVibrations()) {
            int count = current.vibrationCount();
            int safe = count == 0 ? 0 : (frame % count + count) % count;
            stopPlayback();
            vibrationIndex = safe;
            vibrationTick = 0;
            moleculeView.setVibrationMode(safe);
            moleculeView.setVibrationPhase(0f);
            frameSeek.setProgress(safe);
            frameView.setText(vibrationFrameText(safe));
            updateInfoArea();
            updateStatus();
            updatePlaybackButton();
            return;
        }
        int safe = current.frameCount() == 0 ? 0 : (frame % current.frameCount() + current.frameCount()) % current.frameCount();
        moleculeView.setFrameIndex(safe);
        frameSeek.setProgress(safe);
        frameView.setText((safe + 1) + "/" + current.frameCount());
        updateStatus();
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
        boolean canPlay = !loading && current != null && (
                current.hasVibrations()
                        ? current.vibrationAt(vibrationIndex) != null && current.vibrationAt(vibrationIndex).hasDisplacement()
                : current.frameCount() > 1
        );
        playButton.setEnabled(canPlay);
        playButton.setText(playing ? "Pause" : "Play");
        playButton.setBackground(playing ? activeButtonBackground() : buttonBackground());
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
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(currentFileName.isEmpty() ? current.title : currentFileName)
                .append(" | Type: ").append(current.sourceType)
                .append(" | Source: ").append(currentSourceLabel).append('\n');
        text.append("Atoms: ").append(current.atomCount())
                .append(" | Bonds: ").append(current.bonds.size())
                .append(" | Frames: ").append(current.frameCount());
        if (current.hasVibrations()) {
            text.append(" | Modes: ").append(current.vibrationCount());
        }
        text.append('\n');
        if (current.hasVibrations()) {
            text.append(vibrationStatusText()).append('\n');
        }
        text.append("Style: ").append(moleculeView.modeName())
                .append(" | ").append(backgroundModeText());
        statusView.setText(text.toString());
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
        StringBuilder text = new StringBuilder(String.format(Locale.US,
                "Mode: %d / %d\nFrequency: %.1f cm^-1",
                vibrationIndex + 1,
                current.vibrationCount(),
                vibration.frequency));
        if (vibration.frequency < 0f) {
            text.append(" (imaginary)");
        }
        if (!Float.isNaN(vibration.irIntensity)) {
            text.append(String.format(Locale.US, "\nIR intensity: %.2f", vibration.irIntensity));
        }
        if (!Float.isNaN(vibration.reducedMass)) {
            text.append(String.format(Locale.US, " | Reduced mass: %.3f", vibration.reducedMass));
        }
        if (!Float.isNaN(vibration.forceConstant)) {
            text.append(String.format(Locale.US, "\nForce constant: %.4f", vibration.forceConstant));
        }
        return text.toString();
    }

    private String vibrationStatusText() {
        Molecule.VibrationMode vibration = current == null ? null : current.vibrationAt(vibrationIndex);
        if (vibration == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(String.format(Locale.US,
                "Mode: %d/%d | Frequency: %.1f cm^-1",
                vibrationIndex + 1,
                current.vibrationCount(),
                vibration.frequency));
        if (vibration.frequency < 0f) {
            text.append(" imag");
        }
        if (!Float.isNaN(vibration.irIntensity)) {
            text.append(String.format(Locale.US, " | IR: %.2f", vibration.irIntensity));
        }
        return text.toString();
    }

    private String vibrationFrameText(int index) {
        Molecule.VibrationMode vibration = current == null ? null : current.vibrationAt(index);
        if (vibration == null) {
            return "";
        }
        return String.format(Locale.US, "%d/%d\n%.1f cm^-1", index + 1, current.vibrationCount(), vibration.frequency);
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

    private void showFrequencyJumpDialog() {
        if (current == null || !current.hasVibrations()) {
            Toast.makeText(this, "No frequency modes in this file", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("mode # or target cm^-1");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSelectAllOnFocus(true);
        input.setText(String.format(Locale.US, "%.1f", current.vibrationAt(vibrationIndex).frequency));
        new AlertDialog.Builder(this)
                .setTitle("Find frequency")
                .setMessage("Enter a mode index, or a frequency target in cm^-1.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go", (dialog, which) -> jumpToFrequencyInput(input.getText().toString()))
                .show();
    }

    private void jumpToFrequencyInput(String value) {
        if (current == null || !current.hasVibrations()) {
            return;
        }
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            float target = Float.parseFloat(trimmed);
            int index;
            if (isLikelyModeIndex(trimmed, target)) {
                index = Math.max(0, Math.min(Math.round(target) - 1, current.vibrationCount() - 1));
            } else {
                index = nearestFrequencyIndex(target);
            }
            setFrame(index);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLikelyModeIndex(String raw, float value) {
        return raw.indexOf('.') < 0
                && value >= 1f
                && value <= current.vibrationCount();
    }

    private int nearestFrequencyIndex(float target) {
        int best = 0;
        float bestDelta = Float.MAX_VALUE;
        for (int i = 0; i < current.vibrationCount(); i++) {
            Molecule.VibrationMode mode = current.vibrationAt(i);
            float delta = Math.abs(mode.frequency - target);
            if (delta < bestDelta) {
                best = i;
                bestDelta = delta;
            }
        }
        return best;
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
        if (viewHost != null) {
            viewHost.setBackgroundColor(bg);
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
        refreshButtonStyles(rootLayout);
        updatePlaybackButton();
        styleSeekBar(frameSeek);
        configureSystemBars();
        if (moleculeView != null) {
            moleculeView.setBackgroundMode(lightBackground ? MoleculeView.BACKGROUND_LIGHT : MoleculeView.BACKGROUND_DARK);
        }
        updateStatus();
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
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
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

    private interface MoleculeLoader {
        Molecule load() throws IOException;
    }

    private final class UriLoader implements MoleculeLoader {
        private final Uri uri;
        private final String name;

        UriLoader(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        @Override
        public Molecule load() throws IOException {
            String text = readUri(uri);
            return parseMoleculeFile(text, name);
        }
    }

    private final class AssetLoader implements MoleculeLoader {
        private final String assetPath;

        AssetLoader(String assetPath) {
            this.assetPath = assetPath;
        }

        @Override
        public Molecule load() throws IOException {
            try (InputStream input = getAssets().open(assetPath)) {
                String text = readAll(input);
                return parseXyzFile(text, assetPath);
            }
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

    private StateListDrawable activeButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_DISABLED : COLOR_BUTTON_DISABLED,
                lightBackground ? COLOR_LIGHT_BORDER_SOFT : COLOR_BORDER_SOFT
        ));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedRect(
                lightBackground ? 0xffb7f1c5 : 0xff1f7a3a,
                COLOR_ACCENT_ACTIVE
        ));
        states.addState(new int[]{}, roundedRect(
                lightBackground ? 0xffd8f8df : 0xff164f2b,
                COLOR_ACCENT_ACTIVE
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
