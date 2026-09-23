package com.stella.equips.mixin;

import com.stella.equips.tooltip.EnchantmentDescriptions;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends the catalogue descriptions once vanilla has filled the tooltip. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "addDetailsToTooltip", at = @At("RETURN"))
	private void stellaEquips$describeEnchantments(
		final Item.TooltipContext context,
		final TooltipDisplay display,
		final Player player,
		final TooltipFlag tooltipFlag,
		final Consumer<Component> builder,
		final CallbackInfo ci
	) {
		EnchantmentDescriptions.append((ItemStack) (Object) this, context.registries(), builder);
	}
}
