package com.stella.tower.mixin;

import com.stella.tower.TowerMerchants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No mob is raised within a ghost merchant's peace. A spawner block and the world's own spawning both ask this rule of the
 * kind before they create the mob.
 */
@Mixin(SpawnPlacements.class)
public abstract class TowerSpawnMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static <T extends Entity> void stellaTower$noSpawnBesideAMerchant(final EntityType<T> type, final ServerLevelAccessor level,
		final EntitySpawnReason reason, final BlockPos pos, final RandomSource random, final CallbackInfoReturnable<Boolean> cir) {
		if (TowerMerchants.forbids(level, Vec3.atBottomCenterOf(pos))) {
			cir.setReturnValue(false);
		}
	}
}
