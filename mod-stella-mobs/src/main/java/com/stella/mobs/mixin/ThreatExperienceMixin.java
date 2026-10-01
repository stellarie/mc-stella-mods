package com.stella.mobs.mixin;

import com.stella.mobs.scaling.ThreatExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ThreatExperienceMixin {
	@Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
	private void stella$scaleExperience(
		final ServerLevel level, final Entity killer, final CallbackInfoReturnable<Integer> cir
	) {
		if (!((Object) this instanceof Mob mob)
			|| cir.getReturnValue() <= 0
			|| mob.getType() == EntityTypes.ENDER_DRAGON) {
			return;
		}
		ServerPlayer player;
		if (killer instanceof ServerPlayer direct) {
			player = direct;
		} else if (mob.getLastHurtByPlayer() instanceof ServerPlayer credited) {
			player = credited;
		} else {
			return;
		}
		cir.setReturnValue(ThreatExperience.rewardFor(mob, player.experienceLevel));
	}
}
