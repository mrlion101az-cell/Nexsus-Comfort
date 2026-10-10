package com.nexuscraft.nexuscomfort;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** /comfort and everything staff can do with it. */
public final class CommandTest {

    static boolean warUp = true;
    static boolean reloaded;

    static void run(ComfortCommand c, CommandSender s, String... args) {
        expect(c.onCommand(s, new Command(), "comfort", args), "the command always reports success: " + String.join(" ", args));
    }

    public static void main(String[] args) {
        Env e = new Env();
        ComfortCommand c = new ComfortCommand(e.comfort, e.cfg, () -> warUp, () -> reloaded = true);
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer bob = e.player("Bob");
        Kit.TestPlayer staff = e.player("Staff");
        staff.perms.add("nexuscomfort.admin");
        Kit.Console con = new Kit.Console();
        UUID a = ann.getUniqueId();

        // ---- menu
        run(c, ann);
        expect(ann.opened != null && ann.opened.getHolder() instanceof ComfortGui, "/comfort opens the menu");
        ann.opened = null;
        run(c, ann, "MENU");
        expect(ann.opened != null, "/comfort menu too, in any case");
        run(c, con);
        expect(con.heard("Players only") && con.lines.size() == 1, "the console cannot open a menu");
        con.lines.clear();
        run(c, con, "status");
        run(c, con, "pvp", "on");
        run(c, con, "gentle");
        run(c, con, "preset", "gentle");
        expect(con.lines.size() == 4 && con.lines.stream().allMatch(l -> l.contains("Players only")), "and none of the rest is for the console");

        // ---- status
        run(c, ann, "status");
        expect(ann.heard("Standard") && ann.heard("the server's starting preset"), "a new player is told they are on the starting preset: " + ann.messages);
        expect(ann.heard("Scary things: ") && ann.heard("on") && ann.heard("from the preset") && ann.heard("PvP outside wars: ") && ann.messages.size() == 6, "and each switch: " + ann.messages);

        // ---- presets
        ann.messages.clear();
        run(c, ann, "preset", "gentle");
        expect(e.comfort.preset(a) == Preset.GENTLE && ann.heard("Gentle"), "/comfort preset gentle");
        ann.messages.clear();
        run(c, ann, "intense");
        expect(e.comfort.preset(a) == Preset.INTENSE && ann.heard("Intense") && ann.heard("preset now"), "/comfort intense is a shortcut, and says so: " + ann.messages);
        run(c, ann, "preset");
        run(c, ann, "preset", "wild");
        expect(e.comfort.preset(a) == Preset.INTENSE && ann.heard("Use") && ann.heard("/comfort preset gentle"), "no or bad preset: told how");
        run(c, ann, "status");
        expect(ann.heard("Intense") && !ann.heard("starting preset"), "status after choosing");

        // ---- switches
        run(c, ann, "preset", "standard");
        ann.messages.clear();
        run(c, ann, "pvp");
        expect(e.comfort.allows(a, Category.PVP) && ann.heard("PvP outside wars is on for you"), "no word toggles: " + ann.messages);
        run(c, ann, "pvp");
        expect(!e.comfort.allows(a, Category.PVP) && ann.heard("is off for you"), "and again");
        ann.messages.clear();
        run(c, ann, "PVP", "ON");
        expect(e.comfort.allows(a, Category.PVP) && ann.heard("PvP outside wars is on for you"), "on, in any case, and says so: " + ann.messages);
        ann.messages.clear();
        run(c, ann, "pvp", "off");
        expect(!e.comfort.allows(a, Category.PVP) && ann.heard("is off for you"), "off, and says so: " + ann.messages);
        run(c, ann, "scary", "off");
        run(c, ann, "scary", "default");
        expect(e.comfort.allows(a, Category.SCARY) && e.comfort.setting(a, Category.SCARY).source() == ComfortService.Source.PRESET && ann.heard("follows your preset again (on)"), "default: " + ann.messages);
        ann.messages.clear();
        run(c, ann, "pvp", "maybe");
        expect(ann.heard("Use") && ann.heard("/comfort pvp on") && !e.comfort.allows(a, Category.PVP), "a bad value: told how, nothing changed");
        run(c, ann, "status");
        expect(ann.heard("a choice") || ann.heard("from the preset"), "status shows the source");
        e.cfg.offCategories.add("mature");
        ann.messages.clear();
        run(c, ann, "mature", "on");
        expect(ann.heard("turned Mature themes off for everyone") && !e.comfort.allows(a, Category.MATURE), "a ceiling cannot be changed: " + ann.messages);
        run(c, ann, "status");
        expect(ann.heard("the server has turned it off"), "and status says so");
        e.cfg.offCategories.clear();
        run(c, ann, "bogus");
        expect(ann.heard("Try") && ann.heard("/comfort"), "anything else: told how");

        // ---- staff only
        for (String sub : List.of("of", "setfor", "reload", "check")) {
            bob.messages.clear();
            run(c, bob, sub, "Ann", "gentle");
            expect(bob.heard("Only staff") && bob.messages.size() == 1, sub + " is for staff");
        }
        expect(!reloaded && e.comfort.preset(a) == Preset.STANDARD, "and did nothing");

        // ---- of
        staff.messages.clear();
        run(c, staff, "of");
        expect(staff.heard("Use") && staff.heard("/comfort of"), "of with no name");
        run(c, staff, "of", "Ann");
        expect(staff.heard("Ann is on the") && staff.heard("Standard") && staff.messages.size() == 7, "of a player: " + staff.messages);
        staff.messages.clear();
        run(c, staff, "of", "Zed");
        expect(staff.heard("Zed is on the") && staff.heard("Standard"), "of someone offline who has been here: " + staff.messages);
        Bukkit.NEVER_PLAYED.add("Ghost");
        staff.messages.clear();
        run(c, staff, "of", "Ghost");
        expect(staff.heard("Ghost has never been here") && staff.messages.size() == 1, "of someone who never has: " + staff.messages);

        // ---- setfor
        staff.messages.clear();
        run(c, staff, "setfor");
        run(c, staff, "setfor", "Ann");
        run(c, staff, "setfor", "Ann", "gentle");
        expect(staff.messages.size() == 3 && staff.messages.get(0).contains("Use") && staff.messages.get(1).contains("Use") && e.comfort.preset(a) == Preset.GENTLE, "usage for short commands, a preset works with three words: " + staff.messages);
        expect(staff.heard("Ann is on the") && ann.heard("Staff put you on the") && ann.heard("Gentle"), "and the player is told: " + ann.messages);
        ann.messages.clear();
        staff.messages.clear();
        run(c, staff, "setfor", "Ann", "pvp", "on");
        expect(e.comfort.allows(a, Category.PVP) && staff.heard("Ann: PvP outside wars is now on") && ann.heard("Staff set PvP outside wars to on for you"), "a switch: " + staff.messages + ann.messages);
        run(c, staff, "setfor", "Ann", "pvp", "default");
        expect(!e.comfort.allows(a, Category.PVP) && staff.heard("is now off") && ann.heard("to off for you"), "default");
        staff.messages.clear();
        run(c, staff, "setfor", "Ann", "pvp", "maybe");
        run(c, staff, "setfor", "Ann", "pvp");
        expect(staff.messages.size() == 2 && staff.heard("<on|off|default>") && !e.comfort.allows(a, Category.PVP), "a bad or missing value: " + staff.messages);
        staff.messages.clear();
        run(c, staff, "setfor", "Ann", "weather", "on");
        expect(staff.heard("'weather' is not a preset or a category"), "a bad name: " + staff.messages);
        e.cfg.offCategories.add("pvp");
        staff.messages.clear();
        run(c, staff, "setfor", "Ann", "pvp", "on");
        expect(staff.heard("turned PvP outside wars off for everyone") && staff.messages.size() == 1, "a ceiling: " + staff.messages);
        e.cfg.offCategories.clear();
        staff.messages.clear();
        run(c, staff, "setfor", "Zed", "intense");
        UUID zed = Bukkit.getOfflinePlayer("Zed").getUniqueId();
        expect(e.comfort.preset(zed) == Preset.INTENSE && staff.heard("Zed is on the") && staff.messages.size() == 1, "someone offline can be set: " + staff.messages);
        run(c, staff, "setfor", "Zed", "mature", "off");
        expect(!e.comfort.allows(zed, Category.MATURE) && staff.heard("Zed: Mature themes is now off"), "and their switches");
        staff.messages.clear();
        run(c, staff, "setfor", "Ghost", "intense");
        expect(staff.heard("never been here") && e.comfort.preset(UUID.nameUUIDFromBytes("offline:Ghost".getBytes())) != Preset.INTENSE, "never been here: nothing set");

        // ---- reload and check
        run(c, staff, "reload");
        expect(reloaded && staff.heard("Reloaded"), "reload runs the reload");
        staff.messages.clear();
        run(c, staff, "check");
        expect(staff.heard("Starting preset: ") && staff.heard("Standard") && staff.heard("No category is switched off") && staff.heard("NexusWorldWar found"), "check: " + staff.messages);
        expect(staff.heard("darkness") && staff.heard("nothing") && staff.heard("nausea") && staff.heard("up to 8 blocks"), "check lists what the guard cancels: " + staff.messages);
        e.cfg.offCategories.add("mature");
        e.cfg.offCategories.add("weather");
        e.cfg.defaultPreset = "wild";
        e.cfg.guardPvp = false;
        e.cfg.teleportNudgeBlocks = 0;
        warUp = false;
        staff.messages.clear();
        run(c, staff, "check");
        expect(staff.heard("Off for everyone: ") && staff.heard("mature") && staff.heard("PvP guard: ") && staff.heard("off") && staff.heard("not guarded"), "check with settings changed: " + staff.messages);
        expect(staff.heard("'wild' is not gentle, standard or intense") && staff.heard("'weather' is not a category"), "and the config problems: " + staff.messages);
        e.cfg.guardPvp = true;
        staff.messages.clear();
        run(c, staff, "check");
        expect(staff.heard("NexusWorldWar not found"), "without the war plugin it says PvP is blocked for anyone with it off: " + staff.messages);
        expect(con.lines.size() == 4, "the console heard nothing more");
        con.perms.add("nexuscomfort.admin");
        con.lines.clear();
        run(c, con, "check");
        run(c, con, "of", "Ann");
        expect(con.lines.size() > 6, "the console can do the staff commands");

        // ---- tab completion
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {""}).containsAll(List.of("menu", "status", "preset", "pvp", "gentle")), "first word, player");
        expect(!c.onTabComplete(ann, new Command(), "comfort", new String[] {""}).contains("setfor"), "staff words are hidden from players");
        expect(c.onTabComplete(staff, new Command(), "comfort", new String[] {"se"}).equals(List.of("setfor")), "and shown to staff, filtered");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"PR"}).equals(List.of("preset", "pranks")), "in any case");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"preset", "g"}).equals(List.of("gentle")), "preset names");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"pvp", ""}).equals(List.of("on", "off", "default")), "values after a category");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"status", ""}).isEmpty(), "nothing after status");
        expect(c.onTabComplete(staff, new Command(), "comfort", new String[] {"of", "A"}).equals(List.of("Ann")), "online names for staff");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"of", ""}).isEmpty(), "but not for players");
        expect(c.onTabComplete(staff, new Command(), "comfort", new String[] {"setfor", "Ann", "p"}).equals(List.of("pranks", "pvp")), "setfor: presets and categories: " + c.onTabComplete(staff, new Command(), "comfort", new String[] {"setfor", "Ann", "p"}));
        expect(c.onTabComplete(staff, new Command(), "comfort", new String[] {"setfor", "Ann", "pvp", "o"}).equals(List.of("on", "off")), "setfor: values");
        expect(c.onTabComplete(ann, new Command(), "comfort", new String[] {"setfor", "Ann", "p"}).isEmpty(), "and only for staff");
        expect(c.onTabComplete(staff, new Command(), "comfort", new String[] {"setfor", "Ann", "pvp", "on", ""}).isEmpty(), "nothing after that");
        Kit.finish();
    }
}
