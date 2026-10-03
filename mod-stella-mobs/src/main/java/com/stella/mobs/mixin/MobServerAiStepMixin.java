package com.stella.mobs.mixin;

import com.stella.mobs.behavior.CreeperEscapeController;
import com.stella.mobs.behavior.CreeperEscapeController.EscapeDecision;
import com.stella.mobs.behavior.ReactiveState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobServerAiStepMixin {
	@Inject(
		method = "serverAiStep",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"
		)
	)
	private void stella$evadeCreepers(final CallbackInfo callbackInfo) {
		Mob mob = (Mob)(Object)this;
		ReactiveState state = ReactiveState.get(mob);
		// The command text is only read after a failed escape, which needs a swelling creeper.
		String before = com.stella.mobs.behavior.CreeperEscapePositions.anySwelling(mob.level())
			? CreeperEscapeController.nativeCommand(mob.getMoveControl()) : null;
		state.setR02NativeCommands(null, null);
		EscapeDecision decision = CreeperEscapeController.apply(mob);
		if (decision == EscapeDecision.NO_SAFE_ROUTE) {
			state.setR02NativeCommands(before, CreeperEscapeController.nativeCommand(mob.getMoveControl()));
		}
	}
}
