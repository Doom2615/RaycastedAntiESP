package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;

public interface ParticleSpawner {

    enum Colour {
        RED, GREEN, BLUE,
    }

    void spawnParticleAt(Locatable locatable, Colour color);
}
