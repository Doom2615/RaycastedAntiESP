package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import games.cubi.locatables.implementations.MutableBlockLocatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

abstract class AbstractChunkParser<D> implements ChunkParser {
    private static final TileEntity[] EMPTY_TILE_ENTITIES = new TileEntity[0];

    protected final BlockInfoResolver blockInfoResolver;
    private final boolean mutatePackets;
    private final IntUnaryOperator hiddenBlockID;
    private final boolean modernHeightmaps;

    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets, IntUnaryOperator hiddenBlockID) {
        this.blockInfoResolver = blockInfoResolver;
        this.mutatePackets = mutatePackets;
        this.hiddenBlockID = hiddenBlockID;
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        this.modernHeightmaps = version.isNewerThanOrEquals(ServerVersion.V_1_21_5);
    }

    protected abstract @Nullable D parseSection(Chunk_v1_18 section);

    protected abstract void storeSection(BlockView blockView, UUID world, int chunkX, int sectionY, int chunkZ, D data);

    @Override
    public final @Nullable Column parse(BlockView blockView, UUID world, Column column, int minimumSectionY) {
        BaseChunk[] sections = column.getChunks();
        boolean[] includedSections = new boolean[sections.length];
        long[][] managedBySection = new long[sections.length][];
        MutableBlockLocatable key = new MutableBlockLocatable(world);
        boolean mutatedBlock = false;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            BaseChunk rawSection = sections[sectionIndex];
            if (rawSection == null) {
                continue;
            }
            Chunk_v1_18 section = (Chunk_v1_18) rawSection;
            includedSections[sectionIndex] = true;
            int sectionY = minimumSectionY + sectionIndex;
            D data = parseSection(section);
            if (data == null) {
                // Null means the section is empty, so we can skip it.
                blockView.removeChunkSection(world, column.getX(), sectionY, column.getZ());
            } else {
                storeSection(blockView, world, column.getX(), sectionY, column.getZ(), data);
            }

            if (!sectionMayContainManagedTiles(section)) {
                continue;
            }
            long[] managed = null;
            for (int localY = 0; localY < ChunkData.CHUNK_SIZE; localY++) {
                for (int localZ = 0; localZ < ChunkData.CHUNK_SIZE; localZ++) {
                    for (int localX = 0; localX < ChunkData.CHUNK_SIZE; localX++) {
                        int blockID = section.getBlockId(localX, localY, localZ);
                        if (!blockInfoResolver.isTileEntity(blockID)) {
                            continue;
                        }
                        if (managed == null) {
                            managed = new long[ChunkData.WORD_COUNT];
                            managedBySection[sectionIndex] = managed;
                        }
                        int packed = ChunkData.packUncheckedGuarded(localX, localY, localZ);
                        // >>> 6 selects the containing 64-bit word; Java masks the shift distance to the bit within that word.
                        managed[packed >>> 6] |= 1L << packed;
                        int blockX = (column.getX() << 4) + localX;
                        int blockY = (sectionY << 4) + localY;
                        int blockZ = (column.getZ() << 4) + localZ;
                        key.set(blockX, blockY, blockZ);
                        blockView.updateOrInsertTileEntity(key, blockID, !mutatePackets);
                        if (!mutatePackets) {
                            blockView.recordOutboundTileEntityVisibility(key, true);
                        } else if (!blockView.isVisible(key, 0)) {
                            section.set(localX, localY, localZ, hiddenBlockID.applyAsInt(blockY));
                            mutatedBlock = true;
                        }
                    }
                }
            }
        }

        blockView.pruneTileEntitiesAbsentFromIncludedChunkSections(world, column.getX(), column.getZ(), minimumSectionY, includedSections, managedBySection);
        TileEntity[] sourceTileEntities = column.getTileEntities();
        TileEntity[] filtered = sourceTileEntities;
        boolean stripped = false;
        int retainedCount = 0;
        TileEntity[] retained = null;
        if (sourceTileEntities != null) {
            for (int index = 0; index < sourceTileEntities.length; index++) {
                TileEntity tileEntity = sourceTileEntities[index];
                boolean strip = processTileEntity(blockView, world, column.getX(), column.getZ(), sections, minimumSectionY, includedSections, managedBySection, tileEntity, key);
                if (mutatePackets && strip) {
                    stripped = true;
                    if (retained == null && index > 0) {
                        retained = new TileEntity[sourceTileEntities.length];
                        System.arraycopy(sourceTileEntities, 0, retained, 0, index);
                        retainedCount = index;
                    }
                } else if (stripped) {
                    if (retained == null) {
                        retained = new TileEntity[sourceTileEntities.length];
                    }
                    retained[retainedCount++] = tileEntity;
                }
            }
            if (stripped) {
                filtered = retained == null ? EMPTY_TILE_ENTITIES : Arrays.copyOf(retained, retainedCount);
            }
        }

        return mutatePackets && (mutatedBlock || stripped) ? copyColumn(column, filtered) : null;
    }

    private boolean sectionMayContainManagedTiles(Chunk_v1_18 section) {
        var palette = section.getChunkData().palette;
        if (!(palette instanceof GlobalPalette)) {
            for (int index = 0; index < palette.size(); index++) {
                if (blockInfoResolver.isTileEntity(palette.idToState(index))) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean processTileEntity(BlockView blockView, UUID world, int chunkX, int chunkZ, BaseChunk[] sections, int minimumSectionY,
                                      boolean[] includedSections, long[][] managedBySection, TileEntity tileEntity, MutableBlockLocatable key) {
        int blockY = tileEntity.getY();
        int blockX = (chunkX << 4) + tileEntity.getX();
        int blockZ = (chunkZ << 4) + tileEntity.getZ();

        int localX = blockX & ChunkData.LOCAL_MASK;
        int localZ = blockZ & ChunkData.LOCAL_MASK;
        int sectionIndex = (blockY >> 4) - minimumSectionY;
        key.set(blockX, blockY, blockZ);
        if (sectionIndex >= 0 && sectionIndex < includedSections.length && includedSections[sectionIndex]) {
            long[] managed = managedBySection[sectionIndex];
            int packed = ChunkData.packUncheckedGuarded(localX, blockY, localZ);
            if (managed != null && (managed[packed >>> 6] & 1L << packed) != 0L) {
                TileEntityLocatable<PacketEventsTileEntityReplayData> state = tracked(blockView, key);
                if (state != null) {
                    replayData(state).setBlockEntityData(BlockEntityTypes.getById(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), tileEntity.getType()), tileEntity.getNBT());
                    return !state.visible();
                }
                Logger.warning("Managed chunk block entity was missing from tracked state at " + blockX + "," + blockY + "," + blockZ, 3, AbstractChunkParser.class);
                return true;
            }
        }

        if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
            Logger.warning("Received chunk block entity outside included section data at " + blockX + "," + blockY + "," + blockZ, 3, AbstractChunkParser.class);
            return true;
        }
        int blockID = sections[sectionIndex].getBlockId(localX, blockY & ChunkData.LOCAL_MASK, localZ);
        if (blockInfoResolver.hasBlockEntityData(blockID) && !blockInfoResolver.isTileEntity(blockID)) {
            return false;
        }
        Logger.warning("Received invalid or uncached chunk block entity at " + blockX + "," + blockY + "," + blockZ, 3, AbstractChunkParser.class);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static TileEntityLocatable<PacketEventsTileEntityReplayData> tracked(BlockView blockView, MutableBlockLocatable location) {
        return (TileEntityLocatable<PacketEventsTileEntityReplayData>) blockView.getTrackedTileEntity(location);
    }

    private static PacketEventsTileEntityReplayData replayData(TileEntityLocatable<PacketEventsTileEntityReplayData> tileEntity) {
        PacketEventsTileEntityReplayData replayData = tileEntity.extraData();
        if (replayData == null) {
            replayData = new PacketEventsTileEntityReplayData();
            tileEntity.setExtraData(replayData);
        }
        return replayData;
    }

    private Column copyColumn(Column column, TileEntity[] tileEntities) {
        TileEntity[] replacement = tileEntities == null ? EMPTY_TILE_ENTITIES : tileEntities;
        if (column.hasBiomeData()) {
            int[] ints = column.getBiomeDataInts();
            byte[] bytes = column.getBiomeDataBytes();
            if (ints.length >= bytes.length) {
                return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), replacement, column.getHeightMaps(), ints);
            }
            return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), replacement, column.getHeightMaps(), bytes);
        }
        if (modernHeightmaps) {
            return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), replacement, column.getHeightmaps());
        }
        return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), replacement, column.getHeightMaps());
    }
}
