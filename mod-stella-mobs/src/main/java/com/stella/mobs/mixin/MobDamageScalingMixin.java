package com.stella.mobs.mixin;

import com.stella.mobs.scaling.SelfScaled;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Melee reads the scaled attack attribute; a hostile mob's other hits are scaled here. */
@Mixin(LivingEntity.class)
public abstract class MobDamageScalingMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaMobs$scaleMobDamage(final float amount, final ServerLevel level, final DamageSource source) {
		if (!(source.getEntity() instanceof Mob mob) || !SelfScaled.levelHits(mob)) {
			return amount;
		}
		// A potion's harm is a share of the victim's health already.
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion
			|| source.getDirectEntity() instanceof net.minecraft.world.entity.AreaEffectCloud) {
			return amount;
		}
		int threat = ThreatState.level(mob);
		if (SelfScaled.owned(mob)) {
			return amount * (float) SelfScaled.factor(threat);
		}
		// A spear jab reads the scaled attack too.
		boolean melee = source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.SPEAR);
		// Friendly golems keep their fixed special hits.
		boolean hostile = mob instanceof Enemy;
		float scaled = melee || !hostile ? amount : amount * (float) SpawnScaling.damageMultiplier(threat);
		return hostile ? scaled + (float) SpawnScaling.tailFlat(threat) : scaled;
	}
}
