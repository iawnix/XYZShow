package io.iaw.molview;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class MoleculeView extends GLSurfaceView {
    private static final int MODE_SPACE = 0;
    private static final int MODE_STICKS = 1;
    static final int BACKGROUND_DARK = 0;
    static final int BACKGROUND_LIGHT = 1;
    private static final float DEFAULT_YAW = -0.55f;
    private static final float DEFAULT_PITCH = 0.45f;
    private static final float MIN_PITCH = -1.45f;
    private static final float MAX_PITCH = 1.45f;
    private static final float MIN_ZOOM = 0.12f;
    private static final float MAX_ZOOM = 18f;

    private final MoleculeRenderer renderer;
    private final float touchSlop;
    private Molecule molecule;
    private GestureListener gestureListener;
    private int frameIndex;
    private int vibrationIndex;
    private float vibrationPhase;
    private int mode = MODE_SPACE;
    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float fitZoom = 1f;
    private float lastX;
    private float lastY;
    private float lastSpan;
    private float lastFocusX;
    private float lastFocusY;
    private float downX;
    private float downY;
    private boolean movedDuringGesture;
    private boolean multiTouchDuringGesture;
    private long lastTapMs;

    MoleculeView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new MoleculeRenderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setGestureListener(GestureListener listener) {
        gestureListener = listener;
    }

    void setMolecule(Molecule molecule) {
        this.molecule = molecule;
        this.frameIndex = 0;
        this.vibrationIndex = 0;
        this.vibrationPhase = 0f;
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.fitZoom = fitZoomFor(this.molecule);
        this.zoom = fitZoom;
        this.panX = 0f;
        this.panY = 0f;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setMolecule(MoleculeView.this.molecule);
                renderer.setVibration(MoleculeView.this.vibrationIndex, MoleculeView.this.vibrationPhase);
                renderer.setViewState(yaw, pitch, zoom, panX, panY);
            }
        });
        requestRender();
    }

    void setVibrationMode(int vibrationIndex) {
        if (molecule == null || !molecule.hasVibrations()) {
            this.vibrationIndex = 0;
        } else {
            this.vibrationIndex = Math.max(0, Math.min(vibrationIndex, molecule.vibrationCount() - 1));
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setVibration(MoleculeView.this.vibrationIndex, MoleculeView.this.vibrationPhase);
            }
        });
        requestRender();
    }

    void setVibrationPhase(float vibrationPhase) {
        this.vibrationPhase = vibrationPhase;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setVibration(MoleculeView.this.vibrationIndex, MoleculeView.this.vibrationPhase);
            }
        });
        requestRender();
    }

    void setFrameIndex(int frameIndex) {
        if (molecule == null) {
            this.frameIndex = 0;
        } else {
            this.frameIndex = Math.max(0, Math.min(frameIndex, molecule.frameCount() - 1));
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setFrameIndex(MoleculeView.this.frameIndex);
            }
        });
        requestRender();
    }

    int getFrameIndex() {
        return frameIndex;
    }

    int getDisplayMode() {
        return mode;
    }

    void setDisplayMode(final int displayMode) {
        mode = displayMode == MODE_STICKS ? MODE_STICKS : MODE_SPACE;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setMode(mode);
            }
        });
        requestRender();
    }

    float getYaw() {
        return yaw;
    }

    float getPitch() {
        return pitch;
    }

    float getZoom() {
        return zoom;
    }

    float getPanX() {
        return panX;
    }

    float getPanY() {
        return panY;
    }

    void restoreViewState(float yaw, float pitch, float zoom, float panX, float panY) {
        this.yaw = yaw;
        this.pitch = clamp(pitch, MIN_PITCH, MAX_PITCH);
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        this.panX = panX;
        this.panY = panY;
        queueCamera();
    }

    String cycleMode() {
        setDisplayMode(mode == MODE_SPACE ? MODE_STICKS : MODE_SPACE);
        return modeName();
    }

    String modeName() {
        if (mode == MODE_STICKS) {
            return "Stick";
        }
        return "Space";
    }

    void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        fitZoom = fitZoomFor(molecule);
        zoom = fitZoom;
        panX = 0f;
        panY = 0f;
        queueCamera();
    }

    void setBackgroundMode(final int backgroundMode) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setBackgroundMode(backgroundMode);
            }
        });
        requestRender();
    }

    void releaseGl() {
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.releaseGl();
                }
            });
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (molecule == null) {
            return true;
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            downX = lastX;
            downY = lastY;
            lastSpan = 0f;
            movedDuringGesture = false;
            multiTouchDuringGesture = false;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouchDuringGesture = true;
            rememberPinch(event);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() >= 2) {
                float span = span(event);
                float focusX = focusX(event);
                float focusY = focusY(event);
                if (lastSpan > 0f) {
                    zoom = clamp(zoom * (span / lastSpan), MIN_ZOOM, MAX_ZOOM);
                    float unit = Math.max(1f, Math.min(getWidth(), getHeight()));
                    panX += 2f * (focusX - lastFocusX) / unit / Math.max(zoom, MIN_ZOOM);
                    panY -= 2f * (focusY - lastFocusY) / unit / Math.max(zoom, MIN_ZOOM);
                }
                lastSpan = span;
                lastFocusX = focusX;
                lastFocusY = focusY;
            } else {
                float x = event.getX();
                float y = event.getY();
                movedDuringGesture |= distance(x - downX, y - downY) > touchSlop;
                yaw += (x - lastX) * 0.012f;
                pitch = clamp(pitch + (y - lastY) * 0.012f, MIN_PITCH, MAX_PITCH);
                lastX = x;
                lastY = y;
                lastSpan = 0f;
            }
            queueCamera();
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            lastSpan = 0f;
            int lifted = event.getActionIndex();
            int remainingCount = remainingPointerCount(event, lifted);
            if (remainingCount >= 2) {
                rememberPinch(event, lifted);
            } else if (remainingCount == 1) {
                int remaining = firstPointerIndex(event, lifted);
                if (remaining >= 0) {
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                }
            } else {
                lastX = 0f;
                lastY = 0f;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastSpan = 0f;
            if (action == MotionEvent.ACTION_UP && !movedDuringGesture && !multiTouchDuringGesture) {
                performClick();
                handleTap();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTap() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTapMs <= ViewConfiguration.getDoubleTapTimeout()) {
            lastTapMs = 0L;
            resetView();
            if (gestureListener != null) {
                gestureListener.onDoubleTap();
            }
            return;
        }
        lastTapMs = now;
        if (gestureListener != null) {
            gestureListener.onSingleTap();
        }
    }

    private void queueCamera() {
        final float queuedYaw = yaw;
        final float queuedPitch = pitch;
        final float queuedZoom = zoom;
        final float queuedPanX = panX;
        final float queuedPanY = panY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setViewState(queuedYaw, queuedPitch, queuedZoom, queuedPanX, queuedPanY);
            }
        });
        requestRender();
    }

    private void rememberPinch(MotionEvent event) {
        rememberPinch(event, -1);
    }

    private void rememberPinch(MotionEvent event, int skipPointerIndex) {
        lastSpan = span(event, skipPointerIndex);
        lastFocusX = focusX(event, skipPointerIndex);
        lastFocusY = focusY(event, skipPointerIndex);
    }

    private float span(MotionEvent event) {
        return span(event, -1);
    }

    private float span(MotionEvent event, int skipPointerIndex) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        int first = firstPointerIndex(event, skipPointerIndex);
        int second = secondPointerIndex(event, skipPointerIndex);
        if (first < 0 || second < 0) {
            return 0f;
        }
        float dx = event.getX(second) - event.getX(first);
        float dy = event.getY(second) - event.getY(first);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float focusX(MotionEvent event) {
        return focusX(event, -1);
    }

    private float focusX(MotionEvent event, int skipPointerIndex) {
        float sum = 0f;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skipPointerIndex) {
                continue;
            }
            sum += event.getX(i);
            count++;
        }
        return count == 0 ? 0f : sum / count;
    }

    private float focusY(MotionEvent event) {
        return focusY(event, -1);
    }

    private float focusY(MotionEvent event, int skipPointerIndex) {
        float sum = 0f;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skipPointerIndex) {
                continue;
            }
            sum += event.getY(i);
            count++;
        }
        return count == 0 ? 0f : sum / count;
    }

    private static float distance(float dx, float dy) {
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int remainingPointerCount(MotionEvent event, int skipPointerIndex) {
        return event.getPointerCount() - (skipPointerIndex >= 0 ? 1 : 0);
    }

    private int firstPointerIndex(MotionEvent event, int skipPointerIndex) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i != skipPointerIndex) {
                return i;
            }
        }
        return -1;
    }

    private int secondPointerIndex(MotionEvent event, int skipPointerIndex) {
        boolean foundFirst = false;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skipPointerIndex) {
                continue;
            }
            if (!foundFirst) {
                foundFirst = true;
                continue;
            }
            return i;
        }
        return -1;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    interface GestureListener {
        void onSingleTap();

        void onDoubleTap();
    }

    private float fitZoomFor(Molecule molecule) {
        if (molecule == null || molecule.atomCount() == 0) {
            return 1f;
        }
        Molecule.Frame frame = molecule.frameAt(0);
        float[] xyz = frame.xyz;
        if (xyz.length < 3) {
            return 1f;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i + 2 < xyz.length; i += 3) {
            minX = Math.min(minX, xyz[i]);
            minY = Math.min(minY, xyz[i + 1]);
            minZ = Math.min(minZ, xyz[i + 2]);
            maxX = Math.max(maxX, xyz[i]);
            maxY = Math.max(maxY, xyz[i + 1]);
            maxZ = Math.max(maxZ, xyz[i + 2]);
        }
        float extent = Math.max(1f, molecule.maxExtentForFrame(0));
        float width = Math.max(0.25f, (maxX - minX) / extent);
        float height = Math.max(0.25f, (maxY - minY) / extent);
        float depth = Math.max(0.25f, (maxZ - minZ) / extent);
        float projected = Math.max(width, Math.max(height, depth)) * 0.5f;
        return clamp(0.8f / projected, 0.7f, 5.5f);
    }

    private static final class MoleculeRenderer implements Renderer {
        private static final String POINT_VERTEX_SHADER =
                "uniform mat4 uMvp;\n" +
                        "uniform float uPointScale;\n" +
                        "uniform float uMaxPoint;\n" +
                        "attribute vec3 aPosition;\n" +
                        "attribute vec4 aColor;\n" +
                        "attribute float aRadius;\n" +
                        "varying vec4 vColor;\n" +
                        "varying float vDepth;\n" +
                        "void main() {\n" +
                        "  vec4 clip = uMvp * vec4(aPosition, 1.0);\n" +
                        "  gl_Position = clip;\n" +
                        "  vColor = aColor;\n" +
                        "  vDepth = clip.z / clip.w * 0.5 + 0.5;\n" +
                        "  gl_PointSize = clamp(aRadius * uPointScale, 2.0, uMaxPoint);\n" +
                        "}\n";

        private static final String POINT_FRAGMENT_SHADER =
                "precision mediump float;\n" +
                        "varying vec4 vColor;\n" +
                        "varying float vDepth;\n" +
                        "void main() {\n" +
                        "  vec2 p = gl_PointCoord * 2.0 - 1.0;\n" +
                        "  float d = dot(p, p);\n" +
                        "  if (d > 1.0) discard;\n" +
                        "  float z = sqrt(1.0 - d);\n" +
                        "  vec3 n = normalize(vec3(p.x, -p.y, z));\n" +
                        "  vec3 lightDir = normalize(vec3(-0.35, 0.58, 0.74));\n" +
                        "  float diffuse = max(dot(n, lightDir), 0.0);\n" +
                        "  vec3 eye = vec3(0.0, 0.0, 1.0);\n" +
                        "  float spec = pow(max(dot(reflect(-lightDir, n), eye), 0.0), 28.0);\n" +
                        "  float rim = pow(1.0 - max(n.z, 0.0), 2.0);\n" +
                        "  float depth = 0.76 + 0.24 * (1.0 - clamp(vDepth, 0.0, 1.0));\n" +
                        "  vec3 shaded = vColor.rgb * (0.34 + 0.66 * diffuse) * depth;\n" +
                        "  shaded += vec3(0.35) * spec + vec3(0.05, 0.07, 0.09) * rim;\n" +
                        "  float edge = 1.0 - smoothstep(0.82, 1.0, d);\n" +
                        "  gl_FragColor = vec4(shaded, vColor.a * edge);\n" +
                        "}\n";

        private static final String LINE_VERTEX_SHADER =
                "uniform mat4 uMvp;\n" +
                        "attribute vec3 aPosition;\n" +
                        "attribute vec4 aColor;\n" +
                        "varying vec4 vColor;\n" +
                        "void main() {\n" +
                        "  gl_Position = uMvp * vec4(aPosition, 1.0);\n" +
                        "  vColor = aColor;\n" +
                        "}\n";

        private static final String LINE_FRAGMENT_SHADER =
                "precision mediump float;\n" +
                        "varying vec4 vColor;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = vColor;\n" +
                        "}\n";

        private final float[] projection = new float[16];
        private final float[] model = new float[16];
        private final float[] rotation = new float[16];
        private final float[] temp = new float[16];
        private final float[] mvp = new float[16];
        private Molecule molecule;
        private int frameIndex;
        private int vibrationIndex;
        private float vibrationPhase;
        private int mode = MODE_SPACE;
        private int backgroundMode = BACKGROUND_DARK;
        private float yaw = -0.55f;
        private float pitch = 0.45f;
        private float zoom = 1f;
        private float panX;
        private float panY;
        private int width = 1;
        private int height = 1;
        private boolean topologyDirty = true;
        private boolean positionsDirty = true;

        private int pointProgram;
        private int lineProgram;
        private FloatBuffer atomPositions;
        private FloatBuffer atomColors;
        private FloatBuffer atomRadii;
        private FloatBuffer linePositions;
        private FloatBuffer lineColors;
        private float[] atomPositionArray;
        private float[] atomColorArray;
        private float[] atomRadiusArray;
        private float[] linePositionArray;
        private float[] lineColorArray;
        private int atomCount;
        private int lineVertexCount;

        void setMolecule(Molecule molecule) {
            this.molecule = molecule;
            this.frameIndex = 0;
            this.vibrationIndex = 0;
            this.vibrationPhase = 0f;
            this.topologyDirty = true;
            this.positionsDirty = true;
        }

        void setFrameIndex(int frameIndex) {
            if (molecule == null) {
                this.frameIndex = 0;
            } else {
                this.frameIndex = Math.max(0, Math.min(frameIndex, molecule.frameCount() - 1));
            }
            this.positionsDirty = true;
        }

        void setVibration(int vibrationIndex, float vibrationPhase) {
            if (molecule == null || !molecule.hasVibrations()) {
                this.vibrationIndex = 0;
            } else {
                this.vibrationIndex = Math.max(0, Math.min(vibrationIndex, molecule.vibrationCount() - 1));
            }
            this.vibrationPhase = vibrationPhase;
            this.positionsDirty = true;
        }

        void setMode(int mode) {
            this.mode = mode;
            this.topologyDirty = true;
            this.positionsDirty = true;
        }

        void setViewState(float yaw, float pitch, float zoom, float panX, float panY) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
            this.panX = panX;
            this.panY = panY;
        }

        void setBackgroundMode(int backgroundMode) {
            this.backgroundMode = backgroundMode;
            applyClearColor();
        }

        void releaseGl() {
            deletePrograms();
            atomPositions = null;
            atomColors = null;
            atomRadii = null;
            linePositions = null;
            lineColors = null;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            deletePrograms();
            applyClearColor();
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            pointProgram = buildProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER);
            lineProgram = buildProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER);
            topologyDirty = true;
            positionsDirty = true;
        }

        private void applyClearColor() {
            if (backgroundMode == BACKGROUND_LIGHT) {
                GLES20.glClearColor(246f / 255f, 246f / 255f, 247f / 255f, 1f);
            } else {
                GLES20.glClearColor(28f / 255f, 28f / 255f, 30f / 255f, 1f);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            GLES20.glViewport(0, 0, this.width, this.height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (molecule == null || molecule.atomCount() == 0 || pointProgram == 0 || lineProgram == 0) {
                return;
            }
            if (topologyDirty) {
                rebuildTopologyBuffers();
                topologyDirty = false;
                positionsDirty = true;
            }
            if (positionsDirty) {
                rebuildPositionBuffers();
                positionsDirty = false;
            }
            buildMatrix();
            drawLines();
            drawAtoms();
        }

        private void rebuildTopologyBuffers() {
            atomCount = molecule.atomCount();
            atomColorArray = ensureArray(atomColorArray, atomCount * 4);
            atomRadiusArray = ensureArray(atomRadiusArray, atomCount);
            float density = atomCount > 6000 ? 0.36f : atomCount > 1500 ? 0.56f : 1f;
            float modeScale = mode == MODE_STICKS ? 0.12f : 0.42f;
            for (int i = 0; i < atomCount; i++) {
                Molecule.Atom atom = molecule.atoms.get(i);
                int color = ElementTable.color(atom.element);
                int colorIndex = i * 4;
                atomColorArray[colorIndex] = Color.red(color) / 255f;
                atomColorArray[colorIndex + 1] = Color.green(color) / 255f;
                atomColorArray[colorIndex + 2] = Color.blue(color) / 255f;
                atomColorArray[colorIndex + 3] = 1f;
                atomRadiusArray[i] = ElementTable.radius(atom.element) * modeScale * density;
            }
            atomColors = putBuffer(atomColors, atomColorArray, atomCount * 4);
            atomRadii = putBuffer(atomRadii, atomRadiusArray, atomCount);
        }

        private void rebuildPositionBuffers() {
            Molecule.Frame frame = molecule.frameAt(frameIndex);
            atomCount = molecule.atomCount();
            float[] center = molecule.centerForFrame(frameIndex);
            float extent = Math.max(1f, molecule.maxExtentForFrame(frameIndex));
            Molecule.VibrationMode vibration = molecule.vibrationAt(vibrationIndex);
            float vibrate = 0f;
            float vibrationScale = 0f;
            if (vibration != null && vibration.hasDisplacement()) {
                vibrate = (float) Math.sin(vibrationPhase);
                vibrationScale = vibrationScale(vibration, extent);
            }
            atomPositionArray = ensureArray(atomPositionArray, atomCount * 3);

            for (int i = 0; i < atomCount; i++) {
                int source = i * 3;
                int target = i * 3;
                float x = frame.xyz[source];
                float y = frame.xyz[source + 1];
                float z = frame.xyz[source + 2];
                if (vibration != null && source + 2 < vibration.displacement.length) {
                    x += vibration.displacement[source] * vibrationScale * vibrate;
                    y += vibration.displacement[source + 1] * vibrationScale * vibrate;
                    z += vibration.displacement[source + 2] * vibrationScale * vibrate;
                }
                atomPositionArray[target] = (x - center[0]) / extent;
                atomPositionArray[target + 1] = (y - center[1]) / extent;
                atomPositionArray[target + 2] = (z - center[2]) / extent;
            }

            atomPositions = putBuffer(atomPositions, atomPositionArray, atomCount * 3);
            rebuildLineBuffers(atomPositionArray);
        }

        private static float vibrationScale(Molecule.VibrationMode vibration, float extent) {
            float max = vibration.maxDisplacement();
            if (max <= 0.000001f) {
                return 0f;
            }
            float target = Math.max(0.18f, extent * 0.13f);
            return Math.max(0.25f, Math.min(8f, target / max));
        }

        private void rebuildLineBuffers(float[] atomPositionArray) {
            List<Molecule.Bond> drawBonds = molecule.bonds;
            int maxLineVertexCount = drawBonds.size() * 2;
            linePositionArray = ensureArray(linePositionArray, maxLineVertexCount * 3);
            lineColorArray = ensureArray(lineColorArray, maxLineVertexCount * 4);
            float a = mode == MODE_STICKS ? 0.98f : 0.82f;
            int vertex = 0;
            for (Molecule.Bond bond : drawBonds) {
                if (bond.a < 0 || bond.b < 0 || bond.a >= atomCount || bond.b >= atomCount) {
                    continue;
                }
                int ai = bond.a * 3;
                int bi = bond.b * 3;
                float ax = atomPositionArray[ai];
                float ay = atomPositionArray[ai + 1];
                float az = atomPositionArray[ai + 2];
                float bx = atomPositionArray[bi];
                float by = atomPositionArray[bi + 1];
                float bz = atomPositionArray[bi + 2];
                float dx = bx - ax;
                float dy = by - ay;
                float dz = bz - az;
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length <= 0.0001f) {
                    continue;
                }
                float ux = dx / length;
                float uy = dy / length;
                float uz = dz / length;
                float trimA = mode == MODE_SPACE ? bondTrim(molecule.atoms.get(bond.a).element, length) : 0f;
                float trimB = mode == MODE_SPACE ? bondTrim(molecule.atoms.get(bond.b).element, length) : 0f;
                if (trimA + trimB > length * 0.72f) {
                    float trim = length * 0.36f;
                    trimA = trim;
                    trimB = trim;
                }

                int pi = vertex * 3;
                linePositionArray[pi] = ax + ux * trimA;
                linePositionArray[pi + 1] = ay + uy * trimA;
                linePositionArray[pi + 2] = az + uz * trimA;
                putElementColor(lineColorArray, vertex, molecule.atoms.get(bond.a).element, a);
                vertex++;

                pi = vertex * 3;
                linePositionArray[pi] = bx - ux * trimB;
                linePositionArray[pi + 1] = by - uy * trimB;
                linePositionArray[pi + 2] = bz - uz * trimB;
                putElementColor(lineColorArray, vertex, molecule.atoms.get(bond.b).element, a);
                vertex++;
            }
            lineVertexCount = vertex;
            linePositions = putBuffer(linePositions, linePositionArray, lineVertexCount * 3);
            lineColors = putBuffer(lineColors, lineColorArray, lineVertexCount * 4);
        }

        private void buildMatrix() {
            float aspect = width / (float) height;
            if (aspect >= 1f) {
                Matrix.orthoM(projection, 0, -aspect, aspect, -1f, 1f, -8f, 8f);
            } else {
                Matrix.orthoM(projection, 0, -1f, 1f, -1f / aspect, 1f / aspect, -8f, 8f);
            }
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, panX, panY, 0f);
            Matrix.scaleM(model, 0, zoom, zoom, zoom);
            Matrix.setIdentityM(rotation, 0);
            Matrix.rotateM(rotation, 0, (float) Math.toDegrees(pitch), 1f, 0f, 0f);
            Matrix.rotateM(rotation, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
            Matrix.multiplyMM(temp, 0, model, 0, rotation, 0);
            System.arraycopy(temp, 0, model, 0, model.length);
            Matrix.multiplyMM(mvp, 0, projection, 0, model, 0);
        }

        private void drawLines() {
            if (lineVertexCount <= 0 || linePositions == null || lineColors == null) {
                return;
            }
            GLES20.glUseProgram(lineProgram);
            int mvpHandle = GLES20.glGetUniformLocation(lineProgram, "uMvp");
            int positionHandle = GLES20.glGetAttribLocation(lineProgram, "aPosition");
            int colorHandle = GLES20.glGetAttribLocation(lineProgram, "aColor");
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glDepthMask(false);
            GLES20.glLineWidth(mode == MODE_STICKS ? 5.5f : 3.5f);
            linePositions.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, linePositions);
            lineColors.position(0);
            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, lineColors);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            GLES20.glDepthMask(true);
        }

        private void drawAtoms() {
            if (atomCount <= 0 || atomPositions == null || atomColors == null || atomRadii == null) {
                return;
            }
            GLES20.glUseProgram(pointProgram);
            int mvpHandle = GLES20.glGetUniformLocation(pointProgram, "uMvp");
            int pointScaleHandle = GLES20.glGetUniformLocation(pointProgram, "uPointScale");
            int maxPointHandle = GLES20.glGetUniformLocation(pointProgram, "uMaxPoint");
            int positionHandle = GLES20.glGetAttribLocation(pointProgram, "aPosition");
            int colorHandle = GLES20.glGetAttribLocation(pointProgram, "aColor");
            int radiusHandle = GLES20.glGetAttribLocation(pointProgram, "aRadius");
            float pointScale = Math.max(42f, Math.min(width, height) * 0.13f * zoom);
            float maxPoint = mode == MODE_SPACE ? 90f : 24f;

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniform1f(pointScaleHandle, pointScale);
            GLES20.glUniform1f(maxPointHandle, maxPoint);
            atomPositions.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, atomPositions);
            atomColors.position(0);
            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, atomColors);
            atomRadii.position(0);
            GLES20.glEnableVertexAttribArray(radiusHandle);
            GLES20.glVertexAttribPointer(radiusHandle, 1, GLES20.GL_FLOAT, false, 0, atomRadii);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, atomCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            GLES20.glDisableVertexAttribArray(radiusHandle);
        }

        private static void putElementColor(float[] colors, int vertex, String element, float a) {
            int color = ElementTable.color(element);
            float r = Color.red(color) / 255f;
            float g = Color.green(color) / 255f;
            float b = Color.blue(color) / 255f;
            float mix = 0.78f;
            putColor(colors, vertex, r * mix + 0.13f, g * mix + 0.13f, b * mix + 0.13f, a);
        }

        private static float bondTrim(String element, float bondLength) {
            float radius = ElementTable.radius(element);
            float trim = 0.045f + radius * 0.030f;
            return Math.min(trim, bondLength * 0.34f);
        }

        private static void putColor(float[] colors, int vertex, float r, float g, float b, float a) {
            int i = vertex * 4;
            colors[i] = r;
            colors[i + 1] = g;
            colors[i + 2] = b;
            colors[i + 3] = a;
        }

        private static float[] ensureArray(float[] array, int length) {
            return array != null && array.length >= length ? array : new float[length];
        }

        private static FloatBuffer putBuffer(FloatBuffer buffer, float[] values, int length) {
            if (buffer == null || buffer.capacity() < length) {
                ByteBuffer bytes = ByteBuffer.allocateDirect(Math.max(1, length) * 4);
                bytes.order(ByteOrder.nativeOrder());
                buffer = bytes.asFloatBuffer();
            }
            buffer.clear();
            if (length > 0) {
                buffer.put(values, 0, length);
            }
            buffer.flip();
            return buffer;
        }

        private void deletePrograms() {
            if (pointProgram != 0) {
                GLES20.glDeleteProgram(pointProgram);
                pointProgram = 0;
            }
            if (lineProgram != 0) {
                GLES20.glDeleteProgram(lineProgram);
                lineProgram = 0;
            }
        }

        private static int buildProgram(String vertexSource, String fragmentSource) {
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = 0;
            int program = 0;
            try {
                fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
                program = GLES20.glCreateProgram();
                GLES20.glAttachShader(program, vertex);
                GLES20.glAttachShader(program, fragment);
                GLES20.glLinkProgram(program);
                GLES20.glDeleteShader(vertex);
                GLES20.glDeleteShader(fragment);
                vertex = 0;
                fragment = 0;
                int[] status = new int[1];
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
                if (status[0] == 0) {
                    String error = GLES20.glGetProgramInfoLog(program);
                    GLES20.glDeleteProgram(program);
                    throw new IllegalStateException(error);
                }
                return program;
            } finally {
                if (vertex != 0) {
                    GLES20.glDeleteShader(vertex);
                }
                if (fragment != 0) {
                    GLES20.glDeleteShader(fragment);
                }
            }
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String error = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException(error);
            }
            return shader;
        }
    }
}
