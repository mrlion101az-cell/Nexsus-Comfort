package com.nexuscraft.nexuscomfort;

import java.util.List;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** config.yml: defaults, limits, and what is reported when it makes no sense. */
public final class ConfigTest {

    public static void main(String[] args) {
        ComfortConfig d = new ComfortConfig();
        expect(d.startingPreset() == Preset.STANDARD && d.offCategories.isEmpty() && d.welcomeHint, "server defaults");
        expect(d.guardPvp && d.pvpExemptWorlds.isEmpty() && d.teleportNudgeBlocks == 8, "guard defaults");
        expect(d.scaryEffects.equals(List.of("darkness")) && d.bodyEffects.equals(List.of("nausea")) && d.pranksEffects.isEmpty() && d.matureEffects.isEmpty(), "effect defaults");
        expect(d.prefix.contains("Comfort") && d.problems().isEmpty(), "prefix, and no problems");

        ComfortConfig c = new ComfortConfig();
        c.load(new Kit.MapSection()
                .put("server.default-preset", "  GENTLE ")
                .put("server.off-categories", List.of(" Mature ", "", "pvp"))
                .put("server.welcome-hint", false)
                .put("guard.pvp", false)
                .put("guard.pvp-exempt-worlds", List.of("Arena", " "))
                .put("guard.scary-effects", List.of("Darkness", "blindness"))
                .put("guard.pranks-effects", List.of("levitation"))
                .put("guard.body-effects", List.of())
                .put("guard.mature-effects", List.of("hunger"))
                .put("guard.teleport-nudge-blocks", 20)
                .put("messages.prefix", "[C] "));
        expect(c.startingPreset() == Preset.GENTLE, "preset: " + c.defaultPreset);
        expect(c.offCategories.equals(List.of("mature", "pvp")) && c.serverOff(Category.MATURE) && c.serverOff(Category.PVP) && !c.serverOff(Category.SCARY), "off categories: " + c.offCategories);
        expect(!c.welcomeHint && !c.guardPvp, "switches");
        expect(c.pvpExemptWorlds.equals(List.of("arena")), "worlds are lower-cased and blanks dropped: " + c.pvpExemptWorlds);
        expect(c.effectsFor(Category.SCARY).equals(List.of("darkness", "blindness")) && c.effectsFor(Category.PRANKS).equals(List.of("levitation"))
                && c.effectsFor(Category.BODY).isEmpty() && c.effectsFor(Category.MATURE).equals(List.of("hunger")) && c.effectsFor(Category.PVP).isEmpty(), "effects per category");
        expect(c.teleportNudgeBlocks == 20 && c.prefix.equals("[C] "), "nudge and prefix");

        c.load(new Kit.MapSection().put("guard.teleport-nudge-blocks", 500));
        expect(c.teleportNudgeBlocks == 64, "nudge is held to 64: " + c.teleportNudgeBlocks);
        c.load(new Kit.MapSection().put("guard.teleport-nudge-blocks", -3));
        expect(c.teleportNudgeBlocks == 0, "and to 0 at the least: " + c.teleportNudgeBlocks);
        c.load(new Kit.MapSection());
        expect(c.startingPreset() == Preset.GENTLE && c.guardPvp == false, "keys that are missing keep what was there");

        ComfortConfig bad = new ComfortConfig();
        bad.load(new Kit.MapSection().put("server.default-preset", "wild").put("server.off-categories", List.of("mature", "weather")));
        expect(bad.startingPreset() == Preset.STANDARD, "a bad preset means standard");
        List<String> problems = bad.problems();
        expect(problems.size() == 2 && problems.get(0).contains("'wild'") && problems.get(1).contains("'weather'"), "and each problem is named: " + problems);
        expect(bad.serverOff(Category.MATURE), "the good one still counts");
        Kit.finish();
    }
}
