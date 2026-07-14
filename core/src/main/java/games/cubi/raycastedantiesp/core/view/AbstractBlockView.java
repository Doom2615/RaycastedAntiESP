package games.cubi.raycastedantiesp.core.view;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.map.SWMRInt2ObjectHashTable;
import games.cubi.locatables.BlockLocatable;
import games.cubi.locatables.implementations.ImmutableBlockLocatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable;
import games.cubi.raycastedantiesp.core.locatables.NettyTileEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.utils.InvasivelyLinkedSWMRList;
import games.cubi.raycastedantiesp.core.view.chunks.BlockChunkSectionStore;
import games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore;
import games.cubi.raycastedantiesp.core.view.chunks.OccludingChunkSectionStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static games.cubi.raycastedantiesp.core.chunks.ChunkData.packUncheckedGuarded;

public abstract class AbstractBlockView<R extends Clearable, T extends NettyTileEntity<R>> implements BlockView {
    public static final int CHUNK_SIZE = 16;
    public static final int LOCAL_MASK = CHUNK_SIZE - 1;

    private final ChunkSectionStore chunks;
    private final boolean trackAllBlocks;
    /**
     * The int key represents a packed chunk column bucket, with (signed) 16 bits allocated for x and z respectively. While chunk columns over 60k chunks apart may overlap, this is unlikely to ever actually occur.
     * <p> {@code NettyTileEntity<R>} is an {@link InvasivelyLinkedSWMRList}, so all tile entities in the column chain from the head entry.
     * <p> Since direct access to this object outside {@link AbstractBlockView} is impossible, a marker head object is skipped, and head removal is handled separately.
     * **/
    private final SWMRInt2ObjectHashTable<NettyTileEntity<R>> knownTileEntitiesByColumnBucket = new SWMRInt2ObjectHashTable<>();
    private final MultiThreadedQueue<BlockViewTransition> transitions = new MultiThreadedQueue<>();
    private volatile UUID trackedWorld;
    // Bit 0 is enabled; higher bits are a generation. The generation prevents enabled -> disabled -> enabled ABA from
    // accepting an old raycast and also tags transitions that may be drained after their originating mode was replaced.
    private volatile long tileEntityCheckModeToken;

    protected AbstractBlockView(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks) {
        Logger.requireNonNull(blockInfoResolver, "blockInfoResolver was null", 1, AbstractBlockView.class);
        this.trackAllBlocks = trackAllBlocks;
        this.chunks = trackAllBlocks
                ? new BlockChunkSectionStore(blockInfoResolver)
                : new OccludingChunkSectionStore(blockInfoResolver);
    }

    @Deprecated
    protected abstract T createTrackedTileEntity(BlockLocatable location, int blockID, boolean visible);

    protected abstract T createTrackedTileEntity(UUID world, int x, int y, int z, int blockID);


    public boolean isBlockOccluding(UUID world, int x, int y, int z) {
        if (!isTrackedWorld(world)) {
            return false;
        }

        return chunks.isOccluding(x, y, z);
    }

    @Override
    public boolean isBlockOccluding(BlockLocatable location) {
        return location != null
                && location.world() != null
                && isBlockOccluding(location.world(), location.blockX(), location.blockY(), location.blockZ());
    }

    public int loadedChunkCount() {
        return chunks.loadedSectionCount();
    }

    @Override
    public T updateOrInsertTileEntity(BlockLocatable location, int blockID, boolean visibleIfNew) {
        if (location == null || !ensureTrackedWorld(location.world())) {
            return null;
        }
        SWMRInt2ObjectHashTable<NettyTileEntity<R>> map = knownTileEntitiesByColumnBucket;
        int bucketKey = packColumnBucket(location.chunkX(), location.chunkZ());
        NettyTileEntity<R> head = map.get(bucketKey);
        T tileEntity = findTileEntityInBucket(head, location);
        if (tileEntity == null) {
            tileEntity = createTrackedTileEntity(location, blockID, visibleIfNew);
            if (tileEntity == null) {
                Logger.error("createTrackedTileEntity returned null", 3, AbstractBlockView.class);
                return null;
            }
            if (head == null) {
                map.put(bucketKey, tileEntity);
            } else {
                head.linkAfter(tileEntity);
            }
        }
        tileEntity.clearExtraData();
        tileEntity.setBlockID(blockID);
        return tileEntity;
    }

