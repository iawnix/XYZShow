package io.iaw.molview;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

public final class SourceActivity extends Activity {
    static final String EXTRA_TITLE = "io.iaw.molview.extra.SOURCE_TITLE";
    static final String EXTRA_KIND = "io.iaw.molview.extra.SOURCE_KIND";
    static final String EXTRA_LOCATION = "io.iaw.molview.extra.SOURCE_LOCATION";

    private static final int PAGE_LINES = 400;
    private static final String STATE_TITLE = "source_title";
    private static final String STATE_KIND = "source_kind";
    private static final String STATE_LOCATION = "source_location";
    private static final String STATE_PAGE = "source_page";

    private LinearLayout root;
    private LinearLayout header;
    private LinearLayout pagerHost;
    private LinearLayout pagerBar;
    private TextView titleView;
    private TextView pagerView;
    private TextView gutterView;
    private TextView sourceView;
    private ImageButton firstButton;
    private ImageButton prevButton;
    private ImageButton nextButton;
    private ImageButton lastButton;
    private ProgressBar loadingView;
    private ScrollView vertical;
    private final ExecutorService sourceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor();
    private final List<String> currentPageLines = new ArrayList<>();
    private final Object pageIndexLock = new Object();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private FutureTask<PageIndex> pageIndexTask;
    private boolean pageIndexStarted;
    private String sourceTitle = "";
    private String sourceLocation = "";
    private int sourceKind = SourceTextStore.KIND_NONE;
    private int totalLines = 1;
    private int page;
    private boolean pageCountKnown = true;
    private boolean pageHasMore;
    private boolean destroyed;
    private boolean light;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(light));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(8));
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

        ImageButton closeButton = iconButton("Close", R.drawable.ic_close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        header.addView(closeButton, iconLayout(false));

        pagerHost = new LinearLayout(this);
        pagerHost.setOrientation(LinearLayout.VERTICAL);
        pagerHost.setPadding(dp(12), dp(10), dp(12), dp(10));
        pagerHost.setBackgroundColor(AppTheme.bg(light));

        pagerBar = new LinearLayout(this);
        pagerBar.setOrientation(LinearLayout.HORIZONTAL);
        pagerBar.setGravity(Gravity.CENTER_VERTICAL);
        pagerBar.setPadding(dp(8), dp(8), dp(10), dp(8));
        pagerBar.setBackground(round(AppTheme.panel(light), dp(12)));

        firstButton = iconButton("第一页", R.drawable.ic_page_first);
        firstButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(0);
            }
        });
        pagerBar.addView(firstButton, iconLayout(true));

        prevButton = iconButton("上一页", R.drawable.ic_page_prev);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(page - 1);
            }
        });
        pagerBar.addView(prevButton, iconLayout(true));

        pagerView = new TextView(this);
        pagerView.setTextColor(AppTheme.text(light));
        pagerView.setTextSize(13);
        pagerView.setTypeface(Typeface.DEFAULT_BOLD);
        pagerView.setGravity(Gravity.CENTER);
        pagerView.setSingleLine(true);
        pagerView.setEllipsize(TextUtils.TruncateAt.END);
        pagerBar.addView(pagerView, new LinearLayout.LayoutParams(0, dp(42), 1f));

        nextButton = iconButton("下一页", R.drawable.ic_page_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(page + 1);
            }
        });
        pagerBar.addView(nextButton, iconLayout(true));

        lastButton = iconButton("最后一页", R.drawable.ic_page_last);
        lastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderPage(pageCount() - 1);
            }
        });
        pagerBar.addView(lastButton, iconLayout(false));
        pagerHost.addView(pagerBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        vertical = new ScrollView(this);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.HORIZONTAL);
        editor.setPadding(0, dp(10), dp(16), dp(24));

        gutterView = new TextView(this);
        gutterView.setTextColor(AppTheme.gutter(light));
        gutterView.setBackgroundColor(gutterBg());
        gutterView.setTextSize(12);
        gutterView.setGravity(Gravity.TOP | Gravity.END);
        gutterView.setTypeface(Typeface.MONOSPACE);
        gutterView.setIncludeFontPadding(false);
        gutterView.setLineSpacing(0f, 1.08f);
        gutterView.setSingleLine(false);
        gutterView.setHorizontallyScrolling(true);
        gutterView.setPadding(dp(8), 0, dp(8), 0);
        editor.addView(gutterView, new LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.WRAP_CONTENT));

        View gutterDivider = new View(this);
        gutterDivider.setBackgroundColor(AppTheme.border(light));
        editor.addView(gutterDivider, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));

        sourceView = new TextView(this);
        sourceView.setTextColor(AppTheme.secondary(light));
        sourceView.setTextSize(12);
        sourceView.setTypeface(Typeface.MONOSPACE);
        sourceView.setTextIsSelectable(true);
        sourceView.setIncludeFontPadding(false);
        sourceView.setLineSpacing(0f, 1.08f);
        sourceView.setSingleLine(false);
        sourceView.setHorizontallyScrolling(true);
        sourceView.setPadding(dp(12), 0, 0, 0);
        editor.addView(sourceView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        horizontal.addView(editor, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
        vertical.addView(horizontal, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.WRAP_CONTENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout sourceHost = new FrameLayout(this);
        sourceHost.addView(vertical, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        loadingView = new ProgressBar(this);
        loadingView.setIndeterminate(true);
        loadingView.setVisibility(View.GONE);
        sourceHost.addView(loadingView, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));

        applyInsets();
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(pagerHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(sourceHost, new LinearLayout.LayoutParams(
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
        states.addState(new int[]{-android.R.attr.state_enabled}, round(light ? 0xffededf0 : 0xff252527));
        states.addState(new int[]{android.R.attr.state_pressed}, round(AppTheme.ACCENT));
        states.addState(new int[]{}, round(AppTheme.button(light)));
        return states;
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
                            Toast.makeText(SourceActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
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
        StringBuilder gutter = new StringBuilder();
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < currentPageLines.size(); i++) {
            if (i > 0) {
                gutter.append('\n');
                source.append('\n');
            }
            gutter.append(data.startLine + i);
            source.append(currentPageLines.get(i));
        }
        gutterView.setText(gutter.toString());
        sourceView.setText(source.toString());
        updatePagerControls();
        vertical.post(new Runnable() {
            @Override
            public void run() {
                vertical.scrollTo(0, 0);
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
        return new PageIndex(total, pageOffsets);
    }

    private InputStream openInput() throws IOException {
        InputStream input;
        if (sourceKind == SourceTextStore.KIND_URI) {
            input = getContentResolver().openInputStream(Uri.parse(sourceLocation));
        } else if (sourceKind == SourceTextStore.KIND_ASSET) {
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

    private void setLoading(boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            firstButton.setEnabled(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            lastButton.setEnabled(false);
        } else {
            updatePagerControls();
        }
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                header.setPadding(dp(14) + insets.getSystemWindowInsetLeft(),
                        dp(10) + insets.getSystemWindowInsetTop(),
                        dp(14) + insets.getSystemWindowInsetRight(),
                        dp(8));
                pagerHost.setPadding(dp(12) + insets.getSystemWindowInsetLeft(),
                        dp(10),
                        dp(12) + insets.getSystemWindowInsetRight(),
                        dp(10));
                sourceView.setPadding(dp(12), 0,
                        dp(16) + insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
                gutterView.setPadding(dp(8), 0, dp(8), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int gutterBg() {
        return light ? 0xffefeff1 : 0xff202022;
    }

    private int restoreSource(Bundle state) {
        if (state != null) {
            sourceTitle = state.getString(STATE_TITLE, "");
            sourceKind = state.getInt(STATE_KIND, SourceTextStore.KIND_NONE);
            sourceLocation = state.getString(STATE_LOCATION, "");
            return Math.max(0, state.getInt(STATE_PAGE, 0));
        }
        sourceTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (sourceTitle == null || sourceTitle.isEmpty()) {
            sourceTitle = SourceTextStore.title();
        }
        sourceKind = getIntent().getIntExtra(EXTRA_KIND, SourceTextStore.KIND_NONE);
        sourceLocation = getIntent().getStringExtra(EXTRA_LOCATION);
        if (sourceKind == SourceTextStore.KIND_NONE) {
            sourceKind = SourceTextStore.kind();
        }
        if (sourceLocation == null || sourceLocation.isEmpty()) {
            sourceLocation = SourceTextStore.location();
        }
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
                return 0L;
            }
            return pageOffsets.get(page);
        }
    }
}
