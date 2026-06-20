package games.cubi.raycastedantiesp.core.engine;

import games.cubi.locatables.BlockLocatable;
import games.cubi.locatables.Locatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.DebugConfig;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.raycast.ParticleSpawner;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

public abstract class SimpleEngine implements Engine {
    private static final long SLOW_TICK_NANOS = 40 * 1_000_000L;

    private final ConfigManager config;
    private final ParticleSpawner particleSpawner;
    private final IntSupplier currentTickSupplier;
    private final AtomicInteger tickThreadsRunning = new AtomicInteger(0);
    private final AtomicInteger runningTick = new AtomicInteger(-1);

    //private final AtomicBoolean tickPendingOrRunning = new AtomicBoolean(false);

    private final AtomicBoolean tickPending = new AtomicBoolean(false);
    private final AtomicBoolean tickRunning = new AtomicBoolean(false);
    private final AtomicLong tickNanos = new AtomicLong(0);
    private final AsyncRunner asyncRunner;
    private final TimingStats timingStats = new TimingStats();
    private volatile boolean timingStatsWereEnabled = false;

    public SimpleEngine(ConfigManager config, ParticleSpawner particleSpawner, IntSupplier currentTickSupplier, AsyncRunner asyncRunner) {
        this.config = config;
        this.particleSpawner = particleSpawner;
        this.currentTickSupplier = currentTickSupplier;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Sets tickPending to true if both it and tickRunning are false, and returns true if it was able to set it to true. There is however a small possibility of this returning true incorrectly due to it not actually being atomic.
     * @return true if it was able to set tickPending to true, false otherwise. If it returns false, the caller should not schedule a tick, as one is already pending or running.
     */
    public boolean markTickRunning() {
        // If tickRunning is true, we can't run a tick, so return false
        if (tickRunning.get()) {
            timingStats.recordSkipped(-1, System.nanoTime());
            return false;
        }

        if (tickPending.compareAndSet(false, true)) {
            return true;
        }
        timingStats.recordSkipped(-1, System.nanoTime());
        return false;
    }

    @Override
    public void tick(int scheduledTick, long scheduledNanos) {
        if (!tickPending.get()) {
            // This means that this tick was dispatched but not marked as such, or was unmarked at some point.
            Logger.warning("Tick " + scheduledTick + " was dispatched but not marked as pending. This suggests a race condition. Please report this on our GitHub or Discord.", 5, SimpleEngine.class);
        }
        boolean runTick = true;
        int startTick = currentTickSupplier.getAsInt();
        while (runTick) {
            tickPending.set(false);
            if (!tickRunning.compareAndSet(false, true)) {
                // This means there is already a tick running, so we should exit
                Logger.info("Tick " + scheduledTick + " is pending but another tick is already running. Exiting now.", 6, SimpleEngine.class);
                return;
            }
            dispatchTick(scheduledTick, startTick, scheduledNanos);

            int latestTick = currentTickSupplier.getAsInt();
            if (latestTick > startTick) {
                // If running behind, don't yield the thread, just run the next tick immediately
                Logger.warning("Tick thread completed tick #" + startTick + " after tick #" + latestTick + " had already begun. Starting next tick immediately instead of yielding thread to scheduler. This is probably safe to ignore but may suggest that your server is overloaded.", 5);
                startTick = latestTick;
                scheduledNanos = System.nanoTime();
                scheduledTick = latestTick;

                if (tickRunning.get()) {
                    // This means that there is already a tick running, so we should exit
                    Logger.info("Tick finished behind but another thread had already begun ticking the next tick.", 5, SimpleEngine.class);
                    return;
                }
            }
            else runTick = false;
        }
    }

    private void dispatchTick(int scheduledTick, int startTick, long scheduledNanos) {
        int threads = config.getEngineConfig().simpleConfig().asyncProcessingThreads();
        if (threads < 1) threads = 1;

        DebugConfig debugConfig = config.getDebugConfig();
        boolean recordTimings = debugConfig.recordTimings();
        if (recordTimings) {
            timingStatsWereEnabled = true;
        } else if (timingStatsWereEnabled) {
            // Timing stats were enabled last time but not anymore
            timingStats.reset();
            timingStatsWereEnabled = false;
        }

        long startNanos = System.nanoTime();

        int tickAlreadyRunning = runningTick.get();
        // First guard for the current tick already being processed (can happen if the previous tick ran overtime and thus didn't yield the thread)
        if (scheduledTick == tickAlreadyRunning) {
            if (recordTimings) {
                String aggregateReport = timingStats.recordSkipped(threads, startNanos);
                logAggregateReport(aggregateReport);
            }
            Logger.info("RaycastedAntiESP is already processing this tick; skipping duplicate same-tick attempt."
                    + " scheduledTick=" + scheduledTick
                    + " currentServerTick=" + startTick
                    + " currentRunningTick=" + tickAlreadyRunning, 6, SimpleEngine.class);
            return;
        }

        int runningThreads = tickThreadsRunning.compareAndExchange(0, threads);
        // Now guard for previous tick still running
        if (runningThreads != 0) {
            long queueNanos = Math.max(0, startNanos - scheduledNanos);
            Logger.warning("RaycastedAntiESP is still ticking from the last tick! Skipping this tick to avoid concurrent modification issues."
                    + " scheduledTick=" + scheduledTick
                    + " currentServerTick=" + currentTickSupplier.getAsInt()
                    + " currentRunningTick=" + tickAlreadyRunning
                    + " runningThreads=" + runningThreads
                    + " timeSpentInQueue=" + TickTimingFormatter.formatMillis(queueNanos) + "ms", 5, SimpleEngine.class);
            if (recordTimings) {
                String aggregateReport = timingStats.recordSkipped(threads, startNanos);
                logAggregateReport(aggregateReport);
            }
            return;
        }

        TickTimings timings = null;
        boolean handedOffToSubTick = false;
        try {
            tickNanos.set(startNanos);

            final int currentTick = startTick;
            runningTick.set(currentTick);
            Collection<PlayerData> allPlayers = PlayerRegistry.getInstance().getAllPlayerData();

            EntityConfig entityConfig = config.getEntityConfig();
            PlayerConfig playerConfig = config.getPlayerConfig();
            TileEntityConfig tileEntityConfig = config.getTileEntityConfig();
            if (recordTimings) {
                timings = new TickTimings(scheduledTick, scheduledNanos, currentTick, startNanos, threads, allPlayers.size());
            }
            /*
            Logger.debug("Tick #" + currentTick);
            if (currentTick % 1200 == 0) {
                Logger.debug("Printing player data");
                for (PlayerData playerData : allPlayers) {
                    Logger.debug("Player " + playerData.getPlayerUUID() + " location=" + playerData.ownLocation());
                    Logger.debug("EntityView:" + playerData.entityView().getStringDataForDebugging());
                    Logger.debug("PlayerView:" + playerData.playerView().getStringDataForDebugging());
                }
            }*/

            // If only one thread is configured, just use the current async thread to avoid the overhead of scheduling tasks and context switching.
            if (threads == 1) {
                handedOffToSubTick = true;
                subTick(new ArrayList<>(allPlayers), entityConfig, playerConfig, tileEntityConfig, debugConfig, currentTick, timings);
                return;
            }

            List<List<PlayerData>> batches = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                batches.add(new ArrayList<>());
            }

            int index = 0;
            for (PlayerData playerData : allPlayers) {
                batches.get(index++ % threads).add(playerData);
            }

            TickTimings tickTimings = timings; // needs to be effectively-final for lambda
            int scheduledBatches = 0;
            try {
                for (List<PlayerData> batch : batches) {
                    asyncRunner.runNow(() -> subTick(batch, entityConfig, playerConfig, tileEntityConfig, debugConfig, currentTick, tickTimings));
                    scheduledBatches++;
                }
                handedOffToSubTick = true;
            }
            finally {
                if (scheduledBatches < threads) {
                    tickThreadsRunning.addAndGet(-(threads - scheduledBatches));
                    handedOffToSubTick = scheduledBatches > 0;
                }
            }
        }
        finally {
            if (!handedOffToSubTick) {
                tickThreadsRunning.set(0);
                finaliseTick(startTick);
                Logger.error("An error occurred during tick scheduling before handing off to sub-tick processing. Resetting tickThreadsRunning to 0 to avoid deadlock. Current tick: " + currentTickSupplier.getAsInt(), 2, SimpleEngine.class);
            }
        }
    }

