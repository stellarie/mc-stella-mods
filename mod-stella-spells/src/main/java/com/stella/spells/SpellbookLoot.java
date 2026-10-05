package com.stella.spells;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;

public final class SpellbookLoot {
	public static final double CHEST_DROP_CHANCE = 0.10;
	public static final double WITCH_DROP_CHANCE = 0.50;
	/** Scrolls are common: this share of containers holds one or two of a random spell. */
	public static final double SCROLL_CHEST_CHANCE = 0.25;
	public static final int SCROLL_MAX_COUNT = 2;
	private static final Set<Identifier> CONTAINER_TABLES = ConcurrentHashMap.newKeySet();

	private SpellbookLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier table = key.identifier();
			if (!ContainerTables.targets(table)) {
				return;
			}

			if (source.isBuiltin()) {
				CONTAINER_TABLES.add(table);
			} else {
				CONTAINER_TABLES.remove(table);
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.map(key -> key.identifier())
			.filter(CONTAINER_TABLES::contains)
			.ifPresent(id -> modifyChestDrops(id, true, context.getRandom(), drops))
		);
		ServerLivingEntityEvents.AFTER_DEATH.register(SpellbookLoot::onDeath);
	}

	public static ItemStack rollChest(final Identifier table, final boolean builtin, final RandomSource random) {
		if (!builtin || !ContainerTables.targets(table)) {
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
		if (!builtin || !ContainerTables.targets(table)) {
			return;
		}

		// The scroll roll comes first and on its own draw, so the book roll keeps its own chance.
		if (random.nextDouble() < SCROLL_CHEST_CHANCE) {
			ItemStack scroll = Awakening.strip(new ItemStack(Spellbooks.SCROLLS.get(random.nextInt(Spellbooks.SCROLLS.size())), 1 + random.nextInt(SCROLL_MAX_COUNT)));
			drops.add(scroll);
			Spellbooks.LOGGER.info(
				"[SPELL] event=DROP source=chest table_id={} item_id={} count={}",
				table, BuiltInRegistries.ITEM.getKey(scroll.getItem()), scroll.getCount()
			);
		}

		// The clue roll is its own draw too: a tattered note on how to bind one spell.
		if (random.nextDouble() < ClueBooks.CLUE_CHEST_CHANCE) {
			ItemStack clue = ClueBooks.roll(random);
			drops.add(clue);
			Spellbooks.LOGGER.info("[SPELL] event=DROP source=chest table_id={} clue={}", table, ClueBooks.teaches(clue));
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
			table, BuiltInRegistries.ITEM.getKey(roll.stack().getItem())
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
			witch.getUUID(), BuiltInRegistries.ITEM.getKey(roll.stack().getItem())
		);
	}

	private static Roll roll(final double chance, final RandomSource random) {
		double value = random.nextDouble();
		ItemStack stack = value < chance
			? Awakening.strip(new ItemStack(Spellbooks.CATALOG.get(random.nextInt(Spellbooks.CATALOG.size()))))
			: ItemStack.EMPTY;
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
