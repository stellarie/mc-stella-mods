package com.stella.villages.mixin;

import com.stella.villages.raid.VillageRaids;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** V3: a raid this mod builds has one wave; every other raid keeps vanilla's count. */
@Mixin(Raid.class)
public abstract class RaidWaveMixin {
	@Inject(method = "getNumGroups", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$oneWave(final Difficulty difficulty, final CallbackInfoReturnable<Integer> cir) {
		if (VillageRaids.oneWave()) {
			cir.setReturnValue(1);
		}
	}
}
