package dev.seedguard.bukkit;

import dev.seedguard.bukkit.detect.ProbeService;
import dev.seedguard.bukkit.netty.ChannelInjector;
import dev.seedguard.bukkit.netty.PacketRewriter;
import dev.seedguard.bukkit.netty.SeedGuardHandler;
import dev.seedguard.shared.SeedKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class SeedGuardPlugin extends JavaPlugin implements Listener {
    private static final String MODE_PROTECTED = "protected";
    private static final String MODE_VANILLA = "vanilla";
    private static final String MODE_DEFAULT = "default";

    private Platform platform;
    private SeedKey master;
    private YamlConfiguration state;
    private File stateFile;
    private SpigotSalts salts;
    private HashedSeedSpoofer spoofer;
    private ChannelInjector injector;
    private ProbeService probes;
    private PaperFeatureSeeds.State featureState = PaperFeatureSeeds.State.UNAVAILABLE;
    private boolean saltsActive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadState();
        platform = Platform.detect();
        FileConfiguration cfg = getConfig();
        File serverRoot = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();

        // 1) Structure salts. This must run before worlds are built (load: STARTUP).
        if (cfg.getBoolean("structures.enabled", true)) {
            if (platform.atLeast(Platform.SPIGOT)) {
                applySalts(serverRoot, cfg.getBoolean("structures.protect-existing-worlds", false),
                        cfg.getBoolean("structures.slime-chunks", true));
            } else {
                getLogger().warning("CraftBukkit has no seed-* options; structure placement cannot be protected. Use Paper or Purpur.");
            }
        }

        // 2) Decorator seeds (Paper/Purpur)
        if (cfg.getBoolean("features.paper-feature-seeds", true)) {
            if (platform.atLeast(Platform.PAPER)) {
                featureState = PaperFeatureSeeds.ensure(serverRoot, getLogger());
                if (featureState == PaperFeatureSeeds.State.ENABLED_RESTART_REQUIRED) {
                    getLogger().severe("==============================================================");
                    getLogger().severe(" Paper random feature seeds were just enabled. RESTART ONCE");
                    getLogger().severe(" before anyone explores. Chunks generated this boot still");
                    getLogger().severe(" use vanilla decoration (dungeons, desert wells...).");
                    getLogger().severe(" If this is a brand-new world, delete it and restart.");
                    getLogger().severe("==============================================================");
                }
            } else {
                getLogger().warning("Plain Spigot has no feature-seed randomisation, so decorator cracking "
                        + "(dungeons, desert wells, end gateways) stays possible. Switch to Paper or Purpur for full protection.");
            }
        }

        // 3) Netty hook: hashed-seed spoofing + probe answers
        spoofer = new HashedSeedSpoofer(name -> master.derive(name));
        for (World w : Bukkit.getWorlds()) spoofer.register(w);
        probes = new ProbeService(this, p -> injector == null ? null : injector.handlerFor(p));
        probes.configure(cfg.getConfigurationSection("detection"));
        boolean hashed = cfg.getBoolean("hashed-seed.enabled", true);
        injector = new ChannelInjector(getLogger(), () -> new SeedGuardHandler(hashed ? spoofer::map : null, probes));
        if (!injector.start()) {
            // Spigot binds its listening socket after STARTUP plugins load; retry on the first tick.
            Bukkit.getScheduler().runTask(this, () -> {
                if (!injector.start()) {
                    getLogger().severe("Could not hook the network pipeline; hashed seed is NOT spoofed and the probe is disabled.");
                }
                for (Player p : Bukkit.getOnlinePlayers()) injector.handlerFor(p);
            });
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            SeedGuardHandler h = injector.handlerFor(p);
            if (h != null) h.bind(p.getUniqueId());
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(probes, this);
        banner();
    }

    @Override
    public void onDisable() {
        if (injector != null) injector.stop();
    }

    // ---- structure salts ------------------------------------------------------------------

    private void applySalts(File serverRoot, boolean protectExisting, boolean slime) {
        salts = new SpigotSalts(getLogger());
        if (!salts.available()) {
            getLogger().warning("spigot.yml is not accessible; structure salts not applied.");
            return;
        }
        File container = Bukkit.getWorldContainer();
        for (String name : discoverWorlds(serverRoot, container)) {
            String mode = state.getString("worlds." + name);
            if (mode == null) {
                boolean generated = isGenerated(new File(container, name));
                mode = generated && !protectExisting ? MODE_VANILLA : MODE_PROTECTED;
                state.set("worlds." + name, mode);
                getLogger().info("World '" + name + "': " + (mode.equals(MODE_PROTECTED)
                        ? "new → protected" : "already generated → kept vanilla (set structures.protect-existing-worlds to override)"));
            }
            writeMode(name, mode, slime);
        }
        // Worlds created later (Multiverse etc.) inherit the default section.
        salts.write("default", SpigotSalts.protectedSalts(master.derive("__default__"), slime));
        salts.save();
        saveState();
        saltsActive = true;
    }

    private void writeMode(String world, String mode, boolean slime) {
        switch (mode) {
            case MODE_PROTECTED -> salts.write(world, SpigotSalts.protectedSalts(master.derive(world), slime));
            case MODE_DEFAULT -> salts.write(world, SpigotSalts.protectedSalts(master.derive("__default__"), slime));
            default -> salts.write(world, SpigotSalts.VANILLA);
        }
    }

    private static Set<String> discoverWorlds(File serverRoot, File container) {
        Set<String> names = new LinkedHashSet<>();
        String level = "world";
        File props = new File(serverRoot, "server.properties");
        if (props.isFile()) {
            try (InputStream in = new FileInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                level = p.getProperty("level-name", "world").trim();
            } catch (IOException ignored) {
            }
        }
        names.add(level);
        names.add(level + "_nether");
        names.add(level + "_the_end");
        File[] dirs = container.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) if (new File(d, "level.dat").isFile()) names.add(d.getName());
        }
        return names;
    }

    private static boolean isGenerated(File worldDir) {
        if (!worldDir.isDirectory()) return false;
        try (Stream<Path> s = Files.walk(worldDir.toPath(), 3)) {
            return s.anyMatch(p -> p.toString().endsWith(".mca") && p.toFile().length() > 0);
        } catch (IOException e) {
            return true; // be conservative: don't move structures under an unreadable world
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        String name = e.getWorld().getName();
        if (saltsActive && state.getString("worlds." + name) == null) {
            // Built from the default section just now; remember that so it stays consistent.
            state.set("worlds." + name, MODE_DEFAULT);
            writeMode(name, MODE_DEFAULT, getConfig().getBoolean("structures.slime-chunks", true));
            salts.save();
            saveState();
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        spoofer.register(e.getWorld());
    }

    // ---- state ----------------------------------------------------------------------------

    private void loadState() {
        stateFile = new File(getDataFolder(), "state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
        String hex = state.getString("master-key");
        if (hex == null) {
            master = SeedKey.generate();
            state.options().setHeader(List.of(
                    "SeedGuard state. BACK THIS UP WITH YOUR WORLDS and keep it private.",
                    "master-key derives every world's salts and fake hashed seed.",
                    "worlds: protected = random salts | vanilla = untouched | default = created at runtime"));
            state.set("master-key", master.toHex());
            saveState();
        } else {
            master = SeedKey.fromHex(hex);
        }
    }

    private void saveState() {
        try {
            getDataFolder().mkdirs();
            state.save(stateFile);
        } catch (IOException e) {
            getLogger().severe("Could not save state.yml: " + e);
        }
    }

    // ---- UX -------------------------------------------------------------------------------

    private List<String> statusLines() {
        List<String> l = new ArrayList<>();
        l.add("Platform: " + platform.display);
        l.add("Structure salts: " + (saltsActive ? "active" : "off"));
        if (state.isConfigurationSection("worlds")) {
            for (String w : state.getConfigurationSection("worlds").getKeys(false)) {
                l.add("  " + w + " → " + state.getString("worlds." + w));
            }
        }
        l.add("Paper feature seeds: " + switch (featureState) {
            case ACTIVE -> "active";
            case ENABLED_RESTART_REQUIRED -> "enabled, RESTART REQUIRED";
            case UNAVAILABLE -> platform.atLeast(Platform.PAPER) ? "off" : "n/a on " + platform.display;
        });
        l.add("Hashed seed spoof: " + (getConfig().getBoolean("hashed-seed.enabled", true) ? "on" : "off")
                + " (network hook: " + injector.mode() + ")"
                + (PacketRewriter.lastError == null ? "" : " LAST ERROR: " + PacketRewriter.lastError));
        l.add("Mod probe: " + (probes.supported() ? (getConfig().getBoolean("detection.enabled") ? "on" : "off") : "n/a (needs Paper)")
                + ", detections this session: " + probes.detections());
        return l;
    }

    private void banner() {
        getLogger().info("SeedGuard active");
        for (String s : statusLines()) getLogger().info("  " + s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(ChatColor.GOLD + "SeedGuard");
                for (String s : statusLines()) sender.sendMessage(ChatColor.GRAY + s);
            }
            case "probe" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/" + label + " probe <player>");
                    return true;
                }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage(ChatColor.RED + "Player not online.");
                } else if (probes.probe(p)) {
                    sender.sendMessage(ChatColor.GREEN + "Probe sent to " + p.getName() + "; results go to console/staff.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Probe unavailable (needs Paper/Purpur and the network hook).");
                }
            }
            case "reload" -> {
                reloadConfig();
                probes.configure(getConfig().getConfigurationSection("detection"));
                sender.sendMessage(ChatColor.GREEN + "Detection settings reloaded. Salt/feature changes need a restart.");
            }
            default -> sender.sendMessage(ChatColor.RED + "/" + label + " <status|probe <player>|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Stream.of("status", "probe", "reload").filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("probe")) return null; // player names
        return List.of();
    }
}
