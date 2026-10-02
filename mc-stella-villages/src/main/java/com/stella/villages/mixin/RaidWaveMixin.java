package com.stella.villages.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.villages.raid.VillageRaids;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * V3: a raid this mod builds has one wave, and that wave holds three to five pillagers and nothing else. Every other
 * raid keeps vanilla's count and its raiders.
 */
@Mixin(Raid.class)
public abstract class RaidWaveMixin {
	@Shadow
	@Final
	private RandomSource random;

	@Inject(method = "getNumGroups", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$oneWave(final Difficulty difficulty, final CallbackInfoReturnable<Integer> cir) {
		if (VillageRaids.oneWave()) {
			cir.setReturnValue(1);
		}
	}

	// Vanilla's raider type is a private enum,
	// so it arrives as an object.
	@ModifyReturnValue(method = "getDefaultNumSpawns", at = @At("RETURN"))
	private int mcStellaVillages$miniRaidSize(final int original, final @Coerce Object raiderType) {
		return VillageRaids.isMiniRaid((Raid) (Object) this) ? VillageRaids.miniRaidSpawns(((Enum<?>) raiderType).name(), this.random) : original;
	}

	@ModifyReturnValue(method = "getPotentialBonusSpawns", at = @At("RETURN"))
	private int mcStellaVillages$noBonusRaiders(final int original) {
		return VillageRaids.isMiniRaid((Raid) (Object) this) ? 0 : original;
	}
}
