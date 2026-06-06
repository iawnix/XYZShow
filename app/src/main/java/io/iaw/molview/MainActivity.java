package io.iaw.molview;

import android.app.Activity;
import android.content.res.Configuration;
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
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

public final class MainActivity extends Activity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, MoleculeView.GestureListener {
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
    private static final int MAX_PARSE_BYTES = 8 * 1024 * 1024;
    private static final String STATE_SOURCE_KIND = "source_kind";
    private static final String STATE_SOURCE_URI = "source_uri";
    private static final String STATE_ASSET_PATH = "asset_path";
    private static final String STATE_SOURCE_LABEL = "source_label";
    private static final String STATE_FILE_NAME = "file_name";
    private static final String STATE_SOURCE_TITLE = "source_title";
    private static final String STATE_FRAME_INDEX = "frame_index";
    private static final String STATE_VIBRATION_INDEX = "vibration_index";
    private static final String STATE_DISPLAY_MODE = "display_mode";
    private static final String STATE_YAW = "yaw";
    private static final String STATE_PITCH = "pitch";
    private static final String STATE_ZOOM = "zoom";
    private static final String STATE_PAN_X = "pan_x";
    private static final String STATE_PAN_Y = "pan_y";
    private static final String STATE_SESSION_ONLY_SOURCE = "session_only_source";

    private static LoadedMolecule lastLoadedMolecule;
    private static String lastLoadedKey = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private MoleculeView moleculeView;
    private TextView titleView;
    private TextView statusView;
    private TextView frameView;
    private ImageButton openButton;
    private ImageButton prevButton;
    private ImageButton playButton;
    private ImageButton nextButton;
    private ImageButton modeButton;
    private ImageButton resetButton;
    private ImageButton themeButton;
    private ImageButton sourceButton;
    private ImageButton infoButton;
    private ProgressBar loadingView;
    private LinearLayout rootLayout;
    private LinearLayout controls;
    private LinearLayout topToolBar;
    private FrameLayout viewHost;
    private SeekBar frameSeek;
    private Molecule current;
    private String currentFileName = "";
    private String currentSourceLabel = "";
    private Uri currentSourceUri;
    private String currentAssetPath = "";
    private String currentSourceTitle = "";
    private boolean playing;
    private boolean lightBackground;
    private boolean loading;
    private boolean chromeHidden;
    private boolean pendingRestoreView;
    private boolean sessionOnlySource;
    private int pendingFrameIndex;
    private int pendingVibrationIndex;
    private int pendingDisplayMode;
    private float pendingYaw;
    private float pendingPitch;
    private float pendingZoom;
    private float pendingPanX;
    private float pendingPanY;
    private int vibrationIndex;
    private int vibrationTick;

    private final Runnable playTick = new PlaybackTick();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lightBackground = AppTheme.isLight(this);
        configureSystemBars();
        buildLayout();
        if (!restoreLoadedMolecule(savedInstanceState) && !openFromViewIntent(getIntent())) {
            loadDefaultMolecule();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        int sourceKind = currentSourceUri != null ? SourceKind.URI
                : currentAssetPath.isEmpty() ? SourceKind.NONE : SourceKind.ASSET;
        outState.putInt(STATE_SOURCE_KIND, sourceKind);
        outState.putString(STATE_SOURCE_URI, currentSourceUri == null ? "" : currentSourceUri.toString());
        outState.putString(STATE_ASSET_PATH, currentAssetPath);
        outState.putString(STATE_SOURCE_LABEL, currentSourceLabel);
        outState.putString(STATE_FILE_NAME, currentFileName);
        outState.putString(STATE_SOURCE_TITLE, currentSourceTitle);
        outState.putInt(STATE_FRAME_INDEX, moleculeView == null ? 0 : moleculeView.getFrameIndex());
        outState.putInt(STATE_VIBRATION_INDEX, vibrationIndex);
        outState.putInt(STATE_DISPLAY_MODE, moleculeView == null ? 0 : moleculeView.getDisplayMode());
        outState.putFloat(STATE_YAW, moleculeView == null ? 0f : moleculeView.getYaw());
        outState.putFloat(STATE_PITCH, moleculeView == null ? 0f : moleculeView.getPitch());
        outState.putFloat(STATE_ZOOM, moleculeView == null ? 1f : moleculeView.getZoom());
        outState.putFloat(STATE_PAN_X, moleculeView == null ? 0f : moleculeView.getPanX());
        outState.putFloat(STATE_PAN_Y, moleculeView == null ? 0f : moleculeView.getPanY());
        outState.putBoolean(STATE_SESSION_ONLY_SOURCE, sessionOnlySource);
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
        if (moleculeView != null) {
            moleculeView.releaseGl();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        lightBackground = AppTheme.isLight(this);
        applyChromeTheme();
        if (rootLayout != null) {
            rootLayout.requestApplyInsets();
        }
    }

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_BG);

