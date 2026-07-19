package games.cubi.raycastedantiesp.core.view;

import games.cubi.raycastedantiesp.core.locatables.TrackedEntity;
import games.cubi.locatables.api.Spatial;
import games.cubi.raycastedantiesp.core.locatables.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface EntityView<T extends TrackedEntity<?, ?>>  extends Clearable {
    void insertEntity(UUID world, T entity);

    void removeEntity(int entityID, int currentTick);

    void removeEntity(int entityID);

    void removeEntity(UUID entityUUID, int currentTick);

    T getEntity(UUID entityUUID);

    T getEntity(int entityID);

    Spatial getPosition(UUID entityUUID);

    int getEntityID(UUID entityUUID);

    boolean exists(UUID entityUUID);

    boolean exists(int entityID);

    @Deprecated
    boolean isVisible(UUID entityUUID, int currentTick);

    boolean isVisible(UUID entityUUID);

    boolean isVisible(int entityID);

    void setVisibility(UUID entityUUID, boolean visible, int currentTick);

    void setVisibility(NettyEntityLocatable<?,?> entity, boolean visible, int currentTick);

    Collection<UUID> getKnownEntities();

    int[] getKnownEntityIDs();

    /**
     * Iterates currently tracked entities that should be visibility-checked.
     *
     * @return number of entities passed to {@code action}.
     */
    int forEachNeedingRecheck(int recheckTicks, int currentTick, Consumer<UUID> action);

    /**
     * Iterates currently tracked entities that should be visibility-checked.
     *
     * @return number of entities passed to {@code action}, or 0 if {@code countingActuallyNeeded} is false.
     */
    int forEachNeedingRecheckEntity(int recheckTicks, int currentTick, boolean countingActuallyNeeded, Consumer<NettyEntityLocatable<?,?>> action);

    boolean hasPendingTransitions();

    List<EntityViewTransition> drainTransitions();

    boolean isPlayerView();

    default <T> T cast() {
        return (T) this;
    }

    String getStringDataForDebugging();

    interface Factory {
        EntityView<?> createEntityView();
    }
}
