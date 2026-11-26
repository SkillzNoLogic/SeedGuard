# SeedGuard

Stops world-seed cracking (SeedCrackerX and similar tools) while world generation stays **vanilla**. Terrain, biomes and caves are identical to the real seed. Every vanilla structure and feature still generates with vanilla rules, but in places the seed no longer predicts.

| Jar (`./build.sh` → `dist/`) | Runs on |
|---|---|
| `SeedGuard-Bukkit-1.0.0.jar` | Bukkit/CraftBukkit, Spigot, Paper, Purpur, 1.20 → 26.x (one universal jar) |
| `SeedGuard-fabric-mc1.20.1.jar` | Fabric 1.20 – 1.20.4 |
| `SeedGuard-forge-mc1.20.1.jar` | Forge 1.20.1 |
| `SeedGuard-fabric-mc1.21.1.jar` | Fabric 1.20.5 – 1.21.x |
| `SeedGuard-neoforge-mc1.21.1.jar` | NeoForge 1.21.x |
| `SeedGuard-mc26.1-26.2.jar` | **Fabric, Forge and NeoForge** on 26.1 – 26.2 (one jar for all three) |
| `SeedGuard-mc26.3.jar` | **Fabric, Forge and NeoForge** on 26.3+ (one jar for all three) |

All of them are **server-side only**. Vanilla clients join normally.

**Why 26.x is a single jar.** Since 26.1, Minecraft ships unobfuscated, and every loader runs it under Mojang's own names. Nothing needs remapping, so one jar carries `fabric.mod.json`, `META-INF/mods.toml` (Forge) and `META-INF/neoforge.mods.toml`, and its mixins target the real names. 26.3 refactored structure placement (`AbstractSpreadingStructurePlacement`), moved `Slime`, renamed the carver method and changed the sign packets, so it gets its own jar.

## How seed crackers work, and what SeedGuard breaks

SeedCrackerX collects things a client can see. It recovers the low 48 bits of the seed from structure positions and decorators, then the last 16 bits from the "hashed seed" the server sends every client.

| Leak | Bukkit / Spigot | Paper / Purpur | Fabric / Forge / NeoForge |
|---|---|---|---|
| Structure positions (villages, temples, shipwrecks, outposts, monuments, end cities…) | random per-world `seed-*` salts in spigot.yml | same | **keyed PRF (SipHash-2-4)** replaces the LCG per region |
| Strongholds, structure layouts, mineshaft frequency | `seed-stronghold`, `seed-mineshaft` salts | same | secret structure seed + PRF frequency |
| Decorators: dungeons, desert wells, end gateways, ores, geodes… | ✗ (not possible without NMS) | Paper `feature-seeds` (random seed per feature) | **independent PRF seed per chunk × feature** |
| Hashed seed in login/respawn packets | ✓ rewritten by a netty hook (not on CraftBukkit) | ✓ | ✓ |
| End pillars | ✗ | ✗ | ✓ |
| Slime chunks | `seed-slime` | same | ✓ PRF per chunk |
| Detects the SeedCrackerX client mod | ✗ | ✓ | ✓ |

**Why the mod builds are the strongest.** Salts (the Bukkit route) still use Java's 48-bit LCG. They stop SeedCrackerX, but a determined attacker with a custom tool and enough samples of *one* structure type could predict *that* type. The mods swap the LCG for a 128-bit-keyed PRF, so observing any number of structures or dungeons reveals nothing about any other one. Even a custom cracker gets nowhere.

### Mod detection (the translation probe)

About 3 seconds after joining, the server shows the player a fake sign. Each line is a translatable component built from a SeedCrackerX language key plus a random fallback, and the server closes the editor right away. Clients send back the resolved text. A vanilla client can only return the random fallback; a client with SeedCrackerX returns the mod's English strings. The response is consumed and never reaches the real sign logic. The player sees the sign editor flash for one frame.

