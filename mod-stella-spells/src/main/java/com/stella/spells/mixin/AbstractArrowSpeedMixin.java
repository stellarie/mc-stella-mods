package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stella.spells.ArcherSkills;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An Arrow Shower arrow is worth a share of a full-draw arrow, whatever speed its lob has when it lands. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowSpeedMixin {
	// The first length read in onHitEntity is the speed the arrow's damage is made from.
	@ModifyExpressionValue(
		method = "onHitEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;length()D", ordinal = 0)
	)
	private double stellaSpells$fullDrawSpeed(final double speed) {
		return ArcherSkills.damageSpeed((AbstractArrow) (Object) this, speed);
	}
}
