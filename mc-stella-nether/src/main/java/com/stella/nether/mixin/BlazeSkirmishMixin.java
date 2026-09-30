package com.stella.nether.mixin;

import com.stella.nether.BlazeSkirmish;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a blaze has thought, and before it moves: it keeps low and keeps its distance. */
@Mixin(Blaze.class)
public abstract class BlazeSkirmishMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void stellaNether$skirmish(final ServerLevel level, final CallbackInfo ci) {
		BlazeSkirmish.step((Blaze) (Object) this);
	}
}
