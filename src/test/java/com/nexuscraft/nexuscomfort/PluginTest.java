package com.nexuscraft.nexuscomfort;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.nexuscraft.nexuscomfort.Kit.expect;

/** The plugin class started against the stub server, used like a real server would use it. */
public final class PluginTest {

    static void run(CommandExecutor ex, org.bukkit.command.CommandSender who, String... args) {
        ex.onCommand(who, new Command(), "comfort", args);
    }

    public static void main(String[] args) throws Exception {
        Kit.reset();
        File dir = Files.createTempDirectory("nexuscomfort-plugin").toFile();
        JavaPlugin.DATA_FOLDER = dir;
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("stub");
        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                (r.getLevel() == java.util.logging.Level.WARNING ? warnings : infos).add(r.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        log.addHandler(capture);

        Kit.TestPlayer ann = Kit.online(new Kit.TestPlayer("Ann", 0, 0));
        Kit.TestPlayer bob = Kit.online(new Kit.TestPlayer("Bob", 0, 0));
        Kit.TestPlayer cy = Kit.online(new Kit.TestPlayer("Cy", 0, 0));
        Kit.TestPlayer staff = Kit.online(new Kit.TestPlayer("Staff", 0, 0));
        staff.perms.add("nexuscomfort.admin");

        // Ann chose gentle in an earlier life of the server; Bob turned pvp on
        ComfortStore earlier = new ComfortStore(new File(dir, "data.yml"), w -> { });
        earlier.setPreset(ann.getUniqueId(), Preset.GENTLE);
        earlier.setPreset(bob.getUniqueId(), Preset.STANDARD);
        earlier.setOwn(bob.getUniqueId(), Category.PVP, true);
        earlier.flush();

        AtomicLong clock = new AtomicLong(Env.T0);
        ComfortPlugin plugin = new ComfortPlugin();
        plugin.clock = clock::get;
        plugin.onEnable();
        expect(JavaPlugin.CALLS.contains("saveDefaultConfig"), "the default config is written");
        expect(infos.contains("NexusComfort enabled -- 2 players have chosen their own settings."), "start-up says how many have chosen: " + infos);
        ComfortListener listener = null;
        for (Listener l : PluginManager.REGISTERED) {
            if (l instanceof ComfortListener cl) {
                listener = cl;
            }
        }
        expect(listener != null, "the guard is registered");
        expect(BukkitScheduler.REPEATING.size() == 1, "the save task is scheduled");
        CommandExecutor cmd = plugin.getCommand("comfort").getExecutor();
        expect(cmd instanceof ComfortCommand && plugin.getCommand("comfort").getTabCompleter() instanceof ComfortCommand, "the command and its completer are wired");
        ComfortApi api = Bukkit.getServicesManager().load(ComfortApi.class);
        expect(api != null, "the API is registered");
        expect(!api.allows(ann.getUniqueId(), "scary") && api.preset(ann.getUniqueId()).equals("gentle"), "Ann's choice from before is remembered");
        expect(api.allows(bob.getUniqueId(), "pvp") && api.allows(bob.getUniqueId(), "scary"), "and Bob's own switch");
        expect(api.allows(cy.getUniqueId(), "scary") && !api.allows(cy.getUniqueId(), "pvp") && api.preset(cy.getUniqueId()).equals("standard"), "a new player gets standard");
        expect(api.allows(cy.getUniqueId(), "weather"), "a category that is not ours is allowed");

        // ---- players use the command, the API follows
        run(cmd, cy, "scary", "off");
        run(cmd, cy, "pvp", "on");
        expect(!api.allows(cy.getUniqueId(), "scary") && api.allows(cy.getUniqueId(), "pvp"), "Cy's changes show in the API at once");

        // ---- the guard is live
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(bob, cy, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamage(hit);
        expect(!hit.isCancelled(), "Bob and Cy both have pvp on");
        EntityDamageByEntityEvent toAnn = new EntityDamageByEntityEvent(bob, ann, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamage(toAnn);
        expect(toAnn.isCancelled() && bob.heard("Ann has PvP turned off"), "but Ann does not: " + bob.messages);
        PlayerJoinEvent join = new PlayerJoinEvent(cy, "");
        listener.onJoin(join);
        expect(cy.heard("/comfort"), "joining shows the hint once");

        // ---- staff: reload and the ceiling
        plugin.getConfig().set("server.off-categories", List.of("mature"));
        JavaPlugin.CALLS.clear();
        run(cmd, staff, "reload");
        expect(JavaPlugin.CALLS.contains("reloadConfig"), "reload asks the server to re-read config.yml");
        run(cmd, cy, "mature", "on");
        expect(!api.allows(cy.getUniqueId(), "mature") && cy.heard("turned Mature themes off for everyone"), "the ceiling applies after a reload: " + cy.messages);
        plugin.getConfig().set("server.off-categories", List.of());
        plugin.getConfig().set("server.default-preset", "gentle");
        run(cmd, staff, "reload");
        expect(api.preset(UUID.randomUUID()).equals("gentle"), "a new starting preset applies to players who never chose");
        plugin.getConfig().set("server.default-preset", "wild");
        run(cmd, staff, "reload");
        expect(warnings.size() == 1 && warnings.get(0).contains("'wild'"), "a bad setting is reported in the log: " + warnings);
        warnings.clear();
        plugin.getConfig().set("server.default-preset", "standard");
        run(cmd, staff, "reload");
        expect(warnings.isEmpty() && api.preset(UUID.randomUUID()).equals("standard"), "and fixing it is quiet");

        run(cmd, staff, "setfor", "Bob", "gentle");
        expect(!api.allows(bob.getUniqueId(), "pvp") && api.preset(bob.getUniqueId()).equals("gentle"), "staff can change anyone's settings");

        // ---- saving and starting again
        File saved = new File(dir, "data.yml");
        String before = Files.readString(saved.toPath());
        BukkitScheduler.REPEATING.get(0).run();
        String after = Files.readString(saved.toPath());
        expect(!before.equals(after) && after.contains(cy.getUniqueId().toString()) && after.contains("hinted"), "the save task writes the changes: " + after);
        run(cmd, ann, "pvp", "on");
        plugin.onDisable();
        expect(BukkitScheduler.HANDLES.size() == 1 && BukkitScheduler.HANDLES.stream().allMatch(org.bukkit.scheduler.BukkitTask::isCancelled), "the save task is cancelled");
        expect(Bukkit.getServicesManager().load(ComfortApi.class) == null, "the API is unregistered");
        expect(Files.readString(saved.toPath()).contains(ann.getUniqueId().toString() + ".pvp"), "disabling saves the last change");

        infos.clear();
        BukkitScheduler.REPEATING.clear();
        ComfortPlugin second = new ComfortPlugin();
        second.clock = clock::get;
        second.getConfig().set("server.off-categories", List.of("pvp"));
        second.onEnable();
        expect(infos.contains("NexusComfort enabled -- 3 players have chosen their own settings."), "a second start remembers Ann, Bob and Cy: " + infos);
        ComfortApi api2 = Bukkit.getServicesManager().load(ComfortApi.class);
        expect(!api2.allows(ann.getUniqueId(), "pvp") && api2.preset(bob.getUniqueId()).equals("gentle") && api2.preset(ann.getUniqueId()).equals("gentle"), "with the right settings, and the config read at start-up (pvp off for everyone)");
        second.onDisable();
        log.removeHandler(capture);
        expect(warnings.isEmpty(), "no warnings the whole way: " + warnings);
        Kit.finish();
    }
}