    @Override
    public void removeTileEntity(BlockLocatable location) {
        if (location == null || !isTrackedWorld(location.world())) {
            return;
        }
        SWMRInt2ObjectHashTable<NettyTileEntity<R>> map = knownTileEntitiesByColumnBucket;
        int bucketKey = packColumnBucket(location.chunkX(), location.chunkZ());
        NettyTileEntity<R> head = map.get(bucketKey);
        T tileEntity = findTileEntityInBucket(head, location);
        if (tileEntity != null) {
            removeNode(map, bucketKey, head, tileEntity);
        }
    }

    @Override
    public T getTrackedTileEntity(BlockLocatable location) {
        if (location == null || !isTrackedWorld(location.world())) {
            return null;
        }
        return findTileEntityInBucket(knownTileEntitiesByColumnBucket.get(packColumnBucket(location.chunkX(), location.chunkZ())), location);
    }

    @Override
    public T getTrackedTileEntity(ImmutableBlockLocatable location) {
        if (location == null || !isTrackedWorld(location.world())) {
            return null;
        }
        return getTrackedTileEntity((BlockLocatable) location);
    }

    @Override
    public boolean isVisible(BlockLocatable location, int currentTick) {
        if (location == null || !isTrackedWorld(location.world())) {
            return true;
        }
        T state = getTrackedTileEntity(location);
        return state == null || state.visible();
    }

    @Override
    public void applyTileEntityVisibilityDecision(BlockLocatable location, boolean visible, int currentTick) {
        if (location == null || !isTrackedWorld(location.world())) {
            return;
        }
        T existing = getTrackedTileEntity(location);
        if (existing == null) {
            return;
        }
        commitTileEntityVisibilityDecision(existing, existing.visible(), visible, currentTick, tileEntityCheckModeToken);
    }

    private void commitTileEntityVisibilityDecision(T tileEntity, boolean currentVisibility, boolean shouldBeVisible, int currentTick, long modeToken) {
        if (!modeEnabled(modeToken)) {
            shouldBeVisible = true;
        }
        if (currentVisibility != shouldBeVisible) {
            transitions.add(new BlockViewTransition(
                    shouldBeVisible ? BlockViewTransition.Type.SHOW : BlockViewTransition.Type.HIDE,
                    tileEntity,
                    modeToken
            ));
        }
        tileEntity.setVisible(shouldBeVisible);
        tileEntity.setLastChecked(currentTick);
    }

    @Override
    public void recordOutboundTileEntityVisibility(TileEntityLocatable<?> tileEntity, boolean visible) {
        if (tileEntity != null) {
            tileEntity.setVisible(visible);
            tileEntity.setLastChecked(TileEntityLocatable.NEVER_CHECKED);
        }
    }

    @Override
    public void applyTileEntityCheckMode(boolean enabled, int currentTick) {
        long current = tileEntityCheckModeToken;
        if (modeEnabled(current) == enabled) {
            return;
        }
        // Drop the enabled bit, increment the generation, then restore bit 0 below only for enabled mode.
        long next = ((current >>> 1) + 1L) << 1;
        if (enabled) {
            forEachTileEntity(tileEntity -> tileEntity.setLastChecked(TileEntityLocatable.NEVER_CHECKED));
            tileEntityCheckModeToken = next | 1L;
            return;
        }

        tileEntityCheckModeToken = next;
        forEachTileEntity(tileEntity -> {
            boolean visible = tileEntity.visible();
            if (!visible) {
                @SuppressWarnings("unchecked") T typed = (T) tileEntity;
                commitTileEntityVisibilityDecision(typed, false, true, currentTick, next);
            }
        });
    }

    @Override
    public long tileEntityCheckModeToken() {
        return tileEntityCheckModeToken;
    }

    @Override
    public boolean isCurrentEnabledTileEntityMode(long modeToken) {
        return modeEnabled(modeToken) && tileEntityCheckModeToken == modeToken;
    }

    @Override
    public Collection<BlockLocatable> getKnownTileEntities() {
        ArrayList<BlockLocatable> snapshot = new ArrayList<>();
        forEachTileEntity(snapshot::add);
        return List.copyOf(snapshot);
    }

    @Override
    public int forEachNeedingRecheck(int recheckTicks, int currentTick, Consumer<BlockLocatable> action) {
        return knownTileEntitiesByColumnBucket.forEachValueSummed(head -> {
            int processed = 0;
            for (NettyTileEntity<R> tileEntity = head; tileEntity != null; tileEntity = tileEntity.nextAcquire()) {
                int lastChecked = tileEntity.lastChecked();
                if (tileEntity.visible() && lastChecked != TileEntityLocatable.NEVER_CHECKED && (recheckTicks < 0 || currentTick - lastChecked < recheckTicks)) {
                    continue;
                }
                action.accept(tileEntity);
                processed++;
            }
            return processed;
        });
    }

