package io.iaw.molview;

final class SourceTextStore {
    static final int KIND_NONE = 0;
    static final int KIND_URI = 1;
    static final int KIND_ASSET = 2;

    private static String title = "";
    private static String location = "";
    private static int kind = KIND_NONE;

    private SourceTextStore() {
    }

    static void setUri(String titleValue, String uriString) {
        set(titleValue, KIND_URI, uriString);
    }

    static void setAsset(String titleValue, String assetPath) {
        set(titleValue, KIND_ASSET, assetPath);
    }

    private static void set(String titleValue, int sourceKind, String sourceLocation) {
        title = titleValue == null ? "" : titleValue;
        kind = sourceKind;
        location = sourceLocation == null ? "" : sourceLocation;
    }

    static String title() {
        return title;
    }

    static int kind() {
        return kind;
    }

    static String location() {
        return location;
    }
}
