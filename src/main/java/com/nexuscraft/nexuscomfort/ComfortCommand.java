package com.nexuscraft.nexuscomfort;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** /comfort: the menu, quick changes, and (for staff) looking at or changing anyone's settings. */
public final class ComfortCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("menu", "status", "preset", "scary", "pranks", "body", "pvp", "mature", "gentle", "standard", "intense");
    private static final List<String> ADMIN_SUBS = List.of("of", "setfor", "reload", "check");
    private static final List<String> VALUES = List.of("on", "off", "default");

    private final ComfortService comfort;
    private final ComfortConfig cfg;
    private final BooleanSupplier warUp;
    private final ComfortGui.Ctx menu;
    private final Runnable reload;

    ComfortCommand(ComfortService comfort, ComfortConfig cfg, BooleanSupplier warUp, Runnable reload) {
        this.comfort = comfort;
        this.cfg = cfg;
        this.warUp = warUp;
        this.menu = new ComfortGui.Ctx(comfort, cfg);
        this.reload = reload;
    }

    private void say(CommandSender to, String text) {
        to.sendMessage(ColorText.translate(cfg.prefix + text));
    }

    private static boolean admin(CommandSender s) {
        return s.hasPermission("nexuscomfort.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu")) {
            if (sender instanceof Player p) {
                ComfortGui.open(menu, p);
            } else {
                say(sender, "&7Players only.");
            }
        } else if (ADMIN_SUBS.contains(sub)) {
            if (!admin(sender)) {
                say(sender, "&cOnly staff can do that.");
            } else {
                adminCommand(sender, sub, args);
            }
        } else if (!(sender instanceof Player p)) {
            say(sender, "&7Players only.");
        } else if (sub.equals("status")) {
            status(p, p.getUniqueId(), "You");
        } else if (sub.equals("preset")) {
            Preset preset = args.length > 1 ? Preset.of(args[1]) : null;
            if (preset == null) {
                say(p, "&7Use &f/comfort preset gentle&7, &fstandard&7 or &fintense&7.");
            } else {
                comfort.choosePreset(p.getUniqueId(), preset);
                say(p, Texts.preset(preset));
            }
        } else if (Preset.of(sub) != null) {
            Preset preset = Preset.of(sub);
            comfort.choosePreset(p.getUniqueId(), preset);
            say(p, Texts.preset(preset));
        } else if (Category.of(sub) != null) {
            change(p, p.getUniqueId(), Category.of(sub), args.length > 1 ? args[1] : null);
        } else {
            say(p, "&7Try &f/comfort&7, &f/comfort status&7, &f/comfort gentle&7 (or standard, intense) or &f/comfort pvp on&7.");
        }
        return true;
    }

    /** Sets or toggles one switch for {@code target}; {@code byStaff} only changes the wording. */
    private void change(CommandSender who, UUID target, Category c, String value) {
        if (cfg.serverOff(c)) {
            say(who, "&cThe server has turned " + c.title() + " off for everyone, so there is nothing to change.");
            return;
        }
        String v = value == null ? null : value.toLowerCase(Locale.ROOT);
        if (v == null) {
            boolean now = !comfort.allows(target, c);
            comfort.choose(target, c, now);
            say(who, Texts.category(c, now));
        } else if (v.equals("on") || v.equals("off")) {
            comfort.choose(target, c, v.equals("on"));
            say(who, Texts.category(c, v.equals("on")));
        } else if (v.equals("default")) {
            comfort.choose(target, c, null);
            say(who, Texts.backToPreset(c, comfort.allows(target, c)));
        } else {
            say(who, "&7Use &f/comfort " + c.key() + " on&7, &foff&7 or &fdefault&7.");
        }
    }

    private void status(CommandSender to, UUID id, String who) {
        say(to, "&6" + who + (who.equals("You") ? " are" : " is") + " on the &f" + comfort.preset(id).title() + "&6 preset"
                + (comfort.chosePreset(id) ? "." : " (the server's starting preset)."));
        for (Category c : Category.values()) {
            ComfortService.Setting s = comfort.setting(id, c);
            String why = switch (s.source()) {
                case SERVER -> "the server has turned it off";
                case OWN -> "a choice";
                case PRESET -> "from the preset";
            };
            say(to, "&f" + c.title() + ": " + (s.on() ? "&aon" : "&coff") + " &7(" + why + ")");
        }
    }

    // ------------------------------------------------------------------ staff

    private void adminCommand(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "of" -> {
                UUID id = args.length > 1 ? lookup(sender, args[1]) : null;
                if (id != null) {
                    status(sender, id, args[1]);
                } else if (args.length < 2) {
                    say(sender, "&cUse &f/comfort of <player>&c.");
                }
            }
            case "setfor" -> setFor(sender, args);
            case "reload" -> {
                reload.run();
                say(sender, "&aReloaded.");
            }
            default -> check(sender);
        }
    }

    private void setFor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            say(sender, "&cUse &f/comfort setfor <player> <gentle|standard|intense>&c or &f/comfort setfor <player> <category> <on|off|default>&c.");
            return;
        }
        UUID id = lookup(sender, args[1]);
        if (id == null) {
            return;
        }
        Preset preset = Preset.of(args[2]);
        Category c = Category.of(args[2]);
        Player online = Bukkit.getPlayerExact(args[1]);
        if (preset != null) {
            comfort.choosePreset(id, preset);
            say(sender, "&a" + args[1] + " is on the &f" + preset.title() + "&a preset now.");
            if (online != null) {
                say(online, "&7Staff put you on the &f" + preset.title() + "&7 preset. Change it any time with &f/comfort&7.");
            }
        } else if (c != null) {
            if (cfg.serverOff(c)) {
                say(sender, "&cThe server has turned " + c.title() + " off for everyone.");
                return;
            }
            String v = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
            if (!VALUES.contains(v)) {
                say(sender, "&cUse &f/comfort setfor " + args[1] + " " + c.key() + " <on|off|default>&c.");
                return;
            }
            comfort.choose(id, c, v.equals("default") ? null : v.equals("on"));
            say(sender, "&a" + args[1] + ": " + c.title() + " is now " + (comfort.allows(id, c) ? "on" : "off") + ".");
            if (online != null) {
                say(online, "&7Staff set " + c.title() + " to " + (comfort.allows(id, c) ? "on" : "off") + " for you. Change it any time with &f/comfort&7.");
            }
        } else {
            say(sender, "&c'" + args[2] + "' is not a preset or a category.");
        }
    }

    private UUID lookup(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off == null || !off.hasPlayedBefore()) {
            say(sender, "&c" + name + " has never been here.");
            return null;
        }
        return off.getUniqueId();
    }

    private void check(CommandSender sender) {
        say(sender, "&7Starting preset: &f" + cfg.startingPreset().title() + "&7. Players who have chosen something: &f" + comfort.store().chosenCount() + "&7.");
        List<String> off = new ArrayList<>();
        for (Category c : Category.values()) {
            if (cfg.serverOff(c)) {
                off.add(c.key());
            }
        }
        say(sender, off.isEmpty() ? "&7No category is switched off for the whole server." : "&7Off for everyone: &f" + String.join(", ", off) + "&7.");
        say(sender, cfg.guardPvp ? "&7PvP guard: &aon&7 (" + (warUp.getAsBoolean() ? "NexusWorldWar found, so war fighters can fight" : "NexusWorldWar not found, so PvP is blocked for anyone with it off") + ")."
                : "&7PvP guard: &coff&7.");
        say(sender, "&7Effect guard cancels: scary &f" + names(cfg.scaryEffects) + "&7, pranks &f" + names(cfg.pranksEffects)
                + "&7, body &f" + names(cfg.bodyEffects) + "&7, mature &f" + names(cfg.matureEffects) + "&7. Small teleports: &f"
                + (cfg.teleportNudgeBlocks > 0 ? "up to " + cfg.teleportNudgeBlocks + " blocks" : "not guarded") + "&7.");
        for (String problem : cfg.problems()) {
            say(sender, "&c" + problem);
        }
    }

    private static String names(List<String> l) {
        return l.isEmpty() ? "nothing" : String.join("/", l);
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        boolean staff = admin(sender);
        if (args.length == 1) {
            addMatching(out, PLAYER_SUBS, args[0]);
            if (staff) {
                addMatching(out, ADMIN_SUBS, args[0]);
            }
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("preset")) {
                addMatching(out, List.of("gentle", "standard", "intense"), args[1]);
            } else if (Category.of(first) != null) {
                addMatching(out, VALUES, args[1]);
            } else if (staff && (first.equals("of") || first.equals("setfor"))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    addMatching(out, List.of(p.getName()), args[1]);
                }
            }
        } else if (args.length == 3 && staff && args[0].equalsIgnoreCase("setfor")) {
            addMatching(out, List.of("gentle", "standard", "intense", "scary", "pranks", "body", "pvp", "mature"), args[2]);
        } else if (args.length == 4 && staff && args[0].equalsIgnoreCase("setfor")) {
            addMatching(out, VALUES, args[3]);
        }
        return out;
    }

    private static void addMatching(List<String> out, List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(o);
            }
        }
    }
}
