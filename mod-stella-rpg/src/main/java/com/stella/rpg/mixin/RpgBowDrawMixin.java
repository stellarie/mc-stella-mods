package com.stella.rpg.mixin;

import com.stella.rpg.RpgCurve;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
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
		if (!(user instanceof ServerPlayer player)) {
			return held;
		}
		int full = RpgCurve.fullDrawTicks(StatBonuses.effective(player, Stat.AGI));
		return (int) Math.floor(held * 20.0 / full);
	}
}
