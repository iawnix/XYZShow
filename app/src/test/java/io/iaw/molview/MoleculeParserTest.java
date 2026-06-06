package io.iaw.molview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public final class MoleculeParserTest {
    @Test
    public void parsesSingleFrameXyz() throws IOException {
        Molecule molecule = MoleculeParser.parseXyzOnly(
                "3\nwater\nO 0 0 0\nH 0 0 1\nH 1 0 0\n",
                "water.xyz");

        assertEquals("water", molecule.title);
        assertEquals("XYZ", molecule.sourceType);
        assertEquals(3, molecule.atomCount());
        assertEquals(1, molecule.frameCount());
    }

    @Test
    public void parsesMultiFrameXyzTrajectory() throws IOException {
        Molecule molecule = MoleculeParser.parseXyzOnly(
                "2\nstep 1\nH 0 0 0\nH 0 0 1\n2\nstep 2\nH 0 0 0\nH 0 0 1.1\n",
                "h2.xyz");

        assertEquals(2, molecule.atomCount());
        assertEquals(2, molecule.frameCount());
    }

    @Test(expected = IOException.class)
    public void rejectsChangedXyzFrameAtomCount() throws IOException {
        MoleculeParser.parseXyzOnly(
                "2\nstep 1\nH 0 0 0\nH 0 0 1\n3\nstep 2\nH 0 0 0\nH 0 0 1\nH 1 0 0\n",
                "bad.xyz");
    }

    @Test
    public void acceptsFortranDExponentInXyz() throws IOException {
        Molecule molecule = MoleculeParser.parseXyzOnly(
                "1\nsingle\nC 1.23D+00 -2.00d+00 3.0\n",
                "d.xyz");

        assertEquals(1.23f, molecule.frameAt(0).xyz[0], 0.0001f);
        assertEquals(-2.0f, molecule.frameAt(0).xyz[1], 0.0001f);
    }

    @Test
    public void parsesGaussianGeometryAndFrequencyDisplacements() throws IOException {
        Molecule molecule = MoleculeParser.parseGaussianOutput(gaussianOutput(true), "freq.log");

        assertEquals("Gaussian", molecule.sourceType);
        assertEquals(2, molecule.atomCount());
        assertEquals(1, molecule.frameCount());
        assertEquals(2, molecule.vibrationCount());
        assertTrue(molecule.vibrationAt(0).hasDisplacement());
        assertTrue(molecule.infoText().contains("Normal termination"));
    }

    @Test
    public void warnsWhenGaussianDisplacementsAreIncomplete() throws IOException {
        Molecule molecule = MoleculeParser.parseGaussianOutput(gaussianOutput(false), "freq.log");

        assertEquals(2, molecule.vibrationCount());
        assertFalse(molecule.vibrationAt(0).hasDisplacement());
        assertTrue(molecule.infoText().contains("Warning: Gaussian displacement block incomplete"));
    }

    @Test
    public void parsesGaussianWithoutFrequencies() throws IOException {
        Molecule molecule = MoleculeParser.parseGaussianOutput(gaussianOutputWithoutFrequencies(), "opt.out");

        assertEquals(2, molecule.atomCount());
        assertFalse(molecule.hasVibrations());
        assertTrue(molecule.infoText().contains("0 freqs"));
    }

    private static String gaussianOutput(boolean completeDisplacements) {
        String secondAtomRow = completeDisplacements
                ? "     2     1      -0.010     -0.020     -0.030      0.040      0.050      0.060\n"
                : "";
        return gaussianOutputWithoutFrequencies()
                + " Frequencies --  -123.45   456.78\n"
                + " Red. masses --     1.00     2.00\n"
                + " Frc consts  --     0.10     0.20\n"
                + " IR Inten    --    10.00    20.00\n"
                + " Atom  AN      X         Y         Z          X         Y         Z\n"
                + "     1     6       0.010      0.020      0.030     -0.040     -0.050     -0.060\n"
                + secondAtomRow
                + "\n";
    }

    private static String gaussianOutputWithoutFrequencies() {
        return " Entering Gaussian System\n"
                + " #p opt freq\n"
                + "\n"
                + " Charge = 0 Multiplicity = 1\n"
                + " Standard orientation:\n"
                + " ---------------------------------------------------------------------\n"
                + " Center     Atomic      Atomic             Coordinates (Angstroms)\n"
                + " Number     Number       Type             X           Y           Z\n"
                + " ---------------------------------------------------------------------\n"
                + "      1          6           0        0.000000    0.000000    0.000000\n"
                + "      2          1           0        0.000000    0.000000    1.090000\n"
                + " ---------------------------------------------------------------------\n"
                + " SCF Done:  E(RB3LYP) =  -40.000000     A.U. after   10 cycles\n"
                + " Normal termination of Gaussian\n";
    }
}
