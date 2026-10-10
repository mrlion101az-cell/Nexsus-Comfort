package com.nexuscraft.nexuscomfort;

import java.util.UUID;

/** The answer to "may this thing happen to this player?" and the ways a setting changes. */
final class ComfortService {

    /** Why a switch is the way it is. */
    enum Source { SERVER, OWN, PRESET }

    record Setting(boolean on, Source source) {
    }

    private final ComfortConfig cfg;
    private final ComfortStore store;

    ComfortService(ComfortConfig cfg, ComfortStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    ComfortStore store() {
        return store;
    }

    /** The preset the player is on: the one they chose, or the server's starting preset. */
    Preset preset(UUID id) {
        Preset p = store.preset(id);
        return p == null ? cfg.startingPreset() : p;
    }

    boolean chosePreset(UUID id) {
        return store.preset(id) != null;
    }

    Setting setting(UUID id, Category c) {
        if (cfg.serverOff(c)) {
            return new Setting(false, Source.SERVER);
        }
        Boolean own = store.own(id, c);
        if (own != null) {
            return new Setting(own, Source.OWN);
        }
        return new Setting(preset(id).allows(c), Source.PRESET);
    }

    boolean allows(UUID id, Category c) {
        return setting(id, c).on();
    }

    /** For other plugins: an unknown category is not ours to judge, so it is allowed. */
    boolean allows(UUID id, String category) {
        Category c = Category.of(category);
        return c == null || allows(id, c);
    }

    void choosePreset(UUID id, Preset preset) {
        store.setPreset(id, preset);
    }

    /** Turns one switch on or off; null puts it back to what the preset says. */
    void choose(UUID id, Category c, Boolean value) {
        if (value != null) {
            // keep the starting preset the player was on, even if the server changes it later
            store.pinPreset(id, preset(id));
        }
        store.setOwn(id, c, value);
    }
}
