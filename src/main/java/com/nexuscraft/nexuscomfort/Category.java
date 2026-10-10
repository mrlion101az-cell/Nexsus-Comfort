package com.nexuscraft.nexuscomfort;

import org.bukkit.Material;

import java.util.Locale;

/** The kinds of thing a player can turn down. */
public enum Category {

    SCARY("scary", "Scary things", Material.SKELETON_SKULL,
            "Cave whispers, hallucinations, blood moons and other things meant to unsettle you."),
    PRANKS("pranks", "Pranks", Material.ENDER_PEARL,
            "Small surprises: a mini-teleport, a shuffled item, a fake message."),
    BODY("body", "Body effects", Material.POTION,
            "Things that happen to your character's body: dizziness, hangovers, sleep debt."),
    PVP("pvp", "PvP outside wars", Material.IRON_SWORD,
            "Other players hurting you, and you hurting them, outside a world war. In a war you only fight if you joined it."),
    MATURE("mature", "Mature themes", Material.WRITABLE_BOOK,
            "Drinking and drugs, shackles and prison roleplay, and similar grown-up content.");

    private final String key;
    private final String title;
    private final Material icon;
    private final String about;

    Category(String key, String title, Material icon, String about) {
        this.key = key;
        this.title = title;
        this.icon = icon;
        this.about = about;
    }

    /** The lower-case name used in commands, config and the API. */
    public String key() {
        return key;
    }

    String title() {
        return title;
    }

    Material icon() {
        return icon;
    }

    String about() {
        return about;
    }

    /** Finds a category by its key, in any case; null if there is none. */
    public static Category of(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (Category c : values()) {
            if (c.key.equals(t)) {
                return c;
            }
        }
        return null;
    }
}
