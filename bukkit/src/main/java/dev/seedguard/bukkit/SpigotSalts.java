package dev.seedguard.bukkit;

import dev.seedguard.shared.SeedKey;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Writes per-world random structure salts into Spigot's in-memory spigot.yml (and to disk).
 *
 * <p>Spigot reads {@code world-settings.<world>.seed-*} when it constructs each world.
 * SeedGuard loads at STARTUP, before any world exists, so the salts apply on the first boot
 * with no restart. They also persist in spigot.yml, so structures stay where they are even if
 * SeedGuard is later removed.
 */
final class SpigotSalts {
    /** Vanilla salts (as Spigot's defaults). Pinned explicitly on unprotected worlds. */
    static final Map<String, Object> VANILLA = new LinkedHashMap<>();

    static {
        VANILLA.put("seed-village", 10387312);
        VANILLA.put("seed-desert", 14357617);
        VANILLA.put("seed-igloo", 14357618);
        VANILLA.put("seed-jungle", 14357619);
        VANILLA.put("seed-swamp", 14357620);
        VANILLA.put("seed-monument", 10387313);
        VANILLA.put("seed-shipwreck", 165745295);
        VANILLA.put("seed-ocean", 14357621);
        VANILLA.put("seed-outpost", 165745296);
        VANILLA.put("seed-endcity", 10387313);
        VANILLA.put("seed-nether", 30084232);
        VANILLA.put("seed-mansion", 10387319);
        VANILLA.put("seed-fossil", 14357921);
        VANILLA.put("seed-portal", 34222645);
        VANILLA.put("seed-ancientcity", 20083232);
        VANILLA.put("seed-trailruins", 83469867);
        VANILLA.put("seed-trialchambers", 94251327);
        VANILLA.put("seed-abandonedcamp", 91231127);
        VANILLA.put("seed-buriedtreasure", 10387320);
        VANILLA.put("seed-slime", 987234911);
        VANILLA.put("seed-mineshaft", "default");
        VANILLA.put("seed-stronghold", "default");
    }

    private final Logger log;
    private YamlConfiguration config;
    private File file;

    SpigotSalts(Logger log) {
        this.log = log;
    }

    boolean available() {
        try {
            Class<?> c = Class.forName("org.spigotmc.SpigotConfig");
            Field f = c.getField("config");
            config = (YamlConfiguration) f.get(null);
            try {
                Field cf = c.getDeclaredField("CONFIG_FILE");
                cf.setAccessible(true);
                file = (File) cf.get(null);
            } catch (ReflectiveOperationException ignored) {
            }
            if (config == null) config = Bukkit.spigot().getConfig();
            if (file == null) file = new File("spigot.yml");
            return config != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static Map<String, Object> protectedSalts(SeedKey worldKey, boolean slime) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : VANILLA.entrySet()) {
            String opt = e.getKey();
            if (opt.equals("seed-slime") && !slime) {
                out.put(opt, e.getValue());
            } else if (opt.equals("seed-stronghold")) {
                out.put(opt, Long.toString(worldKey.structureSeed(0x5354524f4e47L)));
            } else if (opt.equals("seed-mineshaft")) {
                out.put(opt, Integer.toString(worldKey.spigotSalt(opt)));
            } else {
                out.put(opt, worldKey.spigotSalt(opt));
            }
        }
        return out;
    }

    void write(String section, Map<String, Object> values) {
        String base = "world-settings." + section + ".";
        for (Map.Entry<String, Object> e : values.entrySet()) {
            config.set(base + e.getKey(), e.getValue());
        }
    }

    Object read(String section, String option) {
        return config.get("world-settings." + section + "." + option);
    }

    void save() {
        try {
            config.save(file);
        } catch (Exception e) {
            log.warning("Could not save " + file + " (salts are still active in memory this boot): " + e);
        }
    }
}
