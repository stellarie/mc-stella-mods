package com.stella.mobs.mixin;

import com.stella.mobs.scaling.ThreatExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class ThreatExperienceMixin {
	// The formula replaces the value, so this mixin runs first (priority 900). The others then multiply it.
	@ModifyReturnValue(method = "getExperienceReward", at = @At("RETURN"))
	private int stella$scaleExperience(final int experience, final ServerLevel level, final Entity killer) {
		if (!((Object) this instanceof Mob mob) || experience <= 0 || mob.getType() == EntityTypes.ENDER_DRAGON) {
			return experience;
		}
		ServerPlayer player;
		if (killer instanceof ServerPlayer direct) {
			player = direct;
		} else if (mob.getLastHurtByPlayer() instanceof ServerPlayer credited) {
			player = credited;
		} else {
			return experience;
		}
		return ThreatExperience.rewardFor(mob, player.experienceLevel);
	}
}
