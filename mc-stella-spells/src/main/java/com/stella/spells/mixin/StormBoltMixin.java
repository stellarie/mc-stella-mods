package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.stella.spells.SpellStorms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A storm bolt is only light and sound: it neither scrapes copper nor powers a lightning rod. */
@Mixin(LightningBolt.class)
public abstract class StormBoltMixin {
	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;powerLightningRod()V"))
	private boolean modStellaSpells$rodOnlyForRealBolts(final LightningBolt bolt) {
		return !bolt.entityTags().contains(SpellStorms.STORM_BOLT_TAG);
	}

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;clearCopperOnLightningStrike(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
	private boolean modStellaSpells$copperOnlyForRealBolts(final Level level, final BlockPos pos) {
		return !((LightningBolt) (Object) this).entityTags().contains(SpellStorms.STORM_BOLT_TAG);
	}
}
