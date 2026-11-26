package dev.seedguard.mod;

import dev.seedguard.shared.ModProbe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Global settings in {@code config/seedguard.properties}. */
public final class SeedGuardConfig {
    public boolean protectExistingWorlds = false;
    public boolean carvers = false;
    public boolean detection = true;
    public int detectionDelayTicks = 60;
    public int detectionTimeoutTicks = 200;
    public int detectionThreshold = 2;
    public String detectionKeys = ModProbe.DEFAULT_KEYS;
    public boolean detectionKick = true;
    public String kickMessage = "Seed-cracking mods (SeedCrackerX) are not allowed on this server.";
    public boolean notifyOps = true;

    static SeedGuardConfig load(Path file) {
        SeedGuardConfig c = new SeedGuardConfig();
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                SeedGuard.LOG.warn("Could not read {}: {}", file, e.toString());
            }
        }
        c.protectExistingWorlds = bool(p, "protect-existing-worlds", c.protectExistingWorlds);
        c.carvers = bool(p, "obfuscate-carvers", c.carvers);
        c.detection = bool(p, "detection.enabled", c.detection);
        c.detectionDelayTicks = integer(p, "detection.delay-ticks", c.detectionDelayTicks);
        c.detectionTimeoutTicks = integer(p, "detection.timeout-ticks", c.detectionTimeoutTicks);
        c.detectionThreshold = integer(p, "detection.threshold", c.detectionThreshold);
        c.detectionKeys = p.getProperty("detection.keys", c.detectionKeys);
        c.detectionKick = "kick".equalsIgnoreCase(p.getProperty("detection.action", c.detectionKick ? "kick" : "log"));
        c.kickMessage = p.getProperty("detection.kick-message", c.kickMessage);
        c.notifyOps = bool(p, "detection.notify-ops", c.notifyOps);
        if (!Files.exists(file)) c.write(file);
        return c;
    }

    private void write(Path file) {
        Properties p = new Properties();
        p.setProperty("protect-existing-worlds", Boolean.toString(protectExistingWorlds));
        p.setProperty("obfuscate-carvers", Boolean.toString(carvers));
        p.setProperty("detection.enabled", Boolean.toString(detection));
        p.setProperty("detection.delay-ticks", Integer.toString(detectionDelayTicks));
        p.setProperty("detection.timeout-ticks", Integer.toString(detectionTimeoutTicks));
        p.setProperty("detection.threshold", Integer.toString(detectionThreshold));
        p.setProperty("detection.keys", detectionKeys);
        p.setProperty("detection.action", detectionKick ? "kick" : "log");
        p.setProperty("detection.kick-message", kickMessage);
        p.setProperty("detection.notify-ops", Boolean.toString(notifyOps));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, """
                         SeedGuard global settings
                         protect-existing-worlds: also move structures/slime chunks/end pillars in worlds that already have chunks
                                                  (creates cut-off structures at the explored border). Per-world choices live in <world>/seedguard.properties
                         obfuscate-carvers: also re-seed caves/ravines (terrain then differs from vanilla seed maps)
                         detection.*: translation-key probe for SeedCrackerX (dedicated servers only). action = kick | log""");
            }
        } catch (IOException e) {
            SeedGuard.LOG.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }

    private static int integer(Properties p, String k, int d) {
        try {
            String v = p.getProperty(k);
            return v == null ? d : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }
}
