package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.locatables.api.BlockSpatial;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.locatables.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.AbstractBlockView;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsTileEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;

import java.util.function.IntSupplier;

public class PacketEventsBlockView extends AbstractBlockView<PacketEventsTileEntityReplayData, PacketEventsTileEntity> {
    public PacketEventsBlockView(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks, IntSupplier worldEpochSupplier) {
        super(blockInfoResolver, trackAllBlocks, worldEpochSupplier);
    }

    @Override
    protected PacketEventsTileEntity createTrackedTileEntity(BlockSpatial position, char blockID, boolean visible) {
        return new PacketEventsTileEntity(position, visible, TrackedTileEntity.NEVER_CHECKED, blockID);
    }
}
