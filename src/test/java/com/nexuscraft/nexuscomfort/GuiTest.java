package com.nexuscraft.nexuscomfort;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** The comfort menu and the words players are told. */
public final class GuiTest {

    static String lore(ItemStack i) {
        return i == null ? "" : String.join("\n", i.getItemMeta().getLore());
    }

    static String name(ItemStack i) {
        return i == null ? "" : i.getItemMeta().getDisplayName();
    }

    static ComfortGui gui(Kit.TestPlayer p) {
        return (ComfortGui) p.opened.getHolder();
    }

    public static void main(String[] args) {
        Env e = new Env();
        ComfortGui.Ctx ctx = new ComfortGui.Ctx(e.comfort, e.cfg);
        Kit.TestPlayer ann = e.player("Ann");

        // ---- a new player
        ComfortGui.open(ctx, ann);
        expect(ann.opened != null && gui(ann) != null, "the menu opens");
        boolean inside = true;
        for (int slot = ComfortGui.SIZE; slot < ann.opened.getSize(); slot++) {
            inside &= ann.opened.getItem(slot) == null;
        }
        expect(inside, "every button is inside the 36 slots");
        ItemStack gentle = ann.opened.getItem(ComfortGui.PRESET_SLOTS[0]);
        ItemStack standard = ann.opened.getItem(ComfortGui.PRESET_SLOTS[1]);
        ItemStack intense = ann.opened.getItem(ComfortGui.PRESET_SLOTS[2]);
        expect(name(gentle).contains("Gentle") && name(standard).contains("Standard") && name(intense).contains("Intense"), "three presets in order");
        expect(gentle.getType() == Material.LIME_WOOL && standard.getType() == Material.YELLOW_WOOL && intense.getType() == Material.RED_WOOL, "colour-coded");
        expect(lore(standard).contains("This is where you start") && !lore(gentle).contains("This is"), "standard is where a new player starts: " + lore(standard));
        expect(lore(gentle).contains("Everything off") && lore(standard).contains("pranks and body effects on") && lore(intense).contains("Everything on"), "each preset says what it does: " + lore(gentle) + lore(standard));
        expect(lore(gentle).contains("Click to use it") && lore(gentle).contains("reset"), "the others say what clicking does: " + lore(gentle));
        List<Category> cats = List.of(Category.values());
        for (int i = 0; i < cats.size(); i++) {
            ItemStack b = ann.opened.getItem(ComfortGui.CATEGORY_SLOTS[i]);
            boolean on = e.comfort.allows(ann.getUniqueId(), cats.get(i));
            expect(b.getType() == cats.get(i).icon() && name(b).contains(cats.get(i).title()) && name(b).endsWith(on ? ": on" : ": off"), cats.get(i) + " shows its state: " + name(b));
            expect(lore(b).contains(cats.get(i).about().substring(0, 20)) && lore(b).contains("Set by your Standard preset"), cats.get(i) + " says why: " + lore(b));
        }
        expect(ann.opened.getItem(ComfortGui.INFO).getType() == Material.BOOK && lore(ann.opened.getItem(ComfortGui.INFO)).contains("Each switch is only about") && lore(ann.opened.getItem(ComfortGui.INFO)).contains("do to YOU"), "the info book");
        expect(ann.opened.getItem(ComfortGui.CLOSE).getType() == Material.BARRIER, "close");
        expect(gui(ann).actions().size() == 9, "nine buttons do something: " + gui(ann).actions().keySet());

        // ---- clicking a switch
        int pvpSlot = ComfortGui.CATEGORY_SLOTS[Category.PVP.ordinal()];
        gui(ann).click(ann, pvpSlot);
        expect(e.comfort.allows(ann.getUniqueId(), Category.PVP) && ann.heard("PvP outside wars is on for you"), "clicking turns it on: " + ann.messages);
        ItemStack pvp = ann.opened.getItem(pvpSlot);
        expect(name(pvp).endsWith(": on") && lore(pvp).contains("Your own choice") && lore(pvp).contains("switch it off"), "and the menu shows it: " + lore(pvp));
        gui(ann).click(ann, pvpSlot);
        expect(!e.comfort.allows(ann.getUniqueId(), Category.PVP) && ann.heard("is off for you"), "clicking again turns it off: " + ann.messages);
        int scarySlot = ComfortGui.CATEGORY_SLOTS[Category.SCARY.ordinal()];
        gui(ann).click(ann, scarySlot);
        expect(!e.comfort.allows(ann.getUniqueId(), Category.SCARY) && ann.heard("Scary things is off for you"), "scary off");

        // ---- clicking a preset resets the switches
        gui(ann).click(ann, ComfortGui.PRESET_SLOTS[2]);
        expect(e.comfort.preset(ann.getUniqueId()) == Preset.INTENSE && e.comfort.allows(ann.getUniqueId(), Category.SCARY) && e.comfort.allows(ann.getUniqueId(), Category.MATURE), "intense turns everything on, scary included");
        expect(ann.heard("Intense") && ann.heard("preset now"), "and says so: " + ann.messages);
        intense = ann.opened.getItem(ComfortGui.PRESET_SLOTS[2]);
        expect(lore(intense).contains("This is your preset") && name(intense).contains("&a") == false && name(intense).startsWith("§a"), "the chosen preset is marked: " + name(intense) + lore(intense));
        expect(lore(ann.opened.getItem(ComfortGui.PRESET_SLOTS[1])).contains("Click to use it"), "and the old one is not");
        gui(ann).click(ann, ComfortGui.PRESET_SLOTS[0]);
        expect(!e.comfort.allows(ann.getUniqueId(), Category.SCARY), "gentle turns everything off");

        // ---- a ceiling set by the server
        e.cfg.offCategories.add("mature");
        ComfortGui.open(ctx, ann);
        int matureSlot = ComfortGui.CATEGORY_SLOTS[Category.MATURE.ordinal()];
        ItemStack mature = ann.opened.getItem(matureSlot);
        expect(mature.getType() == Material.BARRIER && name(mature).endsWith(": off") && lore(mature).contains("turned this off for everyone"), "the server's ceiling is shown: " + lore(mature));
        expect(gui(ann).actions().size() == 8 && !gui(ann).actions().containsKey(matureSlot), "and cannot be clicked");
        ann.messages.clear();
        gui(ann).click(ann, matureSlot);
        expect(ann.messages.isEmpty() && !e.comfort.allows(ann.getUniqueId(), Category.MATURE), "clicking it does nothing");
        // a stale menu cannot get round the ceiling
        gui(ann).actions().put(matureSlot, "cat:mature");
        gui(ann).click(ann, matureSlot);
        expect(!e.comfort.allows(ann.getUniqueId(), Category.MATURE), "even a stale button");
        e.cfg.offCategories.clear();

        // ---- other slots, junk actions
        ComfortGui.open(ctx, ann);
        gui(ann).click(ann, 0);
        gui(ann).click(ann, ComfortGui.INFO);
        gui(ann).actions().put(5, "preset:wild");
        gui(ann).click(ann, 5);
        gui(ann).actions().put(5, "cat:weather");
        gui(ann).click(ann, 5);
        gui(ann).actions().put(5, "nonsense");
        gui(ann).click(ann, 5);
        expect(e.comfort.preset(ann.getUniqueId()) == Preset.GENTLE, "other slots and odd actions do nothing");
        gui(ann).click(ann, ComfortGui.CLOSE);
        expect(ann.opened == null, "close closes");

        // ---- words
        expect(Texts.preset(Preset.GENTLE).contains("Gentle") && Texts.preset(Preset.GENTLE).contains("Everything off"), "preset words");
        expect(Texts.category(Category.PVP, true).contains("PvP outside wars is on for you"), "on words");
        expect(Texts.category(Category.PVP, false).contains("is off for you") && Texts.category(Category.PVP, false).contains("will not happen"), "off words");
        expect(Texts.backToPreset(Category.PVP, false).contains("follows your preset again (off)") && Texts.backToPreset(Category.PVP, true).contains("(on)"), "default words");

        // ---- wrapping
        expect(ComfortGui.wrap("&7short", 44).equals(List.of("&7short")), "short lines stay");
        List<String> w = ComfortGui.wrap("&7" + "word ".repeat(20).trim(), 44);
        boolean ok = w.size() == 3;
        for (String piece : w) {
            ok &= piece.startsWith("&7") && piece.length() - 2 <= 44;
        }
        expect(ok, "long lines wrap and keep their colour: " + w);
        expect(ComfortGui.wrap("&", 44).equals(List.of("&")) && ComfortGui.wrap("", 44).equals(List.of("")), "odd input");
        String a = "a".repeat(21), b = "b".repeat(22);
        w = ComfortGui.wrap(a + " " + b + " ccccc", 44);
        expect(w.size() == 2 && w.get(0).equals(a + " " + b) && w.get(1).equals("ccccc"), "a piece may be exactly the width: " + w);
        w = ComfortGui.wrap("a".repeat(40) + " ddddd " + "z".repeat(3), 44);
        expect(w.size() == 2 && w.get(0).equals("a".repeat(40)) && w.get(1).equals("ddddd zzz"), "a word that would pass the width starts a new piece: " + w);
        w = ComfortGui.wrap("&7" + "a".repeat(50) + " b", 44);
        boolean blank = false;
        for (String piece : w) {
            blank |= piece.replace("&7", "").isBlank();
        }
        expect(!blank && w.size() == 2, "no empty piece: " + w);
        Kit.finish();
    }
}
