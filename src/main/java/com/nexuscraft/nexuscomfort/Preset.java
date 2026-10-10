package com.nexuscraft.nexuscomfort;

import org.bukkit.Material;

import java.util.Locale;

/** A starting point for all the switches at once. */
public enum Preset {

    GENTLE("gentle", "Gentle", Material.LIME_WOOL, "Everything off. Nothing scary, no pranks, no body effects, no PvP, no mature themes."),
    STANDARD("standard", "Standard", Material.YELLOW_WOOL, "Scary things, pranks and body effects on. PvP outside wars and mature themes off."),
    INTENSE("intense", "Intense", Material.RED_WOOL, "Everything on.");

    private final String key;
    private final String title;
    private final Material icon;
    private final String about;

    Preset(String key, String title, Material icon, String about) {
        this.key = key;
        this.title = title;
        this.icon = icon;
        this.about = about;
    }

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

    /** Whether this preset has the category on. */
    boolean allows(Category c) {
        return switch (this) {
            case GENTLE -> false;
            case STANDARD -> c == Category.SCARY || c == Category.PRANKS || c == Category.BODY;
            case INTENSE -> true;
        };
    }

    static Preset of(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (Preset p : values()) {
            if (p.key.equals(t)) {
                return p;
            }
        }
        return null;
    }
}
