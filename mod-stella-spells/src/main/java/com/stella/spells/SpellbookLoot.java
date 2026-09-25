package com.stella.spells;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;

public final class SpellbookLoot {
	public static final double CHEST_DROP_CHANCE = 0.05;
	public static final double WITCH_DROP_CHANCE = 0.50;
	private static final Set<Identifier> VANILLA_CHEST_TABLES = ConcurrentHashMap.newKeySet();

	private SpellbookLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier table = key.identifier();
			if (!isVanillaChest(table)) {
				return;
			}

			if (source.isBuiltin()) {
				VANILLA_CHEST_TABLES.add(table);
			} else {
				VANILLA_CHEST_TABLES.remove(table);
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.map(key -> key.identifier())
			.filter(VANILLA_CHEST_TABLES::contains)
			.ifPresent(id -> modifyChestDrops(id, true, context.getRandom(), drops))
		);
		ServerLivingEntityEvents.AFTER_DEATH.register(SpellbookLoot::onDeath);
	}

	public static ItemStack rollChest(final Identifier table, final boolean builtin, final RandomSource random) {
		if (!builtin || !isVanillaChest(table)) {
			return ItemStack.EMPTY;
		}
		return roll(CHEST_DROP_CHANCE, random).stack();
	}

	public static ItemStack rollWitch(final RandomSource random) {
		return roll(WITCH_DROP_CHANCE, random).stack();
	}

	public static void modifyChestDrops(
		final Identifier table, final boolean builtin, final RandomSource random, final List<ItemStack> drops
	) {
		if (!builtin || !isVanillaChest(table)) {
			return;
		}

		Roll roll = roll(CHEST_DROP_CHANCE, random);
		if (roll.stack().isEmpty()) {
			Spellbooks.LOGGER.debug(
				"[SPELL] event=LOOT_ROLL source=chest table_id={} chance={} roll={} result=FAIL",
				table, CHEST_DROP_CHANCE, roll.value()
			);
			return;
		}

		drops.add(roll.stack());
		Spellbooks.LOGGER.info(
			"[SPELL] event=DROP source=chest table_id={} item_id={}",
			table, Spellbooks.SPARK_SHOT_ID
		);
	}

	public static void applyWitchDeath(final Witch witch, final DamageSource source, final RandomSource random) {
		if (!(witch.level() instanceof ServerLevel level)) {
			return;
		}

		Roll roll = roll(WITCH_DROP_CHANCE, random);
		if (roll.stack().isEmpty()) {
			Spellbooks.LOGGER.debug(
				"[SPELL] event=LOOT_ROLL source=witch chance={} roll={} result=FAIL",
				WITCH_DROP_CHANCE, roll.value()
			);
			return;
		}

		witch.spawnAtLocation(level, roll.stack());
		Spellbooks.LOGGER.info(
			"[SPELL] event=DROP source=witch source_entity={} item_id={}",
			witch.getUUID(), Spellbooks.SPARK_SHOT_ID
		);
	}

	private static boolean isVanillaChest(final Identifier table) {
		return "minecraft".equals(table.getNamespace()) && table.getPath().startsWith("chests/");
	}

	private static Roll roll(final double chance, final RandomSource random) {
		double value = random.nextDouble();
		ItemStack stack = value < chance ? new ItemStack(Spellbooks.SPARK_SHOT) : ItemStack.EMPTY;
		return new Roll(value, stack);
	}

	private static void onDeath(final LivingEntity entity, final DamageSource source) {
		if (entity instanceof Witch witch) {
			applyWitchDeath(witch, source, witch.getRandom());
		}
	}

	private record Roll(double value, ItemStack stack) {
	}
}
