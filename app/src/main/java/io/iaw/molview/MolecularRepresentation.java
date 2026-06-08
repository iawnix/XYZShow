package io.iaw.molview;

import android.graphics.Color;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

final class MolecularRepresentation {
    private static final int VBO_ATOM_THRESHOLD = 2000;

    private Molecule molecule;
    private RepresentationState.Snapshot state = new RepresentationState().snapshot();
    private int frameIndex;
    private int vibrationIndex;
    private float vibrationPhase;
    private float vibrationAmplitude = 1f;
    private boolean topologyDirty = true;
    private boolean positionsDirty = true;
    private Buffers buffers = new Buffers();

    void setMolecule(Molecule molecule) {
        this.molecule = molecule;
        frameIndex = 0;
        vibrationIndex = 0;
        vibrationPhase = 0f;
        topologyDirty = true;
        positionsDirty = true;
    }

    void setFrameIndex(int frameIndex) {
        if (molecule == null) {
            this.frameIndex = 0;
        } else {
            this.frameIndex = Math.max(0, Math.min(frameIndex, molecule.frameCount() - 1));
        }
        positionsDirty = true;
    }

    void setVibration(int vibrationIndex, float vibrationPhase) {
        if (molecule == null || !molecule.hasVibrations()) {
            this.vibrationIndex = 0;
        } else {
            this.vibrationIndex = Math.max(0, Math.min(vibrationIndex, molecule.vibrationCount() - 1));
        }
        this.vibrationPhase = vibrationPhase;
        positionsDirty = true;
    }

    void setVibrationAmplitude(float vibrationAmplitude) {
        this.vibrationAmplitude = Math.max(0f, Math.min(2.5f, vibrationAmplitude));
        positionsDirty = true;
    }

    void setState(RepresentationState state) {
        RepresentationState.Snapshot next = state == null
                ? new RepresentationState().snapshot()
                : state.snapshot();
        boolean modeChanged = this.state.mode != next.mode;
        boolean detailChanged = this.state.detailLevel != next.detailLevel;
        this.state = next;
        if (modeChanged || detailChanged) {
            topologyDirty = true;
            positionsDirty = true;
        }
    }

    void forceRebuild() {
        topologyDirty = true;
        positionsDirty = true;
    }

    Buffers buffers() {
        if (molecule == null || molecule.atomCount() == 0) {
            buffers.clearCounts();
            return buffers;
        }
        if (topologyDirty) {
            rebuildTopologyBuffers();
            buffers.topologyChanged = true;
            topologyDirty = false;
            positionsDirty = true;
        }
        if (positionsDirty) {
            rebuildPositionBuffers();
            buffers.positionsChanged = true;
            positionsDirty = false;
        }
        return buffers;
    }

    boolean useVbo() {
        return buffers.useVbo;
    }

    private void rebuildTopologyBuffers() {
        int atomCount = molecule.atomCount();
        boolean shouldUseVbo = atomCount > VBO_ATOM_THRESHOLD;
        if (buffers.useVbo != shouldUseVbo) {
            buffers.vboPolicyChanged = true;
        }
        buffers.useVbo = shouldUseVbo;
        buffers.atomCount = atomCount;
        buffers.atomVertexCount = atomCount * 6;
        buffers.atomColorArray = ensureArray(buffers.atomColorArray, buffers.atomVertexCount * 4);
        buffers.atomRadiusArray = ensureArray(buffers.atomRadiusArray, buffers.atomVertexCount);
        buffers.atomCornerArray = ensureArray(buffers.atomCornerArray, buffers.atomVertexCount * 2);
        float density = atomCount > 6000 ? 0.36f : atomCount > 1500 ? 0.56f : 1f;
        float modeScale = state.mode == RepresentationState.MODE_STICKS ? 0.12f : 0.42f;
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
        buffers.atomColors = putBuffer(buffers.atomColors, buffers.atomColorArray, buffers.atomVertexCount * 4);
        buffers.atomCorners = putBuffer(buffers.atomCorners, buffers.atomCornerArray, buffers.atomVertexCount * 2);
        buffers.atomRadii = putBuffer(buffers.atomRadii, buffers.atomRadiusArray, buffers.atomVertexCount);
    }

