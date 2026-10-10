package com.nexuscraft.nexuscomfort;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** What survives a restart: each player's preset, their own switches, and who has seen the hint. */
final class ComfortStore {

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, Preset> presets = new HashMap<>();
    private final Map<UUID, Map<Category, Boolean>> own = new HashMap<>();
    private final Set<UUID> hinted = new LinkedHashSet<>();
    private boolean dirty;

    ComfortStore(File file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
    }

    void load() {
        presets.clear();
        own.clear();
        hinted.clear();
        dirty = false;
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection p = yml.getConfigurationSection("preset");
        if (p != null) {
            for (String key : p.getKeys(false)) {
                UUID id = parse(key, "preset");
                Preset preset = Preset.of(p.getString(key));
                if (id != null && preset == null) {
                    warn.accept("data.yml: preset." + key + " is '" + p.getString(key) + "', not a preset -- skipped.");
                } else if (id != null) {
                    presets.put(id, preset);
                }
            }
        }
        ConfigurationSection o = yml.getConfigurationSection("own");
        if (o != null) {
            for (String key : o.getKeys(false)) {
                UUID id = parse(key, "own");
                ConfigurationSection mine = o.getConfigurationSection(key);
                if (id == null || mine == null) {
                    continue;
                }
                for (String name : mine.getKeys(false)) {
                    Category c = Category.of(name);
                    Boolean flag = flag(mine, name);
                    if (c == null) {
                        warn.accept("data.yml: own." + key + "." + name + " is not a category -- skipped.");
                    } else if (flag == null) {
                        warn.accept("data.yml: own." + key + "." + name + " is '" + mine.getString(name) + "', not true or false -- skipped.");
                    } else {
                        own.computeIfAbsent(id, k -> new EnumMap<>(Category.class)).put(c, flag);
                    }
                }
            }
        }
        ConfigurationSection h = yml.getConfigurationSection("hinted");
        if (h != null) {
            for (String key : h.getKeys(false)) {
                UUID id = parse(key, "hinted");
                Boolean flag = flag(h, key);
                if (id != null && flag == null) {
                    warn.accept("data.yml: hinted." + key + " is '" + h.getString(key) + "', not true or false -- skipped.");
                } else if (id != null && flag) {
                    hinted.add(id);
                }
            }
        }
    }

    /** The true or false stored at the key; null if it holds anything else (a word, a number, a list). */
    private static Boolean flag(ConfigurationSection section, String key) {
        boolean asked = section.getBoolean(key, false);
        return asked == section.getBoolean(key, true) ? Boolean.valueOf(asked) : null;
    }

    private UUID parse(String key, String section) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException bad) {
            warn.accept("data.yml: '" + section + "." + key + "' is not a player id -- skipped.");
            return null;
        }
    }

    /** The preset this player chose, or null if they never did. */
    Preset preset(UUID id) {
        return presets.get(id);
    }

    /** Choosing a preset puts every switch back to what the preset says. */
    void setPreset(UUID id, Preset preset) {
        presets.put(id, preset);
        own.remove(id);
        dirty = true;
    }

    /** Fixes the player's baseline preset without touching their own switches. */
    void pinPreset(UUID id, Preset preset) {
        if (presets.putIfAbsent(id, preset) == null) {
            dirty = true;
        }
    }

    /** The player's own setting for a category, or null if they have not set it. */
    Boolean own(UUID id, Category c) {
        Map<Category, Boolean> m = own.get(id);
        return m == null ? null : m.get(c);
    }

    /** Sets one switch; null puts it back to what the preset says. */
    void setOwn(UUID id, Category c, Boolean value) {
        if (value == null) {
            Map<Category, Boolean> m = own.get(id);
            if (m != null && m.remove(c) != null) {
                dirty = true;
                if (m.isEmpty()) {
                    own.remove(id);
                }
            }
            return;
        }
        Boolean before = own.computeIfAbsent(id, k -> new EnumMap<>(Category.class)).put(c, value);
        if (!value.equals(before)) {
            dirty = true;
        }
    }

    boolean hinted(UUID id) {
        return hinted.contains(id);
    }

    void markHinted(UUID id) {
        if (hinted.add(id)) {
            dirty = true;
        }
    }

    /** How many players have chosen anything. */
    int chosenCount() {
        Set<UUID> all = new LinkedHashSet<>(presets.keySet());
        all.addAll(own.keySet());
        return all.size();
    }

    boolean dirty() {
        return dirty;
    }

    void flush() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Preset> e : presets.entrySet()) {
            yml.set("preset." + e.getKey(), e.getValue().key());
        }
        for (Map.Entry<UUID, Map<Category, Boolean>> e : own.entrySet()) {
            for (Map.Entry<Category, Boolean> c : e.getValue().entrySet()) {
                yml.set("own." + e.getKey() + "." + c.getKey().key(), c.getValue());
            }
        }
        for (UUID id : hinted) {
            yml.set("hinted." + id, true);
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            warn.accept("Could not save data.yml: " + ex.getMessage());
        }
    }
}
