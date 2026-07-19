package games.cubi.raycastedantiesp.core.locatables;

import games.cubi.locatables.api.ImmutableSpatial;
import games.cubi.locatables.api.MutableFloatingSpatial;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-player platform-independent representation of an entity
 */
public interface TrackedEntity<EntityType, PacketReplayData> extends MutableFloatingSpatial {

    int entityID();

    UUID entityUUID();

    boolean visible();
    TrackedEntity<?, ?> setVisible(boolean visible);

    int lastChecked();
    TrackedEntity<?, ?> setLastChecked(int lastChecked);

    boolean clientVisible();
    TrackedEntity<?, ?> setClientVisible(boolean clientVisible);

    boolean isSelfEntity();

    float yaw();
    TrackedEntity<?, ?> setYaw(float yaw);

    float pitch();
    TrackedEntity<?, ?> setPitch(float pitch);

    float headYaw();
    TrackedEntity<?, ?> setHeadYaw(float headYaw);

    double velocityX();
    double velocityY();
    double velocityZ();
    TrackedEntity<?, ?> setVelocity(double velocityX, double velocityY, double velocityZ);

    boolean onGround();
    TrackedEntity<?, ?> setOnGround(boolean onGround);

    EntityType entityType();

    int entityData();
    TrackedEntity<?, ?> setEntityData(int entityData);

    int[] passengerIDs();
    TrackedEntity<?, ?> setPassengerIDs(int[] passengerIDs);

    void setVehicleID(int vehicleID);
    int vehicleID();

    void addLeashedEntity(int leashedEntityID);
    void removeLeashedEntity(int leashedEntityID);
    int@Nullable[] leashedEntityIDsOrNull();
    int leashingEntity();
    void setLeashingEntity(int leashingEntityID);

    PacketReplayData packetReplayData();
    TrackedEntity<?, ?> setPacketReplayData(PacketReplayData packetReplayData);

    ImmutableSpatial getOffsetPosition();

    /**
     * For use when the player disconnects, clears all data.
     */
    void clear();

    default <T> T cast() {
        return (T) this;
    }
}