        viewHost = new FrameLayout(this);
        moleculeView = new MoleculeView(this);
        moleculeView.setGestureListener(this);
        viewHost.addView(moleculeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        openButton = railButton("Open", R.drawable.ic_folder_open);
        openButton.setId(R.id.btn_open);
        openButton.setOnClickListener(this);
        modeButton = railButton(styleButtonText(), R.drawable.ic_style);
        modeButton.setId(R.id.btn_style);
        modeButton.setOnClickListener(this);
        resetButton = railButton("Reset view", R.drawable.ic_reset);
        resetButton.setId(R.id.btn_reset);
        resetButton.setOnClickListener(this);
        themeButton = railButton("Toggle background", R.drawable.ic_bg_dark);
        themeButton.setId(R.id.btn_theme);
        themeButton.setOnClickListener(this);
        sourceButton = railButton("Source text", R.drawable.ic_source);
        sourceButton.setId(R.id.btn_source);
        sourceButton.setOnClickListener(this);
        infoButton = railButton("Details", R.drawable.ic_info);
        infoButton.setId(R.id.btn_details);
        infoButton.setOnClickListener(this);

        topToolBar = new LinearLayout(this);
        topToolBar.setOrientation(LinearLayout.HORIZONTAL);
        topToolBar.setGravity(Gravity.CENTER);
        topToolBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        topToolBar.setBackground(railBackground());
        topToolBar.addView(openButton, railLayout());
        topToolBar.addView(modeButton, railLayout());
        topToolBar.addView(resetButton, railLayout());
        topToolBar.addView(themeButton, railLayout());
        topToolBar.addView(sourceButton, railLayout());
        topToolBar.addView(infoButton, railLayoutLast());

        titleView = label("", 15, COLOR_TEXT);
        titleView.setGravity(Gravity.CENTER);
        titleView.setBackgroundColor(COLOR_PANEL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        loadingView = new ProgressBar(this);
        loadingView.setIndeterminate(true);
        loadingView.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        viewHost.addView(loadingView, loadingParams);
        rootLayout.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
        ));
        rootLayout.addView(topToolBar, new LinearLayout.LayoutParams(
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
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));
        playButton = railButton("Play", R.drawable.ic_play);
        playButton.setId(R.id.btn_play);
        playButton.setOnClickListener(this);
        prevButton = railButton("Previous mode", R.drawable.ic_prev);
        prevButton.setId(R.id.btn_prev);
        prevButton.setOnClickListener(this);
        nextButton = railButton("Next mode", R.drawable.ic_next);
        nextButton.setId(R.id.btn_next);
        nextButton.setOnClickListener(this);
        frameSeek = new SeekBar(this);
        frameSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(frameSeek);
        frameView = label("1/1", 13, COLOR_TEXT_SECONDARY);
        frameView.setGravity(Gravity.CENTER);
        controls.addView(prevButton, railLayout());
        controls.addView(playButton, railLayout());
        controls.addView(nextButton, railLayout());
        controls.addView(frameSeek, new LinearLayout.LayoutParams(0, dp(42), 1f));
        controls.addView(frameView, new LinearLayout.LayoutParams(dp(126), dp(42)));

