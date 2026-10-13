package com.stella.equips.mixin;

import com.stella.equips.tooltip.DescriptionLines;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Splits the lore lines that this mod marked, so a long refine or mastery line fits the tooltip width. */
@Mixin(ItemLore.class)
public abstract class ItemLoreMixin {
	@ModifyVariable(method = "addToTooltip", at = @At("HEAD"), argsOnly = true)
	private Consumer<Component> stellaEquips$wrapMarkedLines(final Consumer<Component> consumer) {
		return line -> {
			if (DescriptionLines.isMarked(line)) {
				DescriptionLines.add(consumer, line);
			} else {
				consumer.accept(line);
			}
		};
	}
}
