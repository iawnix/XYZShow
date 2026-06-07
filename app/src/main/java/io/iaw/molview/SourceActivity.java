package io.iaw.molview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.android.material.button.MaterialButton;

public final class SourceActivity extends Activity {
    static final String EXTRA_TITLE = "io.iaw.molview.extra.SOURCE_TITLE";
    static final String EXTRA_KIND = "io.iaw.molview.extra.SOURCE_KIND";
    static final String EXTRA_LOCATION = "io.iaw.molview.extra.SOURCE_LOCATION";

    private static final int PAGE_LINES = 160;
    private static final String STATE_TITLE = "source_title";
    private static final String STATE_KIND = "source_kind";
    private static final String STATE_LOCATION = "source_location";
    private static final String STATE_PAGE = "source_page";

    private ConstraintLayout root;
    private LinearLayout header;
    private LinearLayout pagerHost;
    private LinearLayout pagerBar;
    private TextView titleView;
    private TextView pagerView;
    private ListView sourceList;
    private SourceLineAdapter sourceAdapter;
    private MaterialButton firstButton;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private MaterialButton lastButton;
    private MaterialButton searchButton;
    private ProgressBar loadingView;
    private final ExecutorService sourceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor();
    private final List<String> currentPageLines = new ArrayList<>();
    private final Object pageIndexLock = new Object();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private FutureTask<PageIndex> pageIndexTask;
    private boolean pageIndexStarted;
    private String sourceTitle = "";
    private String sourceLocation = "";
    private int sourceKind = SourceKind.NONE;
    private int totalLines = 1;
    private int page;
    private boolean pageCountKnown = true;
    private boolean pageHasMore;
    private boolean destroyed;
    private boolean light;
    private int pendingFocusLine = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.bind(this);
        light = AppTheme.isLight(this);
        AppTheme.applySystemBars(this, light);
        int initialPage = restoreSource(savedInstanceState);
        buildLayout();
        titleView.setText(sourceTitle);
        renderPage(initialPage);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TITLE, sourceTitle);
        outState.putInt(STATE_KIND, sourceKind);
        outState.putString(STATE_LOCATION, sourceLocation);
        outState.putInt(STATE_PAGE, page);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        renderGeneration.incrementAndGet();
        sourceExecutor.shutdownNow();
        indexExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildLayout() {
        setContentView(R.layout.activity_source);
        root = findViewById(R.id.source_root);
        header = findViewById(R.id.source_header);
        pagerHost = findViewById(R.id.source_pager_host);
        pagerBar = findViewById(R.id.source_pager_bar);
        titleView = findViewById(R.id.source_title);
        pagerView = findViewById(R.id.source_pager);
        firstButton = findViewById(R.id.source_first);
        prevButton = findViewById(R.id.source_prev);
        nextButton = findViewById(R.id.source_next);
        searchButton = findViewById(R.id.source_search);
        lastButton = findViewById(R.id.source_last);
        sourceList = findViewById(R.id.source_list);
        loadingView = findViewById(R.id.source_loading);
        MaterialButton closeButton = findViewById(R.id.source_close);

        root.setBackgroundColor(AppTheme.bg(light));
        header.setBackgroundColor(AppTheme.panel(light));
        pagerHost.setBackgroundColor(AppTheme.bg(light));
        pagerBar.setBackground(round(AppTheme.panel(light), dp(12)));
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

        configureIconButton(firstButton, "第一页", R.drawable.ic_page_first);
        firstButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(0);
            }
        });

        configureIconButton(prevButton, "上一页", R.drawable.ic_page_prev);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(page - 1);
            }
        });

        pagerView.setTextColor(AppTheme.text(light));
        pagerView.setTypeface(Typeface.DEFAULT_BOLD);
        pagerView.setEllipsize(TextUtils.TruncateAt.END);

        configureIconButton(nextButton, "下一页", R.drawable.ic_page_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(page + 1);
            }
        });

        configureIconButton(searchButton, "搜索或跳行", R.drawable.ic_search);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSearchDialog();
            }
        });

        configureIconButton(lastButton, "最后一页", R.drawable.ic_page_last);
        lastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(pageCount() - 1);
            }
        });

        sourceList.setDivider(null);
        sourceList.setCacheColorHint(0x00000000);
        sourceList.setFastScrollEnabled(true);
        sourceList.setBackgroundColor(AppTheme.bg(light));
        sourceAdapter = new SourceLineAdapter();
        sourceList.setAdapter(sourceAdapter);
        applyInsets();
    }

    private void configureIconButton(MaterialButton button, String description, int imageResId) {
        button.setIconResource(imageResId);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
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

    private void renderPage(int targetPage) {
        renderPage(targetPage, -1);
    }

    private void renderPage(int targetPage, int focusLine) {
        pendingFocusLine = focusLine;
        setLoading(true);
        final int generation = renderGeneration.incrementAndGet();
        final int requestedPage = Math.max(0, targetPage);
        if (requestedPage == 0) {
            ensurePageIndexStarted();
        }
        sourceExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final PageData data;
                    if (requestedPage == 0 && !isPageIndexReady()) {
                        data = readFirstPage();
                    } else {
                        data = readPageFromIndex(pageIndex(), requestedPage);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != renderGeneration.get()) {
                                return;
                            }
                            applyPage(data);
                            setLoading(false);
                        }
                    });
                } catch (final IOException | RuntimeException ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != renderGeneration.get()) {
                                return;
                            }
                            setLoading(false);
                            Toast.makeText(SourceActivity.this, errorMessage(ex), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void applyPage(PageData data) {
        page = data.page;
        pageCountKnown = data.totalKnown;
        pageHasMore = data.hasMore;
        if (data.totalKnown) {
            totalLines = data.totalLines;
        } else {
            totalLines = Math.max(totalLines, data.startLine + data.lines.size());
        }
        currentPageLines.clear();
        currentPageLines.addAll(data.lines);
        sourceAdapter.setPage(data.startLine, currentPageLines);
        updatePagerControls();
        sourceList.post(new Runnable() {
            @Override
            public void run() {
                if (pendingFocusLine >= data.startLine
                        && pendingFocusLine < data.startLine + currentPageLines.size()) {
                    int lineOffset = pendingFocusLine - data.startLine;
                    sourceList.setSelection(Math.max(0, lineOffset));
                    pendingFocusLine = -1;
                } else {
                    sourceList.setSelection(0);
                }
            }
        });
    }

    private void updatePagerControls() {
        if (pagerView == null) {
            return;
        }
        pagerView.setText(String.format(Locale.US,
                "Page %d/%s",
                page + 1,
                pageCountKnown ? String.valueOf(pageCount()) : "..."));
        firstButton.setEnabled(page > 0);
        prevButton.setEnabled(page > 0);
        nextButton.setEnabled(pageCountKnown ? page + 1 < pageCount() : pageHasMore);
        lastButton.setEnabled(pageCountKnown && page + 1 < pageCount());
        searchButton.setEnabled(!loadingView.isShown());
    }

    private void showSearchDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("line number or text");
        input.setSelectAllOnFocus(true);
        input.setTextColor(AppTheme.text(light));
        input.setHintTextColor(AppTheme.gutter(light));
        input.setTextSize(15);
        int padding = dp(18);
        input.setPadding(padding, dp(12), padding, dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Find")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String query = input.getText().toString().trim();
                        if (query.isEmpty()) {
                            return;
                        }
                        dialog.dismiss();
                        if (query.matches("\\d+")) {
                            jumpToLine(Math.max(1, parseLineNumber(query)));
                        } else {
                            findText(query);
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private int parseLineNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void jumpToLine(int lineNumber) {
        ensurePageIndexStarted();
        int targetPage = (Math.max(1, lineNumber) - 1) / PAGE_LINES;
        renderPage(targetPage, lineNumber);
    }

    private void findText(final String query) {
        setLoading(true);
        final int generation = renderGeneration.incrementAndGet();
        sourceExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final int line = firstMatchingLine(query);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != renderGeneration.get()) {
                                return;
                            }
                            setLoading(false);
                            if (line <= 0) {
                                Toast.makeText(SourceActivity.this, "No match", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            renderPage((line - 1) / PAGE_LINES, line);
                        }
                    });
                } catch (final IOException | RuntimeException ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (generation != renderGeneration.get()) {
                                return;
                            }
                            setLoading(false);
                            Toast.makeText(SourceActivity.this, errorMessage(ex), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private int firstMatchingLine(String query) throws IOException {
        String needle = query.toLowerCase(Locale.US);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openInput(), StandardCharsets.UTF_8), 8192)) {
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase(Locale.US).contains(needle)) {
                    return lineNumber;
                }
                lineNumber++;
            }
        }
        return -1;
    }

    private int pageCount() {
        return Math.max(1, (totalLines + PAGE_LINES - 1) / PAGE_LINES);
    }

    private PageData readFirstPage() throws IOException {
        List<String> pageLines = new ArrayList<>();
        boolean hasMore;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openInput(), StandardCharsets.UTF_8), 8192)) {
            String line;
            while (pageLines.size() < PAGE_LINES && (line = reader.readLine()) != null) {
                pageLines.add(line);
            }
            hasMore = pageLines.size() == PAGE_LINES && reader.readLine() != null;
        }
        if (pageLines.isEmpty()) {
            pageLines.add("");
            return new PageData(0, 1, 1, pageLines, true, false);
        }
        int visibleTotal = hasMore ? PAGE_LINES + 1 : pageLines.size();
        return new PageData(0, 1, visibleTotal, pageLines, !hasMore, hasMore);
    }

    private PageData readPageFromIndex(PageIndex index, int requestedPage) throws IOException {
        List<String> pageLines = new ArrayList<>();
        int pages = Math.max(1, index.pageCount());
        int safePage = Math.max(0, Math.min(requestedPage, pages - 1));
        int startLineIndex = safePage * PAGE_LINES;
        if (index.totalLines == 0) {
            pageLines.add("");
            return new PageData(0, 1, 1, pageLines, true, false);
        }
        try (InputStream input = openInput()) {
            skipFully(input, index.pageOffset(safePage));
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 8192);
            String line;
            while (pageLines.size() < PAGE_LINES && (line = reader.readLine()) != null) {
                pageLines.add(line);
            }
        }
        return new PageData(safePage,
                startLineIndex + 1,
                Math.max(1, index.totalLines),
                pageLines,
                true,
                safePage + 1 < pages);
    }

    private PageIndex pageIndex() throws IOException {
        ensurePageIndexStarted();
        FutureTask<PageIndex> task = ensurePageIndexTask();
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while indexing source text", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Cannot index source text", cause);
        }
    }

    private boolean isPageIndexReady() {
        synchronized (pageIndexLock) {
            return pageIndexTask != null && pageIndexTask.isDone();
        }
    }

    private void ensurePageIndexStarted() {
        final FutureTask<PageIndex> task;
        synchronized (pageIndexLock) {
            task = ensurePageIndexTaskLocked();
            if (pageIndexStarted) {
                return;
            }
            pageIndexStarted = true;
        }
        indexExecutor.execute(new Runnable() {
            @Override
            public void run() {
                task.run();
                try {
                    final PageIndex index = task.get();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed) {
                                applyPageIndex(index);
                            }
                        }
                    });
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ignored) {
                }
            }
        });
    }

    private FutureTask<PageIndex> ensurePageIndexTask() {
        synchronized (pageIndexLock) {
            return ensurePageIndexTaskLocked();
        }
    }

    private FutureTask<PageIndex> ensurePageIndexTaskLocked() {
        if (pageIndexTask == null) {
            pageIndexTask = new FutureTask<>(new Callable<PageIndex>() {
                @Override
                public PageIndex call() throws IOException {
                    return buildPageIndex();
                }
            });
        }
        return pageIndexTask;
    }

    private void applyPageIndex(PageIndex index) {
        totalLines = Math.max(1, index.totalLines);
        pageCountKnown = true;
        pageHasMore = page + 1 < pageCount();
        updatePagerControls();
    }

    private PageIndex buildPageIndex() throws IOException {
        List<Long> pageOffsets = new ArrayList<>();
        pageOffsets.add(0L);
        int newlineCount = 0;
        long position = 0L;
        boolean sawAnyByte = false;
        boolean endedWithNewline = false;
        byte[] buffer = new byte[8192];
        try (InputStream input = openInput()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                sawAnyByte = true;
                for (int i = 0; i < read; i++) {
                    position++;
                    if (buffer[i] == '\n') {
                        newlineCount++;
                        endedWithNewline = true;
                        if (newlineCount % PAGE_LINES == 0) {
                            pageOffsets.add(position);
                        }
                    } else {
                        endedWithNewline = false;
                    }
                }
            }
        }
        int total = sawAnyByte ? newlineCount + (endedWithNewline ? 0 : 1) : 0;
        int pages = Math.max(1, (Math.max(1, total) + PAGE_LINES - 1) / PAGE_LINES);
        while (pageOffsets.size() > pages) {
            pageOffsets.remove(pageOffsets.size() - 1);
        }
        while (pageOffsets.size() < pages) {
            pageOffsets.add(position);
        }
        return new PageIndex(total, pageOffsets);
    }

    private InputStream openInput() throws IOException {
        InputStream input;
        if (sourceKind == SourceKind.URI) {
            input = getContentResolver().openInputStream(Uri.parse(sourceLocation));
        } else if (sourceKind == SourceKind.ASSET) {
            input = getAssets().open(sourceLocation);
        } else {
            input = null;
        }
        if (input == null) {
            throw new IOException("Cannot open source text");
        }
        return input;
    }

    private void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            if (input.read() == -1) {
                throw new IOException("Cannot seek source text");
            }
            remaining--;
        }
    }

    private static String errorMessage(Throwable ex) {
        if (ex == null) {
            return "Source read failed";
        }
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private void setLoading(boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            firstButton.setEnabled(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            lastButton.setEnabled(false);
            searchButton.setEnabled(false);
        } else {
            updatePagerControls();
        }
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
                        dp(8));
                pagerHost.setPadding(dp(12) + bars.left,
                        dp(10),
                        dp(12) + bars.right,
                        dp(10));
                sourceList.setPadding(0, 0, dp(16) + bars.right, bars.bottom);
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int gutterBg() {
        return AppTheme.gutterBg(light);
    }

    private int restoreSource(Bundle state) {
        if (state != null) {
            sourceTitle = state.getString(STATE_TITLE, "");
            sourceKind = state.getInt(STATE_KIND, SourceKind.NONE);
            sourceLocation = state.getString(STATE_LOCATION, "");
            return Math.max(0, state.getInt(STATE_PAGE, 0));
        }
        sourceTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (sourceTitle == null || sourceTitle.isEmpty()) {
            sourceTitle = "Source";
        }
        sourceKind = getIntent().getIntExtra(EXTRA_KIND, SourceKind.NONE);
        sourceLocation = getIntent().getStringExtra(EXTRA_LOCATION);
        return 0;
    }

    private static final class PageData {
        final int page;
        final int startLine;
        final int totalLines;
        final List<String> lines;
        final boolean totalKnown;
        final boolean hasMore;

        PageData(int page, int startLine, int totalLines, List<String> lines, boolean totalKnown, boolean hasMore) {
            this.page = page;
            this.startLine = startLine;
            this.totalLines = totalLines;
            this.lines = lines;
            this.totalKnown = totalKnown;
            this.hasMore = hasMore;
        }
    }

    private static final class PageIndex {
        final int totalLines;
        final List<Long> pageOffsets;

        PageIndex(int totalLines, List<Long> pageOffsets) {
            this.totalLines = totalLines;
            this.pageOffsets = pageOffsets;
        }

        int pageCount() {
            return Math.max(1, (Math.max(1, totalLines) + PAGE_LINES - 1) / PAGE_LINES);
        }

        long pageOffset(int page) {
            if (page < 0 || page >= pageOffsets.size()) {
                throw new IllegalArgumentException("Page offset missing for page " + (page + 1));
            }
            return pageOffsets.get(page);
        }
    }

    private final class SourceLineAdapter extends BaseAdapter {
        private int startLine = 1;
        private final List<String> lines = new ArrayList<>();

        void setPage(int startLine, List<String> sourceLines) {
            this.startLine = startLine;
            lines.clear();
            lines.addAll(sourceLines);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return lines.size();
        }

        @Override
        public Object getItem(int position) {
            return lines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return startLine + position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LineRow row;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof LineRow) {
                row = (LineRow) convertView.getTag();
            } else {
                row = new LineRow();
                row.root.setTag(row);
            }
            row.bind(startLine + position, lines.get(position));
            return row.root;
        }
    }

    private final class LineRow {
        final LinearLayout root;
        final TextView gutter;
        final TextView source;

        LineRow() {
            root = new LinearLayout(SourceActivity.this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.TOP);
            root.setPadding(0, dp(1), dp(16), dp(1));
            root.setMinimumWidth(dp(820));

            gutter = new TextView(SourceActivity.this);
            gutter.setTextColor(AppTheme.gutter(light));
            gutter.setBackgroundColor(gutterBg());
            gutter.setTextSize(12);
            gutter.setGravity(Gravity.TOP | Gravity.END);
            gutter.setTypeface(Typeface.MONOSPACE);
            gutter.setIncludeFontPadding(false);
            gutter.setSingleLine(true);
            gutter.setPadding(dp(8), dp(2), dp(8), dp(2));
            root.addView(gutter, new LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.WRAP_CONTENT));

            View divider = new View(SourceActivity.this);
            divider.setBackgroundColor(AppTheme.border(light));
            root.addView(divider, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));

            source = new TextView(SourceActivity.this);
            source.setTextColor(AppTheme.secondary(light));
            source.setTextSize(12);
            source.setTypeface(Typeface.MONOSPACE);
            source.setTextIsSelectable(true);
            source.setIncludeFontPadding(false);
            source.setSingleLine(true);
            source.setHorizontallyScrolling(true);
            source.setPadding(dp(12), dp(2), dp(16), dp(2));
            root.addView(source, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        void bind(int lineNumber, String text) {
            gutter.setText(String.valueOf(lineNumber));
            source.setText(text);
        }
    }
}
