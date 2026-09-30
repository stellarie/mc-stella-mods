package com.stella.rpg;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class StatPoints {
	public static final int STARTING_POINTS = 12;
	public static final int RESPEC_EMERALDS = 320;

	public enum Result {
		OK, NOT_ENOUGH_POINTS, ABOVE_CEILING, NOT_ENOUGH_EMERALDS, NOTHING_TO_RESET, INVALID_AMOUNT, INVALID_STAT
	}

	private StatPoints() {
	}

	public static int total(final int experienceLevel) {
		return STARTING_POINTS + Math.max(0, experienceLevel);
	}

	public static int unspent(final int experienceLevel, final StatSheet sheet) {
		return Math.max(0, total(experienceLevel) - sheet.allocated());
	}

	public static Result check(final int experienceLevel, final StatSheet sheet, final Stat stat, final int amount) {
		if (amount < 1) {
			return Result.INVALID_AMOUNT;
		}
		if ((long) amount > unspent(experienceLevel, sheet)) {
			return Result.NOT_ENOUGH_POINTS;
		}
		if ((long) sheet.get(stat) + amount > StatSheet.CEILING) {
			return Result.ABOVE_CEILING;
		}
		return Result.OK;
	}

	public static StatSheet sheet(final ServerPlayer player) {
		return player.getAttachedOrElse(StatSheet.ATTACHMENT, StatSheet.EMPTY);
	}

	public static void setSheet(final ServerPlayer player, final StatSheet sheet) {
		player.setAttached(StatSheet.ATTACHMENT, sheet);
		RpgAttributes.refresh(player);
	}

	public static Result allocate(final ServerPlayer player, final Stat stat, final int amount) {
		StatSheet sheet = sheet(player);
		Result result = check(player.experienceLevel, sheet, stat, amount);
		if (result == Result.OK) {
			setSheet(player, sheet.with(stat, sheet.get(stat) + amount));
		}
		return result;
	}

	public static Result respec(final ServerPlayer player) {
		StatSheet sheet = sheet(player);
		if (sheet.allocated() == 0) {
			return Result.NOTHING_TO_RESET;
		}
		if (countEmeralds(player) < RESPEC_EMERALDS) {
			return Result.NOT_ENOUGH_EMERALDS;
		}
		removeEmeralds(player, RESPEC_EMERALDS);
		setSheet(player, StatSheet.EMPTY);
		return Result.OK;
	}

	public static int countEmeralds(final ServerPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}
		ItemStack offhand = player.getOffhandItem();
		return offhand.is(Items.EMERALD) ? count + offhand.getCount() : count;
	}

	private static void removeEmeralds(final ServerPlayer player, final int amount) {
		int remaining = amount;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			remaining -= take(stack, remaining);
		}
		take(player.getOffhandItem(), remaining);
		player.getInventory().setChanged();
	}

	private static int take(final ItemStack stack, final int wanted) {
		if (wanted <= 0 || !stack.is(Items.EMERALD)) {
			return 0;
		}
		int taken = Math.min(wanted, stack.getCount());
		stack.shrink(taken);
		return taken;
	}
}
