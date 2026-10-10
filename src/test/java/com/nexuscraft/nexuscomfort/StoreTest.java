package com.nexuscraft.nexuscomfort;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** What is saved: presets, own switches, who has seen the hint. */
public final class StoreTest {

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("cf-store").toFile();
        File f = new File(dir, "data.yml");
        List<String> warns = new ArrayList<>();
        ComfortStore s = new ComfortStore(f, warns::add);
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        expect(!s.dirty() && s.chosenCount() == 0 && s.preset(ann) == null && s.own(ann, Category.PVP) == null && !s.hinted(ann), "empty at first");
        s.setPreset(ann, Preset.GENTLE);
        s.setOwn(ann, Category.PVP, true);
        s.setOwn(ann, Category.SCARY, false);
        s.setOwn(bob, Category.MATURE, true);
        s.markHinted(ann);
        expect(s.dirty() && s.chosenCount() == 2 && s.preset(ann) == Preset.GENTLE && Boolean.TRUE.equals(s.own(ann, Category.PVP)) && Boolean.FALSE.equals(s.own(ann, Category.SCARY)), "choices are kept");
        s.flush();
        expect(!s.dirty() && f.exists(), "saved");

        ComfortStore again = new ComfortStore(f, warns::add);
        again.load();
        expect(again.preset(ann) == Preset.GENTLE && again.preset(bob) == null, "presets survive");
        expect(Boolean.TRUE.equals(again.own(ann, Category.PVP)) && Boolean.FALSE.equals(again.own(ann, Category.SCARY)) && Boolean.TRUE.equals(again.own(bob, Category.MATURE)), "own switches survive, on and off");
        expect(again.own(ann, Category.MATURE) == null && again.hinted(ann) && !again.hinted(bob) && again.chosenCount() == 2 && !again.dirty(), "the rest is as it was");

        // setting a preset forgets own switches; setting the same value is not a change
        again.setPreset(ann, Preset.INTENSE);
        expect(again.own(ann, Category.PVP) == null && again.own(ann, Category.SCARY) == null && again.dirty(), "a preset resets the own switches");
        again.flush();
        again.setOwn(bob, Category.MATURE, true);
        expect(!again.dirty(), "the same value again changes nothing");
        again.setOwn(bob, Category.MATURE, false);
        expect(again.dirty() && Boolean.FALSE.equals(again.own(bob, Category.MATURE)), "a different value does");
        again.flush();
        again.setOwn(bob, Category.MATURE, null);
        expect(again.dirty() && again.own(bob, Category.MATURE) == null, "default removes it");
        again.flush();
        again.setOwn(bob, Category.MATURE, null);
        again.setOwn(UUID.randomUUID(), Category.PVP, null);
        expect(!again.dirty(), "removing what is not there changes nothing");
        again.markHinted(ann);
        expect(!again.dirty(), "hinting twice changes nothing");
        again.pinPreset(bob, Preset.STANDARD);
        expect(again.dirty() && again.preset(bob) == Preset.STANDARD, "pinning sets a baseline");
        again.flush();
        again.setOwn(bob, Category.PVP, true);
        again.flush();
        again.pinPreset(bob, Preset.INTENSE);
        expect(!again.dirty() && again.preset(bob) == Preset.STANDARD && Boolean.TRUE.equals(again.own(bob, Category.PVP)), "pinning again changes nothing, and keeps own switches");
        again.flush();
        ComfortStore third = new ComfortStore(f, warns::add);
        third.load();
        expect(third.preset(ann) == Preset.INTENSE && third.own(ann, Category.PVP) == null && third.preset(bob) == Preset.STANDARD && Boolean.TRUE.equals(third.own(bob, Category.PVP)), "all of that saved");
        expect(third.own(bob, Category.MATURE) == null, "and the removed switch stayed removed");

        // load() forgets what was only in memory
        ComfortStore fresh = new ComfortStore(new File(dir, "nothing.yml"), warns::add);
        fresh.setPreset(ann, Preset.GENTLE);
        fresh.markHinted(ann);
        fresh.setOwn(ann, Category.PVP, true);
        fresh.load();
        expect(fresh.chosenCount() == 0 && !fresh.hinted(ann) && fresh.own(ann, Category.PVP) == null && !fresh.dirty(), "load() starts clean");

