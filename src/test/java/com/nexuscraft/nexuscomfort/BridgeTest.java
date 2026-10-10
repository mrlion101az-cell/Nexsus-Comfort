package com.nexuscraft.nexuscomfort;

import com.nexuscraft.nexusworldwar.WorldWarApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** The soft link to NexusWorldWar. */
public final class BridgeTest {

    public static void main(String[] args) {
        Kit.reset();
        AtomicLong clock = new AtomicLong(Env.T0);
        WarBridge bridge = new WarBridge(clock::get);
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        expect(!bridge.connected() && !bridge.fighting(ann), "installed but not running: nobody is fighting");

        AtomicLong later = new AtomicLong(Env.T0);
        WarBridge missing = new WarBridge(later::get, "com.nope.NotInstalled");
        expect(!missing.connected() && !missing.fighting(ann), "a plugin that is not installed at all: nobody is fighting");
        later.addAndGet(WarBridge.RETRY_MS);
        expect(!missing.connected(), "and asking again later still says no");

        Set<UUID> fighters = new HashSet<>();
        boolean[] broken = {false};
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        Bukkit.getServicesManager().register(WorldWarApi.class, new WorldWarApi() {
            @Override
            public boolean warActive() {
                return true;
            }

            @Override
            public boolean isFighter(UUID player) {
                if (broken[0]) {
                    throw new IllegalStateException("boom");
                }
                return fighters.contains(player);
            }

            @Override
            public boolean hasJoined(UUID player) {
                return false;
            }
        }, plugin, ServicePriority.Normal);
        expect(!bridge.fighting(ann), "it is not looked for again straight away");
        clock.addAndGet(WarBridge.RETRY_MS - 1);
        expect(!bridge.connected(), "not a moment early");
        clock.addAndGet(1);
        expect(bridge.connected(), "but it is found when the wait is over");
        fighters.add(ann);
        expect(bridge.fighting(ann) && !bridge.fighting(bob), "and says who is fighting");
        fighters.remove(ann);
        expect(!bridge.fighting(ann), "live, not remembered");
        broken[0] = true;
        fighters.add(ann);
        expect(!bridge.fighting(ann), "an error from the war plugin reads as not fighting, not a crash");
        Kit.finish();
    }
}
