package games.cubi.raycastedantiesp.core.locatables;

import games.cubi.locatables.api.ImmutableBlockSpatial;
import games.cubi.raycastedantiesp.core.utils.Clearable;

public interface TrackedTileEntity<T> extends ImmutableBlockSpatial, Clearable {
    int NEVER_CHECKED = Integer.MIN_VALUE;

    boolean visible();
    TrackedTileEntity<T> setVisible(boolean visible);

    int lastChecked();
    TrackedTileEntity<T> setLastChecked(int lastChecked);

    int blockID();
    TrackedTileEntity<T> setBlockID(char blockID);

    T extraData();
    TrackedTileEntity<T> setExtraData(T extraData);
    void clearExtraData();
}
