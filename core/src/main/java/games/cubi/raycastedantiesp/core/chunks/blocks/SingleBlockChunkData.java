package games.cubi.raycastedantiesp.core.chunks.blocks;

import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import org.jetbrains.annotations.Range;

// If it is air, just use a null reference
public class SingleBlockChunkData implements BlockChunkData {
    final char blockID;
    final boolean occluding;

    public SingleBlockChunkData(char blockID, boolean occluding) {
        this.blockID = blockID;
        this.occluding = occluding;
    }

    @Override
    public char getBlockID(int x, int y, int z) {
        return blockID;
    }

    @Override
    public BlockChunkData setBlockID(int x, int y, int z, char blockID) {

    }

    @Override
    public boolean isOccludingLocal(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z) {
        return occluding;
    }

    @Override
    public OccludingChunkData setOccluding(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z, boolean occluding) {

    }
}
