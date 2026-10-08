package com.stella.spells.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stella.spells.ArcherSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An aimed bolt is drawn half again as slowly: the whole charge of one bow or crossbow shot stretches. */
@Mixin(Item.class)
public abstract class AimedBoltDrawMixin {
	@ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
	private int stellaSpells$aimedBoltDraw(final int duration, final ItemStack stack, final LivingEntity user) {
		if (!(user instanceof ServerPlayer player)) {
			return duration;
		}
		return ArcherSkills.aimedBoltDraw(player, stack, duration);
	}
}
