package com.stella.rpg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stella.rpg.roll.OreExperience;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public abstract class RpgOreExperienceMixin {
	// The player break is the only call that carries the breaker, so it marks the ore for the orb call below.
	@WrapOperation(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;"
			+ "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak("
				+ "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/item/ItemStack;Z)V"
		)
	)
	private static void stellaRpg$markBreaker(
		final BlockState state, final ServerLevel level, final BlockPos pos, final ItemStack tool, final boolean dropXp,
		final Operation<Void> original, @Local(argsOnly = true) final Entity breaker
	) {
		OreExperience.during(state, breaker, () -> original.call(state, level, pos, tool, dropXp));
	}

	@WrapOperation(
		method = "popExperience",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ExperienceOrb;award("
				+ "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"
		)
	)
	private void stellaRpg$deepOre(
		final ServerLevel level, final Vec3 at, final int amount, final Operation<Void> original
	) {
		original.call(level, at, OreExperience.forBreak(amount, Math.floor(at.y), level.getRandom()));
	}
}
