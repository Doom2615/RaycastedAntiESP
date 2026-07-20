package games.cubi.raycastedantiesp.paper.utils;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import games.cubi.raycastedantiesp.core.utils.VarHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.lang.invoke.VarHandle;
import java.util.function.IntSupplier;

public class PaperTicker extends PaperListener implements IntSupplier {
    private volatile int currentTick; private static final VarHandle CURRENT_TICK = VarHandler.get(PaperTicker.class, "currentTick", int.class);

    public PaperTicker() {}

    @Override
    public int getAsInt() {
        return (int) CURRENT_TICK.getOpaque(this);
    }

    @EventHandler(priority = EventPriority.LOWEST) //Runs first
    public void serverTickStartEvent(ServerTickStartEvent event) {
        int current = (int) CURRENT_TICK.getOpaque(this); //this runs on the paper tick thread so there's no race possible
        CURRENT_TICK.setOpaque(this, current + 1);
    }
}
