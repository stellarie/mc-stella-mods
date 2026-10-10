package com.stella.rpg.mixin;

import com.stella.rpg.job.JobItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BowItem.class)
public abstract class RpgBowDrawMixin {
	// Local 7 is the held-tick int that feeds getPowerForTime.
	@ModifyVariable(method = "releaseUsing", at = @At("STORE"), index = 7)
	private int stellaRpg$scaleHeldTicks(
		final int held,
		final ItemStack stack,
		final Level level,
		final LivingEntity user,
		final int remaining
	) {
		// AGI and DEX speed the use countdown itself (RpgDrawSpeedMixin), so only the mechanical bow factor stays here.
		if (!(user instanceof ServerPlayer)) {
			return held;
		}
		return (int) Math.floor(held * JobItems.drawFactor(stack));
	}
}
