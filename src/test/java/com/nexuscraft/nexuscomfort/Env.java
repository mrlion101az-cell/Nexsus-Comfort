package com.nexuscraft.nexuscomfort;

import java.util.concurrent.atomic.AtomicLong;

/** A NexusComfort wired to a stub server: a clock, a settings store in memory, and players. */
final class Env {

    static final long T0 = 1_800_000_000_000L;

    final ComfortConfig cfg = new ComfortConfig();
    final ComfortStore store = new ComfortStore(null, w -> { });
    final ComfortService comfort = new ComfortService(cfg, store);
    final AtomicLong clock = new AtomicLong(T0);

    Env() {
        Kit.reset();
    }

    Kit.TestPlayer player(String name) {
        return Kit.online(new Kit.TestPlayer(name, 0, 0));
    }

    /** A player who has picked a preset. */
    Kit.TestPlayer on(String name, Preset preset) {
        Kit.TestPlayer p = player(name);
        comfort.choosePreset(p.getUniqueId(), preset);
        return p;
    }
}
