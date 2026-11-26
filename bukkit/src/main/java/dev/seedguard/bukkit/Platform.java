package dev.seedguard.bukkit;

/** Server flavour detection. Each tier is a superset of the one before it. */
public enum Platform {
    CRAFTBUKKIT("CraftBukkit"),
    SPIGOT("Spigot"),
    PAPER("Paper"),
    PURPUR("Purpur");

    public final String display;

    Platform(String display) {
        this.display = display;
    }

    public boolean atLeast(Platform other) {
        return ordinal() >= other.ordinal();
    }

    public static Platform detect() {
        if (has("org.purpurmc.purpur.PurpurConfig")) return PURPUR;
        if (has("io.papermc.paper.configuration.Configurations") || has("com.destroystokyo.paper.PaperConfig")) return PAPER;
        if (has("org.spigotmc.SpigotConfig")) return SPIGOT;
        return CRAFTBUKKIT;
    }

    static boolean has(String cls) {
        try {
            Class.forName(cls, false, Platform.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
