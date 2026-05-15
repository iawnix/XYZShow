package io.iaw.molview;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ElementTable {
    private static final Map<String, Float> RADII = new HashMap<>();
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final String[] ATOMIC_SYMBOLS = {
            "",
            "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
            "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar",
            "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
            "Ga", "Ge", "As", "Se", "Br", "Kr",
            "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd",
            "In", "Sn", "Sb", "Te", "I", "Xe",
            "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy",
            "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt",
            "Au", "Hg", "Tl", "Pb", "Bi"
    };

    static {
        put("H", 0.31f, Color.rgb(238, 242, 247));
        put("C", 0.76f, Color.rgb(68, 76, 86));
        put("N", 0.71f, Color.rgb(76, 126, 231));
        put("O", 0.66f, Color.rgb(230, 72, 72));
        put("F", 0.57f, Color.rgb(99, 214, 173));
        put("P", 1.07f, Color.rgb(232, 154, 63));
        put("S", 1.05f, Color.rgb(238, 201, 74));
        put("Cl", 1.02f, Color.rgb(96, 199, 87));
        put("Br", 1.20f, Color.rgb(166, 74, 62));
        put("I", 1.39f, Color.rgb(116, 77, 168));
        put("B", 0.85f, Color.rgb(236, 179, 120));
        put("Si", 1.11f, Color.rgb(194, 170, 130));
        put("Na", 1.66f, Color.rgb(112, 105, 214));
        put("K", 2.03f, Color.rgb(131, 79, 204));
        put("Ca", 1.76f, Color.rgb(90, 189, 91));
        put("Mg", 1.41f, Color.rgb(118, 204, 118));
        put("Fe", 1.24f, Color.rgb(199, 92, 63));
        put("Zn", 1.22f, Color.rgb(140, 140, 170));
        put("Cu", 1.32f, Color.rgb(195, 121, 65));
        put("Mn", 1.39f, Color.rgb(156, 122, 199));
        put("Co", 1.26f, Color.rgb(86, 118, 198));
        put("Ni", 1.24f, Color.rgb(82, 166, 138));
        put("X", 0.77f, Color.rgb(175, 183, 194));
    }

    private ElementTable() {
    }

    static String normalizeElement(String raw) {
        if (raw == null) {
            return "X";
        }
        String cleaned = raw.replaceAll("[^A-Za-z]", "");
        if (cleaned.isEmpty()) {
            return "X";
        }
        String one = cleaned.substring(0, 1).toUpperCase(Locale.US);
        if (cleaned.length() == 1) {
            return RADII.containsKey(one) ? one : "X";
        }
        String two = one + cleaned.substring(1, 2).toLowerCase(Locale.US);
        if (RADII.containsKey(two)) {
            return two;
        }
        return RADII.containsKey(one) ? one : "X";
    }

    static String inferPdbElement(String atomName, String elementColumn) {
        String fromColumn = normalizeElement(elementColumn);
        if (!"X".equals(fromColumn)) {
            return fromColumn;
        }
        if (atomName == null) {
            return "X";
        }
        String cleaned = atomName.trim().replaceAll("^[0-9]+", "");
        if (cleaned.isEmpty()) {
            return "X";
        }
        char first = 0;
        char second = 0;
        int found = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isLetter(c)) {
                if (found == 0) {
                    first = c;
                } else if (found == 1) {
                    second = c;
                    break;
                }
                found++;
            }
        }
        if (found == 0) {
            return "X";
        }
        if (found > 1 && Character.isLowerCase(second)) {
            return normalizeElement("" + first + second);
        }
        return normalizeElement("" + first);
    }

    static String elementForAtomicNumber(int atomicNumber) {
        if (atomicNumber > 0 && atomicNumber < ATOMIC_SYMBOLS.length) {
            return normalizeElement(ATOMIC_SYMBOLS[atomicNumber]);
        }
        return "X";
    }

    static float radius(String element) {
        Float radius = RADII.get(normalizeElement(element));
        return radius == null ? 0.77f : radius;
    }

    static int color(String element) {
        Integer color = COLORS.get(normalizeElement(element));
        return color == null ? Color.rgb(175, 183, 194) : color;
    }

    private static void put(String element, float radius, int color) {
        RADII.put(element, radius);
        COLORS.put(element, color);
    }
}
