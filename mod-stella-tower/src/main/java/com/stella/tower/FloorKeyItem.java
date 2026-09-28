package com.stella.tower;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Dropped by a boss, naming its floor. Carried into the tower's portal outside, the highest key held
 * decides the floor a player arrives on. It is never spent.
 */
public class FloorKeyItem extends Item {
	public FloorKeyItem(final Properties properties) {
		super(properties);
	}

	/** The floor a key opens, or 0 for none. */
	public static int floorOf(final ItemStack stack) {
		Integer floor = stack.get(TowerItems.FLOOR);
		return stack.is(TowerItems.FLOOR_KEY) && floor != null ? floor : 0;
	}

	public static ItemStack forFloor(final int floor) {
		ItemStack key = new ItemStack(TowerItems.FLOOR_KEY);
		key.set(TowerItems.FLOOR, floor);
		return key;
	}

	@Override
	public Component getName(final ItemStack stack) {
		int floor = floorOf(stack);
		return floor > 0 ? Component.translatable("item.mod-stella-tower.floor_key.floor", floor) : super.getName(stack);
	}

	@Override
	public void appendHoverText(
		final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> lines, final TooltipFlag flag
	) {
		lines.accept(Component.translatable("item.mod-stella-tower.floor_key.desc").withStyle(ChatFormatting.GRAY));
	}
}
