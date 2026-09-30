package com.stella.mobs.mixin;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class MobDamageTailMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float stellaMobs$tailFlat(final float amount, final ServerLevel level, final DamageSource source) {
		if (!(source.getEntity() instanceof Mob mob) || mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)) {
			return amount;
		}
		return amount + (float) SpawnScaling.tailFlat(ThreatState.level(mob));
	}
}
