package games.cubi.raycastedantiesp.core.chunks;

public interface BlockChunkData extends ChunkData {
    /**
     * char is used to denote an unsigned 16-bit integer.
     */
    char getBlockID(int x, int y, int z);

    /**
     * char is used to denote an unsigned 16-bit integer.
     */
    void setBlockID(int x, int y, int z, char blockID);
}
