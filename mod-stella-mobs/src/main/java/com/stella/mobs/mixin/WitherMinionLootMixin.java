package com.stella.mobs.mixin;

import com.stella.mobs.behavior.WitherEncounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WitherMinionLootMixin {
	/** A minion of the Wither drops no loot, no equipment and no experience, so the fight cannot be farmed. */
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void stellaMobs$noMinionLoot(final ServerLevel level, final DamageSource source, final CallbackInfo callbackInfo) {
		if (WitherEncounter.isMinion((LivingEntity) (Object) this)) {
			callbackInfo.cancel();
		}
	}
}
