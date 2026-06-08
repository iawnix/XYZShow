package io.iaw.molview;

final class ViewerState {
    private final SourceState source = new SourceState();
    private final CameraState camera = new CameraState();
    private final PlaybackState playback = new PlaybackState();
    private final RepresentationState representation = new RepresentationState();
    private Molecule molecule;

    Molecule molecule() {
        return molecule;
    }

    void setMolecule(Molecule molecule) {
        this.molecule = molecule;
    }

    SourceState source() {
        return source;
    }

    CameraState camera() {
        return camera;
    }

    PlaybackState playback() {
        return playback;
    }

    RepresentationState representation() {
        return representation;
    }

    boolean hasSourceReference() {
        return source.hasReference();
    }

    static final class SourceState {
        private int kind = SourceKind.NONE;
        private String uri = "";
        private String assetPath = "";
        private String label = "";
        private String fileName = "";
        private String title = "";
        private boolean sessionOnly;

        int kind() {
            return kind;
        }

        String uri() {
            return uri;
        }

        String assetPath() {
            return assetPath;
        }

        String label() {
            return label;
        }

        String fileName() {
            return fileName;
        }

        String title() {
            return title;
        }

        boolean sessionOnly() {
            return sessionOnly;
        }

        void setUri(String uri, String label, String fileName, String title, boolean sessionOnly) {
            this.kind = SourceKind.URI;
            this.uri = clean(uri);
            this.assetPath = "";
            this.label = clean(label);
            this.fileName = clean(fileName);
            this.title = clean(title);
            this.sessionOnly = sessionOnly;
        }

        void setAsset(String assetPath, String label, String fileName, String title) {
            this.kind = SourceKind.ASSET;
            this.uri = "";
            this.assetPath = clean(assetPath);
            this.label = clean(label);
            this.fileName = clean(fileName);
            this.title = clean(title);
            this.sessionOnly = false;
        }

        void clear() {
            kind = SourceKind.NONE;
            uri = "";
            assetPath = "";
            label = "";
            fileName = "";
            title = "";
            sessionOnly = false;
        }

        void setSessionOnly(boolean sessionOnly) {
            this.sessionOnly = sessionOnly;
        }

        void setTitle(String title) {
            this.title = clean(title);
        }

        boolean hasReference() {
            return kind == SourceKind.URI && !uri.isEmpty()
                    || kind == SourceKind.ASSET && !assetPath.isEmpty();
        }

        private static String clean(String value) {
            return value == null ? "" : value;
        }
    }

    static final class CameraState {
        private int frameIndex;
        private float zoom = 1f;
        private float panX;
        private float panY;
        private float[] orientation;

        int frameIndex() {
            return frameIndex;
        }

        void setFrameIndex(int frameIndex) {
            this.frameIndex = Math.max(0, frameIndex);
        }

        float zoom() {
            return zoom;
        }

        float panX() {
            return panX;
        }

        float panY() {
            return panY;
        }

        float[] orientation() {
            if (orientation == null) {
                return null;
            }
            float[] copy = new float[orientation.length];
            System.arraycopy(orientation, 0, copy, 0, orientation.length);
            return copy;
        }

        void setView(float[] orientation, float zoom, float panX, float panY) {
            if (orientation != null && orientation.length == 16) {
                this.orientation = new float[16];
                System.arraycopy(orientation, 0, this.orientation, 0, 16);
            } else {
                this.orientation = null;
            }
            this.zoom = zoom;
            this.panX = panX;
            this.panY = panY;
        }
    }

    static final class PlaybackState {
        private int vibrationIndex;
        private float phase;
        private float amplitude = 1f;
        private float speed = 1f;
        private boolean panelVisible;

        int vibrationIndex() {
            return vibrationIndex;
        }

        void setVibrationIndex(int vibrationIndex) {
            this.vibrationIndex = Math.max(0, vibrationIndex);
        }

        float phase() {
            return phase;
        }

        void setPhase(float phase) {
            this.phase = phase;
        }

        float amplitude() {
            return amplitude;
        }

        void setAmplitude(float amplitude) {
            this.amplitude = amplitude;
        }

        float speed() {
            return speed;
        }

        void setSpeed(float speed) {
            this.speed = speed;
        }

        boolean panelVisible() {
            return panelVisible;
        }

        void setPanelVisible(boolean panelVisible) {
            this.panelVisible = panelVisible;
        }
    }
}
