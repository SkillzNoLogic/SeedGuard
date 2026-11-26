package dev.seedguard.shared;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detects client mods by their translation keys (a "translation probe").
 *
 * <p>The server shows the client a fake sign. Each line is a translatable component made of
 * a mod's language key plus a random fallback string, and the server closes the sign editor
 * right away. The client resolves every line against its loaded language files and sends the
 * text back. A line that comes back as anything other than our random fallback means the
 * client has that key, so the mod is installed.
 *
 * <p>This is best-effort. Clients running anti-detection mods (ExploitPreventer,
 * ModDetectionPreventer and similar) return the fallback, so they look clean. A positive
 * result is reliable, because a vanilla client can't know a random nonce's translation.
 */
public final class ModProbe {
    public static final int MAX_LINES = 4;
    private static final SecureRandom RNG = new SecureRandom();

    /** SeedCrackerX keys (from assets/seedcrackerx/lang/en_us.json). These four are distinctive; generic ones like "title" are avoided. */
    public static final String DEFAULT_KEYS =
            "info.noWorldSeeds|No world seeds found;" +
            "finder.buriedTreasures|Buried treasures;" +
            "settings.openDatabase|Open the database;" +
            "finder.endCities|End cities";

    public record Key(String key, String english) {}

    public record Probe(List<Key> keys, List<String> nonces) {}

    public record Result(boolean detected, int hits, List<String> matchedKeys) {}

    private final List<Key> keys;
    private final int threshold;

    public ModProbe(String keySpec, int threshold) {
        this.keys = parse(keySpec);
        this.threshold = Math.max(1, Math.min(threshold, this.keys.size()));
    }

    public static List<Key> parse(String spec) {
        List<Key> out = new ArrayList<>();
        for (String part : spec.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int bar = p.indexOf('|');
            out.add(bar < 0 ? new Key(p, "") : new Key(p.substring(0, bar).trim(), p.substring(bar + 1).trim()));
            if (out.size() == MAX_LINES) break;
        }
        if (out.isEmpty()) throw new IllegalArgumentException("detection keys are empty");
        return List.copyOf(out);
    }

    public Probe newProbe() {
        List<String> nonces = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            nonces.add(String.format(Locale.ROOT, "sg%08x", RNG.nextInt()));
        }
        return new Probe(keys, List.copyOf(nonces));
    }

    public Result evaluate(Probe probe, String[] lines) {
        int hits = 0;
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < probe.keys().size() && i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            Key k = probe.keys().get(i);
            boolean resolved = !line.isEmpty()
                    && !line.equals(probe.nonces().get(i))
                    && !line.equals(k.key());
            if (resolved) {
                hits++;
                matched.add(k.key() + (line.equalsIgnoreCase(k.english()) ? "" : " (\"" + line + "\")"));
            }
        }
        return new Result(hits >= threshold, hits, matched);
    }
}