    private void rebuildPositionBuffers() {
        Molecule.Frame frame = molecule.frameAt(frameIndex);
        int atomCount = molecule.atomCount();
        float[] center = molecule.centerForFrame(frameIndex);
        float extent = Math.max(1f, molecule.maxExtentForFrame(frameIndex));
        Molecule.VibrationMode vibration = molecule.vibrationAt(vibrationIndex);
        float vibrate = 0f;
        float vibrationScale = 0f;
        if (vibration != null && vibration.hasDisplacement()) {
            vibrate = (float) Math.sin(vibrationPhase);
            vibrationScale = vibrationScale(vibration, extent) * vibrationAmplitude;
        }
        buffers.atomPositionArray = ensureArray(buffers.atomPositionArray, atomCount * 3);
        buffers.atomCenterArray = ensureArray(buffers.atomCenterArray, buffers.atomVertexCount * 3);

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
            buffers.atomPositionArray[target] = (x - center[0]) / extent;
            buffers.atomPositionArray[target + 1] = (y - center[1]) / extent;
            buffers.atomPositionArray[target + 2] = (z - center[2]) / extent;
            int quad = i * 18;
            for (int j = 0; j < 6; j++) {
                int targetCenter = quad + j * 3;
                buffers.atomCenterArray[targetCenter] = buffers.atomPositionArray[target];
                buffers.atomCenterArray[targetCenter + 1] = buffers.atomPositionArray[target + 1];
                buffers.atomCenterArray[targetCenter + 2] = buffers.atomPositionArray[target + 2];
            }
        }

        buffers.atomPositions = putBuffer(buffers.atomPositions, buffers.atomPositionArray, atomCount * 3);
        buffers.atomCenters = putBuffer(buffers.atomCenters, buffers.atomCenterArray, buffers.atomVertexCount * 3);
        rebuildLineBuffers(buffers.atomPositionArray);
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
        buffers.linePositionArray = ensureArray(buffers.linePositionArray, maxLineVertexCount * 3);
        buffers.lineColorArray = ensureArray(buffers.lineColorArray, maxLineVertexCount * 4);
        float a = state.mode == RepresentationState.MODE_STICKS ? 0.98f : 0.82f;
        int vertex = 0;
        int atomCount = molecule.atomCount();
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
            float trimA = state.mode == RepresentationState.MODE_SPACE
                    ? bondTrim(molecule.atoms.get(bond.a).element, length)
                    : 0f;
            float trimB = state.mode == RepresentationState.MODE_SPACE
                    ? bondTrim(molecule.atoms.get(bond.b).element, length)
                    : 0f;
            if (trimA + trimB > length * 0.72f) {
                float trim = length * 0.36f;
                trimA = trim;
                trimB = trim;
            }

            int pi = vertex * 3;
            buffers.linePositionArray[pi] = ax + ux * trimA;
            buffers.linePositionArray[pi + 1] = ay + uy * trimA;
            buffers.linePositionArray[pi + 2] = az + uz * trimA;
            putElementColor(buffers.lineColorArray, vertex, molecule.atoms.get(bond.a).element, a);
            vertex++;

            pi = vertex * 3;
            buffers.linePositionArray[pi] = bx - ux * trimB;
            buffers.linePositionArray[pi + 1] = by - uy * trimB;
            buffers.linePositionArray[pi + 2] = bz - uz * trimB;
            putElementColor(buffers.lineColorArray, vertex, molecule.atoms.get(bond.b).element, a);
            vertex++;
        }
        buffers.lineVertexCount = vertex;
        buffers.linePositions = putBuffer(buffers.linePositions, buffers.linePositionArray, buffers.lineVertexCount * 3);
        buffers.lineColors = putBuffer(buffers.lineColors, buffers.lineColorArray, buffers.lineVertexCount * 4);
    }

    private void putAtomVertex(int vertex, float cornerX, float cornerY, float r, float g, float b, float radius) {
        int cornerIndex = vertex * 2;
        buffers.atomCornerArray[cornerIndex] = cornerX;
        buffers.atomCornerArray[cornerIndex + 1] = cornerY;
        int colorIndex = vertex * 4;
        buffers.atomColorArray[colorIndex] = r;
        buffers.atomColorArray[colorIndex + 1] = g;
        buffers.atomColorArray[colorIndex + 2] = b;
        buffers.atomColorArray[colorIndex + 3] = 1f;
        buffers.atomRadiusArray[vertex] = radius;
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

    static final class Buffers {
        FloatBuffer atomPositions;
        FloatBuffer atomCenters;
        FloatBuffer atomCorners;
        FloatBuffer atomColors;
        FloatBuffer atomRadii;
        FloatBuffer linePositions;
        FloatBuffer lineColors;
        float[] atomPositionArray;
        float[] atomCenterArray;
        float[] atomCornerArray;
        float[] atomColorArray;
        float[] atomRadiusArray;
        float[] linePositionArray;
        float[] lineColorArray;
        int atomCount;
        int atomVertexCount;
        int lineVertexCount;
        boolean useVbo;
        boolean vboPolicyChanged;
        boolean topologyChanged;
        boolean positionsChanged;

        void clearCounts() {
            atomCount = 0;
            atomVertexCount = 0;
            lineVertexCount = 0;
        }

        boolean consumeVboPolicyChanged() {
            boolean changed = vboPolicyChanged;
            vboPolicyChanged = false;
            return changed;
        }

        boolean consumeTopologyChanged() {
            boolean changed = topologyChanged;
            topologyChanged = false;
            return changed;
        }

        boolean consumePositionsChanged() {
            boolean changed = positionsChanged;
            positionsChanged = false;
            return changed;
        }
    }
}
