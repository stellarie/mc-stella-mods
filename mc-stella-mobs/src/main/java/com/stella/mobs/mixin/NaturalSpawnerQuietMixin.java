package com.stella.mobs.mixin;

import com.stella.mobs.behavior.SpawnPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every natural spawn attempt asks {@link SpawnPressure} first, so elites and bosses can quiet the ground around them. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerQuietMixin {
	@Inject(
		method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void stellaMobs$quietNearEliteAndBoss(
		final MobCategory category,
		final ServerLevel level,
		final ChunkAccess chunk,
		final BlockPos pos,
		final NaturalSpawner.SpawnPredicate predicate,
		final NaturalSpawner.AfterSpawnCallback callback,
		final CallbackInfo callbackInfo
	) {
		if (SpawnPressure.skips(level, category, pos, level.getRandom())) {
			callbackInfo.cancel();
		}
	}
}
