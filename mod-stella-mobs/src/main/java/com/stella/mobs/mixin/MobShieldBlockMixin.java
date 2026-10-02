package com.stella.mobs.mixin;

import com.stella.mobs.behavior.ShieldUseGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla calls this method only for a block of a direct hit from a living attacker, and only a player's override
 * disables the shield. This hook tells the shield goal of a mob about the block, so a mob can strike back and an axe
 * can disable its shield. It runs at the head, while the shield still blocks.
 */
@Mixin(LivingEntity.class)
public abstract class MobShieldBlockMixin {
	@Inject(method = "blockUsingItem", at = @At("HEAD"))
	private void stellaMobs$shieldBlocked(
		final ServerLevel level,
		final LivingEntity attacker,
		final DamageSource source,
		final float damage,
		final boolean fullyBlocked,
		final CallbackInfo callbackInfo
	) {
		if ((Object) this instanceof Mob mob) {
			ShieldUseGoal.onBlocked(mob, attacker);
		}
	}
}