    @Override
    public int updateVisibilityForEachNeedingRecheck(int recheckTicks, int currentTick, long modeToken, VisibilityResolver action) {
        if (!isCurrentEnabledTileEntityMode(modeToken)) {
            return 0;
        }
        return knownTileEntitiesByColumnBucket.forEachValueSummed(head -> {
            int processed = 0;
            for (NettyTileEntity<R> tileEntity = head; tileEntity != null; tileEntity = tileEntity.nextAcquire()) {
                boolean currentVisibility = tileEntity.visible();
                int lastChecked = tileEntity.lastChecked();
                if (currentVisibility && lastChecked != TileEntityLocatable.NEVER_CHECKED && (recheckTicks < 0 || currentTick - lastChecked < recheckTicks)) {
                    continue;
                }
                byte shouldBeVisible = action.setVisible(tileEntity);
                if (shouldBeVisible != VisibilityResolver.SKIPPED && isCurrentEnabledTileEntityMode(modeToken)) {
                    @SuppressWarnings("unchecked") T typed = (T) tileEntity;
                    commitTileEntityVisibilityDecision(typed, currentVisibility, shouldBeVisible == VisibilityResolver.SHOW, currentTick, modeToken);
                }
                processed++;
            }
            return processed;
        });
    }

    @Override
    public boolean hasPendingTransitions() {
        return !transitions.isEmpty();
    }

    @Override
    public List<BlockViewTransition> drainTransitions() {
        List<BlockViewTransition> drained = new ArrayList<>();
        BlockViewTransition transition;
        while ((transition = transitions.poll()) != null) {
            drained.add(transition);
        }
        return drained;
    }

    @Override
    public void upsertBlock(UUID world, int x, int y, int z, int blockID) {
        if (!ensureTrackedWorld(world)) {
            return;
        }
        chunks.setBlockID(x, y, z, blockID);
    }

    @Override
    public void removeChunk(UUID world, int chunkX, int chunkZ) {
        if (!isTrackedWorld(world)) {
            return;
        }
        chunks.removeColumn(chunkX, chunkZ);
        removeTileEntitiesInChunk(chunkX, chunkZ);
    }

    @Override
    public void removeChunkSection(UUID world, int chunkX, int chunkY, int chunkZ) {
        if (!isTrackedWorld(world)) {
            return;
        }
        chunks.removeSection(chunkX, chunkY, chunkZ);
    }

    @Override
    public void pruneTileEntitiesAbsentFromIncludedChunkSections(UUID world, int chunkX, int chunkZ, int minimumSectionY, boolean[] includedSections, long[][] presentBySection) {
        if (!isTrackedWorld(world) || includedSections.length != presentBySection.length) {
            return;
        }
        SWMRInt2ObjectHashTable<NettyTileEntity<R>> map = knownTileEntitiesByColumnBucket;
        int bucketKey = packColumnBucket(chunkX, chunkZ);
        // The wrapped key selects a collision bucket; full chunk coordinates below establish ownership.
        NettyTileEntity<R> head = map.get(bucketKey);
        NettyTileEntity<R> currentHead = head;
        for (NettyTileEntity<R> current = head; current != null; ) {
            NettyTileEntity<R> next = current.nextAcquire();
            if (current.chunkX() == chunkX && current.chunkZ() == chunkZ) {
                int sectionIndex = current.chunkY() - minimumSectionY;
                // A section omitted from the packet is not authoritative, so its previous tracking state is retained.
                if (sectionIndex >= 0 && sectionIndex < includedSections.length && includedSections[sectionIndex]) {
                    long[] present = presentBySection[sectionIndex];
                    int packed = packUncheckedGuarded(current.blockX(), current.blockY(), current.blockZ());
                    // Guarded packing masks full coordinates to local x/y/z. Null means this included section has no managed tiles.
                    if (present == null || (present[packed >>> 6] & 1L << packed) == 0L) {
                        currentHead = removeNode(map, bucketKey, currentHead, current);
                    }
                }
            }
            current = next;
        }
    }

    @Override
    public void replaceChunkSection(UUID world, int chunkX, int chunkY, int chunkZ, BlockChunkData data) {
        if (!ensureTrackedWorld(world)) {
            return;
        }
        chunks.replaceSection(chunkX, chunkY, chunkZ, data);
    }

