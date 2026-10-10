package com.nexuscraft.nexuscomfort;

import com.nexuscraft.nexusworldwar.WorldWarApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** The guard, as the server's events see it. */
public final class ListenerTest {

    /** Who the war says is fighting. */
    static final class FakeWar implements WorldWarApi {
        final Set<UUID> fighters = new HashSet<>();

        @Override
        public boolean warActive() {
            return !fighters.isEmpty();
        }

        @Override
        public boolean isFighter(UUID player) {
            return fighters.contains(player);
        }

        @Override
        public boolean hasJoined(UUID player) {
            return fighters.contains(player);
        }
    }

    static EntityDamageByEntityEvent hit(Entity from, Entity to) {
        return new EntityDamageByEntityEvent(from, to, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    }

    static boolean cancelledHit(ComfortListener l, Entity from, Entity to) {
        EntityDamageByEntityEvent ev = hit(from, to);
        l.onDamage(ev);
        return ev.isCancelled();
    }

    static boolean effectCancelled(ComfortListener l, Kit.TestPlayer p, PotionEffectType type, EntityPotionEffectEvent.Cause cause, EntityPotionEffectEvent.Action action) {
        EntityPotionEffectEvent ev = new EntityPotionEffectEvent(p, null, new PotionEffect(type, 100, 0), cause, action, false);
        l.onEffect(ev);
        return ev.isCancelled();
    }

    static boolean nudgeCancelled(ComfortListener l, Kit.TestPlayer p, Location from, Location to, PlayerTeleportEvent.TeleportCause cause) {
        PlayerTeleportEvent ev = new PlayerTeleportEvent(p, from, to, cause);
        l.onTeleport(ev);
        return ev.isCancelled();
    }

    public static void main(String[] args) throws Exception {
        Env e = new Env();
        FakeWar war = new FakeWar();
        Bukkit.getServicesManager().register(WorldWarApi.class, war, new Kit.TestPlugin(), ServicePriority.Normal);
        WarBridge bridge = new WarBridge(e.clock::get);
        ComfortListener l = new ComfortListener(e.comfort, e.cfg, bridge, e.clock::get);

        // ---- PvP: needs both sides to have it on
        Kit.TestPlayer ann = e.on("Ann", Preset.INTENSE);
        Kit.TestPlayer bee = e.on("Bee", Preset.INTENSE);
        Kit.TestPlayer cal = e.player("Cal");
        Kit.TestPlayer dan = e.on("Dan", Preset.GENTLE);
        expect(!cancelledHit(l, ann, bee) && !cancelledHit(l, bee, ann), "both have pvp on: they can fight");
        expect(cancelledHit(l, cal, ann) && cal.heard("You have PvP turned off") && cal.heard("/comfort"), "pvp off: you cannot hurt anyone, and you are told how to change it: " + cal.messages);
        e.clock.addAndGet(5000L);
        expect(cancelledHit(l, ann, cal) && ann.heard("Cal has PvP turned off"), "and nobody can hurt you: " + ann.messages);
        expect(cancelledHit(l, cal, dan) && cancelledHit(l, dan, cal), "both off: no");
        expect(!cancelledHit(l, ann, ann), "hurting yourself is not PvP");
        expect(!cancelledHit(l, ann, new Zombie()), "a mob is not a player");
        expect(!cancelledHit(l, new Zombie(), ann), "a mob hurting a player is not PvP");
        expect(!cancelledHit(l, null, ann), "no damager");

        // arrows
        Projectile arrowFromCal = new Projectile(cal);
        Projectile arrowFromAnn = new Projectile(ann);
        expect(cancelledHit(l, arrowFromCal, ann), "an arrow counts as its shooter (pvp off)");
        expect(cancelledHit(l, arrowFromAnn, cal), "and at a player who has it off");
        expect(!cancelledHit(l, arrowFromAnn, bee), "and is fine between players who both have it on");
        expect(!cancelledHit(l, new Projectile(null), cal), "an arrow from a dispenser is nobody's PvP");

        // a war overrides it for the two who are fighting
        expect(cancelledHit(l, cal, ann), "no war yet: still no");
        war.fighters.add(cal.getUniqueId());
        war.fighters.add(dan.getUniqueId());
        expect(!cancelledHit(l, cal, dan) && !cancelledHit(l, dan, cal), "two war fighters can hurt each other even with pvp off");
        expect(cancelledHit(l, cal, ann), "a fighter with pvp off still cannot hurt someone who is not fighting");
        war.fighters.add(ann.getUniqueId());
        war.fighters.remove(cal.getUniqueId());
        expect(cancelledHit(l, ann, cal) && cancelledHit(l, cal, ann), "one fighter, one not: the usual rule");
        war.fighters.clear();

        // exempt worlds, the switch, a ceiling
        e.cfg.pvpExemptWorlds.add("world");
        expect(!cancelledHit(l, cal, dan), "an exempt world: the guard does not apply");
        e.cfg.pvpExemptWorlds.clear();
        e.cfg.guardPvp = false;
        expect(!cancelledHit(l, cal, dan), "guard.pvp off: the guard does not apply");
        e.cfg.guardPvp = true;
        e.cfg.offCategories.add("pvp");
        e.clock.addAndGet(5000L);
        expect(cancelledHit(l, ann, bee) && ann.heard("PvP is off on this server"), "a server-wide ceiling: nobody, whatever they chose: " + ann.messages);
        e.cfg.offCategories.clear();

        // told once in a while
        e.clock.addAndGet(5000L);
        cal.messages.clear();
        cancelledHit(l, cal, ann);
        cancelledHit(l, cal, ann);
        expect(cal.messages.size() == 1, "two hits in a row are explained once: " + cal.messages);
        e.clock.addAndGet(ComfortListener.NAG_MS);
        cal.messages.clear();
        cancelledHit(l, cal, ann);
        expect(cal.messages.size() == 1, "and again after a few seconds");
        cal.messages.clear();
        l.onQuit(new PlayerQuitEvent(cal, ""));
        cancelledHit(l, cal, ann);
        expect(cal.messages.size() == 1, "leaving forgets that they were told");

        // ---- effects other plugins add
        Kit.TestPlayer gentle = e.on("Gentle", Preset.GENTLE);
        Kit.TestPlayer standard = e.on("Standard", Preset.STANDARD);
        EntityPotionEffectEvent.Cause plugin = EntityPotionEffectEvent.Cause.PLUGIN;
        EntityPotionEffectEvent.Action added = EntityPotionEffectEvent.Action.ADDED;
        expect(effectCancelled(l, gentle, PotionEffectType.DARKNESS, plugin, added), "darkness from a plugin: cancelled when scary is off");
        expect(effectCancelled(l, gentle, PotionEffectType.NAUSEA, plugin, EntityPotionEffectEvent.Action.CHANGED), "nausea from a plugin: cancelled when body is off (also when it changes an effect)");
        expect(!effectCancelled(l, standard, PotionEffectType.DARKNESS, plugin, added) && !effectCancelled(l, standard, PotionEffectType.NAUSEA, plugin, added), "not when they are on");
        expect(!effectCancelled(l, gentle, PotionEffectType.SPEED, plugin, added), "an effect nobody listed is left alone");
        expect(!effectCancelled(l, gentle, PotionEffectType.DARKNESS, EntityPotionEffectEvent.Cause.COMMAND, added)
                && !effectCancelled(l, gentle, PotionEffectType.DARKNESS, EntityPotionEffectEvent.Cause.POTION_DRINK, added)
                && !effectCancelled(l, gentle, PotionEffectType.DARKNESS, EntityPotionEffectEvent.Cause.ATTACK, added), "only a plugin's effects, not a drink, a command or a monster's attack");
        expect(!effectCancelled(l, gentle, PotionEffectType.DARKNESS, plugin, EntityPotionEffectEvent.Action.REMOVED)
                && !effectCancelled(l, gentle, PotionEffectType.DARKNESS, plugin, EntityPotionEffectEvent.Action.CLEARED), "taking an effect away is always fine");
        EntityPotionEffectEvent none = new EntityPotionEffectEvent(gentle, null, null, plugin, added, false);
        l.onEffect(none);
        EntityPotionEffectEvent mob = new EntityPotionEffectEvent(new org.bukkit.entity.LivingEntity(), null, new PotionEffect(PotionEffectType.DARKNESS, 100, 0), plugin, added, false);
        l.onEffect(mob);
        expect(!none.isCancelled() && !mob.isCancelled(), "no effect, or not a player: ignored");
        e.cfg.pranksEffects.add("levitation");
        e.cfg.matureEffects.add("hunger");
        expect(effectCancelled(l, gentle, PotionEffectType.LEVITATION, plugin, added) && effectCancelled(l, gentle, PotionEffectType.HUNGER, plugin, added), "pranks and mature lists work the same way");
        expect(!effectCancelled(l, standard, PotionEffectType.LEVITATION, plugin, added), "pranks are on for standard");
        e.comfort.choose(standard.getUniqueId(), Category.SCARY, false);
        expect(effectCancelled(l, standard, PotionEffectType.DARKNESS, plugin, added), "one switch turned off is enough");
        e.comfort.choose(standard.getUniqueId(), Category.SCARY, null);

        // ---- small teleports by plugins
        Location from = new Location(Kit.WORLD, 0, 64, 0);
        PlayerTeleportEvent.TeleportCause by = PlayerTeleportEvent.TeleportCause.PLUGIN;
        expect(nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 3, 64, 4), by), "a 5 block nudge: cancelled when pranks are off");
        expect(nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 8, 64, 0), by), "exactly 8 blocks: still a nudge");
        expect(!nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 9, 64, 0), by), "9 blocks: a real teleport");
        expect(!nudgeCancelled(l, standard, from, new Location(Kit.WORLD, 3, 64, 0), by), "not when pranks are on");
        expect(!nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 3, 64, 0), PlayerTeleportEvent.TeleportCause.COMMAND)
                && !nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 3, 64, 0), PlayerTeleportEvent.TeleportCause.ENDER_PEARL), "only a plugin's, not a command or a pearl");
        expect(!nudgeCancelled(l, gentle, from, new Location(Kit.NETHER, 3, 64, 0), by), "another world is not a nudge");
        expect(!nudgeCancelled(l, gentle, from, null, by) && !nudgeCancelled(l, gentle, null, from, by), "no destination: ignored");
        e.cfg.teleportNudgeBlocks = 0;
        expect(!nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 1, 64, 0), by), "nudge guard off");
        expect(!nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 0, 64, 0), by), "off means off, even for a teleport to the same spot");
        e.cfg.teleportNudgeBlocks = 8;
        expect(!nudgeCancelled(l, gentle, new Location(null, 0, 64, 0), new Location(Kit.WORLD, 1, 64, 0), by)
                && !nudgeCancelled(l, gentle, from, new Location(null, 1, 64, 0), by), "a location with no world is ignored, not a crash");
        e.cfg.teleportNudgeBlocks = 0;
        e.cfg.teleportNudgeBlocks = 2;
        expect(nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 2, 64, 0), by) && !nudgeCancelled(l, gentle, from, new Location(Kit.WORLD, 3, 64, 0), by), "and the distance is the setting's");
        e.cfg.teleportNudgeBlocks = 8;

        // ---- the hint
        Kit.TestPlayer newbie = e.player("Newbie");
        l.onJoin(new PlayerJoinEvent(newbie, ""));
        expect(newbie.heard("/comfort") && newbie.messages.size() == 1 && e.store.hinted(newbie.getUniqueId()), "a new player is told once: " + newbie.messages);
        l.onJoin(new PlayerJoinEvent(newbie, ""));
        expect(newbie.messages.size() == 1, "only once");
        e.cfg.welcomeHint = false;
        Kit.TestPlayer other = e.player("Other");
        l.onJoin(new PlayerJoinEvent(other, ""));
        expect(other.messages.isEmpty() && !e.store.hinted(other.getUniqueId()), "with the hint off, nothing is said");
        e.cfg.welcomeHint = true;

        // ---- the menu
        ComfortGui.open(new ComfortGui.Ctx(e.comfort, e.cfg), ann);
        InventoryView v = new InventoryView();
        v.setTopInventory(ann.opened);
        v.setPlayer(ann);
        InventoryClickEvent click = new InventoryClickEvent(v, InventoryType.SlotType.CONTAINER, ComfortGui.CLOSE, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(click);
        expect(click.isCancelled() && ann.opened == null, "menu clicks are cancelled and reach the button");
        InventoryDragEvent drag = new InventoryDragEvent(v);
        l.onDrag(drag);
        expect(drag.isCancelled(), "drags are cancelled");
        InventoryView foreign = new InventoryView();
        foreign.setTopInventory(new Inventory());
        foreign.setPlayer(ann);
        InventoryClickEvent fc = new InventoryClickEvent(foreign, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(fc);
        InventoryDragEvent fd = new InventoryDragEvent(foreign);
        l.onDrag(fd);
        expect(!fc.isCancelled() && !fd.isCancelled(), "other menus are left alone");
        InventoryClickEvent noTop = new InventoryClickEvent(new InventoryView(), InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(noTop);
        l.onDrag(new InventoryDragEvent(new InventoryView()));
        expect(!noTop.isCancelled(), "no top inventory: ignored");
        ComfortGui.open(new ComfortGui.Ctx(e.comfort, e.cfg), ann);
        InventoryView v2 = new InventoryView();
        v2.setTopInventory(ann.opened);
        v2.setPlayer(ann);
        InventoryClickEvent below = new InventoryClickEvent(v2, InventoryType.SlotType.QUICKBAR, ComfortGui.CLOSE, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        l.onClick(below);
        expect(below.isCancelled() && ann.opened != null, "a click in the player's own inventory is cancelled but is not a button");

        // ---- how each handler is registered
        Set<String> late = Set.of("onJoin", "onQuit");
        Set<String> menu = Set.of("onClick", "onDrag");
        int handlers = 0;
        for (Method method : ComfortListener.class.getDeclaredMethods()) {
            EventHandler h = method.getAnnotation(EventHandler.class);
            if (h == null) {
                continue;
            }
            handlers++;
            if (late.contains(method.getName())) {
                expect(h.priority() == EventPriority.MONITOR, method.getName() + " only watches");
            } else if (menu.contains(method.getName())) {
                expect(h.priority() == EventPriority.HIGH && !h.ignoreCancelled(), method.getName() + " runs high and always");
            } else {
                expect(h.priority() == EventPriority.HIGH && h.ignoreCancelled(), method.getName() + " runs high and skips events already cancelled");
            }
        }
        expect(handlers == 7, "seven handlers: " + handlers);
        Kit.finish();
    }
}
