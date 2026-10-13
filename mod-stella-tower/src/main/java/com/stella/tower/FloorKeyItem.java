package com.stella.tower;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Dropped by a boss, naming its floor. Used anywhere, it takes the player to its floor. Carried into the
 * tower's portal outside, the highest key held decides the floor a player arrives on. It is never spent.
 */
public class FloorKeyItem extends Item {
	public FloorKeyItem(final Properties properties) {
		super(properties);
	}

	public static final int COOLDOWN_TICKS = 20;

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!(player instanceof ServerPlayer traveller)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack key = player.getItemInHand(hand);
		int floor = floorOf(key);
		if (floor < 1 || !TowerTravel.jumpTo(traveller, floor)) {
			return InteractionResult.FAIL;
		}
		traveller.getCooldowns().addCooldown(key, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
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
		for (Component line : ItemDescription.block("floor_key").styledLines()) {
			DescriptionLines.add(lines, line);
		}
	}
}
