package io.iaw.molview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Molecule {
    final String title;
    final String sourceType;
    final List<Atom> atoms;
    final List<Frame> frames;
    final List<Bond> bonds;
    final List<VibrationMode> vibrations;
    final List<String> infoLines;

    Molecule(
            String title,
            String sourceType,
            List<Atom> atoms,
            List<Frame> frames,
            List<Bond> bonds
    ) {
        this(title, sourceType, atoms, frames, bonds, new ArrayList<VibrationMode>(), new ArrayList<String>());
    }

    Molecule(
            String title,
            String sourceType,
            List<Atom> atoms,
            List<Frame> frames,
            List<Bond> bonds,
            List<VibrationMode> vibrations
    ) {
        this(title, sourceType, atoms, frames, bonds, vibrations, new ArrayList<String>());
    }

    Molecule(
            String title,
            String sourceType,
            List<Atom> atoms,
            List<Frame> frames,
            List<Bond> bonds,
            List<VibrationMode> vibrations,
            List<String> infoLines
    ) {
        this.title = title == null || title.trim().isEmpty() ? "Molecule" : title.trim();
        this.sourceType = sourceType;
        this.atoms = atoms;
        this.frames = frames;
        this.bonds = bonds;
        this.vibrations = vibrations == null ? new ArrayList<VibrationMode>() : vibrations;
        this.infoLines = infoLines == null ? new ArrayList<String>() : infoLines;
    }

    int atomCount() {
        return atoms.size();
    }

    int frameCount() {
        return frames.size();
    }

    boolean hasVibrations() {
        return !vibrations.isEmpty();
    }

    int vibrationCount() {
        return vibrations.size();
    }

    VibrationMode vibrationAt(int index) {
        if (vibrations.isEmpty()) {
            return null;
        }
        int safe = Math.max(0, Math.min(index, vibrations.size() - 1));
        return vibrations.get(safe);
    }

    Frame frameAt(int index) {
        if (frames.isEmpty()) {
            return new Frame(new float[0]);
        }
        int safe = Math.max(0, Math.min(index, frames.size() - 1));
        return frames.get(safe);
    }

    float[] centerForFrame(int index) {
        Frame frame = frameAt(index);
        float[] xyz = frame.xyz;
        if (xyz.length == 0) {
            return new float[]{0f, 0f, 0f};
        }
        float cx = 0f;
        float cy = 0f;
        float cz = 0f;
        int count = xyz.length / 3;
        for (int i = 0; i < count; i++) {
            int j = i * 3;
            cx += xyz[j];
            cy += xyz[j + 1];
            cz += xyz[j + 2];
        }
        return new float[]{cx / count, cy / count, cz / count};
    }

    float maxExtentForFrame(int index) {
        Frame frame = frameAt(index);
        float[] xyz = frame.xyz;
        if (xyz.length == 0) {
            return 1f;
        }
        float[] center = centerForFrame(index);
        float max = 1f;
        int count = xyz.length / 3;
        for (int i = 0; i < count; i++) {
            int j = i * 3;
            float dx = xyz[j] - center[0];
            float dy = xyz[j + 1] - center[1];
            float dz = xyz[j + 2] - center[2];
            max = Math.max(max, Math.abs(dx));
            max = Math.max(max, Math.abs(dy));
            max = Math.max(max, Math.abs(dz));
        }
        return max;
    }

    String summary() {
        String base = atoms.size() + " atoms  " + bonds.size() + " bonds  " + frames.size() + " frames";
        if (!vibrations.isEmpty()) {
            return base + "  " + vibrations.size() + " freqs";
        }
        return base;
    }

    String infoText() {
        if (infoLines.isEmpty()) {
            return summary();
        }
        StringBuilder builder = new StringBuilder();
        for (String line : infoLines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
        return builder.length() == 0 ? summary() : builder.toString();
    }

    List<String> elementsInUse() {
        Set<String> seen = new LinkedHashSet<>();
        for (Atom atom : atoms) {
            seen.add(atom.element);
        }
        return new ArrayList<>(seen);
    }

    static final class Atom {
        final String element;
        final String name;
        final String residueName;
        final String chainId;
        final int residueSeq;
        final int serial;

        Atom(String element, String name, String residueName, String chainId, int residueSeq, int serial) {
            this.element = ElementTable.normalizeElement(element);
            this.name = name == null ? "" : name.trim();
            this.residueName = residueName == null ? "" : residueName.trim();
            this.chainId = chainId == null ? "" : chainId.trim();
            this.residueSeq = residueSeq;
            this.serial = serial;
        }
    }

    static final class Bond {
        final int a;
        final int b;

        Bond(int a, int b) {
            this.a = a;
            this.b = b;
        }

        long key() {
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            return (((long) lo) << 32) | (hi & 0xffffffffL);
        }
    }

    static final class Frame {
        final float[] xyz;

        Frame(float[] xyz) {
            this.xyz = xyz;
        }
    }

    static final class VibrationMode {
        final float frequency;
        final float reducedMass;
        final float forceConstant;
        final float irIntensity;
        final float[] displacement;

        VibrationMode(
                float frequency,
                float reducedMass,
                float forceConstant,
                float irIntensity,
                float[] displacement
        ) {
            this.frequency = frequency;
            this.reducedMass = reducedMass;
            this.forceConstant = forceConstant;
            this.irIntensity = irIntensity;
            this.displacement = displacement == null ? new float[0] : displacement;
        }

        boolean hasDisplacement() {
            for (float value : displacement) {
                if (Math.abs(value) > 0.000001f) {
                    return true;
                }
            }
            return false;
        }

        float maxDisplacement() {
            float max = 0f;
            for (int i = 0; i + 2 < displacement.length; i += 3) {
                float dx = displacement[i];
                float dy = displacement[i + 1];
                float dz = displacement[i + 2];
                max = Math.max(max, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            return max;
        }
    }
}
