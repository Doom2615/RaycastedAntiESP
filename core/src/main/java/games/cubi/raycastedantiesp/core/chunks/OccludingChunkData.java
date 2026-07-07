package games.cubi.raycastedantiesp.core.chunks;

import org.jetbrains.annotations.Range;

import java.util.BitSet;

public sealed interface OccludingChunkData permits ChunkData, OccludingChunkDataImpl {
    /**
     * The parameters here are local coordinates within the chunk, so they should be in the range [0, 15].
     * @return true if the block at the given coordinates is occluding, false otherwise.
     */
    boolean isOccluding(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z);

    /**
     * The parameters here are local coordinates within the chunk, so they should be in the range [0, 15].
     */
    void setOccluding(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z, boolean occluding);

    default boolean get(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z) {
        return isOccluding(x, y, z);
    }

    default void set(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z, boolean value) {
        setOccluding(x, y, z, value);
    }

    static OccludingChunkData empty() {
        return new OccludingChunkDataImpl();
    }

    static OccludingChunkData copyOf(BitSet bitSet) {
        long[] words = bitSet.toLongArray();
        if (words.length != ChunkData.WORD_COUNT) {
            long[] resized = new long[ChunkData.WORD_COUNT];
            System.arraycopy(words, 0, resized, 0, Math.min(words.length, resized.length));
            words = resized;
        }
        return new OccludingChunkDataImpl(words);
    }
}
