package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Slime chunks: same 1-in-10 odds, but taken from the keyed PRF per chunk. */
@Mixin(Slime.class)
public abstract class SlimeMixin {
    @Redirect(method = "checkSlimeSpawnRules",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;seedSlimeChunk(IIJJ)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource seedguard$slimeChunk(int x, int z, long seed, long salt) {
        Long s = SeedGuard.slimeSeed(x, z);
        return s == null ? WorldgenRandom.seedSlimeChunk(x, z, seed, salt) : RandomSource.create(s);
    }
}