        // a switch put back to default leaves no trace of the player
        ComfortStore tidy = new ComfortStore(null, warns::add);
        tidy.setOwn(ann, Category.PVP, true);
        expect(tidy.chosenCount() == 1, "one switch counts as a choice");
        tidy.setOwn(ann, Category.PVP, null);
        expect(tidy.chosenCount() == 0, "back to default: no longer counted");
        tidy.load();
        expect(tidy.chosenCount() == 0, "load() with no file at all starts clean");

        // a save that fails is reported, and tried again
        File blocker = new File(dir, "blocker");
        Files.writeString(blocker.toPath(), "x");
        List<String> bad = new ArrayList<>();
        ComfortStore stuck = new ComfortStore(new File(blocker, "sub/data.yml"), bad::add);
        stuck.setPreset(ann, Preset.GENTLE);
        stuck.flush();
        expect(bad.size() == 1 && bad.get(0).contains("Could not save data.yml") && stuck.dirty(), "a failed save is reported and stays pending: " + bad);

        // a clean store does not write
        File untouched = new File(dir, "untouched.yml");
        new ComfortStore(untouched, warns::add).flush();
        expect(!untouched.exists(), "nothing changed, nothing written");
        ComfortStore nofile = new ComfortStore(null, warns::add);
        nofile.setPreset(ann, Preset.GENTLE);
        nofile.flush();
        expect(nofile.dirty(), "with no file the changes just wait");

        // a file in a folder that does not exist yet
        File deep = new File(dir, "a/b/data.yml");
        ComfortStore d = new ComfortStore(deep, warns::add);
        d.setPreset(ann, Preset.GENTLE);
        d.flush();
        expect(deep.exists() && !d.dirty() && warns.isEmpty(), "the folder is made when it is needed: " + warns);

        // odd contents
        File odd = new File(dir, "odd.yml");
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        UUID good = UUID.randomUUID();
        y.set("preset." + good, "intense");
        y.set("preset.nonsense", "gentle");
        y.set("preset." + bob, "wild");
        y.set("own." + good + ".pvp", true);
        y.set("own." + good + ".weather", true);
        y.set("own.nonsense.pvp", true);
        y.set("hinted." + good, true);
        y.set("hinted." + bob, false);
        y.set("hinted.nonsense", true);
        y.set("own." + good + ".mature", "maybe");
        y.set("hinted." + UUID.randomUUID(), "perhaps");
        y.set("own." + good + ".body", false);
        y.save(odd);
        List<String> w2 = new ArrayList<>();
        ComfortStore r = new ComfortStore(odd, w2::add);
        r.load();
        expect(r.preset(good) == Preset.INTENSE && r.preset(bob) == null && Boolean.TRUE.equals(r.own(good, Category.PVP)) && r.hinted(good) && !r.hinted(bob), "the good parts load");
        expect(r.chosenCount() == 1, "and nothing odd gets in: " + r.chosenCount());
        expect(w2.size() == 7, "each odd entry is reported once: " + w2);
        expect(w2.stream().anyMatch(m -> m.contains("'wild'")) && w2.stream().anyMatch(m -> m.contains("weather")) && w2.stream().anyMatch(m -> m.contains("preset.nonsense"))
                && w2.stream().anyMatch(m -> m.contains("own.nonsense")) && w2.stream().anyMatch(m -> m.contains("hinted.nonsense"))
                && w2.stream().anyMatch(m -> m.contains("'maybe', not true or false")) && w2.stream().anyMatch(m -> m.contains("'perhaps', not true or false")), "by name: " + w2);
        expect(Boolean.FALSE.equals(r.own(good, Category.BODY)) && r.own(good, Category.MATURE) == null, "a switch written as false is off; one written as 'maybe' is not a choice");
        r.markHinted(bob);
        r.flush();
        String back = Files.readString(odd.toPath());
        expect(!back.contains("nonsense") && !back.contains("weather") && !back.contains("wild"), "and not carried into the next save: " + back);
        Kit.finish();
    }
}
