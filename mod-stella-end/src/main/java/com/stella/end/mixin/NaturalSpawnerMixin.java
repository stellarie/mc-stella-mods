package com.stella.end.mixin;

import com.stella.end.EndCastle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The castle's floor spawner owns its population. Vanilla's End spawns endermen in any dark spot with air above, and a
 * castle full of dark rooms would fill with them on top of the spawner's mobs.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
	private static void stellaEnd$keepTheCastleToItsSpawner(
		final ServerLevel level, final MobCategory category, final StructureManager structures, final ChunkGenerator generator,
		final MobSpawnSettings.SpawnerData spawner, final BlockPos.MutableBlockPos pos, final double distance,
		final CallbackInfoReturnable<Boolean> cir
	) {
		if (level.dimension().equals(Level.END) && EndCastle.volumeContains(pos)) {
			cir.setReturnValue(false);
		}
	}
}
