package games.cubi.raycastedantiesp.core.view.chunks;

import ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable;
import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packChunkCoords;
import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packGlobalCoords;

public final class BlockChunkSectionStore extends SWMRLong2ObjectHashTable<BlockChunkData> implements ChunkSectionStore {
    private final BlockInfoResolver blockInfoResolver;

    public BlockChunkSectionStore(BlockInfoResolver blockInfoResolver) {
        this.blockInfoResolver = blockInfoResolver;
    }

    @Override
    public boolean isOccluding(int x, int y, int z) {
        long key = packGlobalCoords(x, y, z);
        BlockChunkData chunk = get(key);
        return chunk != null && chunk.isOccludingLocal(x & ChunkData.LOCAL_MASK, y & ChunkData.LOCAL_MASK, z & ChunkData.LOCAL_MASK);
    }

    @Override
    public void setBlockID(int x, int y, int z, int blockID) {
        long key = packGlobalCoords(x, y, z);
        char storedBlockID = toStoredBlockID(blockID);
        BlockChunkData existing = get(key);
        if (existing == null) {
            if (storedBlockID == 0) {
                return;
            }
            existing = BlockChunkData.filled((char) 0, blockInfoResolver);
        }

        BlockChunkData updated = existing.setBlockID(x & ChunkData.LOCAL_MASK, y & ChunkData.LOCAL_MASK, z & ChunkData.LOCAL_MASK, storedBlockID);
        if (updated != existing || get(key) == null) {
            put(key, updated);
        }
    }

    @Override
    public void replaceSection(int chunkX, int sectionY, int chunkZ, char[] packedBlockIDs) {
        if (packedBlockIDs.length != ChunkData.BLOCK_COUNT) {
            throw new IllegalArgumentException("packedBlockIDs must contain " + ChunkData.BLOCK_COUNT + " entries");
        }
        long key = packChunkCoords(chunkX, sectionY, chunkZ);
        if (isAllAir(packedBlockIDs)) {
            remove(key);
            return;
        }
        put(key, BlockChunkData.copyOf(packedBlockIDs, blockInfoResolver));
    }

    @Override
    public void replaceSectionOcclusion(int chunkX, int sectionY, int chunkZ, long[] occlusionData) {
        throw new UnsupportedOperationException("Block chunk storage requires full block IDs for section replacement");
    }

    @Override
    public void removeSection(int chunkX, int sectionY, int chunkZ) {
        remove(packChunkCoords(chunkX, sectionY, chunkZ));
    }

    @Override
    public void removeColumn(int chunkX, int chunkZ) {
        long key = packChunkCoords(chunkX, SECTION_Y_MIN, chunkZ);
        for (int i = 0; i < SECTION_Y_COUNT; i++) {
            remove(key);
            key += SECTION_Y_INCREMENT;
        }
    }

    @Override
    public int loadedSectionCount() {
        return size();
    }

    @Override
    public void clear() {
        super.clear();
    }

    private static char toStoredBlockID(int blockID) {
        if (blockID < 0 || blockID > Character.MAX_VALUE) {
            throw new IllegalArgumentException("blockID must fit in an unsigned 16-bit value, but was " + blockID);
        }
        return (char) blockID;
    }

    private static boolean isAllAir(char[] packedBlockIDs) {
        for (char blockID : packedBlockIDs) {
            if (blockID != 0) {
                return false;
            }
        }
        return true;
    }
}
