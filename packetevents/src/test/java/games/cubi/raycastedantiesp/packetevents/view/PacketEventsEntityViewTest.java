package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsEntityReplayData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsEntityViewTest {
    @Test
    void staleWorldEpochCannotMutateReplacement() {
        AtomicInteger worldEpoch = new AtomicInteger(2);
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView(worldEpoch::getAcquire);
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        UUID entityUUID = UUID.randomUUID();
        PacketEventsEntity original = entity(1, entityUUID);
        view.insertEntity(firstWorld, original);
        int staleEpoch = worldEpoch.getAcquire();

        PacketEventsEntity replacement = entity(1, entityUUID);
        worldEpoch.setRelease(4);
        view.insertEntity(secondWorld, replacement);
        view.setVisibility(original, false, 1, staleEpoch);

        assertTrue(replacement.visible());
        assertFalse(view.hasPendingTransitions());
        assertTrue(worldEpoch.getAcquire() > staleEpoch);
    }

    @Test
    void replacedObjectCannotCommitWithinSameWorld() {
        AtomicInteger worldEpoch = new AtomicInteger(2);
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView(worldEpoch::getAcquire);
        UUID world = UUID.randomUUID();
        UUID entityUUID = UUID.randomUUID();
        PacketEventsEntity original = entity(1, entityUUID);
        view.insertEntity(world, original);
        int epoch = worldEpoch.getAcquire();

        PacketEventsEntity replacement = entity(1, entityUUID);
        view.insertEntity(world, replacement);
        view.setVisibility(original, false, 1, epoch);

        assertTrue(replacement.visible());
        assertFalse(view.hasPendingTransitions());
        assertEquals(epoch, worldEpoch.getAcquire());
    }

    @Test
    void currentEntityTransitionRetainsIdentityAndEpoch() {
        AtomicInteger worldEpoch = new AtomicInteger(2);
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView(worldEpoch::getAcquire);
        UUID world = UUID.randomUUID();
        PacketEventsEntity entity = entity(1, UUID.randomUUID());
        view.insertEntity(world, entity);
        int epoch = worldEpoch.getAcquire();

        view.setVisibility(entity, false, 1, epoch);
        EntityViewTransition transition = view.drainTransitions().getFirst();

        assertSame(entity, transition.entity());
        assertEquals(epoch, transition.worldEpoch());
        assertFalse(entity.visible());
        assertEquals(1, entity.lastChecked());
    }

    @Test
    void transitionPublicationIncludesCommittedEntityState() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            AtomicInteger worldEpoch = new AtomicInteger(2);
            PacketEventsEntityView view = PacketEventsEntityView.createEntityView(worldEpoch::getAcquire);
            UUID world = UUID.randomUUID();
            PacketEventsEntity entity = entity(1, UUID.randomUUID());
            view.insertEntity(world, entity);
            AtomicInteger acknowledgedTick = new AtomicInteger();
            int transitions = 1_000;

            Thread producer = Thread.startVirtualThread(() -> {
                for (int tick = 1; tick <= transitions; tick++) {
                    view.setVisibility(entity, (tick & 1) == 0, tick, worldEpoch.getAcquire());
                    while (acknowledgedTick.getAcquire() != tick) {
                        Thread.onSpinWait();
                    }
                }
            });

            for (int tick = 1; tick <= transitions; tick++) {
                List<EntityViewTransition> drained;
                do {
                    drained = view.drainTransitions();
                    if (drained.isEmpty()) Thread.onSpinWait();
                } while (drained.isEmpty());

                assertEquals(1, drained.size());
                boolean expectedVisible = (tick & 1) == 0;
                assertEquals(expectedVisible ? EntityViewTransition.Type.SHOW : EntityViewTransition.Type.HIDE, drained.getFirst().type());
                assertEquals(expectedVisible, entity.visible());
                assertEquals(tick, entity.lastChecked());
                acknowledgedTick.setRelease(tick);
            }
            producer.join();
        });
    }

    @Test
    void clearingPersistentSelfEntityResetsWorldScopedState() {
        PacketEventsEntity self = PacketEventsEntity.createSelfEntity(null, 1, UUID.randomUUID());
        self.setPassengerIDs(new int[]{2});
        self.setVehicleID(3);
        self.addLeashedEntity(4);
        self.setLeashingEntity(5);
        self.setPacketReplayData(PacketEventsEntityReplayData.create());

        self.clear();

        assertNull(self.passengerIDs());
        assertEquals(-1, self.vehicleID());
        assertNull(self.leashedEntityIDsOrNull());
        assertEquals(-2, self.leashingEntity());
        assertNull(self.packetReplayData());
    }

    @Test
    void packetThreadOnlyStateRoundTripsAndProtectsLeashStorage() {
        PacketEventsEntity entity = entity(1, UUID.randomUUID());
        entity.setYaw(1.25f);
        entity.setPitch(2.5f);
        entity.setHeadYaw(3.75f);
        entity.setVelocity(4.0, 5.0, 6.0);
        entity.setOnGround(false);
        entity.setEntityData(7);
        entity.setClientVisible(false);
        entity.addLeashedEntity(8);

        assertEquals(1.25f, entity.yaw());
        assertEquals(2.5f, entity.pitch());
        assertEquals(3.75f, entity.headYaw());
        assertEquals(4.0, entity.velocityX());
        assertEquals(5.0, entity.velocityY());
        assertEquals(6.0, entity.velocityZ());
        assertFalse(entity.onGround());
        assertEquals(7, entity.entityData());
        assertFalse(entity.clientVisible());

        int[] leashSnapshot = entity.leashedEntityIDsOrNull();
        assertArrayEquals(new int[]{8}, leashSnapshot);
        leashSnapshot[0] = 9;
        assertArrayEquals(new int[]{8}, entity.leashedEntityIDsOrNull());
    }

    private static PacketEventsEntity entity(int entityID, UUID entityUUID) {
        return new PacketEventsEntity(null, 1, 2, 3, entityID, entityUUID, false, null, true);
    }
}