    private void subTick(List<PlayerData> batch, EntityConfig entityConfig, PlayerConfig playerConfig, TileEntityConfig tileEntityConfig, DebugConfig debugConfig, int currentTick, TickTimings timings) {
        TickTimingBatch batchTimings = timings == null ? TickTimingBatchNoOp.INSTANCE : new TickTimingBatch();
        long batchStartNanos = batchTimings.startBatch();
        try {
            processTickForPlayers(batch, entityConfig, playerConfig, tileEntityConfig, debugConfig.showDebugParticles(), currentTick, batchTimings);
        }
        finally {
            if (timings != null) {
                timings.recordBatch(batchTimings, batchTimings.elapsedSince(batchStartNanos));
            }
            int threadsRemaining = tickThreadsRunning.decrementAndGet();
            if (threadsRemaining < 0) {
                Logger.error("tickThreadsRunning went below 0! This should never happen. Resetting to 0 to avoid further issues.", 2, SimpleEngine.class);
                tickThreadsRunning.set(0);
                finaliseTick(currentTick);
            }
            if (threadsRemaining == 0) {
                try {
                    long completionNanos = System.nanoTime();
                    if (timings != null) {
                        TickTimingSnapshot snapshot = timings.snapshot(currentTickSupplier.getAsInt(), completionNanos);
                        String aggregateReport = timingStats.recordCompleted(snapshot, completionNanos);
                        if (snapshot.wallNanos() > SLOW_TICK_NANOS) {
                            Logger.warning(snapshot.toSlowTickMessage(), 5, SimpleEngine.class);
                        }
                        logAggregateReport(aggregateReport);
                    } else {
                        long elapsedNanos = completionNanos - tickNanos.get();
                        if (elapsedNanos > SLOW_TICK_NANOS) {
                            Logger.warning("Tick completed in " + (elapsedNanos / 1_000_000.0) + " ms. If you see this warning frequently, consider reducing the raycasting load by adjusting the configuration.", 5, SimpleEngine.class);
                        }
                    }
                } finally {
                    finaliseTick(currentTick);
                }
            }
        }
    }

