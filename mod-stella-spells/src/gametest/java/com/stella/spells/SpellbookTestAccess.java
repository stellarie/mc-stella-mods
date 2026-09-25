package com.stella.spells;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;

final class SpellbookTestAccess {
	private static final String LOOT_CLASS = "com.stella.spells.SpellbookLoot";

	private SpellbookTestAccess() {
	}

	static double chance(final String fieldName) {
		try {
			return getField(LOOT_CLASS, fieldName).getDouble(null);
		} catch (IllegalAccessException error) {
			throw new IllegalStateException("Cannot read production field " + fieldName, error);
		}
	}

	static int cooldownTicks() {
		try {
			return getField("com.stella.spells.SpellbookItem", "BASE_COOLDOWN_TICKS").getInt(null);
		} catch (IllegalAccessException error) {
			throw new IllegalStateException("Cannot read BASE_COOLDOWN_TICKS", error);
		}
	}

	static ItemStack rollChest(final Identifier table, final boolean builtin, final RandomSource random) {
		return (ItemStack) invoke(
			"rollChest", new Class<?>[] {Identifier.class, boolean.class, RandomSource.class}, table, builtin, random
		);
	}

	static ItemStack rollWitch(final RandomSource random) {
		return (ItemStack) invoke("rollWitch", new Class<?>[] {RandomSource.class}, random);
	}

	static void applyWitchDeath(final Witch witch, final DamageSource source, final RandomSource random) {
		invoke(
			"applyWitchDeath", new Class<?>[] {Witch.class, DamageSource.class, RandomSource.class},
			witch, source, random
		);
	}

	static void modifyChestDrops(
		final Identifier table, final boolean builtin, final RandomSource random, final List<ItemStack> drops
	) {
		invoke(
			"modifyChestDrops",
			new Class<?>[] {Identifier.class, boolean.class, RandomSource.class, List.class},
			table, builtin, random, drops
		);
	}

	private static Field getField(final String className, final String fieldName) {
		try {
			return Class.forName(className).getField(fieldName);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Missing production field " + className + "." + fieldName, error);
		}
	}

	private static Object invoke(final String name, final Class<?>[] parameterTypes, final Object... arguments) {
		try {
			Class<?> lootClass = Class.forName(LOOT_CLASS);
			Method method = lootClass.getMethod(name, parameterTypes);
			return method.invoke(null, arguments);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Missing production method SpellbookLoot." + name, error);
		}
	}
}
