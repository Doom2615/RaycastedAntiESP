package games.cubi.raycastedantiesp.core.view.chunks;

import ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;
import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packChunkCoords;
import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packGlobalCoords;

public final class OccludingChunkSectionStore extends SWMRLong2ObjectHashTable<OccludingChunkData> implements ChunkSectionStore {
    private final BlockInfoResolver blockInfoResolver;

    public OccludingChunkSectionStore(BlockInfoResolver blockInfoResolver) {
        this.blockInfoResolver = blockInfoResolver;
    }

    @Override
    public boolean isOccluding(int x, int y, int z) {
        long key = packGlobalCoords(x, y, z);
        OccludingChunkData chunk = get(key);
        return chunk != null && chunk.isOccludingLocal(x & ChunkData.LOCAL_MASK, y & ChunkData.LOCAL_MASK, z & ChunkData.LOCAL_MASK);
    }

    @Override
    public void setBlockID(int x, int y, int z, int blockID) {
        long key = packGlobalCoords(x, y, z);
        boolean occluding = blockID != 0 && blockInfoResolver.isOccluding(blockID);
        OccludingChunkData existing = get(key);
        if (existing == null) {
            if (!occluding) {
                return;
            }
            existing = OccludingChunkData.empty();
            put(key, existing);
        }

        OccludingChunkData updated = existing.setOccluding(x & ChunkData.LOCAL_MASK, y & ChunkData.LOCAL_MASK, z & ChunkData.LOCAL_MASK, occluding);
        if (updated != existing) {
            put(key, updated);
        }
    }

    @Override
    public void replaceSection(int chunkX, int sectionY, int chunkZ, char[] packedBlockIDs) {
        if (packedBlockIDs.length != ChunkData.BLOCK_COUNT) {
            throw new IllegalArgumentException("packedBlockIDs must contain " + ChunkData.BLOCK_COUNT + " entries");
        }

        long[] occlusionData = new long[ChunkData.WORD_COUNT];
        boolean hasOccluding = false;
        for (int packed = 0; packed < ChunkData.BLOCK_COUNT; packed++) {
            int blockID = packedBlockIDs[packed];
            if (blockID != 0 && blockInfoResolver.isOccluding(blockID)) {
                occlusionData[packed >>> 6] |= 1L << packed;
                hasOccluding = true;
            }
        }

        long key = packChunkCoords(chunkX, sectionY, chunkZ);
        if (hasOccluding) {
            put(key, new OccludingChunkDataImpl(occlusionData));
        }
        else {
            remove(key);
        }
    }

    @Override
    public void replaceSectionOcclusion(int chunkX, int sectionY, int chunkZ, long[] occlusionData) {
        if (occlusionData.length != ChunkData.WORD_COUNT) {
            throw new IllegalArgumentException("occlusionData must contain " + ChunkData.WORD_COUNT + " longs");
        }

        long key = packChunkCoords(chunkX, sectionY, chunkZ);
        for (long word : occlusionData) {
            if (word != 0L) {
                put(key, new OccludingChunkDataImpl(occlusionData));
                return;
            }
        }
        remove(key);
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
}
