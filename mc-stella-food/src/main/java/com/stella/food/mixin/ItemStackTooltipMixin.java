package com.stella.food.mixin;

import com.stella.food.FoodTooltips;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@ModifyVariable(method = "addDetailsToTooltip", at = @At("HEAD"), argsOnly = true)
	private TooltipDisplay stellaFood$hideSourcePotionDuration(final TooltipDisplay display) {
		return FoodTooltips.displayFor((ItemStack) (Object) this, display);
	}

	@Inject(method = "addDetailsToTooltip", at = @At("RETURN"))
	private void stellaFood$describeFood(
		final Item.TooltipContext context,
		final TooltipDisplay display,
		final Player player,
		final TooltipFlag flag,
		final Consumer<Component> lines,
		final CallbackInfo callback
	) {
		FoodTooltips.append((ItemStack) (Object) this, display, lines);
	}
}