        statusView = label("", 12, COLOR_TEXT_SECONDARY);
        statusView.setBackgroundColor(COLOR_PANEL);
        statusView.setPadding(dp(10), dp(4), dp(10), dp(8));
        statusView.setSingleLine(false);
        statusView.setMaxLines(3);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        applySystemInsets(rootLayout, titleView, topToolBar, controls, statusView);

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
        AppTheme.applySystemBars(this, lightBackground);
    }

    private void applySystemInsets(
            final View root,
            final TextView title,
            final LinearLayout toolbar,
            final LinearLayout controls,
            final TextView status
    ) {
        final int titleLeft = dp(12);
        final int titleTop = 0;
        final int titleRight = dp(12);
        final int titleBottom = dp(6);
        final int toolbarLeft = dp(8);
        final int toolbarTop = dp(6);
        final int toolbarRight = dp(8);
        final int toolbarBottom = dp(6);
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
                title.setPadding(titleLeft + left, titleTop + top, titleRight + right, titleBottom);
                toolbar.setPadding(toolbarLeft + left, toolbarTop, toolbarRight + right, toolbarBottom);
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
        if (id == R.id.btn_open) {
            openFile();
        } else if (id == R.id.btn_style) {
            moleculeView.cycleMode();
            updateModeButton();
            updateStatus();
        } else if (id == R.id.btn_prev) {
            stopPlayback();
            setFrame(navigationIndex() - 1);
        } else if (id == R.id.btn_play) {
            togglePlayback();
        } else if (id == R.id.btn_next) {
            stopPlayback();
            setFrame(navigationIndex() + 1);
        } else if (id == R.id.btn_reset) {
            moleculeView.resetView();
        } else if (id == R.id.btn_theme) {
            lightBackground = !lightBackground;
            AppTheme.setLight(this, lightBackground);
            applyChromeTheme();
        } else if (id == R.id.btn_details) {
            showDetailsPage();
        } else if (id == R.id.btn_source) {
            showSourcePage();
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

    @Override
    public void onSingleTap() {
        chromeHidden = !chromeHidden;
        updateChromeVisibility();
    }

    @Override
    public void onDoubleTap() {
        chromeHidden = false;
        updateChromeVisibility();
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
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        boolean persisted = false;
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                persisted = true;
            } catch (SecurityException ex) {
                Toast.makeText(this, "File access is session-only. Reopen if Android removes access.", Toast.LENGTH_LONG).show();
            }
        }
        String name = displayName(uri);
        setUriSource(uri, name, "File", !persisted);
        loadMoleculeAsync(new UriLoader(uri, name), "File", name);
    }

    private void loadDefaultMolecule() {
        stopPlayback();
        currentSourceUri = null;
        currentAssetPath = DEFAULT_ASSET;
        currentSourceLabel = "Default";
        currentFileName = DEFAULT_ASSET;
        currentSourceTitle = "Default  -  " + DEFAULT_ASSET;
        sessionOnlySource = false;
        pendingRestoreView = false;
        loadMoleculeAsync(new AssetLoader(DEFAULT_ASSET), "Default", DEFAULT_ASSET);
    }

    private boolean restoreLoadedMolecule(Bundle state) {
        if (state == null) {
            return false;
        }
        int sourceKind = state.getInt(STATE_SOURCE_KIND, SourceKind.NONE);
        String fileName = state.getString(STATE_FILE_NAME, "");
        String sourceLabel = state.getString(STATE_SOURCE_LABEL, "");
        currentSourceTitle = state.getString(STATE_SOURCE_TITLE, "");
        sessionOnlySource = state.getBoolean(STATE_SESSION_ONLY_SOURCE, false);
        if (sourceKind == SourceKind.URI) {
            String uriText = state.getString(STATE_SOURCE_URI, "");
            if (uriText == null || uriText.isEmpty()) {
                return false;
            }
            capturePendingRestore(state);
            Uri uri = Uri.parse(uriText);
            currentSourceUri = uri;
            currentAssetPath = "";
            currentSourceLabel = sourceLabel.isEmpty() ? "File" : sourceLabel;
            currentFileName = fileName.isEmpty() ? displayName(uri) : fileName;
            if (displayCachedMolecule(sourceKey(SourceKind.URI, uri.toString()),
                    currentSourceLabel,
                    currentFileName,
                    new UriLoader(uri, currentFileName))) {
                return true;
            }
            loadMoleculeAsync(new UriLoader(uri, currentFileName), currentSourceLabel, currentFileName);
            return true;
        }
        if (sourceKind == SourceKind.ASSET) {
            String assetPath = state.getString(STATE_ASSET_PATH, "");
            if (assetPath == null || assetPath.isEmpty()) {
                return false;
            }
            capturePendingRestore(state);
            currentSourceUri = null;
            currentAssetPath = assetPath;
            currentSourceLabel = sourceLabel.isEmpty() ? "Default" : sourceLabel;
            currentFileName = fileName.isEmpty() ? assetPath : fileName;
            if (displayCachedMolecule(sourceKey(SourceKind.ASSET, assetPath),
                    currentSourceLabel,
                    currentFileName,
                    new AssetLoader(assetPath))) {
                return true;
            }
            loadMoleculeAsync(new AssetLoader(assetPath), currentSourceLabel, currentFileName);
            return true;
        }
        return false;
    }

    private boolean openFromViewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) {
            return false;
        }
        Uri uri = intent.getData();
        String name = displayName(uri);
        setUriSource(uri, name, "File", true);
        loadMoleculeAsync(new UriLoader(uri, name), "File", name);
        return true;
    }

    private void setUriSource(Uri uri, String name, String sourceLabel, boolean sessionOnly) {
        currentSourceUri = uri;
        currentAssetPath = "";
        currentSourceLabel = sourceLabel == null || sourceLabel.isEmpty() ? "File" : sourceLabel;
        currentFileName = name == null || name.isEmpty() ? displayName(uri) : name;
        currentSourceTitle = currentSourceLabel + "  -  " + currentFileName;
        sessionOnlySource = sessionOnly;
        pendingRestoreView = false;
    }

    private void capturePendingRestore(Bundle state) {
        pendingRestoreView = true;
        pendingFrameIndex = state.getInt(STATE_FRAME_INDEX, 0);
        pendingVibrationIndex = state.getInt(STATE_VIBRATION_INDEX, 0);
        pendingDisplayMode = state.getInt(STATE_DISPLAY_MODE, 0);
        pendingYaw = state.getFloat(STATE_YAW, -0.55f);
        pendingPitch = state.getFloat(STATE_PITCH, 0.45f);
        pendingZoom = state.getFloat(STATE_ZOOM, 1f);
        pendingPanX = state.getFloat(STATE_PAN_X, 0f);
        pendingPanY = state.getFloat(STATE_PAN_Y, 0f);
    }

    private void loadMoleculeAsync(final MoleculeLoader loader, final String sourceLabel, final String fileName) {
        final int generation = loadGeneration.incrementAndGet();
        stopPlayback();
        LoadedMolecule cached = cachedMolecule(loader);
        if (cached != null) {
            displayMolecule(cached.molecule, sourceLabel, fileName, loader);
            setLoading(false, "");
            return;
        }
        setLoading(true, "Loading " + fileName + "...");
        loadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoadedMolecule loaded = loader.load();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != loadGeneration.get()) {
                                return;
                            }
                            cacheMolecule(loader, loaded);
                            displayMolecule(loaded.molecule, sourceLabel, fileName, loader);
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
                            pendingRestoreView = false;
                            setLoading(false, "");
                            String message = loadErrorMessage(ex);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            if (statusView != null) {
                                statusView.setText("Load failed\n" + message
                                        + "\nUse the source-text button to inspect this file.");
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
        if (openButton != null) {
            openButton.setEnabled(!loading);
        }
        if (modeButton != null) {
            modeButton.setEnabled(!loading && current != null);
        }
        if (resetButton != null) {
            resetButton.setEnabled(!loading && current != null);
        }
        if (sourceButton != null) {
            sourceButton.setEnabled(!loading && hasSourceReference());
        }
        if (frameSeek != null) {
            frameSeek.setEnabled(!loading && current != null && (current.hasVibrations()
                    ? current.vibrationCount() > 1
                    : current.frameCount() > 1));
        }
        updateNavigationButtons();
        updatePlaybackButton();
        if (statusView != null && loading) {
            statusView.setText(message);
        }
        updateChromeVisibility();
    }

    private void updateChromeVisibility() {
        int visibility = chromeHidden ? View.GONE : View.VISIBLE;
        if (titleView != null) {
            titleView.setVisibility(visibility);
        }
        if (topToolBar != null) {
            topToolBar.setVisibility(visibility);
        }
        if (controls != null) {
            controls.setVisibility(visibility);
        }
        if (statusView != null) {
            statusView.setVisibility(visibility);
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

    private void displayMolecule(Molecule molecule, String sourceLabel, String fileName, MoleculeLoader loader) {
        current = molecule;
        currentSourceLabel = sourceLabel;
        currentFileName = fileName == null ? "" : fileName;
        currentSourceUri = loader instanceof UriLoader ? ((UriLoader) loader).uri : null;
        currentAssetPath = loader instanceof AssetLoader ? ((AssetLoader) loader).assetPath : "";
        if (loader instanceof AssetLoader) {
            sessionOnlySource = false;
        }
        currentSourceTitle = titleText();
        int restoreVibration = pendingRestoreView ? pendingVibrationIndex : 0;
        int restoreFrame = pendingRestoreView ? pendingFrameIndex : 0;
        vibrationTick = 0;
        moleculeView.setMolecule(molecule);
        if (pendingRestoreView) {
            moleculeView.setDisplayMode(pendingDisplayMode);
            updateModeButton();
        }
        titleView.setText(titleText());
        updateStatus();
        if (molecule.hasVibrations()) {
            frameSeek.setMax(Math.max(0, molecule.vibrationCount() - 1));
            frameSeek.setEnabled(molecule.vibrationCount() > 1);
        } else {
            frameSeek.setMax(Math.max(0, molecule.frameCount() - 1));
            frameSeek.setEnabled(molecule.frameCount() > 1);
        }
        setFrame(molecule.hasVibrations() ? restoreVibration : restoreFrame);
        if (pendingRestoreView) {
            moleculeView.restoreViewState(pendingYaw, pendingPitch, pendingZoom, pendingPanX, pendingPanY);
            pendingRestoreView = false;
        }
        updateNavigationButtons();
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
            vibrationIndex = safe;
            vibrationTick = 0;
            moleculeView.setVibrationMode(safe);
            moleculeView.setVibrationPhase((float) (Math.PI * 0.5));
            frameSeek.setProgress(safe);
            frameView.setText(vibrationFrameText(safe));
            updateStatus();
            updateNavigationButtons();
            updatePlaybackButton();
            return;
        }
        int safe = current.frameCount() == 0 ? 0 : (frame % current.frameCount() + current.frameCount()) % current.frameCount();
        moleculeView.setFrameIndex(safe);
        frameSeek.setProgress(safe);
        frameView.setText((safe + 1) + "/" + current.frameCount());
        updateStatus();
        updateNavigationButtons();
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
        playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playButton.setContentDescription(playing ? "Pause animation" : "Play animation");
        playButton.setBackground(playing ? activeRailButtonBackground() : railButtonBackground());
    }

    private void updateNavigationButtons() {
        boolean canNavigate = !loading && current != null && (
                current.hasVibrations()
                        ? current.vibrationCount() > 1
                        : current.frameCount() > 1
        );
        String unit = current != null && current.hasVibrations() ? "mode" : "frame";
        if (prevButton != null) {
            prevButton.setEnabled(canNavigate);
            prevButton.setContentDescription("Previous " + unit);
        }
        if (nextButton != null) {
            nextButton.setEnabled(canNavigate);
            nextButton.setContentDescription("Next " + unit);
        }
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
        text.append(compactText(currentFileName.isEmpty() ? current.title : currentFileName, 48))
                .append(" | ").append(current.sourceType)
                .append(" | ").append(currentSourceLabel).append('\n');
        if (sessionOnlySource) {
            text.append("Session-only | reopen if restore fails\n");
        }
        text.append("A").append(current.atomCount())
                .append(" | B").append(current.bonds.size())
                .append(" | F").append(current.frameCount());
        if (current.hasVibrations()) {
            text.append(" | M").append(current.vibrationCount());
        }
        text.append('\n');
        if (current.hasVibrations()) {
            text.append(vibrationStatusText()).append('\n');
        }
        text.append("Style: ").append(moleculeView.modeName())
                .append(" | ").append(backgroundModeText());
        statusView.setText(text.toString());
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

    private void showDetailsPage() {
        if (current == null) {
            Toast.makeText(this, "No molecule loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, titleText());
        intent.putExtra(DetailsActivity.EXTRA_SUMMARY, detailText());
        startActivity(intent);
    }

    private void showSourcePage() {
        if (!hasSourceReference()) {
            Toast.makeText(this, "No source text loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentSourceUri != null) {
            Intent intent = new Intent(this, SourceActivity.class);
            intent.putExtra(SourceActivity.EXTRA_TITLE, sourceTitleText());
            intent.putExtra(SourceActivity.EXTRA_KIND, SourceKind.URI);
            intent.putExtra(SourceActivity.EXTRA_LOCATION, currentSourceUri.toString());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, SourceActivity.class);
            intent.putExtra(SourceActivity.EXTRA_TITLE, sourceTitleText());
            intent.putExtra(SourceActivity.EXTRA_KIND, SourceKind.ASSET);
            intent.putExtra(SourceActivity.EXTRA_LOCATION, currentAssetPath);
            startActivity(intent);
        }
    }

    private boolean hasSourceReference() {
        return currentSourceUri != null || !currentAssetPath.isEmpty();
    }

    private String sourceTitleText() {
        if (current != null) {
            return titleText();
        }
        if (currentSourceTitle == null || currentSourceTitle.isEmpty()) {
            return currentFileName == null || currentFileName.isEmpty() ? "Source" : currentFileName;
        }
        return currentSourceTitle;
    }

    private String detailText() {
        if (current == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        text.append("File\n");
        text.append(currentFileName.isEmpty() ? current.title : currentFileName).append("\n\n");
        text.append("Source\n");
        text.append(currentSourceLabel).append(" | ").append(current.sourceType).append("\n\n");
        text.append("Structure\n");
        text.append("Atoms: ").append(current.atomCount()).append('\n');
        text.append("Bonds: ").append(current.bonds.size()).append('\n');
        text.append("Frames: ").append(current.frameCount()).append('\n');
        if (current.hasVibrations()) {
            text.append("Modes: ").append(current.vibrationCount()).append('\n');
        }
        text.append("\nMetadata\n");
        text.append(current.infoText()).append("\n");
        text.append("\nElements\n");
        text.append(elementLegend()).append("\n");
        if (current.hasVibrations()) {
            text.append("\nSelected vibration\n");
            text.append(selectedVibrationInfo()).append("\n");
        }
        text.append("\nDisplay\n");
        text.append("Style: ").append(moleculeView.modeName()).append('\n');
        text.append(backgroundModeText()).append('\n');
        return text.toString();
    }

    private String elementLegend() {
        if (current == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String element : current.elementsInUse()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(element).append("  ").append(ElementTable.hexColor(element));
        }
        return text.length() == 0 ? "None" : text.toString();
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
        return String.format(Locale.US, "Mode %d/%d\n%.1f cm^-1", index + 1, current.vibrationCount(), vibration.frequency);
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

    private static String compactText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void updateModeButton() {
        if (modeButton == null) {
            return;
        }
        modeButton.setContentDescription(styleButtonText());
    }

    private void updateThemeButton() {
        if (themeButton == null) {
            return;
        }
        themeButton.setImageResource(lightBackground ? R.drawable.ic_bg_light : R.drawable.ic_bg_dark);
        themeButton.setContentDescription(lightBackground ? "Switch to black background" : "Switch to white background");
    }

    private void applyChromeTheme() {
        int bg = lightBackground ? COLOR_LIGHT_BG : COLOR_BG;
        int panel = lightBackground ? COLOR_LIGHT_PANEL : COLOR_PANEL;
        int title = lightBackground ? COLOR_LIGHT_TEXT : COLOR_TEXT;
        int secondary = lightBackground ? COLOR_LIGHT_TEXT_SECONDARY : COLOR_TEXT_SECONDARY;

        if (rootLayout != null) {
            rootLayout.setBackgroundColor(bg);
        }
        if (controls != null) {
            controls.setBackgroundColor(panel);
        }
        if (viewHost != null) {
            viewHost.setBackgroundColor(bg);
        }
        if (topToolBar != null) {
            topToolBar.setBackground(railBackground());
        }
        if (titleView != null) {
            titleView.setTextColor(title);
            titleView.setBackgroundColor(panel);
        }
        if (statusView != null) {
            statusView.setTextColor(secondary);
            statusView.setBackgroundColor(panel);
        }
        if (frameView != null) {
            frameView.setTextColor(secondary);
        }
        refreshButtonStyles(rootLayout);
        updateThemeButton();
        updateModeButton();
        updatePlaybackButton();
        if (sourceButton != null) {
            sourceButton.setEnabled(hasSourceReference());
        }
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
        if (view instanceof ImageButton) {
            ImageButton button = (ImageButton) view;
            button.setImageTintList(buttonTextColors());
            button.setBackground(railButtonBackground());
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
        } catch (SecurityException ex) {
            if (sessionOnlySource) {
                throw new IOException("Android no longer grants access to this session-only file. Reopen it from the folder button.", ex);
            }
            throw ex;
        }
    }

    private interface MoleculeLoader {
        LoadedMolecule load() throws IOException;

        int kind();

        String location();
    }

    private static final class LoadedMolecule {
        final Molecule molecule;

        LoadedMolecule(Molecule molecule) {
            this.molecule = molecule;
        }
    }

    private static String sourceKey(int kind, String location) {
        return kind + ":" + (location == null ? "" : location);
    }

    private static void cacheMolecule(MoleculeLoader loader, LoadedMolecule loaded) {
        lastLoadedKey = sourceKey(loader.kind(), loader.location());
        lastLoadedMolecule = loaded;
    }

    private static LoadedMolecule cachedMolecule(MoleculeLoader loader) {
        if (loader == null || lastLoadedMolecule == null) {
            return null;
        }
        String key = sourceKey(loader.kind(), loader.location());
        return key.equals(lastLoadedKey) ? lastLoadedMolecule : null;
    }

    private boolean displayCachedMolecule(String key, String sourceLabel, String fileName, MoleculeLoader loader) {
        if (lastLoadedMolecule == null || !key.equals(lastLoadedKey)) {
            return false;
        }
        displayMolecule(lastLoadedMolecule.molecule, sourceLabel, fileName, loader);
        return true;
    }

    private static String loadErrorMessage(Throwable ex) {
        if (ex == null) {
            return "Load failed";
        }
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private final class UriLoader implements MoleculeLoader {
        private final Uri uri;
        private final String name;

        UriLoader(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        @Override
        public LoadedMolecule load() throws IOException {
            String text = readUri(uri);
            return new LoadedMolecule(parseMoleculeFile(text, name));
        }

        @Override
        public int kind() {
            return SourceKind.URI;
        }

        @Override
        public String location() {
            return uri.toString();
        }
    }

    private final class AssetLoader implements MoleculeLoader {
        private final String assetPath;

        AssetLoader(String assetPath) {
            this.assetPath = assetPath;
        }

        @Override
        public LoadedMolecule load() throws IOException {
            try (InputStream input = getAssets().open(assetPath)) {
                String text = readAll(input);
                return new LoadedMolecule(parseXyzFile(text, assetPath));
            }
        }

        @Override
        public int kind() {
            return SourceKind.ASSET;
        }

        @Override
        public String location() {
            return assetPath;
        }
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_PARSE_BYTES) {
                throw new IOException("File is too large to parse in the viewer. Use the source-text page to inspect it.");
            }
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

    private ImageButton railButton(String description, int imageResId) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResId);
        button.setImageTintList(buttonTextColors());
        button.setBackground(railButtonBackground());
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setHapticFeedbackEnabled(true);
        button.setContentDescription(description);
        button.setTooltipText(description);
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

    private LinearLayout.LayoutParams railLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams railLayoutLast() {
        return new LinearLayout.LayoutParams(dp(40), dp(40));
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

    private StateListDrawable railButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_DISABLED : COLOR_BUTTON_DISABLED,
                lightBackground ? COLOR_LIGHT_BORDER_SOFT : COLOR_BORDER_SOFT,
                dp(20)
        ));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_PRESSED : COLOR_BUTTON_PRESSED,
                COLOR_ACCENT,
                dp(20)
        ));
        states.addState(new int[]{android.R.attr.state_focused}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON_FOCUSED : COLOR_BUTTON_FOCUSED,
                COLOR_ACCENT,
                dp(20)
        ));
        states.addState(new int[]{}, roundedRect(
                lightBackground ? COLOR_LIGHT_BUTTON : COLOR_BUTTON,
                lightBackground ? COLOR_LIGHT_BORDER : COLOR_BORDER,
                dp(20)
        ));
        return states;
    }

    private StateListDrawable activeRailButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, roundedRect(
                lightBackground ? 0xffb7f1c5 : 0xff1f7a3a,
                COLOR_ACCENT_ACTIVE,
                dp(20)
        ));
        states.addState(new int[]{}, roundedRect(
                lightBackground ? 0xffd8f8df : 0xff164f2b,
                COLOR_ACCENT_ACTIVE,
                dp(20)
        ));
        return states;
    }

    private GradientDrawable railBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(lightBackground ? 0xeeffffff : 0xee242426);
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), lightBackground ? COLOR_LIGHT_BORDER : COLOR_BORDER_SOFT);
        return drawable;
    }

    private GradientDrawable roundedRect(int fill, int stroke) {
        return roundedRect(fill, stroke, dp(8));
    }

    private GradientDrawable roundedRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PlaybackTick implements Runnable {
        @Override
        public void run() {
            if (!playing || current == null) {
                playing = false;
                updatePlaybackButton();
                return;
            }
            if (current.hasVibrations()) {
                Molecule.VibrationMode vibration = current.vibrationAt(vibrationIndex);
                if (vibration != null && vibration.hasDisplacement()) {
                    vibrationTick = (vibrationTick + 1) % 48;
                    float phase = (float) (vibrationTick * Math.PI * 2.0 / 48.0);
                    moleculeView.setVibrationPhase(phase);
                    handler.postDelayed(this, 55);
                    return;
                }
                playing = false;
                updatePlaybackButton();
                return;
            }
            if (current.frameCount() <= 1) {
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
