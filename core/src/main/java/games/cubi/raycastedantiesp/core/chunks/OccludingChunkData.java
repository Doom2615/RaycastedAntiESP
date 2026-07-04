package games.cubi.raycastedantiesp.core.chunks;

import org.jetbrains.annotations.Range;

public sealed interface OccludingChunkData permits ChunkData, OccludingChunkData.Impl {
    /**
     * The parameters here are local coordinates within the chunk, so they should be in the range [0, 15].
     * @return true if the block at the given coordinates is occluding, false otherwise.
     */
    boolean isOccluding(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z);

    final class Impl implements OccludingChunkData {
        private final long[] occlusionData;

        public Impl(long[] occlusionData) {
            this.occlusionData = occlusionData;
        }

        @Override
        public boolean isOccluding(int x, int y, int z) {
            return false;// occlusionData[x][y][z];
        }

        public void setOccluding(int x, int y, int z, boolean occluding) {
            //occlusionData[x][y][z] = occluding;
        }
    }
}
