package com.nexuscraft.nexuscomfort;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class ComfortPlugin extends JavaPlugin {

    /** The clock the guard's message throttle and the war lookup run on; tests replace it. */
    LongSupplier clock = System::currentTimeMillis;

    private final ComfortConfig cfg = new ComfortConfig();
    private ComfortStore store;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();
        store = new ComfortStore(new File(getDataFolder(), "data.yml"), getLogger()::warning);
        store.load();
        ComfortService comfort = new ComfortService(cfg, store);
        WarBridge war = new WarBridge(clock);
        getServer().getPluginManager().registerEvents(new ComfortListener(comfort, cfg, war, clock), this);
        ComfortCommand command = new ComfortCommand(comfort, cfg, war::connected, this::reload);
        getCommand("comfort").setExecutor(command);
        getCommand("comfort").setTabCompleter(command);
        getServer().getServicesManager().register(ComfortApi.class, new ComfortApi() {
            @Override
            public boolean allows(UUID player, String category) {
                return comfort.allows(player, category);
            }

            @Override
            public String preset(UUID player) {
                return comfort.preset(player).key();
            }
        }, this, ServicePriority.Normal);
        saveTask = getServer().getScheduler().runTaskTimer(this, store::flush, 600L, 600L);
        getLogger().info("NexusComfort enabled -- " + store.chosenCount() + " players have chosen their own settings.");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (store != null) {
            store.flush();
        }
    }

    private void readConfig() {
        cfg.load(getConfig());
        for (String problem : cfg.problems()) {
            getLogger().warning(problem);
        }
    }

    private void reload() {
        reloadConfig();
        readConfig();
    }
}
