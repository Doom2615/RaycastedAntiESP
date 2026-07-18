package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.locatables.implementations.ImmutableBlockLocatable;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable;
import games.cubi.raycastedantiesp.core.view.BlockViewTransition;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsBlockView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsBlockViewControllerTest {
    private static final BlockInfoResolver RESOLVER = new BlockInfoResolver() {
        @Override public boolean isOccluding(int blockStateID) { return false; }
        @Override public boolean isTileEntity(int blockStateID) { return blockStateID != 0; }
        @Override public boolean hasBlockEntityData(int blockStateID) { return blockStateID != 0; }
    };

    @Test
    void transitionCannotTargetReplacementAtSameCoordinates() {
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, 3, 64, 5);
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true);
        view.applyTileEntityCheckMode(true, 0);
        view.updateOrInsertTileEntity(location, 1, true);
        TileEntityLocatable<?> original = view.getTrackedTileEntity(location);
        view.applyTileEntityVisibilityDecision(location, false, 1);
        BlockViewTransition transition = view.drainTransitions().getFirst();

        view.removeTileEntity(location);
        view.updateOrInsertTileEntity(location, 2, true);
        TileEntityLocatable<?> replacement = view.getTrackedTileEntity(location);
        int replacementLastChecked = replacement.lastChecked();

        assertSame(original, transition.tileEntity());
        assertNull(PacketEventsBlockViewController.resolveCurrentTransitionState(view, transition));
        assertTrue(replacement.visible());
        assertEquals(replacementLastChecked, replacement.lastChecked());
    }

    @Test
    void currentTransitionStillResolvesByIdentity() {
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, 3, 64, 5);
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true);
        view.applyTileEntityCheckMode(true, 0);
        view.updateOrInsertTileEntity(location, 1, true);
        view.applyTileEntityVisibilityDecision(location, false, 1);
        BlockViewTransition transition = view.drainTransitions().getFirst();

        assertSame(transition.tileEntity(), PacketEventsBlockViewController.resolveCurrentTransitionState(view, transition));
    }

    @Test
    void showTransitionCannotTargetReplacementAtSameCoordinates() {
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, 3, 64, 5);
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true);
        view.applyTileEntityCheckMode(true, 0);
        view.updateOrInsertTileEntity(location, 1, false);
        view.applyTileEntityVisibilityDecision(location, true, 1);
        BlockViewTransition transition = view.drainTransitions().getFirst();

        view.removeTileEntity(location);
        view.updateOrInsertTileEntity(location, 2, false);
        TileEntityLocatable<?> replacement = view.getTrackedTileEntity(location);
        int replacementLastChecked = replacement.lastChecked();

        assertNull(PacketEventsBlockViewController.resolveCurrentTransitionState(view, transition));
        assertFalse(replacement.visible());
        assertEquals(replacementLastChecked, replacement.lastChecked());
    }
}
