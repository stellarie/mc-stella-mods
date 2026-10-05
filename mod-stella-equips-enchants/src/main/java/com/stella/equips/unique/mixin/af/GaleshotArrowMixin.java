package com.stella.equips.unique.mixin.af;

import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.af.Af;
import com.stella.equips.unique.af.GaleshotCrossbow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scales knockback after vanilla applies the bolt's enchantment push. */
@Mixin(AbstractArrow.class)
public abstract class GaleshotArrowMixin {
	@Inject(method = "doKnockback", at = @At("TAIL"))
	private void stella$galeshotPush(final LivingEntity target, final DamageSource source, final CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		if (arrow.getOwner() instanceof ServerPlayer && mark != null && GaleshotCrossbow.ID.equals(mark.id())) {
			Af.scaleKnockback(target, GaleshotCrossbow.knockback(mark.refine()));
		}
	}
}
