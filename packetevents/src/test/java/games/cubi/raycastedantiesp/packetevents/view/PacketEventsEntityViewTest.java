package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsEntityReplayData;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static PacketEventsEntity entity(int entityID, UUID entityUUID) {
        return new PacketEventsEntity(null, 1, 2, 3, entityID, entityUUID, false, null, true);
    }
}
