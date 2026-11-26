#!/usr/bin/env bash
# Builds every SeedGuard jar into ./dist
#   ./build.sh                 -> Bukkit plugin + mods for 1.20.1 (Fabric, Forge) and 1.21.1 (Fabric, NeoForge)
#   MC_VERSIONS="1.21.1" ./build.sh   -> only the listed legacy mod targets (see mod/versions/*.properties)
#   MC26_VERSIONS="26.3" ./build.sh   -> only the listed 26.x targets (see mod26/versions/*.properties)
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p dist
rm -f dist/*.jar

(cd bukkit && ./gradlew --console=plain -q build)
cp bukkit/build/libs/SeedGuard-Bukkit-*.jar dist/

for mc in ${MC_VERSIONS:-1.20.1 1.21.1}; do
  (cd mod && ./gradlew --console=plain -q build -Pmc="$mc")
  for loader in fabric forge neoforge; do
    f="mod/$loader/build/libs/seedguard-$loader-1.0.0+mc$mc.jar"
    [ -f "$f" ] && cp "$f" "dist/SeedGuard-$loader-mc$mc.jar"
  done
done
# Minecraft 26.x: one jar per target that loads on Fabric, Forge AND NeoForge.
for mc in ${MC26_VERSIONS:-26.1 26.3}; do
  (cd mod26 && ./gradlew --console=plain -q build -Pmc="$mc")
done
cp mod26/build/libs/SeedGuard-mc26*.jar dist/
ls -1 dist
