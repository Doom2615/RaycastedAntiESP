package games.cubi.raycastedantiesp.core.chunks;

// Literally just because OccludingChunkData as a base interface is weird, especially for instances of BlockChunkData.
public non-sealed interface ChunkData extends OccludingChunkData{
    static final int CHUNK_SIZE = 16;
    static final int LOCAL_MASK = CHUNK_SIZE - 1;
    static final int BLOCK_COUNT = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
    static final int WORD_COUNT = BLOCK_COUNT / Long.SIZE;

    static int pack(int x, int y, int z) {
        return (x & LOCAL_MASK) | ((y & LOCAL_MASK) << 4) | ((z & LOCAL_MASK) << 8);
    }

    static int unpackX(int packed) {
        return packed & LOCAL_MASK;
    }

    static int unpackY(int packed) {
        return (packed >> 4) & LOCAL_MASK;
    }

    static int unpackZ(int packed) {
        return (packed >> 8) & LOCAL_MASK;
    }

    static void checkPacked(int packed) {
        if (packed < 0 || packed >= BLOCK_COUNT) {
            throw new IndexOutOfBoundsException("Packed block position out of chunk section range: " + packed);
        }
    }
}
