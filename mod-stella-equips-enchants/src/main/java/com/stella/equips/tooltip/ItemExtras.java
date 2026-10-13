package com.stella.equips.tooltip;

import com.stella.equips.bag.EmeraldBag;
import com.stella.equips.heart.SlimeHeart;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The tooltip lines of the slime heart and the emerald bag. */
public final class ItemExtras {
	private ItemExtras() {
	}

	public static void append(final ItemStack stack, final Consumer<Component> lines) {
		if (SlimeHeart.holdsHeart(stack)) {
			DescriptionLines.addBlock(lines, Component.literal("worn").withStyle(ChatFormatting.WHITE),
				Component.translatable("tooltip.mod-stella-equips-enchants.slime_heart").withStyle(ChatFormatting.RED));
		}
		if (EmeraldBag.isBag(stack)) {
			DescriptionLines.add(lines, Component.translatable("item.mod-stella-equips-enchants.emerald_bag.count", EmeraldBag.stored(stack))
				.withStyle(ChatFormatting.GREEN));
		}
	}
}