    @Override
    public void replaceChunkSectionOcclusion(UUID world, int chunkX, int chunkY, int chunkZ, OccludingChunkData data) {
        if (trackAllBlocks) {
            throw new UnsupportedOperationException("Block chunk storage requires full block IDs for section replacement");
        }
        if (!ensureTrackedWorld(world)) {
            return;
        }
        chunks.replaceSectionOcclusion(chunkX, chunkY, chunkZ, data);
    }

    @Override
    public void clear() {
        chunks.clear();
        knownTileEntitiesByColumnBucket.clear();
        transitions.clear();
        trackedWorld = null;
    }

    private boolean isTrackedWorld(UUID world) {
        UUID current = trackedWorld;
        return world != null && current != null && current.equals(world);
    }

    private boolean ensureTrackedWorld(UUID world) {
        if (world == null) {
            return false;
        }
        UUID current = trackedWorld;
        if (current != null && current.equals(world)) {
            return true;
        }
        clearTrackedState();
        trackedWorld = world;
        return true;
    }

    private void clearTrackedState() {
        chunks.clear();
        knownTileEntitiesByColumnBucket.clear();
        transitions.clear();
    }

    private void removeTileEntitiesInChunk(int chunkX, int chunkZ) {
        SWMRInt2ObjectHashTable<NettyTileEntity<R>> map = knownTileEntitiesByColumnBucket;
        int bucketKey = packColumnBucket(chunkX, chunkZ);
        NettyTileEntity<R> head = map.get(bucketKey);
        if (head == null) {
            return;
        }

        boolean collision = false;
        for (NettyTileEntity<R> current = head; current != null; current = current.nextAcquire()) {
            if (current.chunkX() != chunkX || current.chunkZ() != chunkZ) {
                collision = true;
                break;
            }
        }
        if (!collision) {
            // Removing the map entry publishes removal of the whole bucket. Keep the old chain intact so readers that
            // already acquired its head can finish their weakly-consistent traversal; detached nodes are never reused.
            map.remove(bucketKey, head);
            return;
        }

        Logger.info("Chunk collision occured, failing back to linear scan for tile entity removal in chunk " + chunkX + ", " + chunkZ, 5, AbstractBlockView.class); // I want to see if this occurs frequently

        // Wrapped-key collisions are rare. Remove matching nodes individually while preserving other columns.
        NettyTileEntity<R> currentHead = head;
        for (NettyTileEntity<R> current = head; current != null; ) {
            NettyTileEntity<R> next = current.nextAcquire();
            if (current.chunkX() == chunkX && current.chunkZ() == chunkZ) {
                currentHead = removeNode(map, bucketKey, currentHead, current);
            }
            current = next;
        }
    }

    private NettyTileEntity<R> removeNode(SWMRInt2ObjectHashTable<NettyTileEntity<R>> map, int bucketKey, NettyTileEntity<R> head, NettyTileEntity<R> node) {
        if (node != head) {
            node.unlink();
            return head;
        }
        NettyTileEntity<R> successor = node.nextAcquire();
        if (successor == null) {
            map.remove(bucketKey, node);
        } else {
            map.put(bucketKey, successor);
        }
        node.detachHeadWriterOnly();
        return successor;
    }

    @SuppressWarnings("unchecked")
    private T findTileEntityInBucket(NettyTileEntity<R> bucketHead, BlockLocatable location) {
        for (NettyTileEntity<R> current = bucketHead; current != null; current = current.nextAcquire()) {
            if (current.blockX() == location.blockX() && current.blockY() == location.blockY() && current.blockZ() == location.blockZ()) {
                return (T) current;
            }
        }
        return null;
    }

    private void forEachTileEntity(Consumer<NettyTileEntity<R>> action) {
        knownTileEntitiesByColumnBucket.forEachValue(head -> {
            for (NettyTileEntity<R> current = head; current != null; current = current.nextAcquire()) {
                action.accept(current);
            }
        });
    }

    private static int packColumnBucket(int chunkX, int chunkZ) {
        // Keep the low 16 bits of X, then put the low 16 bits of Z in the upper half of the int.
        // Full coordinates on every tile disambiguate columns whose wrapped keys collide.
        return (chunkX & 0xFFFF) | (chunkZ & 0xFFFF) << 16;
    }

    private static boolean modeEnabled(long modeToken) {
        return (modeToken & 1L) != 0L;
    }
}