    private void finaliseTick(int currentTick) {
        runningTick.compareAndSet(currentTick, -1);
        if (!tickRunning.getAndSet(false)) {
            // This should never happen as it means the tick was marked as completed before processing was completed
            Logger.warning("tickRunning was false when completing tick! This should never happen. Please report this on our GitHub or Discord.", 5, SimpleEngine.class);
        }
    }

    private void processTickForPlayers(List<PlayerData> playerDataList, EntityConfig entityConfig, PlayerConfig playerConfig, TileEntityConfig tileEntityConfig,
                                       boolean debugParticles, int currentTick, TickTimingBatch timings) {

        for (PlayerData playerData : playerDataList) {
            playerData.nettyData().markPendingPostSpawnTasksForEviction();
            if (playerData.hasBypassPermission()) {
                timings.incrementBypassSkippedPlayers();
                continue;
            }

            BlockView blockView = playerData.blockView();

            Locatable playerLocation = playerData.ownLocation();
            if (playerLocation == null) {
                timings.incrementNullLocationSkippedPlayers();
                continue;
            }
            timings.incrementProcessedPlayers();

            if (entityConfig.enabled()) {
                long sectionStartNanos = timings.startEntitySection();
                checkEntities(playerData, playerLocation, entityConfig, debugParticles, blockView, currentTick, timings);
                timings.finishEntitySection(sectionStartNanos);
            }
            if (playerConfig.enabled()) {
                long sectionStartNanos = timings.startPlayerSection();
                checkPlayers(playerData, playerLocation, playerConfig, debugParticles, blockView, currentTick, timings);
                timings.finishPlayerSection(sectionStartNanos);
            }
            if (tileEntityConfig.enabled()) {
                long sectionStartNanos = timings.startTileSection();
                checkTileEntities(playerData, playerLocation, tileEntityConfig, debugParticles, blockView, currentTick, timings);
                timings.finishTileSection(sectionStartNanos);
            }
        }
    }

