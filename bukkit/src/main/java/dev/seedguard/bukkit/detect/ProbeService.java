package dev.seedguard.bukkit.detect;

import dev.seedguard.bukkit.netty.ChannelInjector;
import dev.seedguard.bukkit.netty.SeedGuardHandler;
import io.netty.channel.Channel;
import dev.seedguard.shared.ModProbe;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Runs the translation-key probe (see {@link ModProbe}) for each joining player. */
public final class ProbeService implements Listener, SeedGuardHandler.ProbeSink {
    private record Pending(ModProbe.Probe probe, Location at, long sentAt) {}

    private final Plugin plugin;
    private final Function<Player, SeedGuardHandler> handlerLookup;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private volatile ModProbe probe;
    private volatile ConfigurationSection cfg;
    private final boolean supported;
    private int detections;

    public ProbeService(Plugin plugin, Function<Player, SeedGuardHandler> handlerLookup) {
        this.plugin = plugin;
        this.handlerLookup = handlerLookup;
        boolean s;
        try {
            s = PaperSignSender.supported();
        } catch (Throwable t) { // Spigot: Adventure/Paper classes absent
            s = false;
        }
        this.supported = s;
    }

    public boolean supported() {
        return supported;
    }

    public int detections() {
        return detections;
    }

    public void configure(ConfigurationSection section) {
        this.cfg = section;
        this.probe = new ModProbe(section.getString("keys", ModProbe.DEFAULT_KEYS), section.getInt("threshold", 2));
    }

    private boolean enabled() {
        return supported && cfg != null && cfg.getBoolean("enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        SeedGuardHandler h = handlerLookup.apply(p);
        if (h != null) h.bind(p.getUniqueId());
        if (!enabled() || p.hasPermission("seedguard.bypass")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) probe(p);
        }, Math.max(20, cfg.getInt("delay-ticks", 60)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    /** @return false if a probe could not be sent */
    public boolean probe(Player p) {
        if (!supported || probe == null) return false;
        SeedGuardHandler h = handlerLookup.apply(p);
        if (h == null) return false; // without the netty hook, the answer would be lost
        h.bind(p.getUniqueId());
        Location at = probeLocation(p);
        ModProbe.Probe pr = probe.newProbe();
        Channel channel = ChannelInjector.channelOf(p);
        if (channel == null) return false;
        pending.put(p.getUniqueId(), new Pending(pr, at, System.nanoTime()));
        PaperSignSender.place(p, at, pr);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (!PaperSignSender.openEditor(p, channel, at)) {
                pending.remove(p.getUniqueId());
                restore(p, at);
                return;
            }
            // Closing the editor makes the client send the resolved lines back.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.closeInventory();
            }, 2L);
        }, 1L);
        int timeout = cfg == null ? 200 : cfg.getInt("timeout-ticks", 200);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending left = pending.remove(p.getUniqueId());
            if (left != null && p.isOnline()) {
                restore(p, left.at());
                plugin.getLogger().info(p.getName() + " did not answer the mod probe (anti-detection client or lag).");
            }
        }, timeout);
        return true;
    }

    private static Location probeLocation(Player p) {
        Location l = p.getLocation().getBlock().getLocation();
        World w = l.getWorld();
        if (l.getBlockY() - 2 >= w.getMinHeight()) return l.subtract(0, 2, 0);
        return l.add(0, 3, 0);
    }

    private static void restore(Player p, Location at) {
        p.sendBlockChange(at, at.getBlock().getBlockData());
    }

    /** Called on the netty thread. */
    @Override
    public boolean onSignUpdate(UUID uuid, String[] lines) {
        Pending pend = pending.remove(uuid);
        if (pend == null) return false;
        ModProbe.Result r = probe.evaluate(pend.probe(), lines);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;
            restore(p, pend.at());
            if (r.detected()) act(p, r);
        });
        return true;
    }

    private void act(Player p, ModProbe.Result r) {
        detections++;
        String what = String.join(", ", r.matchedKeys());
        plugin.getLogger().warning(p.getName() + " has a seed-cracking client mod (resolved: " + what + ")");
        if (cfg.getBoolean("notify-staff", true)) {
            String msg = ChatColor.RED + "[SeedGuard] " + ChatColor.GRAY + p.getName() + " was flagged for SeedCrackerX.";
            for (Player o : Bukkit.getOnlinePlayers()) if (o.hasPermission("seedguard.notify")) o.sendMessage(msg);
        }
        List<String> cmds = cfg.getStringList("commands");
        for (String c : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
        if ("kick".equalsIgnoreCase(cfg.getString("action", "kick"))) {
            p.kickPlayer(ChatColor.translateAlternateColorCodes('&', cfg.getString("kick-message", "Seed-cracking mods are not allowed.")));
        }
    }
}
