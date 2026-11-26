plugins {
    java
}

group = "dev.seedguard"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API is a superset of Spigot/Bukkit. Paper-only calls are isolated in
    // dev.seedguard.bukkit.detect.PaperSignSender, so one jar runs on all of them.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-transport:4.1.97.Final")
}

sourceSets.main {
    java.srcDir("../shared/src/main/java")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17) // MC 1.20–1.20.4 servers run on Java 17
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveFileName.set("SeedGuard-Bukkit-${project.version}.jar")
}
