package com.stella.nether.mixin;

import com.stella.nether.GuardianHub;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The spawn rules that natural, world-generation, patrol and reinforcement spawns pass refuse a spot near a Blaze Guardian. */
@Mixin(SpawnPlacements.class)
public abstract class GuardianSpawnMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static <T extends Entity> void stellaNether$keepGuardiansQuiet(final EntityType<T> type, final ServerLevelAccessor level,
		final EntitySpawnReason reason, final BlockPos pos, final RandomSource random, final CallbackInfoReturnable<Boolean> cir) {
		if (GuardianHub.blocksSpawn(level, type, reason, pos)) {
			cir.setReturnValue(false);
		}
	}
}
