package io.iaw.molview;

import android.content.res.Configuration;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends ComponentActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, MoleculeView.GestureListener {
    private static final String DEFAULT_ASSET = "samples/dopamine.xyz";
    private static final int MAX_PARSE_BYTES = 100 * 1024 * 1024;
    private static final String STATE_SOURCE_KIND = "source_kind";
    private static final String STATE_SOURCE_URI = "source_uri";
    private static final String STATE_ASSET_PATH = "asset_path";
    private static final String STATE_SOURCE_LABEL = "source_label";
    private static final String STATE_FILE_NAME = "file_name";
    private static final String STATE_SOURCE_TITLE = "source_title";
    private static final String STATE_FRAME_INDEX = "frame_index";
    private static final String STATE_VIBRATION_INDEX = "vibration_index";
    private static final String STATE_DISPLAY_MODE = "display_mode";
    private static final String STATE_ZOOM = "zoom";
    private static final String STATE_PAN_X = "pan_x";
    private static final String STATE_PAN_Y = "pan_y";
    private static final String STATE_ORIENTATION = "orientation";
    private static final String STATE_SESSION_ONLY_SOURCE = "session_only_source";
    private static final String STATE_VIBRATION_AMPLITUDE = "vibration_amplitude";
    private static final String STATE_PLAYBACK_SPEED = "playback_speed";
    private static final String STATE_VIBRATION_PANEL_VISIBLE = "vibration_panel_visible";

    private static LoadedMolecule lastLoadedMolecule;
    private static String lastLoadedKey = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private MoleculeView moleculeView;
    private TextView titleView;
    private TextView statusView;
    private TextView frameView;
    private MaterialButton openButton;
    private MaterialButton prevButton;
    private MaterialButton playButton;
    private MaterialButton nextButton;
    private MaterialButton vibrationControlsButton;
    private MaterialButton modeButton;
    private MaterialButton resetButton;
    private MaterialButton themeButton;
    private MaterialButton sourceButton;
    private MaterialButton infoButton;
    private ProgressBar loadingView;
    private ConstraintLayout rootLayout;
    private LinearLayout controls;
    private LinearLayout vibrationPanel;
    private LinearLayout topToolBar;
    private FrameLayout viewHost;
    private SeekBar frameSeek;
    private SeekBar amplitudeSeek;
    private SeekBar speedSeek;
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
    private float pendingZoom;
    private float pendingPanX;
    private float pendingPanY;
    private float[] pendingOrientation;
    private int vibrationIndex;
    private float animationPhase;
    private float vibrationAmplitude = 1f;
    private float playbackSpeed = 1f;
    private boolean vibrationPanelVisible;
    private ActivityResultLauncher<Intent> openDocumentLauncher;
    private ActivityResultLauncher<Intent> detailsLauncher;

    private final Runnable playTick = new PlaybackTick();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.bind(this);
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleOpenDocumentResult);
        detailsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleDetailsResult);
        lightBackground = AppTheme.isLight(this);
        restoreUiState(savedInstanceState);
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
        outState.putFloat(STATE_ZOOM, moleculeView == null ? 1f : moleculeView.getZoom());
        outState.putFloat(STATE_PAN_X, moleculeView == null ? 0f : moleculeView.getPanX());
        outState.putFloat(STATE_PAN_Y, moleculeView == null ? 0f : moleculeView.getPanY());
        outState.putFloatArray(STATE_ORIENTATION, moleculeView == null ? null : moleculeView.getOrientationMatrix());
        outState.putBoolean(STATE_SESSION_ONLY_SOURCE, sessionOnlySource);
        outState.putFloat(STATE_VIBRATION_AMPLITUDE, vibrationAmplitude);
        outState.putFloat(STATE_PLAYBACK_SPEED, playbackSpeed);
        outState.putBoolean(STATE_VIBRATION_PANEL_VISIBLE, vibrationPanelVisible);
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
        setContentView(R.layout.activity_main);
        rootLayout = findViewById(R.id.root_layout);
        titleView = findViewById(R.id.title_view);
        topToolBar = findViewById(R.id.top_toolbar);
        viewHost = findViewById(R.id.view_host);
        loadingView = findViewById(R.id.loading_view);
        controls = findViewById(R.id.controls);
        playButton = findViewById(R.id.btn_play);
        prevButton = findViewById(R.id.btn_prev);
        nextButton = findViewById(R.id.btn_next);
        vibrationControlsButton = findViewById(R.id.btn_vibration_controls);
        frameSeek = findViewById(R.id.frame_seek);
        frameView = findViewById(R.id.frame_view);
        vibrationPanel = findViewById(R.id.vibration_panel);
        amplitudeSeek = findViewById(R.id.amplitude_seek);
        speedSeek = findViewById(R.id.speed_seek);
        statusView = findViewById(R.id.status_view);
        openButton = findViewById(R.id.btn_open);
        modeButton = findViewById(R.id.btn_style);
        resetButton = findViewById(R.id.btn_reset);
        themeButton = findViewById(R.id.btn_theme);
        sourceButton = findViewById(R.id.btn_source);
        infoButton = findViewById(R.id.btn_details);

        moleculeView = new MoleculeView(this);
        moleculeView.setGestureListener(this);
        viewHost.addView(moleculeView, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        configureIconButton(openButton, "Open", R.drawable.ic_folder_open);
        configureIconButton(modeButton, styleButtonText(), R.drawable.ic_style);
        configureIconButton(resetButton, "Reset view", R.drawable.ic_reset);
        configureIconButton(themeButton, "Toggle background", R.drawable.ic_bg_dark);
        configureIconButton(sourceButton, "Source text", R.drawable.ic_source);
        configureIconButton(infoButton, "Details", R.drawable.ic_info);
        configureIconButton(playButton, "Play", R.drawable.ic_play);
        configureIconButton(prevButton, "Previous mode", R.drawable.ic_prev);
        configureIconButton(nextButton, "Next mode", R.drawable.ic_next);
        configureIconButton(vibrationControlsButton, "Vibration controls", R.drawable.ic_tune);

        frameSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(frameSeek);
        amplitudeSeek.setProgress(amplitudeProgress());
        amplitudeSeek.setContentDescription("Vibration amplitude");
        amplitudeSeek.setTooltipText("Vibration amplitude");
        amplitudeSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(amplitudeSeek);
        speedSeek.setProgress(speedProgress());
        speedSeek.setContentDescription("Animation speed");
        speedSeek.setTooltipText("Animation speed");
        speedSeek.setOnSeekBarChangeListener(this);
        styleSeekBar(speedSeek);
        vibrationPanel.setVisibility(vibrationPanelVisible ? View.VISIBLE : View.GONE);

        applySystemInsets(rootLayout, titleView, topToolBar, controls, vibrationPanel, statusView);
        applyChromeTheme();
    }

    private void configureSystemBars() {
        AppTheme.applySystemBars(this, lightBackground);
    }

    private void applySystemInsets(
            final View root,
            final TextView title,
            final LinearLayout toolbar,
            final LinearLayout controls,
            final LinearLayout vibrationPanel,
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
                WindowInsetsCompat compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, view);
                Insets bars = compatInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                int left = bars.left;
                int top = bars.top;
                int right = bars.right;
                int bottom = bars.bottom;
                title.setPadding(titleLeft + left, titleTop + top, titleRight + right, titleBottom);
                toolbar.setPadding(toolbarLeft + left, toolbarTop, toolbarRight + right, toolbarBottom);
                controls.setPadding(controlsLeft + left, controlsTop, controlsRight + right, controlsBottom);
                vibrationPanel.setPadding(controlsLeft + left, controlsTop, controlsRight + right, controlsBottom);
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
        } else if (id == R.id.btn_vibration_controls) {
            vibrationPanelVisible = !vibrationPanelVisible;
            updateVibrationPanel();
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
        if (seekBar == frameSeek && fromUser) {
            setFrame(progress);
        } else if (seekBar == amplitudeSeek) {
            vibrationAmplitude = amplitudeFromProgress(progress);
            if (moleculeView != null) {
                moleculeView.setVibrationAmplitude(vibrationAmplitude);
            }
            updateStatus();
        } else if (seekBar == speedSeek) {
            playbackSpeed = speedFromProgress(progress);
            updateStatus();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (seekBar == frameSeek) {
            stopPlayback();
        }
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
        openDocumentLauncher.launch(intent);
    }

    private void handleOpenDocumentResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) {
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
        pendingZoom = state.getFloat(STATE_ZOOM, 1f);
        pendingPanX = state.getFloat(STATE_PAN_X, 0f);
        pendingPanY = state.getFloat(STATE_PAN_Y, 0f);
        pendingOrientation = state.getFloatArray(STATE_ORIENTATION);
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
                                statusView.setText(getString(R.string.load_failed_status, message));
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
            loadingView.setIndeterminate(true);
            loadingView.setProgress(0);
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

    private void updateLoadProgress(long bytesRead, long totalBytes) {
        if (totalBytes <= 0L || loadingView == null) {
            return;
        }
        final int progress = (int) Math.max(0L, Math.min(100L, bytesRead * 100L / totalBytes));
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!loading || loadingView == null) {
                    return;
                }
                loadingView.setIndeterminate(false);
                loadingView.setProgress(progress);
            }
        });
    }

    private void restoreUiState(Bundle state) {
        if (state == null) {
            return;
        }
        vibrationAmplitude = clamp(state.getFloat(STATE_VIBRATION_AMPLITUDE, 1f), 0f, 2.5f);
        playbackSpeed = clamp(state.getFloat(STATE_PLAYBACK_SPEED, 1f), 0.25f, 2.5f);
        vibrationPanelVisible = state.getBoolean(STATE_VIBRATION_PANEL_VISIBLE, false);
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
        updateVibrationPanel();
        if (statusView != null) {
            statusView.setVisibility(visibility);
        }
    }

    private void updateVibrationPanel() {
        boolean hasVibration = current != null && current.hasVibrations();
        if (!hasVibration) {
            vibrationPanelVisible = false;
        }
        if (vibrationControlsButton != null) {
            vibrationControlsButton.setEnabled(!loading && hasVibration);
            styleMaterialButton(vibrationControlsButton, vibrationPanelVisible);
        }
        if (vibrationPanel != null) {
            vibrationPanel.setVisibility(!chromeHidden && vibrationPanelVisible && hasVibration
                    ? View.VISIBLE
                    : View.GONE);
            vibrationPanel.setBackgroundColor(AppTheme.panel(lightBackground));
        }
        if (amplitudeSeek != null) {
            amplitudeSeek.setProgress(amplitudeProgress());
            amplitudeSeek.setEnabled(!loading && hasVibration);
        }
        if (speedSeek != null) {
            speedSeek.setProgress(speedProgress());
            speedSeek.setEnabled(!loading && hasVibration);
        }
    }

    private Molecule parseMoleculeFile(Reader reader, String sourceName, boolean gaussian) throws IOException {
        if (gaussian) {
            try {
                return MoleculeParser.parseGaussianOutput(reader, sourceName);
            } catch (IOException ex) {
                throw new IOException("Cannot read Gaussian out/log file. " + ex.getMessage());
            }
        }
        try {
            return MoleculeParser.parseXyzOnly(reader, sourceName);
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
        animationPhase = 0f;
        moleculeView.setMolecule(molecule);
        moleculeView.setVibrationAmplitude(vibrationAmplitude);
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
            moleculeView.restoreViewState(pendingOrientation, pendingZoom, pendingPanX, pendingPanY);
            pendingRestoreView = false;
            pendingOrientation = null;
        }
        updateNavigationButtons();
        updatePlaybackButton();
        updateVibrationPanel();
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
            animationPhase = (float) (Math.PI * 0.5);
            moleculeView.setVibrationMode(safe);
            moleculeView.setVibrationPhase(animationPhase);
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
        frameView.setText(String.format(Locale.US, "%d/%d", safe + 1, current.frameCount()));
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
        playButton.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playButton.setContentDescription(playing ? "Pause animation" : "Play animation");
        styleMaterialButton(playButton, playing);
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
        text.append(compactText(currentFileName.isEmpty() ? current.title : currentFileName, 36))
                .append(" | ").append(current.sourceType);
        if (sessionOnlySource) {
            text.append(" | Session");
        }
        text.append('\n');
        text.append("A").append(current.atomCount())
                .append("  B").append(current.bonds.size())
                .append("  F").append(current.frameCount());
        if (current.hasVibrations()) {
            text.append("  M").append(current.vibrationCount())
                    .append("  ").append(compactText(vibrationStatusText(), 46));
        } else {
            text.append("  ").append(moleculeView.modeName())
                    .append("  ").append(lightBackground ? "White" : "Black");
        }
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
        intent.putExtra(DetailsActivity.EXTRA_ELEMENTS, elementSymbols());
        intent.putExtra(DetailsActivity.EXTRA_ELEMENT_COLORS, elementColors());
        intent.putExtra(DetailsActivity.EXTRA_FREQUENCIES, vibrationFrequencies());
        intent.putExtra(DetailsActivity.EXTRA_IR_INTENSITIES, vibrationIntensities());
        intent.putExtra(DetailsActivity.EXTRA_SELECTED_MODE, vibrationIndex);
        detailsLauncher.launch(intent);
    }

    private void handleDetailsResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != RESULT_OK || data == null || current == null || !current.hasVibrations()) {
            return;
        }
        int mode = data.getIntExtra(DetailsActivity.EXTRA_SELECTED_MODE, -1);
        if (mode >= 0) {
            stopPlayback();
            setFrame(mode);
        }
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

    private String[] elementSymbols() {
        if (current == null) {
            return new String[0];
        }
        java.util.List<String> elements = current.elementsInUse();
        return elements.toArray(new String[0]);
    }

    private int[] elementColors() {
        if (current == null) {
            return new int[0];
        }
        java.util.List<String> elements = current.elementsInUse();
        int[] colors = new int[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            colors[i] = ElementTable.color(elements.get(i));
        }
        return colors;
    }

    private float[] vibrationFrequencies() {
        if (current == null || !current.hasVibrations()) {
            return new float[0];
        }
        float[] values = new float[current.vibrationCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = current.vibrationAt(i).frequency;
        }
        return values;
    }

    private float[] vibrationIntensities() {
        if (current == null || !current.hasVibrations()) {
            return new float[0];
        }
        float[] values = new float[current.vibrationCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = current.vibrationAt(i).irIntensity;
        }
        return values;
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
        themeButton.setIconResource(lightBackground ? R.drawable.ic_bg_light : R.drawable.ic_bg_dark);
        themeButton.setContentDescription(lightBackground ? "Switch to black background" : "Switch to white background");
    }

    private void applyChromeTheme() {
        int bg = AppTheme.bg(lightBackground);
        int panel = AppTheme.panel(lightBackground);
        int title = AppTheme.text(lightBackground);
        int secondary = AppTheme.secondary(lightBackground);

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
        if (vibrationPanel != null) {
            vibrationPanel.setBackgroundColor(panel);
        }
        refreshButtonStyles(rootLayout);
        updateThemeButton();
        updateModeButton();
        updatePlaybackButton();
        updateVibrationPanel();
        if (sourceButton != null) {
            sourceButton.setEnabled(hasSourceReference());
        }
        styleSeekBar(frameSeek);
        styleSeekBar(amplitudeSeek);
        styleSeekBar(speedSeek);
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
        if (view instanceof MaterialButton) {
            styleMaterialButton((MaterialButton) view, false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshButtonStyles(group.getChildAt(i));
            }
        }
    }

    private LoadedMolecule readAndParse(InputStream input, long totalBytes, String sourceName, boolean gaussian) throws IOException {
        try (CountingInputStream counting = new CountingInputStream(input, totalBytes);
             Reader reader = new InputStreamReader(counting, StandardCharsets.UTF_8)) {
            return new LoadedMolecule(parseMoleculeFile(reader, sourceName, gaussian));
        }
    }

    private long contentLength(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return -1L;
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
            long totalBytes = contentLength(uri);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("Cannot open file");
                }
                return readAndParse(input, totalBytes, name, MoleculeParser.looksLikeGaussianName(name));
            } catch (SecurityException ex) {
                if (sessionOnlySource) {
                    throw new IOException("Android no longer grants access to this session-only file. Reopen it from the folder button.", ex);
                }
                throw ex;
            }
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
                return readAndParse(input, -1L, assetPath, MoleculeParser.looksLikeGaussianName(assetPath));
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

    private final class CountingInputStream extends FilterInputStream {
        private final long totalBytes;
        private long bytesRead;

        CountingInputStream(InputStream input, long totalBytes) {
            super(input);
            this.totalBytes = totalBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                noteBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                noteBytes(read);
            }
            return read;
        }

        private void noteBytes(int count) throws IOException {
            bytesRead += count;
            if (bytesRead > MAX_PARSE_BYTES) {
                throw new IOException("File is too large to parse in the viewer. Use the source-text page to inspect it.");
            }
            updateLoadProgress(bytesRead, totalBytes);
        }
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

    private void configureIconButton(MaterialButton button, String description, int imageResId) {
        button.setIconResource(imageResId);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setHapticFeedbackEnabled(true);
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setOnClickListener(this);
        styleMaterialButton(button, false);
    }

    private void styleSeekBar(SeekBar seekBar) {
        if (seekBar == null) {
            return;
        }
        seekBar.setProgressTintList(ColorStateList.valueOf(AppTheme.accent(this)));
        seekBar.setThumbTintList(ColorStateList.valueOf(AppTheme.accent(this)));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(AppTheme.borderSoft(lightBackground)));
        seekBar.setSplitTrack(false);
    }

    private void styleMaterialButton(MaterialButton button, boolean active) {
        button.setIconTint(buttonTextColors());
        button.setTextColor(buttonTextColors());
        button.setBackgroundTintList(ColorStateList.valueOf(active
                ? AppTheme.active(lightBackground)
                : AppTheme.button(lightBackground)));
        button.setStrokeColor(ColorStateList.valueOf(active
                ? AppTheme.accentActive(this)
                : AppTheme.border(lightBackground)));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(20));
        button.setRippleColor(ColorStateList.valueOf(AppTheme.accent(this)));
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
                        AppTheme.textDisabled(lightBackground),
                        AppTheme.text(lightBackground),
                        AppTheme.text(lightBackground),
                        AppTheme.text(lightBackground)
                }
        );
    }

    private GradientDrawable railBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(AppTheme.rail(lightBackground));
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), lightBackground ? AppTheme.border(true) : AppTheme.borderSoft(false));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int amplitudeProgress() {
        return Math.round(clamp(vibrationAmplitude, 0f, 2.5f) / 2.5f * 100f);
    }

    private float amplitudeFromProgress(int progress) {
        return clamp(progress, 0, 100) / 100f * 2.5f;
    }

    private int speedProgress() {
        return Math.round((clamp(playbackSpeed, 0.25f, 2.5f) - 0.25f) / 2.25f * 100f);
    }

    private float speedFromProgress(int progress) {
        return 0.25f + clamp(progress, 0, 100) / 100f * 2.25f;
    }

    private long vibrationDelayMs() {
        return Math.max(16L, Math.round(55f / clamp(playbackSpeed, 0.25f, 2.5f)));
    }

    private long frameDelayMs() {
        return Math.max(45L, Math.round(180f / clamp(playbackSpeed, 0.25f, 2.5f)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
                    animationPhase += (float) (Math.PI * 2.0 / 48.0 * playbackSpeed);
                    if (animationPhase > Math.PI * 2.0) {
                        animationPhase -= (float) (Math.PI * 2.0);
                    }
                    moleculeView.setVibrationPhase(animationPhase);
                    handler.postDelayed(this, vibrationDelayMs());
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
            handler.postDelayed(this, frameDelayMs());
        }
    }

}
