package io.iaw.molview;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MoleculeParser {
    private MoleculeParser() {
    }

    static Molecule parse(String text, String sourceName) throws IOException {
        String first = firstMeaningfulLine(text);
        if (looksLikeGaussianOutput(text, sourceName)) {
            return parseGaussianOutput(text, sourceName);
        }
        if (looksLikeInteger(first)) {
            return parseXyzOnly(text, sourceName);
        }
        throw new IOException("Only XYZ and Gaussian out/log files are supported");
    }

    static boolean looksLikeGaussianOutput(String text, String sourceName) {
        if (looksLikeGaussianName(sourceName)) {
            return true;
        }
        if (text == null) {
            return false;
        }
        return text.contains("Frequencies --")
                || text.contains("Standard orientation:")
                || text.contains("Input orientation:")
                || text.contains("Entering Gaussian System")
                || text.contains("Gaussian, Inc.");
    }

    static boolean looksLikeGaussianName(String sourceName) {
        String lowerName = sourceName == null ? "" : sourceName.toLowerCase(Locale.US);
        return lowerName.endsWith(".out") || lowerName.endsWith(".log");
    }

    static Molecule parseXyzOnly(String text, String sourceName) throws IOException {
        return parseXyzOnly(new StringReader(text == null ? "" : text), sourceName);
    }

    static Molecule parseXyzOnly(Reader input, String sourceName) throws IOException {
        BufferedReader reader = input instanceof BufferedReader
                ? (BufferedReader) input
                : new BufferedReader(input);
        List<Molecule.Atom> atoms = new ArrayList<>();
        List<Molecule.Frame> frames = new ArrayList<>();
        String title = sourceName;
        String line;
        int frameNumber = 0;
        while ((line = nextNonBlank(reader)) != null) {
            int atomCount;
            try {
                atomCount = Integer.parseInt(line.trim());
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid XYZ atom count near frame " + (frameNumber + 1));
            }
            if (atomCount <= 0) {
                throw new IOException("XYZ atom count must be positive");
            }
            String comment = reader.readLine();
            if (frameNumber == 0 && comment != null && !comment.trim().isEmpty()) {
                title = comment.trim();
            }
            if (frameNumber > 0 && atomCount != atoms.size()) {
                throw new IOException("XYZ trajectory frame atom count changed");
            }
            float[] xyz = new float[atomCount * 3];
            for (int i = 0; i < atomCount; i++) {
                String atomLine = reader.readLine();
                if (atomLine == null) {
                    throw new IOException("XYZ frame ended early");
                }
                String[] parts = atomLine.trim().split("\\s+");
                if (parts.length < 4) {
                    throw new IOException("Invalid XYZ atom line: " + atomLine);
                }
                String element = ElementTable.normalizeElement(parts[0]);
                if (frameNumber == 0) {
                    atoms.add(new Molecule.Atom(element, element, "", "", i + 1, i + 1));
                }
                int j = i * 3;
                xyz[j] = parseFloat(parts[1], "x");
                xyz[j + 1] = parseFloat(parts[2], "y");
                xyz[j + 2] = parseFloat(parts[3], "z");
            }
            frames.add(new Molecule.Frame(xyz));
            frameNumber++;
        }
        if (frames.isEmpty()) {
            throw new IOException("No XYZ frames found");
        }
        List<Molecule.Bond> bonds = buildAutoBonds(atoms, frames.get(0));
        List<String> infoLines = new ArrayList<>();
        infoLines.add("XYZ title: " + title);
        infoLines.add(atoms.size() + " atoms | " + bonds.size() + " bonds | " + frames.size() + " frame" + (frames.size() == 1 ? "" : "s"));
        return new Molecule(title, "XYZ", atoms, frames, bonds, new ArrayList<Molecule.VibrationMode>(), infoLines);
    }

    static Molecule parseGaussianOutput(String text, String sourceName) throws IOException {
        return parseGaussianOutput(new StringReader(text == null ? "" : text), sourceName);
    }

    static Molecule parseGaussianOutput(Reader input, String sourceName) throws IOException {
        PushbackLineReader lines = new PushbackLineReader(input);
        OrientationBlock lastInput = null;
        OrientationBlock lastStandard = null;
        List<String> warnings = new ArrayList<>();
        List<Molecule.VibrationMode> vibrations = new ArrayList<>();
        GaussianInfo info = new GaussianInfo();
        StringBuilder route = new StringBuilder();
        boolean routeCollecting = false;
        boolean routeDone = false;
        String line;
        while ((line = lines.next()) != null) {
            if (!routeDone) {
                String trimmed = line.trim();
                if (!routeCollecting && trimmed.startsWith("#")) {
                    routeCollecting = true;
                }
                if (routeCollecting) {
                    if (trimmed.isEmpty()) {
                        routeDone = true;
                        info.route = compact(route.toString(), 96);
                        routeCollecting = false;
                    } else {
                        if (route.length() > 0) {
                            route.append(' ');
                        }
                        route.append(trimmed);
                    }
                }
            }
            updateGaussianInfo(line, info);
            String trimmed = line.trim();
            if (trimmed.startsWith("Input orientation:")) {
                OrientationBlock block = parseGaussianOrientation(lines);
                if (block != null) {
                    lastInput = block;
                }
            } else if (trimmed.startsWith("Standard orientation:")) {
                OrientationBlock block = parseGaussianOrientation(lines);
                if (block != null) {
                    lastStandard = block;
                }
            } else if (line.contains("Frequencies --")) {
                OrientationBlock geometry = lastStandard != null ? lastStandard : lastInput;
                int atomCount = geometry == null ? 0 : geometry.atoms.size();
                parseGaussianFrequencyBlock(lines, line, atomCount, warnings, vibrations);
            }
        }
        if (!routeDone && info.route.isEmpty() && route.length() > 0) {
            info.route = compact(route.toString(), 96);
        }
        OrientationBlock geometry = lastStandard != null ? lastStandard : lastInput;
        if (geometry == null || geometry.atoms.isEmpty() || geometry.xyz.length == 0) {
            throw new IOException("No Gaussian orientation block found");
        }

        List<Molecule.Frame> frames = new ArrayList<>();
        frames.add(new Molecule.Frame(geometry.xyz));
        List<Molecule.Bond> bonds = buildAutoBonds(geometry.atoms, frames.get(0));
        finishGaussianInfo(info, vibrations);
        List<String> infoLines = buildGaussianInfoLines(info, geometry, bonds, vibrations);
        infoLines.addAll(warnings);
        String title = sourceName == null || sourceName.trim().isEmpty() ? "Gaussian output" : sourceName;
        return new Molecule(title, "Gaussian", geometry.atoms, frames, bonds, vibrations, infoLines);
    }

    private static void updateGaussianInfo(String line, GaussianInfo info) {
        String trimmed = line.trim();
        if (trimmed.contains("Normal termination of Gaussian")) {
            info.termination = "Normal termination";
        } else if (trimmed.contains("Error termination")) {
            info.termination = "Error termination";
        } else if (trimmed.startsWith("Charge =")) {
            parseChargeMultiplicity(trimmed, info);
        } else if (trimmed.startsWith("SCF Done:")) {
            parseScfDone(trimmed, info);
        } else if (trimmed.startsWith("Zero-point correction=")) {
            info.zeroPointCorrection = valueAfterEquals(trimmed);
        }
    }

    private static void finishGaussianInfo(GaussianInfo info, List<Molecule.VibrationMode> vibrations) {
        info.frequencyCount = vibrations.size();
        info.imaginaryCount = 0;
        info.lowestFrequency = Float.NaN;
        for (Molecule.VibrationMode mode : vibrations) {
            if (mode.frequency < 0f) {
                info.imaginaryCount++;
            }
            if (Float.isNaN(info.lowestFrequency) || mode.frequency < info.lowestFrequency) {
                info.lowestFrequency = mode.frequency;
            }
        }
        if (info.termination.isEmpty()) {
            info.termination = "Termination unknown";
        }
    }

    private static void parseChargeMultiplicity(String line, GaussianInfo info) {
        String[] parts = line.replace("=", " = ").split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if ("Charge".equals(parts[i]) && i + 2 < parts.length) {
                info.charge = parseIntSafe(parts[i + 2], Integer.MIN_VALUE);
            } else if ("Multiplicity".equals(parts[i]) && i + 2 < parts.length) {
                info.multiplicity = parseIntSafe(parts[i + 2], Integer.MIN_VALUE);
            }
        }
    }

    private static void parseScfDone(String line, GaussianInfo info) {
        int methodStart = line.indexOf("E(");
        int methodEnd = methodStart < 0 ? -1 : line.indexOf(')', methodStart);
        if (methodStart >= 0 && methodEnd > methodStart) {
            info.scfMethod = line.substring(methodStart + 2, methodEnd).trim();
        }
        int eq = line.indexOf('=');
        if (eq >= 0) {
            String[] parts = line.substring(eq + 1).trim().split("\\s+");
            if (parts.length > 0) {
                try {
                    info.scfEnergy = parseFloat(parts[0], "scf energy");
                } catch (IOException ignored) {
                    info.scfEnergy = Float.NaN;
                }
            }
        }
    }

    private static float valueAfterEquals(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return Float.NaN;
        }
        String[] parts = line.substring(eq + 1).trim().split("\\s+");
        if (parts.length == 0) {
            return Float.NaN;
        }
        try {
            return parseFloat(parts[0], "gaussian value");
        } catch (IOException ignored) {
            return Float.NaN;
        }
    }

    private static List<String> buildGaussianInfoLines(
            GaussianInfo info,
            OrientationBlock geometry,
            List<Molecule.Bond> bonds,
            List<Molecule.VibrationMode> vibrations
    ) {
        List<String> lines = new ArrayList<>();
        StringBuilder job = new StringBuilder("Gaussian: ");
        job.append(info.termination);
        if (info.charge != Integer.MIN_VALUE && info.multiplicity != Integer.MIN_VALUE) {
            job.append(" | Charge ").append(info.charge).append(" Mult ").append(info.multiplicity);
        }
        if (!Float.isNaN(info.scfEnergy)) {
            job.append(" | SCF");
            if (!info.scfMethod.isEmpty()) {
                job.append('(').append(info.scfMethod).append(')');
            }
            job.append(' ').append(formatFloat(info.scfEnergy, 6)).append(" Eh");
        }
        if (!Float.isNaN(info.zeroPointCorrection)) {
            job.append(" | ZPE ").append(formatFloat(info.zeroPointCorrection, 6));
        }
        lines.add(compact(job.toString(), 132));

        StringBuilder freq = new StringBuilder();
        if (!info.route.isEmpty()) {
            freq.append("Route: ").append(info.route).append(" | ");
        }
        freq.append(geometry.atoms.size()).append(" atoms | ")
                .append(bonds.size()).append(" bonds | ")
                .append(vibrations.size()).append(" freqs | ")
                .append(info.imaginaryCount).append(" imag");
        if (!Float.isNaN(info.lowestFrequency)) {
            freq.append(" | Lowest ").append(formatFloat(info.lowestFrequency, 1)).append(" cm^-1");
        }
        lines.add(compact(freq.toString(), 132));
        return lines;
    }

    private static OrientationBlock parseGaussianOrientation(PushbackLineReader reader) throws IOException {
        String line;
        boolean firstDash = false;
        boolean secondDash = false;
        while ((line = reader.next()) != null) {
            if (!isGaussianDash(line)) {
                continue;
            }
            if (!firstDash) {
                firstDash = true;
            } else {
                secondDash = true;
                break;
            }
        }
        if (!secondDash) {
            return null;
        }
        List<Molecule.Atom> atoms = new ArrayList<>();
        List<Float> coords = new ArrayList<>();
        while ((line = reader.next()) != null) {
            if (isGaussianDash(line)) {
                break;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) {
                continue;
            }
            int atomicNumber = parseIntSafe(parts[1], 0);
            String element = ElementTable.elementForAtomicNumber(atomicNumber);
            if ("X".equals(element) && atomicNumber <= 0) {
                continue;
            }
            int serial = atoms.size() + 1;
            atoms.add(new Molecule.Atom(element, element, "", "", serial, serial));
            coords.add(parseFloat(parts[3], "gaussian x"));
            coords.add(parseFloat(parts[4], "gaussian y"));
            coords.add(parseFloat(parts[5], "gaussian z"));
        }
        if (atoms.isEmpty()) {
            return null;
        }
        float[] xyz = new float[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            xyz[i] = coords.get(i);
        }
        return new OrientationBlock(atoms, xyz);
    }

    private static boolean isGaussianDash(String line) {
        return line != null && line.trim().startsWith("----");
    }

    private static void parseGaussianFrequencyBlock(
            PushbackLineReader reader,
            String frequencyLine,
            int atomCount,
            List<String> warnings,
            List<Molecule.VibrationMode> modes
    ) throws IOException {
        float[] frequencies = valuesAfterMarker(frequencyLine);
        if (frequencies.length == 0) {
            return;
        }
        int modeCount = frequencies.length;
        float[] reducedMasses = filled(modeCount, Float.NaN);
        float[] forceConstants = filled(modeCount, Float.NaN);
        float[] irIntensities = filled(modeCount, Float.NaN);
        String line;
        boolean foundDisplacementHeader = false;
        while ((line = reader.next()) != null) {
            String trimmed = line.trim();
            if (line.contains("Frequencies --")) {
                reader.pushBack(line);
                break;
            }
            if (line.contains("Red. masses --")) {
                copyValues(valuesAfterMarker(line), reducedMasses);
            } else if (line.contains("Frc consts  --") || line.contains("Frc consts --")) {
                copyValues(valuesAfterMarker(line), forceConstants);
            } else if (line.contains("IR Inten    --") || line.contains("IR Inten --")) {
                copyValues(valuesAfterMarker(line), irIntensities);
            } else if (trimmed.startsWith("Atom") && trimmed.contains("AN")) {
                foundDisplacementHeader = true;
                break;
            }
        }

        float[][] displacements = new float[modeCount][Math.max(0, atomCount) * 3];
        if (foundDisplacementHeader && atomCount > 0) {
            int rows = 0;
            while ((line = reader.next()) != null && rows < atomCount) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.contains("Frequencies --") || isGaussianDash(trimmed)) {
                    if (trimmed.contains("Frequencies --")) {
                        reader.pushBack(line);
                    }
                    break;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2 + modeCount * 3) {
                    break;
                }
                for (int mode = 0; mode < modeCount; mode++) {
                    int source = 2 + mode * 3;
                    int target = rows * 3;
                    displacements[mode][target] = parseFloat(parts[source], "normal mode dx");
                    displacements[mode][target + 1] = parseFloat(parts[source + 1], "normal mode dy");
                    displacements[mode][target + 2] = parseFloat(parts[source + 2], "normal mode dz");
                }
                rows++;
            }
            if (rows != atomCount) {
                addDisplacementWarning(warnings, modes.size(), modeCount, rows, atomCount, true);
                displacements = new float[modeCount][Math.max(0, atomCount) * 3];
            }
        } else {
            addDisplacementWarning(warnings, modes.size(), modeCount, 0, atomCount, false);
        }

        for (int mode = 0; mode < modeCount; mode++) {
            modes.add(new Molecule.VibrationMode(
                    frequencies[mode],
                    reducedMasses[mode],
                    forceConstants[mode],
                    irIntensities[mode],
                    displacements[mode]
            ));
        }
    }

    private static void addDisplacementWarning(
            List<String> warnings,
            int existingModeCount,
            int modeCount,
            int rows,
            int atomCount,
            boolean incomplete
    ) {
        int firstMode = existingModeCount + 1;
        int lastMode = firstMode + modeCount - 1;
        if (incomplete) {
            warnings.add("Warning: Gaussian displacement block incomplete for modes "
                    + firstMode + "-" + lastMode + " (" + rows + "/" + atomCount
                    + " rows parsed); animation disabled for these modes.");
        } else {
            warnings.add("Warning: Gaussian displacement block missing for modes "
                    + firstMode + "-" + lastMode
                    + "; animation disabled for these modes.");
        }
    }

    private static float[] filled(int count, float value) {
        float[] values = new float[count];
        for (int i = 0; i < values.length; i++) {
            values[i] = value;
        }
        return values;
    }

    private static void copyValues(float[] source, float[] target) {
        int count = Math.min(source.length, target.length);
        for (int i = 0; i < count; i++) {
            target[i] = source[i];
        }
    }

    private static float[] valuesAfterMarker(String line) throws IOException {
        int index = line.indexOf("--");
        if (index < 0 || index + 2 >= line.length()) {
            return new float[0];
        }
        String rest = line.substring(index + 2).trim();
        if (rest.isEmpty()) {
            return new float[0];
        }
        String[] parts = rest.split("\\s+");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = parseFloat(parts[i], "gaussian frequency value");
        }
        return values;
    }

    private static List<Molecule.Bond> buildAutoBonds(List<Molecule.Atom> atoms, Molecule.Frame frame) {
        List<Molecule.Bond> bonds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Map<String, List<Integer>> grid = new HashMap<>();
        float[] xyz = frame.xyz;
        float cellSize = 3.0f;
        int maxBonds = Math.min(120000, Math.max(1000, atoms.size() * 5));

        for (int i = 0; i < atoms.size(); i++) {
            int j = i * 3;
            int cx = cell(xyz[j], cellSize);
            int cy = cell(xyz[j + 1], cellSize);
            int cz = cell(xyz[j + 2], cellSize);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> candidates = grid.get(key(cx + dx, cy + dy, cz + dz));
                        if (candidates == null) {
                            continue;
                        }
                        for (int other : candidates) {
                            if (shouldBond(atoms.get(i), atoms.get(other), xyz, i, other)) {
                                Molecule.Bond bond = new Molecule.Bond(i, other);
                                if (seen.add(bond.key())) {
                                    bonds.add(bond);
                                    if (bonds.size() >= maxBonds) {
                                        return bonds;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            String key = key(cx, cy, cz);
            List<Integer> bucket = grid.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grid.put(key, bucket);
            }
            bucket.add(i);
        }
        return bonds;
    }

    private static boolean shouldBond(
            Molecule.Atom a,
            Molecule.Atom b,
            float[] xyz,
            int ai,
            int bi
    ) {
        int aj = ai * 3;
        int bj = bi * 3;
        float dx = xyz[aj] - xyz[bj];
        float dy = xyz[aj + 1] - xyz[bj + 1];
        float dz = xyz[aj + 2] - xyz[bj + 2];
        float dist2 = dx * dx + dy * dy + dz * dz;
        float min = 0.35f;
        float max = ElementTable.radius(a.element) + ElementTable.radius(b.element) + 0.45f;
        return dist2 > min * min && dist2 <= max * max;
    }

    private static String firstMeaningfulLine(String text) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line.trim();
            }
        }
        return "";
    }

    private static String nextNonBlank(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }

    private static boolean looksLikeInteger(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(text.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static float parseFloat(String text, String field) throws IOException {
        try {
            return Float.parseFloat(text.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid numeric value for " + field + ": " + text);
        }
    }

    private static int parseIntSafe(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String oneLine = value.trim().replaceAll("\\s+", " ");
        if (oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String formatFloat(float value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private static int cell(float value, float cellSize) {
        return (int) Math.floor(value / cellSize);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static final class OrientationBlock {
        final List<Molecule.Atom> atoms;
        final float[] xyz;

        OrientationBlock(List<Molecule.Atom> atoms, float[] xyz) {
            this.atoms = atoms;
            this.xyz = xyz;
        }
    }

    private static final class GaussianInfo {
        String termination = "";
        String route = "";
        String scfMethod = "";
        int charge = Integer.MIN_VALUE;
        int multiplicity = Integer.MIN_VALUE;
        float scfEnergy = Float.NaN;
        float zeroPointCorrection = Float.NaN;
        int frequencyCount;
        int imaginaryCount;
        float lowestFrequency = Float.NaN;
    }

    private static final class PushbackLineReader {
        private final BufferedReader reader;
        private String pushedBack;

        PushbackLineReader(Reader input) {
            reader = input instanceof BufferedReader ? (BufferedReader) input : new BufferedReader(input);
        }

        String next() throws IOException {
            if (pushedBack != null) {
                String line = pushedBack;
                pushedBack = null;
                return line;
            }
            return reader.readLine();
        }

        void pushBack(String line) {
            pushedBack = line;
        }
    }
}
