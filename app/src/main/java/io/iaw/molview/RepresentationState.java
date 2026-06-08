package io.iaw.molview;

final class RepresentationState {
    static final int MODE_SPACE = 0;
    static final int MODE_STICKS = 1;
    static final int DETAIL_AUTO = 0;
    static final int DETAIL_REDUCED = 1;
    static final int BACKGROUND_DARK = 0;
    static final int BACKGROUND_LIGHT = 1;

    private int mode = MODE_SPACE;
    private int detailLevel = DETAIL_AUTO;
    private int backgroundMode = BACKGROUND_DARK;

    int mode() {
        return mode;
    }

    void setMode(int mode) {
        this.mode = mode == MODE_STICKS ? MODE_STICKS : MODE_SPACE;
    }

    int cycleMode() {
        setMode(mode == MODE_SPACE ? MODE_STICKS : MODE_SPACE);
        return mode;
    }

    int detailLevel() {
        return detailLevel;
    }

    void setDetailLevel(int detailLevel) {
        this.detailLevel = detailLevel == DETAIL_REDUCED ? DETAIL_REDUCED : DETAIL_AUTO;
    }

    int backgroundMode() {
        return backgroundMode;
    }

    void setBackgroundMode(int backgroundMode) {
        this.backgroundMode = backgroundMode == BACKGROUND_LIGHT ? BACKGROUND_LIGHT : BACKGROUND_DARK;
    }

    String modeName() {
        return mode == MODE_STICKS ? "Stick" : "Space";
    }

    Snapshot snapshot() {
        return new Snapshot(mode, detailLevel, backgroundMode);
    }

    RepresentationState copy() {
        RepresentationState copy = new RepresentationState();
        copy.restore(snapshot());
        return copy;
    }

    void restore(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        setMode(snapshot.mode);
        setDetailLevel(snapshot.detailLevel);
        setBackgroundMode(snapshot.backgroundMode);
    }

    static final class Snapshot {
        final int mode;
        final int detailLevel;
        final int backgroundMode;

        Snapshot(int mode, int detailLevel, int backgroundMode) {
            this.mode = mode;
            this.detailLevel = detailLevel;
            this.backgroundMode = backgroundMode;
        }
    }
}
