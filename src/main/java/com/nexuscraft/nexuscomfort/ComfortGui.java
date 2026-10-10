package com.nexuscraft.nexuscomfort;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The comfort menu: three presets on one row, the five switches under them. Plain chest menu. */
final class ComfortGui implements InventoryHolder {

    static final int SIZE = 36;
    static final int[] PRESET_SLOTS = {11, 13, 15};
    static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    static final int INFO = 29;
    static final int CLOSE = 31;
    static final int WRAP = 44;

    /** Everything the menu needs to know. */
    record Ctx(ComfortService comfort, ComfortConfig cfg) {
    }

    private final Ctx ctx;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    private ComfortGui(Ctx ctx) {
        this.ctx = ctx;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static void open(Ctx ctx, Player p) {
        ComfortGui gui = new ComfortGui(ctx);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&d&lComfort"));
        gui.fill(p);
        p.openInventory(gui.inventory);
    }

    private void fill(Player p) {
        ComfortService comfort = ctx.comfort();
        UUID id = p.getUniqueId();
        Preset mine = comfort.preset(id);
        boolean chose = comfort.chosePreset(id);
        Preset[] presets = Preset.values();
        for (int i = 0; i < presets.length; i++) {
            Preset preset = presets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&7" + preset.about());
            lore.add(preset == mine ? (chose ? "&aThis is your preset." : "&aThis is where you start.") : "&eClick to use it. Your own switches are reset to match.");
            put(PRESET_SLOTS[i], button(preset.icon(), (preset == mine ? "&a&l" : "&f&l") + preset.title(), lore), "preset:" + preset.key());
        }
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            ComfortService.Setting s = comfort.setting(id, c);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + c.about());
            String action;
            switch (s.source()) {
                case SERVER -> {
                    lore.add("&cThe server has turned this off for everyone.");
                    action = null;
                }
                case OWN -> {
                    lore.add("&7Your own choice. Click to switch it " + (s.on() ? "off" : "on") + ".");
                    action = "cat:" + c.key();
                }
                default -> {
                    lore.add("&7Set by your " + mine.title() + " preset. Click to switch it " + (s.on() ? "off" : "on") + ".");
                    action = "cat:" + c.key();
                }
            }
            Material icon = s.source() == ComfortService.Source.SERVER ? Material.BARRIER : c.icon();
            put(CATEGORY_SLOTS[i], button(icon, (s.on() ? "&a&l" : "&c&l") + c.title() + (s.on() ? ": on" : ": off"), lore), action);
        }
        put(INFO, button(Material.BOOK, "&6&lHow this works", List.of(
                "&7Each switch is only about what other things do to YOU. Turning one off never changes what anyone else sees.",
                "&7Systems that do not ask yet are not affected. Ask staff which ones do.")), null);
        put(CLOSE, button(Material.BARRIER, "&c&lClose", List.of()), "close");
    }

    /** The buttons that do something, by slot (for tests and for the click handler). */
    Map<Integer, String> actions() {
        return actions;
    }

    private void put(int slot, ItemStack item, String action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    void click(Player p, int slot) {
        String action = actions.get(slot);
        if (action == null) {
            return;
        }
        UUID id = p.getUniqueId();
        ComfortService comfort = ctx.comfort();
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.startsWith("preset:")) {
            Preset preset = Preset.of(action.substring(7));
            if (preset != null) {
                comfort.choosePreset(id, preset);
                p.sendMessage(ColorText.translate(ctx.cfg().prefix + Texts.preset(preset)));
                open(ctx, p);
            }
        } else if (action.startsWith("cat:")) {
            Category c = Category.of(action.substring(4));
            if (c != null && !ctx.cfg().serverOff(c)) {
                boolean now = !comfort.allows(id, c);
                comfort.choose(id, c, now);
                p.sendMessage(ColorText.translate(ctx.cfg().prefix + Texts.category(c, now)));
                open(ctx, p);
            }
        }
    }

    private static ItemStack button(Material m, String name, List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(name));
        List<String> out = new ArrayList<>();
        for (String s : lore) {
            for (String piece : wrap(s, WRAP)) {
                out.add(ColorText.translate(piece));
            }
        }
        meta.setLore(out);
        item.setItemMeta(meta);
        return item;
    }

    /** Splits a long lore line at spaces; every piece keeps the line's leading colour codes. */
    static List<String> wrap(String line, int width) {
        int start = 0;
        while (start + 1 < line.length() && line.charAt(start) == '&') {
            start += 2;
        }
        String codes = line.substring(0, start);
        String text = line.substring(start);
        List<String> out = new ArrayList<>();
        if (text.length() <= width) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(codes + cur);
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        out.add(codes + cur);
        return out;
    }
}
