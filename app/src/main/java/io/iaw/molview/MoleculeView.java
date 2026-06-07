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
    private static final float MIN_ZOOM = 0.12f;
    private static final float MAX_ZOOM = 18f;
    private static final float ROTATION_DEGREES_PER_PIXEL = 0.55f;
    private static final int VBO_ATOM_THRESHOLD = 2000;

    private final MoleculeRenderer renderer;
    private final float touchSlop;
    private final float[] orientation = new float[16];
    private final float[] dragRotation = new float[16];
    private final float[] orientationTemp = new float[16];
    private Molecule molecule;
    private GestureListener gestureListener;
    private int frameIndex;
    private int vibrationIndex;
    private float vibrationPhase;
    private float vibrationAmplitude = 1f;
    private int mode = MODE_SPACE;
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
        resetOrientation();
    }

    void setGestureListener(GestureListener listener) {
        gestureListener = listener;
    }

    void setMolecule(Molecule molecule) {
        this.molecule = molecule;
        this.frameIndex = 0;
        this.vibrationIndex = 0;
        this.vibrationPhase = 0f;
        resetOrientation();
        this.fitZoom = fitZoomFor(this.molecule);
        this.zoom = fitZoom;
        this.panX = 0f;
        this.panY = 0f;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setMolecule(MoleculeView.this.molecule);
                renderer.setVibration(MoleculeView.this.vibrationIndex, MoleculeView.this.vibrationPhase);
                renderer.setViewState(snapshotOrientation(), zoom, panX, panY);
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

    void setVibrationAmplitude(float vibrationAmplitude) {
        this.vibrationAmplitude = clamp(vibrationAmplitude, 0f, 2.5f);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setVibrationAmplitude(MoleculeView.this.vibrationAmplitude);
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

    float getZoom() {
        return zoom;
    }

    float getPanX() {
        return panX;
    }

    float getPanY() {
        return panY;
    }

    float[] getOrientationMatrix() {
        return snapshotOrientation();
    }

    void restoreViewState(float[] orientationMatrix, float zoom, float panX, float panY) {
        if (orientationMatrix == null || orientationMatrix.length != 16) {
            resetOrientation();
        } else {
            System.arraycopy(orientationMatrix, 0, orientation, 0, orientation.length);
            orthonormalizeOrientation();
        }
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
        resetOrientation();
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
                rotateByDrag(x - lastX, y - lastY);
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
        final float[] queuedOrientation = snapshotOrientation();
        final float queuedZoom = zoom;
        final float queuedPanX = panX;
        final float queuedPanY = panY;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setViewState(queuedOrientation, queuedZoom, queuedPanX, queuedPanY);
            }
        });
        requestRender();
    }

    private void resetOrientation() {
        setOrientationFromAngles(DEFAULT_YAW, DEFAULT_PITCH);
    }

    private void setOrientationFromAngles(float yaw, float pitch) {
        Matrix.setIdentityM(orientation, 0);
        Matrix.rotateM(orientation, 0, (float) Math.toDegrees(pitch), 1f, 0f, 0f);
        Matrix.rotateM(orientation, 0, (float) Math.toDegrees(yaw), 0f, 1f, 0f);
    }

    private float[] snapshotOrientation() {
        float[] copy = new float[16];
        System.arraycopy(orientation, 0, copy, 0, orientation.length);
        return copy;
    }

    private void rotateByDrag(float dx, float dy) {
        float distance = distance(dx, dy);
        if (distance <= 0.001f) {
            return;
        }
        Matrix.setRotateM(dragRotation, 0,
                distance * ROTATION_DEGREES_PER_PIXEL,
                -dy / distance,
                dx / distance,
                0f);
        Matrix.multiplyMM(orientationTemp, 0, dragRotation, 0, orientation, 0);
        System.arraycopy(orientationTemp, 0, orientation, 0, orientation.length);
        orthonormalizeOrientation();
    }

    private void orthonormalizeOrientation() {
        normalizeColumn(orientation, 0);
        float dotXy = dotColumns(orientation, 0, 1);
        orientation[4] -= dotXy * orientation[0];
        orientation[5] -= dotXy * orientation[1];
        orientation[6] -= dotXy * orientation[2];
        normalizeColumn(orientation, 1);
        crossColumns(orientation, 0, 1, 2);
        orientation[3] = 0f;
        orientation[7] = 0f;
        orientation[11] = 0f;
        orientation[12] = 0f;
        orientation[13] = 0f;
        orientation[14] = 0f;
        orientation[15] = 1f;
    }

    private static void normalizeColumn(float[] matrix, int column) {
        int offset = column * 4;
        float x = matrix[offset];
        float y = matrix[offset + 1];
        float z = matrix[offset + 2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length <= 0.000001f) {
            if (column == 0) {
                matrix[offset] = 1f;
                matrix[offset + 1] = 0f;
                matrix[offset + 2] = 0f;
            } else {
                matrix[offset] = 0f;
                matrix[offset + 1] = 1f;
                matrix[offset + 2] = 0f;
            }
            return;
        }
        matrix[offset] = x / length;
        matrix[offset + 1] = y / length;
        matrix[offset + 2] = z / length;
    }

    private static float dotColumns(float[] matrix, int a, int b) {
        int ao = a * 4;
        int bo = b * 4;
        return matrix[ao] * matrix[bo]
                + matrix[ao + 1] * matrix[bo + 1]
                + matrix[ao + 2] * matrix[bo + 2];
    }

    private static void crossColumns(float[] matrix, int a, int b, int target) {
        int ao = a * 4;
        int bo = b * 4;
        int to = target * 4;
        float ax = matrix[ao];
        float ay = matrix[ao + 1];
        float az = matrix[ao + 2];
        float bx = matrix[bo];
        float by = matrix[bo + 1];
        float bz = matrix[bo + 2];
        matrix[to] = ay * bz - az * by;
        matrix[to + 1] = az * bx - ax * bz;
        matrix[to + 2] = ax * by - ay * bx;
        matrix[to + 3] = 0f;
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
                        "uniform vec2 uViewport;\n" +
                        "uniform float uRadiusScale;\n" +
                        "attribute vec3 aCenter;\n" +
                        "attribute vec2 aCorner;\n" +
                        "attribute vec4 aColor;\n" +
                        "attribute float aRadius;\n" +
                        "varying vec4 vColor;\n" +
                        "varying float vDepth;\n" +
                        "varying vec2 vCorner;\n" +
                        "void main() {\n" +
                        "  vec4 center = uMvp * vec4(aCenter, 1.0);\n" +
                        "  float px = max(2.0, aRadius * uRadiusScale);\n" +
                        "  vec2 ndc = aCorner * px * 2.0 / uViewport;\n" +
                        "  gl_Position = center + vec4(ndc * center.w, 0.0, 0.0);\n" +
                        "  vColor = aColor;\n" +
                        "  vDepth = center.z / center.w * 0.5 + 0.5;\n" +
                        "  vCorner = aCorner;\n" +
                        "}\n";

        private static final String POINT_FRAGMENT_SHADER =
                "precision mediump float;\n" +
                        "varying vec4 vColor;\n" +
                        "varying float vDepth;\n" +
                        "varying vec2 vCorner;\n" +
                        "void main() {\n" +
                        "  float d = dot(vCorner, vCorner);\n" +
                        "  if (d > 1.0) discard;\n" +
                        "  float z = sqrt(1.0 - d);\n" +
                        "  vec3 n = normalize(vec3(vCorner.x, -vCorner.y, z));\n" +
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
        private final float[] orientation = new float[16];
        private final float[] temp = new float[16];
        private final float[] mvp = new float[16];
        private Molecule molecule;
        private int frameIndex;
        private int vibrationIndex;
        private float vibrationPhase;
        private int mode = MODE_SPACE;
        private int backgroundMode = BACKGROUND_DARK;
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
        private FloatBuffer atomCenters;
        private FloatBuffer atomCorners;
        private FloatBuffer atomColors;
        private FloatBuffer atomRadii;
        private FloatBuffer linePositions;
        private FloatBuffer lineColors;
        private float[] atomPositionArray;
        private float[] atomCenterArray;
        private float[] atomCornerArray;
        private float[] atomColorArray;
        private float[] atomRadiusArray;
        private float[] linePositionArray;
        private float[] lineColorArray;
        private int atomCount;
        private int atomVertexCount;
        private int lineVertexCount;
        private float vibrationAmplitude = 1f;
        private boolean useVbo;
        private int atomCenterVbo;
        private int atomCornerVbo;
        private int atomColorVbo;
        private int atomRadiusVbo;
        private int linePositionVbo;
        private int lineColorVbo;

        MoleculeRenderer() {
            Matrix.setIdentityM(orientation, 0);
            Matrix.rotateM(orientation, 0, (float) Math.toDegrees(DEFAULT_PITCH), 1f, 0f, 0f);
            Matrix.rotateM(orientation, 0, (float) Math.toDegrees(DEFAULT_YAW), 0f, 1f, 0f);
        }

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

        void setVibrationAmplitude(float vibrationAmplitude) {
            this.vibrationAmplitude = Math.max(0f, Math.min(2.5f, vibrationAmplitude));
            this.positionsDirty = true;
        }

        void setMode(int mode) {
            this.mode = mode;
            this.topologyDirty = true;
            this.positionsDirty = true;
        }

        void setViewState(float[] orientationMatrix, float zoom, float panX, float panY) {
            if (orientationMatrix != null && orientationMatrix.length == 16) {
                System.arraycopy(orientationMatrix, 0, orientation, 0, orientation.length);
            }
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
            deleteVbos();
            atomPositions = null;
            atomCenters = null;
            atomCorners = null;
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
            boolean shouldUseVbo = atomCount > VBO_ATOM_THRESHOLD;
            if (useVbo != shouldUseVbo) {
                deleteVbos();
            }
            useVbo = shouldUseVbo;
            atomVertexCount = atomCount * 6;
            atomColorArray = ensureArray(atomColorArray, atomVertexCount * 4);
            atomRadiusArray = ensureArray(atomRadiusArray, atomVertexCount);
            atomCornerArray = ensureArray(atomCornerArray, atomVertexCount * 2);
            float density = atomCount > 6000 ? 0.36f : atomCount > 1500 ? 0.56f : 1f;
            float modeScale = mode == MODE_STICKS ? 0.12f : 0.42f;
            for (int i = 0; i < atomCount; i++) {
                Molecule.Atom atom = molecule.atoms.get(i);
                int color = ElementTable.color(atom.element);
                float r = Color.red(color) / 255f;
                float g = Color.green(color) / 255f;
                float b = Color.blue(color) / 255f;
                float radius = ElementTable.radius(atom.element) * modeScale * density;
                int vertexStart = i * 6;
                putAtomVertex(vertexStart, -1f, -1f, r, g, b, radius);
                putAtomVertex(vertexStart + 1, 1f, -1f, r, g, b, radius);
                putAtomVertex(vertexStart + 2, 1f, 1f, r, g, b, radius);
                putAtomVertex(vertexStart + 3, -1f, -1f, r, g, b, radius);
                putAtomVertex(vertexStart + 4, 1f, 1f, r, g, b, radius);
                putAtomVertex(vertexStart + 5, -1f, 1f, r, g, b, radius);
            }
            atomColors = putBuffer(atomColors, atomColorArray, atomVertexCount * 4);
            atomCorners = putBuffer(atomCorners, atomCornerArray, atomVertexCount * 2);
            atomRadii = putBuffer(atomRadii, atomRadiusArray, atomVertexCount);
            if (useVbo) {
                atomCornerVbo = uploadArrayVbo(atomCornerVbo, atomCorners, atomVertexCount * 2, GLES20.GL_STATIC_DRAW);
                atomColorVbo = uploadArrayVbo(atomColorVbo, atomColors, atomVertexCount * 4, GLES20.GL_STATIC_DRAW);
                atomRadiusVbo = uploadArrayVbo(atomRadiusVbo, atomRadii, atomVertexCount, GLES20.GL_STATIC_DRAW);
            }
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
                vibrationScale = vibrationScale(vibration, extent) * vibrationAmplitude;
            }
            atomPositionArray = ensureArray(atomPositionArray, atomCount * 3);
            atomCenterArray = ensureArray(atomCenterArray, atomVertexCount * 3);

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
                int quad = i * 18;
                for (int j = 0; j < 6; j++) {
                    int targetCenter = quad + j * 3;
                    atomCenterArray[targetCenter] = atomPositionArray[target];
                    atomCenterArray[targetCenter + 1] = atomPositionArray[target + 1];
                    atomCenterArray[targetCenter + 2] = atomPositionArray[target + 2];
                }
            }

            atomPositions = putBuffer(atomPositions, atomPositionArray, atomCount * 3);
            atomCenters = putBuffer(atomCenters, atomCenterArray, atomVertexCount * 3);
            rebuildLineBuffers(atomPositionArray);
            if (useVbo) {
                atomCenterVbo = uploadArrayVbo(atomCenterVbo, atomCenters, atomVertexCount * 3, GLES20.GL_DYNAMIC_DRAW);
                linePositionVbo = uploadArrayVbo(linePositionVbo, linePositions, lineVertexCount * 3, GLES20.GL_DYNAMIC_DRAW);
                lineColorVbo = uploadArrayVbo(lineColorVbo, lineColors, lineVertexCount * 4, GLES20.GL_DYNAMIC_DRAW);
            }
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
            Matrix.multiplyMM(temp, 0, model, 0, orientation, 0);
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
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(colorHandle);
            if (useVbo && linePositionVbo != 0 && lineColorVbo != 0) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, linePositionVbo);
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lineColorVbo);
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            } else {
                linePositions.position(0);
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, linePositions);
                lineColors.position(0);
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, lineColors);
            }
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            GLES20.glDepthMask(true);
        }

        private void drawAtoms() {
            if (atomCount <= 0 || atomCenters == null || atomCorners == null
                    || atomColors == null || atomRadii == null) {
                return;
            }
            GLES20.glUseProgram(pointProgram);
            int mvpHandle = GLES20.glGetUniformLocation(pointProgram, "uMvp");
            int viewportHandle = GLES20.glGetUniformLocation(pointProgram, "uViewport");
            int radiusScaleHandle = GLES20.glGetUniformLocation(pointProgram, "uRadiusScale");
            int centerHandle = GLES20.glGetAttribLocation(pointProgram, "aCenter");
            int cornerHandle = GLES20.glGetAttribLocation(pointProgram, "aCorner");
            int colorHandle = GLES20.glGetAttribLocation(pointProgram, "aColor");
            int radiusHandle = GLES20.glGetAttribLocation(pointProgram, "aRadius");
            float radiusScale = Math.max(42f, Math.min(width, height) * 0.13f * zoom);

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniform2f(viewportHandle, Math.max(1, width), Math.max(1, height));
            GLES20.glUniform1f(radiusScaleHandle, radiusScale);
            GLES20.glEnableVertexAttribArray(centerHandle);
            GLES20.glEnableVertexAttribArray(cornerHandle);
            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glEnableVertexAttribArray(radiusHandle);
            if (useVbo && atomCenterVbo != 0 && atomCornerVbo != 0 && atomColorVbo != 0 && atomRadiusVbo != 0) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, atomCenterVbo);
                GLES20.glVertexAttribPointer(centerHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, atomCornerVbo);
                GLES20.glVertexAttribPointer(cornerHandle, 2, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, atomColorVbo);
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, atomRadiusVbo);
                GLES20.glVertexAttribPointer(radiusHandle, 1, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            } else {
                atomCenters.position(0);
                GLES20.glVertexAttribPointer(centerHandle, 3, GLES20.GL_FLOAT, false, 0, atomCenters);
                atomCorners.position(0);
                GLES20.glVertexAttribPointer(cornerHandle, 2, GLES20.GL_FLOAT, false, 0, atomCorners);
                atomColors.position(0);
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, atomColors);
                atomRadii.position(0);
                GLES20.glVertexAttribPointer(radiusHandle, 1, GLES20.GL_FLOAT, false, 0, atomRadii);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, atomVertexCount);
            GLES20.glDisableVertexAttribArray(centerHandle);
            GLES20.glDisableVertexAttribArray(cornerHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            GLES20.glDisableVertexAttribArray(radiusHandle);
        }

        private void putAtomVertex(int vertex, float cornerX, float cornerY, float r, float g, float b, float radius) {
            int cornerIndex = vertex * 2;
            atomCornerArray[cornerIndex] = cornerX;
            atomCornerArray[cornerIndex + 1] = cornerY;
            int colorIndex = vertex * 4;
            atomColorArray[colorIndex] = r;
            atomColorArray[colorIndex + 1] = g;
            atomColorArray[colorIndex + 2] = b;
            atomColorArray[colorIndex + 3] = 1f;
            atomRadiusArray[vertex] = radius;
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

        private int uploadArrayVbo(int bufferId, FloatBuffer data, int floatCount, int usage) {
            if (data == null || floatCount <= 0) {
                return bufferId;
            }
            if (bufferId == 0) {
                int[] ids = new int[1];
                GLES20.glGenBuffers(1, ids, 0);
                bufferId = ids[0];
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId);
                data.position(0);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floatCount * 4, data, usage);
            } else {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId);
                data.position(0);
                GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, floatCount * 4, data);
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            return bufferId;
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

        private void deleteVbos() {
            int[] ids = new int[]{
                    atomCenterVbo,
                    atomCornerVbo,
                    atomColorVbo,
                    atomRadiusVbo,
                    linePositionVbo,
                    lineColorVbo
            };
            GLES20.glDeleteBuffers(ids.length, ids, 0);
            atomCenterVbo = 0;
            atomCornerVbo = 0;
            atomColorVbo = 0;
            atomRadiusVbo = 0;
            linePositionVbo = 0;
            lineColorVbo = 0;
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
