package games.cubi.raycastedantiesp.core.locatables;

import games.cubi.locatables.api.ImmutableBlockLocatable;
import games.cubi.raycastedantiesp.core.utils.Clearable;

public interface TileEntityLocatable<T> extends ImmutableBlockLocatable, Clearable {
    int NEVER_CHECKED = Integer.MIN_VALUE;

    boolean visible();
    TileEntityLocatable<T> setVisible(boolean visible);

    int lastChecked();
    TileEntityLocatable<T> setLastChecked(int lastChecked);

    int blockID();
    TileEntityLocatable<T> setBlockID(int blockID);

    T extraData();
    TileEntityLocatable<T> setExtraData(T extraData);
    void clearExtraData();
}
