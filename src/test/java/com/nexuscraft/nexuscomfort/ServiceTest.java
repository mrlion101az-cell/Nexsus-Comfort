package com.nexuscraft.nexuscomfort;

import java.util.UUID;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** The answer to "may this happen to this player?" and the ways it changes. */
public final class ServiceTest {

    public static void main(String[] args) {
        presets();
        sources();
        changes();
        serverOff();
        api();
        Kit.finish();
    }

    static void presets() {
        for (Category c : Category.values()) {
            expect(!Preset.GENTLE.allows(c), "gentle has " + c + " off");
            expect(Preset.INTENSE.allows(c), "intense has " + c + " on");
        }
        expect(Preset.STANDARD.allows(Category.SCARY) && Preset.STANDARD.allows(Category.PRANKS) && Preset.STANDARD.allows(Category.BODY), "standard has scary, pranks and body on");
        expect(!Preset.STANDARD.allows(Category.PVP) && !Preset.STANDARD.allows(Category.MATURE), "standard has pvp and mature off");
        expect(Category.of("PvP") == Category.PVP && Category.of(" scary ") == Category.SCARY && Category.of("nope") == null && Category.of(null) == null, "categories by name");
        expect(Preset.of("Gentle") == Preset.GENTLE && Preset.of("x") == null && Preset.of(null) == null, "presets by name");
        expect(Category.values().length == 5 && Preset.values().length == 3, "five switches, three presets");
        for (Category c : Category.values()) {
            expect(Category.of(c.key()) == c && !c.title().isEmpty() && !c.about().isEmpty() && c.icon() != null, "category " + c + " is complete");
        }
        for (Preset p : Preset.values()) {
            expect(Preset.of(p.key()) == p && !p.title().isEmpty() && !p.about().isEmpty() && p.icon() != null, "preset " + p + " is complete");
        }
    }

    static void sources() {
        Env e = new Env();
        UUID a = UUID.randomUUID();
        expect(e.comfort.preset(a) == Preset.STANDARD && !e.comfort.chosePreset(a), "a new player starts on the server's preset");
        ComfortService.Setting s = e.comfort.setting(a, Category.SCARY);
        expect(s.on() && s.source() == ComfortService.Source.PRESET, "scary: on, from the preset");
        s = e.comfort.setting(a, Category.PVP);
        expect(!s.on() && s.source() == ComfortService.Source.PRESET, "pvp: off, from the preset");
        e.cfg.defaultPreset = "intense";
        expect(e.comfort.preset(a) == Preset.INTENSE && e.comfort.allows(a, Category.PVP), "the server can change where players start");
        e.cfg.defaultPreset = "bogus";
        expect(e.comfort.preset(a) == Preset.STANDARD, "a bad starting preset means standard");
        e.comfort.choosePreset(a, Preset.GENTLE);
        expect(e.comfort.chosePreset(a) && e.comfort.preset(a) == Preset.GENTLE && !e.comfort.allows(a, Category.SCARY), "a chosen preset sticks");
        e.cfg.defaultPreset = "intense";
        expect(e.comfort.preset(a) == Preset.GENTLE, "and does not follow the server's starting preset");
    }

    static void changes() {
        Env e = new Env();
        UUID a = UUID.randomUUID();
        e.comfort.choose(a, Category.PVP, true);
        ComfortService.Setting s = e.comfort.setting(a, Category.PVP);
        expect(s.on() && s.source() == ComfortService.Source.OWN, "turning pvp on is the player's own choice");
        expect(e.comfort.chosePreset(a) && e.comfort.preset(a) == Preset.STANDARD, "the preset they were on is kept as their baseline");
        e.cfg.defaultPreset = "gentle";
        expect(e.comfort.preset(a) == Preset.STANDARD && e.comfort.allows(a, Category.SCARY), "so a later change of the server's start does not move them");
        e.comfort.choose(a, Category.SCARY, false);
        expect(!e.comfort.allows(a, Category.SCARY) && e.comfort.allows(a, Category.PVP), "each switch is its own");
        e.comfort.choose(a, Category.SCARY, null);
        s = e.comfort.setting(a, Category.SCARY);
        expect(s.on() && s.source() == ComfortService.Source.PRESET, "default puts a switch back on the preset");
        e.comfort.choosePreset(a, Preset.GENTLE);
        expect(!e.comfort.allows(a, Category.PVP), "choosing a preset resets the player's own switches");
        e.comfort.choose(a, Category.PVP, true);
        e.comfort.choosePreset(a, Preset.GENTLE);
        expect(e.comfort.setting(a, Category.PVP).source() == ComfortService.Source.PRESET, "even to the same preset");

        // putting a switch back to default does not invent a preset
        UUID fresh = UUID.randomUUID();
        e.comfort.choose(fresh, Category.PVP, null);
        expect(!e.comfort.chosePreset(fresh), "default on a fresh player changes nothing");
    }

    static void serverOff() {
        Env e = new Env();
        UUID a = UUID.randomUUID();
        e.comfort.choosePreset(a, Preset.INTENSE);
        e.cfg.offCategories = new java.util.ArrayList<>(java.util.List.of("mature"));
        ComfortService.Setting s = e.comfort.setting(a, Category.MATURE);
        expect(!s.on() && s.source() == ComfortService.Source.SERVER, "the server's ceiling beats an intense preset");
        e.comfort.choose(a, Category.MATURE, true);
        expect(!e.comfort.allows(a, Category.MATURE), "and a player's own switch");
        expect(e.comfort.allows(a, Category.SCARY), "other categories are untouched");
        e.cfg.offCategories = new java.util.ArrayList<>();
        expect(e.comfort.allows(a, Category.MATURE), "lifting the ceiling gives back the player's own choice");
    }

    static void api() {
        Env e = new Env();
        UUID a = UUID.randomUUID();
        expect(e.comfort.allows(a, "scary") && !e.comfort.allows(a, "pvp") && !e.comfort.allows(a, "MATURE"), "by name, in any case");
        expect(e.comfort.allows(a, "weather") && e.comfort.allows(a, (String) null), "a category that is not ours is not ours to refuse");
        expect(ComfortApi.SCARY.equals("scary") && ComfortApi.PRANKS.equals("pranks") && ComfortApi.BODY.equals("body")
                && ComfortApi.PVP.equals("pvp") && ComfortApi.MATURE.equals("mature"), "the API constants are the category names");
        for (Category c : Category.values()) {
            expect(Category.of(c.key()) == c, "API constant for " + c);
        }
    }
}
