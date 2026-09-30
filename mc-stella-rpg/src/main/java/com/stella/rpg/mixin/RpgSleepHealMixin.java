package com.stella.rpg.mixin;

import com.stella.rpg.RpgSleep;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class RpgSleepHealMixin {
	// Runs only when the night is skipped.
	@Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
	private void stellaRpg$healSleepers(final CallbackInfo callback) {
		RpgSleep.healSleepers((ServerLevel) (Object) this);
	}
}
