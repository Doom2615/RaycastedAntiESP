package games.cubi.raycastedantiesp.core.chunks;

public interface BlockChunkData extends ChunkData {
    /**
     * char is used to denote an unsigned 16-bit integer.
     */
    char getBlockID(int x, int y, int z);

    /**
     * char is used to denote an unsigned 16-bit integer.
     */
    BlockChunkData setBlockID(int x, int y, int z, char blockID);

    default OccludingChunkData setOccluding(int x, int y, int z, boolean occluding) {
        throw new UnsupportedOperationException("BlockChunkData implementations must have block data set, not just occlusion data. Use setBlockID instead.");
    }
}
