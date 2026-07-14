package games.cubi.raycastedantiesp.core.view;

import games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable;

// Retains the originating object identity so a delayed transition cannot target a replacement at the same coordinates.
public record BlockViewTransition(Type type, TileEntityLocatable<?> tileEntity, long modeToken) {
    public enum Type {
        SHOW,
        HIDE,
    }
}
