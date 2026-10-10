package com.nexuscraft.nexuscomfort;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The outside guard: it cancels a few things other plugins do to a player who has turned them down, without
 * those plugins knowing. It can only go by what an event shows (who hit whom, what effect was added by a
 * plugin, how far a plugin moved someone), so it is deliberately narrow.
 */
public final class ComfortListener implements Listener {

    /** A player told "you can't" is not told again for this long. */
    static final long NAG_MS = 3000L;

    private final ComfortService comfort;
    private final ComfortConfig cfg;
    private final WarBridge war;
    private final LongSupplier clock;
    private final Map<UUID, Long> nagged = new HashMap<>();

    ComfortListener(ComfortService comfort, ComfortConfig cfg, WarBridge war, LongSupplier clock) {
        this.comfort = comfort;
        this.cfg = cfg;
        this.war = war;
        this.clock = clock;
    }

    private void nag(Player p, String text) {
        long now = clock.getAsLong();
        Long last = nagged.get(p.getUniqueId());
        if (last == null || now - last >= NAG_MS) {
            nagged.put(p.getUniqueId(), now);
            p.sendMessage(ColorText.translate(cfg.prefix + text));
        }
    }

    private static Player playerBehind(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ PvP

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!cfg.guardPvp || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = playerBehind(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (victim.getWorld() != null && cfg.pvpExemptWorlds.contains(victim.getWorld().getName().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        UUID a = attacker.getUniqueId();
        UUID v = victim.getUniqueId();
        if (war.fighting(a) && war.fighting(v)) {
            return;
        }
        if (!comfort.allows(a, Category.PVP)) {
            event.setCancelled(true);
            nag(attacker, cfg.serverOff(Category.PVP)
                    ? "&7PvP is off on this server outside wars."
                    : "&7You have PvP turned off, so you cannot hurt other players. &f/comfort&7 to change that.");
        } else if (!comfort.allows(v, Category.PVP)) {
            event.setCancelled(true);
            nag(attacker, "&7" + victim.getName() + " has PvP turned off.");
        }
    }

    // ------------------------------------------------------------------ effects other plugins add

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN
                || (event.getAction() != EntityPotionEffectEvent.Action.ADDED && event.getAction() != EntityPotionEffectEvent.Action.CHANGED)
                || event.getNewEffect() == null) {
            return;
        }
        String name = event.getNewEffect().getType().getKey().getKey();
        for (Category c : Category.values()) {
            if (cfg.effectsFor(c).contains(name) && !comfort.allows(p.getUniqueId(), c)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (cfg.teleportNudgeBlocks <= 0 || event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getName().equals(to.getWorld().getName())) {
            return;
        }
        double max = cfg.teleportNudgeBlocks;
        if (from.distanceSquared(to) <= max * max && !comfort.allows(event.getPlayer().getUniqueId(), Category.PRANKS)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ joining and leaving

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (cfg.welcomeHint && !comfort.store().hinted(p.getUniqueId())) {
            comfort.store().markHinted(p.getUniqueId());
            p.sendMessage(ColorText.translate(cfg.prefix + "&7You can choose how intense the server is for you: scary things, pranks, "
                    + "body effects, PvP and mature themes. Type &f/comfort&7 any time."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        nagged.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ the menu

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof ComfortGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p && event.getClickedInventory() == top
                && event.getRawSlot() >= 0 && event.getRawSlot() < ComfortGui.SIZE) {
            gui.click(p, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof ComfortGui) {
            event.setCancelled(true);
        }
    }
}