    private void checkEntities(PlayerData player, Locatable playerLocation, EntityConfig entityConfig, boolean debugParticles, BlockView blockView, int currentTick, TickTimingBatch timings) {
        EntityView<?> entityView = player.entityView();

        Collection<UUID> needingRecheck = entityView.getNeedingRecheck(entityConfig.getVisibleRecheckIntervalTicks(), currentTick);
        timings.addEntityChecked(needingRecheck.size());
        for (UUID entityUUID : needingRecheck) {
            boolean wasVisible = entityView.isVisible(entityUUID, currentTick);
            Locatable entityLocation = entityView.getLocation(entityUUID);
            if (entityLocation == null) {
                timings.incrementEntityNullTargets();
                Logger.debug("SimpleEngine.checkEntities skipped-null-location viewer=" + player.getPlayerUUID()
                        + " target=" + entityUUID
                        + " wasVisible=" + wasVisible
                        + " tick=" + currentTick);
                continue;
            }
            timings.incrementEntityRaycasts();
            boolean canSee = RaycastUtil.raycast(player, playerLocation, entityLocation, entityConfig.getMaxOccludingCount(), entityConfig.getAlwaysShowRadius(), entityConfig.getRaycastRadius(), debugParticles, blockView, 1, particleSpawner);
            entityView.setVisibility(entityUUID, canSee, currentTick);
        }
    }

    private void checkPlayers(PlayerData player, Locatable playerLocation, PlayerConfig playerConfig, boolean debugParticles, BlockView blockView, int currentTick, TickTimingBatch timings) {
        EntityView<?> playerView = player.playerView();

        Collection<UUID> needingRecheck = playerView.getNeedingRecheck(playerConfig.getVisibleRecheckIntervalTicks(), currentTick);
        timings.addPlayerChecked(needingRecheck.size());
        for (UUID otherPlayerUUID : needingRecheck) {
            boolean wasVisible = playerView.isVisible(otherPlayerUUID, currentTick);
            Locatable otherPlayerLocation = playerView.getLocation(otherPlayerUUID);
            if (otherPlayerLocation == null) {
                timings.incrementPlayerNullTargets();
                Logger.debug("SimpleEngine.checkPlayers skipped-null-location viewer=" + player.getPlayerUUID()
                        + " target=" + otherPlayerUUID
                        + " wasVisible=" + wasVisible
                        + " tick=" + currentTick);
                continue;
            }
            timings.incrementPlayerRaycasts();
            boolean canSee = RaycastUtil.raycast(player, playerLocation, otherPlayerLocation, playerConfig.getMaxOccludingCount(), playerConfig.getAlwaysShowRadius(), playerConfig.getRaycastRadius(), debugParticles, blockView, 1, particleSpawner);
            playerView.setVisibility(otherPlayerUUID, canSee, currentTick);
        }
    }

    private void checkTileEntities(PlayerData player, Locatable playerLocation, TileEntityConfig tileEntityConfig, boolean debugParticles, BlockView blockView, int currentTick, TickTimingBatch timings) {
        Collection<BlockLocatable> needingRecheck = blockView.getNeedingRecheck(tileEntityConfig.getVisibleRecheckIntervalTicks(), currentTick);
        timings.addTileChecked(needingRecheck.size());
        for (BlockLocatable tileEntityLocation : needingRecheck) {
            if (tileEntityLocation.world() == null || !tileEntityLocation.world().equals(playerLocation.world())) {
                timings.incrementTileWorldSkipped();
                continue;
            }

            if (playerLocation.distanceSquared(tileEntityLocation) > (double) tileEntityConfig.getRaycastRadius() * tileEntityConfig.getRaycastRadius()) {
                timings.incrementTileRadiusSkipped();
                blockView.setVisibility(tileEntityLocation, false, currentTick);
                continue;
            }
            timings.incrementTileRaycasts();
            boolean canSee = RaycastUtil.raycast(player, playerLocation, tileEntityLocation, tileEntityConfig.getMaxOccludingCount() + 1, tileEntityConfig.getAlwaysShowRadius(), tileEntityConfig.getRaycastRadius(), debugParticles, blockView, 1, particleSpawner);
            blockView.setVisibility(tileEntityLocation, canSee, currentTick);
        }
    }

    private static void logAggregateReport(String aggregateReport) {
        if (aggregateReport != null) {
            Logger.info(aggregateReport, 4, SimpleEngine.class);
        }
    }
}
