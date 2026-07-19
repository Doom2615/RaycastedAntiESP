package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.locatables.api.BlockSpatial;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.locatables.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.AbstractBlockView;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsTileEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;

public class PacketEventsBlockView extends AbstractBlockView<PacketEventsTileEntityReplayData, PacketEventsTileEntity> {
    public PacketEventsBlockView(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks) {
        super(blockInfoResolver, trackAllBlocks);
    }

    @Override
    protected PacketEventsTileEntity createTrackedTileEntity(BlockSpatial position, int blockID, boolean visible) {
        return new PacketEventsTileEntity(position, visible, TrackedTileEntity.NEVER_CHECKED, blockID);
    }
}
