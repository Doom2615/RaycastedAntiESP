package games.cubi.raycastedantiesp.core.locatables;

import games.cubi.locatables.api.BlockSpatial;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.utils.InvasivelyLinkedSWMRList;

public abstract class NettyTileEntity<PacketReplayData extends Clearable> extends InvasivelyLinkedSWMRList<NettyTileEntity<PacketReplayData>> implements TrackedTileEntity<PacketReplayData> {
    private volatile boolean visible;
    private volatile int lastChecked;
    private volatile int blockID;
    private volatile PacketReplayData extraData;

    private final int x, y, z;
    public NettyTileEntity(BlockSpatial position, boolean visible, int lastChecked, int blockID) {
        x = position.blockX();
        y = position.blockY();
        z = position.blockZ();

        this.blockID = blockID;

        this.visible = visible;
        this.lastChecked = lastChecked;
    }

    public NettyTileEntity(int x, int y, int z, boolean visible, int blockID) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.visible = visible;
        this.blockID = blockID;
        lastChecked = NEVER_CHECKED;
    }

    @Override
    public boolean visible() {
        return visible;
    }

    @Override
    public TrackedTileEntity<PacketReplayData> setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public int lastChecked() {
        return lastChecked;
    }

    @Override
    public TrackedTileEntity<PacketReplayData> setLastChecked(int lastChecked) {
        this.lastChecked = lastChecked;
        return this;
    }

    @Override
    public int blockID() {
        return blockID;
    }

    @Override
    public TrackedTileEntity<PacketReplayData> setBlockID(int blockID) {
        this.blockID = blockID;
        return this;
    }

    @Override
    public PacketReplayData extraData() {
        return extraData;
    }

    @Override
    public TrackedTileEntity<PacketReplayData> setExtraData(PacketReplayData extraData) {
        this.extraData = extraData;
        return this;
    }

    @Override
    public void clearExtraData() {
        if (extraData != null) {
            //extraData.clear(); probs not needed
            extraData = null;
        }
    }

    @Override
    public int blockX() {
        return x;
    }

    @Override
    public int blockY() {
        return y;
    }

    @Override
    public int blockZ() {
        return z;
    }

    @Override
    public void clear() {
        if (extraData != null) {
            extraData.clear();
        }
    }

    @Override
    public String toString() {
        return toStringForm();
    }
}
