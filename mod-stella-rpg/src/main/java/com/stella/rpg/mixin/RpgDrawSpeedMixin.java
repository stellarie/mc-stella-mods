package com.stella.rpg.mixin;

import com.stella.rpg.DrawSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speeds up the use countdown of a drawn bow or charged crossbow. Both sides run this tick, so the pull animation,
 * the release power, a crafted bow's own draw curve, and the crossbow charge all follow the same shorter draw.
 */
@Mixin(LivingEntity.class)
public abstract class RpgDrawSpeedMixin {
	@Shadow
	protected int useItemRemaining;

	@Unique
	private double stellaRpg$drawCarry;

	@Inject(method = "startUsingItem", at = @At("RETURN"))
	private void stellaRpg$resetDraw(final net.minecraft.world.InteractionHand hand, final CallbackInfo callback) {
		this.stellaRpg$drawCarry = 0.0;
	}

	@Inject(method = "updateUsingItem", at = @At("RETURN"))
	private void stellaRpg$quickenDraw(final ItemStack useItem, final CallbackInfo callback) {
		if (!(useItem.getItem() instanceof ProjectileWeaponItem)) {
			return;
		}
		double reduction = DrawSpeed.reductionOf((LivingEntity) (Object) this);
		if (reduction <= 0.0) {
			this.stellaRpg$drawCarry = 0.0;
			return;
		}
		// A draw that takes (1 - r) of the time runs 1 / (1 - r) ticks of work per tick. The extra ticks carry over.
		this.stellaRpg$drawCarry += 1.0 / (1.0 - reduction) - 1.0;
		while (this.stellaRpg$drawCarry >= 1.0 && this.useItemRemaining > 1) {
			this.useItemRemaining--;
			this.stellaRpg$drawCarry -= 1.0;
		}
		if (this.useItemRemaining <= 1) {
			this.stellaRpg$drawCarry = 0.0;
		}
	}
}