Limits: clients with ExploitPreventer, ModDetectionPreventer or similar mods hide this, so treat detection as a bonus. The generation changes are the real protection; even an undetected cracker gets nothing useful.

## Install

### Paper / Purpur (recommended Bukkit-family option)
1. Drop `SeedGuard-Bukkit-1.0.0.jar` into `plugins/`.
2. Start the server once. SeedGuard writes salts into `spigot.yml` before the worlds load, and turns on `feature-seeds.generate-random-seeds-for-all` in `config/paper-world-defaults.yml`.
3. **Restart once** (Paper reads world defaults before plugins load). For a brand-new world, delete it before that restart so the spawn chunks get protected decoration.
4. `/seedguard status` should show `Paper feature seeds: active`.

### Spigot / Bukkit
Same jar, same steps. Plain Spigot can't randomise decorators, so dungeons and desert wells stay crackable there. CraftBukkit (no Spigot) gets hashed-seed spoofing only. Paper or Purpur is strongly recommended.

### Fabric / Forge / NeoForge
Drop the matching jar into `mods/`. On first start SeedGuard creates:
- `config/seedguard.properties` with global settings and detection options.
- `<world>/seedguard.properties`, which holds the world's **secret key** and switches. **Keep it with the world and back it up.** Losing or changing it moves structures in chunks generated afterwards.

## Existing worlds

Moving structures under land that's already explored would leave cut-off structures at the border. So **worlds that already have chunks keep vanilla structure placement** (and vanilla slime chunks and end pillars). They still get decorator protection and hashed-seed spoofing, both of which are seamless. To force full protection anyway, set `structures.protect-existing-worlds: true` (Bukkit) or `protect-existing-worlds=true` (mods) **before** SeedGuard first sees the world.

On Bukkit-family servers, per-world decisions are stored in `plugins/SeedGuard/state.yml` and the salts live in `spigot.yml`. Structures stay put even if you remove the plugin later.

## "Rotating"

Every world (and every Bukkit world name) gets its own secret, so no two servers or dimensions share placement. Rotating a secret on a live world is possible: change the key in `<world>/seedguard.properties`, or delete the world's entry in `state.yml`. But it only affects newly generated chunks and can cut structures at the border, so do it only together with a map reset or a newly opened area.

## Commands (Bukkit)

`/seedguard status` · `/seedguard probe <player>` · `/seedguard reload` (permission `seedguard.admin`). Staff with `seedguard.notify` get alerts; `seedguard.bypass` skips the probe.

## Building

```bash
./build.sh                          # all jars → dist/
MC_VERSIONS="1.21.1" ./build.sh     # one legacy mod target (Architectury: Fabric/Forge/NeoForge jars)
MC26_VERSIONS="26.3" ./build.sh     # one 26.x target (single multi-loader jar)
```
Needs **JDK 25** (Minecraft 26.x requires it; the legacy targets are still compiled for Java 17/21). Tested with Gradle 9.4.

- 1.20–1.21: `mod/` uses Architectury Loom. Add a version by copying a file in `mod/versions/`.
- 26.x: `mod26/` compiles directly against the official server jar, which it downloads from Mojang. Add a version by copying a file in `mod26/versions/`. If Mojang renames a hooked method, add a variant folder under `mod/versioned/`.

## Layout

```
shared/   pure-Java core: SipHash PRF, per-world keys, probe logic (compiled into every jar)
bukkit/   universal plugin: spigot.yml salts, Paper feature seeds, netty hook (hashed seed + probe)
mod/      Architectury multi-loader build for 1.20–1.21: common mixins + fabric / forge / neoforge entrypoints
  common/     version-independent code and mixins (shared with mod26)
  versioned/  the few classes that differ by Minecraft version: legacy (1.20–1.21), v26_1 (26.1–26.2), v26_3 (26.3+)
mod26/    Minecraft 26.x build: one jar for Fabric + Forge + NeoForge, no remapping
```
