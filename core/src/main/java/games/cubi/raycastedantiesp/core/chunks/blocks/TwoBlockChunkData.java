package games.cubi.raycastedantiesp.core.chunks.blocks;

import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

//functionally a bitset
public class TwoBlockChunkData extends BlockChunkData {
    final char trueBitBlock;
    final char falseBitBlock;
    final boolean trueBitBlockIsOccluding;
    final boolean falseBitBlockIsOccluding;

    private static final VarHandle LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
    // Plain reads/writes would probably be fine as occluding state being stale for a few seconds causes no issues, and tearing is a non-issue as this is a bitset, so only a single bit value matters at a time.
    // However, plain reads can technically be cached indefinitely, so opaque reads are more correct.

    private final long[] blockData;

    public TwoBlockChunkData(char trueBitBlock, char falseBitBlock, boolean trueBitBlockIsOccluding, boolean falseBitBlockIsOccluding, long[] blockData) {
        this.trueBitBlock = trueBitBlock;
        this.falseBitBlock = falseBitBlock;
        this.trueBitBlockIsOccluding = trueBitBlockIsOccluding;
        this.falseBitBlockIsOccluding = falseBitBlockIsOccluding;
        if (blockData.length != ChunkData.WORD_COUNT) {
            throw new IllegalArgumentException("blockData must have length " + ChunkData.WORD_COUNT + ", but has length " + blockData.length);
        }
        this.blockData = blockData;
    }

    @Override
    public boolean isOccludingLocal(int x, int y, int z) {
        int packed = ChunkData.pack(x, y, z);
        // >>> 6 divides the packed block index by 64 to select the containing long.
        int wordIndex = packed >>> 6;
        // 1L << packed creates a mask with only the packed block's bit set.
        long mask = 1L << packed;
        long word = (long) LONG_ARRAY_HANDLE.getOpaque(blockData, wordIndex);
        // & keeps only the target bit; non-zero means that bit is set.
        return (word & mask) != 0 ? trueBitBlockIsOccluding : falseBitBlockIsOccluding;
    }

    @Override
    public BlockChunkData setBlockID(int x, int y, int z, char blockID) {
        int packed = ChunkData.pack(x, y, z);
        // >>> 6 divides the packed block index by 64 to select the containing long. Avoids signed division; probably an unnecessary optimisation but why not
        int wordIndex = packed >>> 6;
        // 1L << packed creates a mask for only the packed block's bit.
        long mask = 1L << packed;

    }
}
