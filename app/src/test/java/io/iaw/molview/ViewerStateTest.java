package io.iaw.molview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViewerStateTest {
    @Test
    public void representationCopyIsIndependent() {
        RepresentationState state = new RepresentationState();
        state.setMode(RepresentationState.MODE_STICKS);
        state.setDetailLevel(RepresentationState.DETAIL_REDUCED);
        state.setBackgroundMode(RepresentationState.BACKGROUND_LIGHT);

        RepresentationState copy = state.copy();
        state.setMode(RepresentationState.MODE_SPACE);
        state.setBackgroundMode(RepresentationState.BACKGROUND_DARK);

        assertEquals(RepresentationState.MODE_STICKS, copy.mode());
        assertEquals(RepresentationState.DETAIL_REDUCED, copy.detailLevel());
        assertEquals(RepresentationState.BACKGROUND_LIGHT, copy.backgroundMode());
    }

    @Test
    public void sourceStateTracksReferenceKinds() {
        ViewerState state = new ViewerState();

        assertFalse(state.hasSourceReference());

        state.source().setAsset("samples/dopamine.xyz", "Default", "dopamine.xyz", "Default - dopamine.xyz");
        assertTrue(state.hasSourceReference());
        assertEquals(SourceKind.ASSET, state.source().kind());
        assertEquals("samples/dopamine.xyz", state.source().assetPath());

        state.source().setUri("content://example/mol.xyz", "File", "mol.xyz", "File - mol.xyz", true);
        assertTrue(state.hasSourceReference());
        assertEquals(SourceKind.URI, state.source().kind());
        assertEquals("content://example/mol.xyz", state.source().uri());
        assertTrue(state.source().sessionOnly());

        state.source().clear();
        assertFalse(state.hasSourceReference());
    }
}
