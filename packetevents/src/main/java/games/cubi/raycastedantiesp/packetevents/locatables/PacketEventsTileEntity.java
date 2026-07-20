package games.cubi.raycastedantiesp.packetevents.locatables;

import games.cubi.locatables.api.BlockSpatial;
import games.cubi.raycastedantiesp.core.locatables.NettyTileEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;

public class PacketEventsTileEntity extends NettyTileEntity<PacketEventsTileEntityReplayData> {
    public PacketEventsTileEntity(BlockSpatial position, boolean visible, int lastChecked, int blockID) {
        super(position, visible, lastChecked, blockID);
    }
}
