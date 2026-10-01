package com.stella.tower.mixin;

import com.stella.tower.TowerRewards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A tower mob's experience grows with its floor. */
@Mixin(LivingEntity.class)
public abstract class TowerExperienceMixin {
	// Chains with the other mods' XP hooks; a cancelling inject would skip them.
	@ModifyReturnValue(method = "getExperienceReward", at = @At("RETURN"))
	private int stellaTower$grow(final int experience, final ServerLevel level, final Entity killer) {
		return TowerRewards.experience((LivingEntity) (Object) this, experience);
	}
}
