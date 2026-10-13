package com.stella.tower;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A rune of one element, as strong as the floor it came from; set into a weapon or armor at the anvil. */
public class RuneItem extends Item {
	public RuneItem(final Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(final ItemStack stack) {
		RuneData rune = stack.get(TowerItems.RUNE_DATA);
		if (rune == null) {
			return super.getName(stack);
		}
		return Component.translatable("item.mod-stella-tower.rune.of", Component.literal(rune.element().label()).withStyle(rune.element().color()));
	}

	@Override
	public void appendHoverText(
		final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> lines, final TooltipFlag flag
	) {
		RuneData rune = stack.get(TowerItems.RUNE_DATA);
		Component requires = Component.translatable("item.mod-stella-tower.rune.requires", TowerRunes.EMERALDS).withStyle(ChatFormatting.WHITE);
		if (rune == null) {
			DescriptionLines.addBlock(lines, requires, Component.translatable("item.mod-stella-tower.rune.desc").withStyle(ChatFormatting.GRAY));
			return;
		}
		DescriptionLines.addBlock(lines, requires, TowerRunes.effects(rune, RuneStats::local));
		TowerRunes.numbers(rune, line -> DescriptionLines.add(lines, line));
	}
}
