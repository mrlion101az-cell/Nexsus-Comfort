package com.nexuscraft.nexuscomfort;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings from config.yml, each held to something sensible. */
final class ComfortConfig {

    String defaultPreset = "standard";
    List<String> offCategories = new ArrayList<>();
    boolean welcomeHint = true;

    boolean guardPvp = true;
    List<String> pvpExemptWorlds = new ArrayList<>();
    List<String> scaryEffects = new ArrayList<>(List.of("darkness"));
    List<String> pranksEffects = new ArrayList<>();
    List<String> bodyEffects = new ArrayList<>(List.of("nausea"));
    List<String> matureEffects = new ArrayList<>();
    int teleportNudgeBlocks = 8;

    String prefix = "&d&l[Comfort] &r";

    void load(ConfigurationSection c) {
        defaultPreset = c.getString("server.default-preset", defaultPreset).trim().toLowerCase(Locale.ROOT);
        offCategories = lower(c, "server.off-categories", offCategories);
        welcomeHint = c.getBoolean("server.welcome-hint", welcomeHint);

        guardPvp = c.getBoolean("guard.pvp", guardPvp);
        pvpExemptWorlds = lower(c, "guard.pvp-exempt-worlds", pvpExemptWorlds);
        scaryEffects = lower(c, "guard.scary-effects", scaryEffects);
        pranksEffects = lower(c, "guard.pranks-effects", pranksEffects);
        bodyEffects = lower(c, "guard.body-effects", bodyEffects);
        matureEffects = lower(c, "guard.mature-effects", matureEffects);
        teleportNudgeBlocks = clamp(c.getInt("guard.teleport-nudge-blocks", teleportNudgeBlocks), 0, 64);

        prefix = c.getString("messages.prefix", prefix);
    }

    /** The preset new players start on; STANDARD if the setting is not a preset. */
    Preset startingPreset() {
        Preset p = Preset.of(defaultPreset);
        return p == null ? Preset.STANDARD : p;
    }

    /** True if staff have turned this category off for everyone. */
    boolean serverOff(Category c) {
        return offCategories.contains(c.key());
    }

    /** The effect names the guard cancels for players who have this category off. */
    List<String> effectsFor(Category c) {
        return switch (c) {
            case SCARY -> scaryEffects;
            case PRANKS -> pranksEffects;
            case BODY -> bodyEffects;
            case MATURE -> matureEffects;
            case PVP -> List.of();
        };
    }

    /** Things in the config that do not mean anything, for the log and /comfort check. */
    List<String> problems() {
        List<String> out = new ArrayList<>();
        if (Preset.of(defaultPreset) == null) {
            out.add("server.default-preset: '" + defaultPreset + "' is not gentle, standard or intense -- using standard.");
        }
        for (String s : offCategories) {
            if (Category.of(s) == null) {
                out.add("server.off-categories: '" + s + "' is not a category (scary, pranks, body, pvp, mature) -- ignored.");
            }
        }
        return out;
    }

    private static List<String> lower(ConfigurationSection c, String path, List<String> fallback) {
        if (!c.isSet(path)) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (String s : c.getStringList(path)) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
