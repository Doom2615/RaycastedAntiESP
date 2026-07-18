package games.cubi.raycastedantiesp.core.view.chunks;

import games.cubi.locatables.api.BlockLocatable;
import games.cubi.locatables.implementations.ImmutableBlockLocatable;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;
import games.cubi.raycastedantiesp.core.chunks.blocks.CharArrayBlockChunkData;
import games.cubi.raycastedantiesp.core.locatables.NettyTileEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.AbstractBlockView;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSectionStoreTest {
    private static final BlockInfoResolver ODD_OCCLUDING = new BlockInfoResolver() {
        @Override
        public boolean isOccluding(int blockStateID) {
            return (blockStateID & 1) == 1;
        }

        @Override
        public boolean isTileEntity(int blockStateID) {
            return blockStateID == 99;
        }

        @Override
        public boolean hasBlockEntityData(int blockStateID) {
            return isTileEntity(blockStateID);
        }
    };

    @Test
    void occlusionOnlyAndBlockStoresAgreeForIdenticalBlockIds() {
        char[] section = new char[ChunkData.BLOCK_COUNT];
        section[ChunkData.packUncheckedGuarded(0, 0, 0)] = 1;
        section[ChunkData.packUncheckedGuarded(15, 15, 15)] = 2;
        section[ChunkData.packUncheckedGuarded(3, 2, 1)] = 3;

        ChunkSectionStore occlusionOnly = new OccludingChunkSectionStore(ODD_OCCLUDING);
        ChunkSectionStore blockAware = new BlockChunkSectionStore(ODD_OCCLUDING);
        occlusionOnly.replaceSectionOcclusion(-2, -1, 3, occlusionOf(section));
        blockAware.replaceSection(-2, -1, 3, BlockChunkData.copyOfStates(packed -> section[packed], ODD_OCCLUDING));

        assertTrue(occlusionOnly.isOccluding((-2 << 4), (-1 << 4), (3 << 4)));
        assertTrue(blockAware.isOccluding((-2 << 4), (-1 << 4), (3 << 4)));
        assertFalse(occlusionOnly.isOccluding((-2 << 4) + 15, (-1 << 4) + 15, (3 << 4) + 15));
        assertFalse(blockAware.isOccluding((-2 << 4) + 15, (-1 << 4) + 15, (3 << 4) + 15));
        assertTrue(occlusionOnly.isOccluding((-2 << 4) + 3, (-1 << 4) + 2, (3 << 4) + 1));
        assertTrue(blockAware.isOccluding((-2 << 4) + 3, (-1 << 4) + 2, (3 << 4) + 1));
    }

    @Test
    void blockStoreStoresRepresentationUpgradesFromMutation() {
        BlockChunkSectionStore store = new BlockChunkSectionStore(ODD_OCCLUDING);
        long key = ChunkSectionStore.packChunkCoords(0, 0, 0);

        for (int packed = 1; packed <= 256; packed++) {
            store.setBlockID(ChunkData.unpackX(packed), ChunkData.unpackY(packed), ChunkData.unpackZ(packed), packed);
        }

        assertInstanceOf(CharArrayBlockChunkData.class, store.get(key));
        assertTrue(store.isOccluding(ChunkData.unpackX(255), ChunkData.unpackY(255), ChunkData.unpackZ(255)));
        assertFalse(store.isOccluding(ChunkData.unpackX(256), ChunkData.unpackY(256), ChunkData.unpackZ(256)));
    }

    @Test
    void storesReplaceRemoveColumnAndClearSignedSectionCoordinates() {
        ChunkSectionStore store = new BlockChunkSectionStore(ODD_OCCLUDING);
        char[] section = new char[ChunkData.BLOCK_COUNT];
        section[ChunkData.packUncheckedGuarded(1, 2, 3)] = 1;

        BlockChunkData data = BlockChunkData.copyOfStates(packed -> section[packed], ODD_OCCLUDING);
        store.replaceSection(-1, ChunkSectionStore.SECTION_Y_MIN, 3, data);
        store.replaceSection(-1, ChunkSectionStore.SECTION_Y_MAX, 3, data);
        store.replaceSection(-1, -2, 3, data);
        store.replaceSection(4, 5, -6, data);

        assertEquals(4, store.loadedSectionCount());
        assertTrue(store.isOccluding((-1 << 4) + 1, (-2 << 4) + 2, (3 << 4) + 3));
        assertTrue(store.isOccluding((4 << 4) + 1, (5 << 4) + 2, (-6 << 4) + 3));

        store.removeSection(-1, -2, 3);
        assertEquals(3, store.loadedSectionCount());
        assertFalse(store.isOccluding((-1 << 4) + 1, (-2 << 4) + 2, (3 << 4) + 3));

        store.removeColumn(-1, 3);
        assertEquals(1, store.loadedSectionCount());

        store.removeColumn(4, -6);
        assertEquals(0, store.loadedSectionCount());

        store.replaceSection(-1, -2, 3, data);
        store.clear();
        assertEquals(0, store.loadedSectionCount());
    }

    @Test
    void blockViewWorldSwitchClearsOldStateAndDifferentWorldReadsMiss() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, true);
        UUID worldA = UUID.randomUUID();
        UUID worldB = UUID.randomUUID();
        ImmutableBlockLocatable locationA = new ImmutableBlockLocatable(worldA, 1, 2, 3);

        view.applyTileEntityCheckMode(true, 0);
        view.upsertBlock(worldA, 1, 2, 3, 1);
        view.updateOrInsertTileEntity(locationA, 99, true);
        view.applyTileEntityVisibilityDecision(locationA, false, 1);

        Assertions.assertTrue(view.isBlockOccluding(worldA, 1, 2, 3));
        Assertions.assertFalse(view.isBlockOccluding(worldB, 1, 2, 3));
        Assertions.assertTrue(view.hasPendingTransitions());

        view.removeChunk(worldB, 0, 0);
        view.removeChunkSection(worldB, 0, 0, 0);
        view.removeTileEntity(new ImmutableBlockLocatable(worldB, 1, 2, 3));

        Assertions.assertTrue(view.isBlockOccluding(worldA, 1, 2, 3));
        assertNotNull(view.getTrackedTileEntity(locationA));
        Assertions.assertTrue(view.hasPendingTransitions());

        view.upsertBlock(worldB, 17, 2, 3, 1);

        Assertions.assertFalse(view.isBlockOccluding(worldA, 1, 2, 3));
        assertNull(view.getTrackedTileEntity(locationA));
        Assertions.assertFalse(view.hasPendingTransitions());
        Assertions.assertTrue(view.isBlockOccluding(worldB, 17, 2, 3));
    }

    @Test
    void blockViewTileMapRepopulatesAfterClear() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        ImmutableBlockLocatable first = new ImmutableBlockLocatable(firstWorld, 1, 64, 2);
        ImmutableBlockLocatable second = new ImmutableBlockLocatable(secondWorld, 17, 65, 18);

        view.updateOrInsertTileEntity(first, 99, true);
        assertNotNull(view.getTrackedTileEntity(first));

        view.clear();
        assertNull(view.getTrackedTileEntity(first));
        assertEquals(0, view.forEachNeedingRecheck(-1, 0, ignored -> {}));

        view.updateOrInsertTileEntity(second, 99, true);
        assertNotNull(view.getTrackedTileEntity(second));
        assertEquals(1, view.forEachNeedingRecheck(-1, 0, ignored -> {}));
    }

    @Test
    void occlusionOnlyReplacementStoresAndRemovesBitsetSections() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        long[] occlusionData = new long[ChunkData.WORD_COUNT];
        int packed = ChunkData.packUncheckedGuarded(1, 2, 3);
        occlusionData[packed >>> 6] |= 1L << packed;

        view.replaceChunkSectionOcclusion(world, -1, -2, 3, new OccludingChunkDataImpl(occlusionData));

        Assertions.assertEquals(1, view.loadedChunkCount());
        Assertions.assertTrue(view.isBlockOccluding(world, (-1 << 4) + 1, (-2 << 4) + 2, (3 << 4) + 3));
        Assertions.assertFalse(view.isBlockOccluding(world, (-1 << 4) + 2, (-2 << 4) + 2, (3 << 4) + 3));

        view.removeChunkSection(world, -1, -2, 3);

        Assertions.assertEquals(0, view.loadedChunkCount());
        Assertions.assertFalse(view.isBlockOccluding(world, (-1 << 4) + 1, (-2 << 4) + 2, (3 << 4) + 3));
    }

    @Test
    void blockStorageRejectsOcclusionOnlyReplacement() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, true);

        assertThrows(UnsupportedOperationException.class, () ->
                view.replaceChunkSectionOcclusion(UUID.randomUUID(), 0, 0, 0, OccludingChunkData.empty())
        );
    }

    @Test
    void wrappedColumnCollisionsRemainSemanticallyIndependent() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable first = new ImmutableBlockLocatable(world, 1, 64, 2);
        ImmutableBlockLocatable sameChunk = new ImmutableBlockLocatable(world, 2, 68, 3);
        ImmutableBlockLocatable collidingX = new ImmutableBlockLocatable(world, (65_536 << 4) + 1, 65, 2);
        ImmutableBlockLocatable collidingZ = new ImmutableBlockLocatable(world, 1, 66, (65_536 << 4) + 2);
        ImmutableBlockLocatable otherBucket = new ImmutableBlockLocatable(world, 17, 67, 2);

        view.updateOrInsertTileEntity(first, 99, true);
        view.updateOrInsertTileEntity(collidingX, 99, true);
        view.updateOrInsertTileEntity(sameChunk, 99, true);
        view.updateOrInsertTileEntity(collidingZ, 99, true);
        view.updateOrInsertTileEntity(otherBucket, 99, true);

        AtomicInteger visited = new AtomicInteger();
        assertEquals(5, view.forEachNeedingRecheck(-1, 0, ignored -> visited.incrementAndGet()));
        assertEquals(5, visited.get());

        assertNotNull(view.getTrackedTileEntity(first));
        assertNotNull(view.getTrackedTileEntity(sameChunk));
        assertNotNull(view.getTrackedTileEntity(collidingX));
        assertNotNull(view.getTrackedTileEntity(collidingZ));
        assertNotNull(view.getTrackedTileEntity(otherBucket));

        view.removeChunk(world, first.chunkX(), first.chunkZ());
        assertNull(view.getTrackedTileEntity(first));
        assertNull(view.getTrackedTileEntity(sameChunk));
        assertNotNull(view.getTrackedTileEntity(collidingX));
        assertNotNull(view.getTrackedTileEntity(collidingZ));

        view.removeTileEntity(collidingX);
        assertNull(view.getTrackedTileEntity(collidingX));
        assertNotNull(view.getTrackedTileEntity(collidingZ));
    }

    @Test
    void homogeneousChunkRemovalKeepsAcquiredChainTraversable() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable first = new ImmutableBlockLocatable(world, 1, 64, 2);
        ImmutableBlockLocatable second = new ImmutableBlockLocatable(world, 2, 65, 3);
        ImmutableBlockLocatable third = new ImmutableBlockLocatable(world, 3, 66, 4);

        view.updateOrInsertTileEntity(first, 99, true);
        view.updateOrInsertTileEntity(second, 99, true);
        view.updateOrInsertTileEntity(third, 99, true);
        TestTileEntity acquiredHead = view.getTrackedTileEntity(first);

        view.removeChunk(world, first.chunkX(), first.chunkZ());

        assertNull(view.getTrackedTileEntity(first));
        assertNull(view.getTrackedTileEntity(second));
        assertNull(view.getTrackedTileEntity(third));
        assertEquals(0, view.forEachNeedingRecheck(-1, 0, ignored -> {}));
        int acquiredCount = 0;
        for (NettyTileEntity<TestExtraData> current = acquiredHead; current != null; current = current.nextAcquire()) {
            acquiredCount++;
        }
        assertEquals(3, acquiredCount);
    }

    @Test
    void modeGenerationRejectsInFlightHideAndForcesFirstEnabledCheck() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, 1, 64, 2);
        view.applyTileEntityCheckMode(true, 0);
        view.updateOrInsertTileEntity(location, 99, true);
        long staleToken = view.tileEntityCheckModeToken();

        int processed = view.updateVisibilityForEachNeedingRecheck(-1, 1, staleToken, ignored -> {
            view.applyTileEntityCheckMode(false, 1);
            return BlockView.VisibilityResolver.HIDE;
        });

        assertEquals(1, processed);
        assertTrue(view.isVisible(location, 1));

        view.applyTileEntityCheckMode(true, 2);
        long enabledToken = view.tileEntityCheckModeToken();
        AtomicInteger checks = new AtomicInteger();
        view.updateVisibilityForEachNeedingRecheck(-1, 2, enabledToken, ignored -> {
            checks.incrementAndGet();
            return BlockView.VisibilityResolver.SHOW;
        });
        assertEquals(1, checks.get());
    }

    @Test
    void authoritativeSectionPruningRemovesAbsentAndOutOfRangeTilesWhilePreservingCollisions() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable retained = new ImmutableBlockLocatable(world, 1, 1, 1);
        ImmutableBlockLocatable removed = new ImmutableBlockLocatable(world, 2, 2, 2);
        ImmutableBlockLocatable emptySection = new ImmutableBlockLocatable(world, 3, 17, 3);
        ImmutableBlockLocatable outsideColumn = new ImmutableBlockLocatable(world, 4, 33, 4);
        ImmutableBlockLocatable collision = new ImmutableBlockLocatable(world, (65_536 << 4) + 1, 2, 1);
        view.updateOrInsertTileEntity(retained, 99, true);
        view.updateOrInsertTileEntity(removed, 99, true);
        view.updateOrInsertTileEntity(emptySection, 99, true);
        view.updateOrInsertTileEntity(outsideColumn, 99, true);
        view.updateOrInsertTileEntity(collision, 99, true);

        long[][] present = new long[2][];
        present[0] = new long[ChunkData.WORD_COUNT];
        int packed = ChunkData.packUncheckedGuarded(retained.blockX(), retained.blockY(), retained.blockZ());
        present[0][packed >>> 6] |= 1L << packed;
        view.pruneTileEntitiesAbsentFromChunkSections(world, 0, 0, 0, 2, present);

        assertNotNull(view.getTrackedTileEntity(retained));
        assertNull(view.getTrackedTileEntity(removed));
        assertNull(view.getTrackedTileEntity(emptySection));
        assertNull(view.getTrackedTileEntity(outsideColumn));
        assertNotNull(view.getTrackedTileEntity(collision));
    }

    @Test
    void nullSectionPresenceRemovesAllTilesInTheAuthoritativeColumn() {
        TestBlockView view = new TestBlockView(ODD_OCCLUDING, false);
        UUID world = UUID.randomUUID();
        ImmutableBlockLocatable first = new ImmutableBlockLocatable(world, 1, 1, 1);
        ImmutableBlockLocatable second = new ImmutableBlockLocatable(world, 2, 17, 2);
        view.updateOrInsertTileEntity(first, 99, true);
        view.updateOrInsertTileEntity(second, 99, true);

        view.pruneTileEntitiesAbsentFromChunkSections(world, 0, 0, 0, 2, null);

        assertNull(view.getTrackedTileEntity(first));
        assertNull(view.getTrackedTileEntity(second));
    }

    private static OccludingChunkData occlusionOf(char[] section) {
        long[] words = new long[ChunkData.WORD_COUNT];
        for (int packed = 0; packed < section.length; packed++) {
            if (ODD_OCCLUDING.isOccluding(section[packed])) {
                words[packed >>> 6] |= 1L << packed;
            }
        }
        return new OccludingChunkDataImpl(words);
    }

    private static final class TestBlockView extends AbstractBlockView<TestExtraData, TestTileEntity> {
        private TestBlockView(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks) {
            super(blockInfoResolver, trackAllBlocks);
        }

        @Override
        protected TestTileEntity createTrackedTileEntity(BlockLocatable location, int blockID, boolean visible) {
            return new TestTileEntity(location, visible, blockID);
        }

        @Override
        protected TestTileEntity createTrackedTileEntity(UUID world, int x, int y, int z, int blockID) {
            return new TestTileEntity(world, x, y, z, blockID);
        }
    }

    private static final class TestTileEntity extends NettyTileEntity<TestExtraData> {
        private TestTileEntity(BlockLocatable location, boolean visible, int blockID) {
            super(location, visible, games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable.NEVER_CHECKED, blockID);
        }

        private TestTileEntity(UUID world, int x, int y, int z, int blockID) {
            super(world, x, y, z, false, blockID);
        }

        @Override
        public boolean strictlyEquals(Object other) {
            return equals(other);
        }
    }

    private static final class TestExtraData implements Clearable {
        @Override
        public void clear() {
        }
    }
}
