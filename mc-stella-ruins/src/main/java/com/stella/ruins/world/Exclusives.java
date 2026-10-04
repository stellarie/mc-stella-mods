package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.phys.Vec3;

/**
 * The exclusivity rule: an item is exclusive when its namespace is {@code mod-stella-ruins} and it is not the clue.
 * Only a site's own loot tables and its own mob drops may produce one. The code tests that rule; no tag lists them.
 */
public final class Exclusives {
	/** The namespaces whose loot tables may hold exclusives: the mod's own, and its game tests'. */
	public static final Set<String> OWN_NAMESPACES = Set.of(StellaRuins.MOD_ID, StellaRuins.MOD_ID + "-test");

	/** The outcome of rolling one table many times. */
	public record Probe(boolean rolled, Set<Identifier> exclusives) {
	}

	private Exclusives() {
	}

	public static boolean isExclusive(final Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return StellaRuins.MOD_ID.equals(id.getNamespace()) && !RuinClueItem.ID.equals(id);
	}

	public static boolean isExclusive(final ItemStack stack) {
		return !stack.isEmpty() && isExclusive(stack.getItem());
	}

	/** True when the table belongs to the mod or its tests, so it may produce exclusives. */
	public static boolean ownsTable(final Identifier table) {
		return OWN_NAMESPACES.contains(table.getNamespace());
	}

	/**
	 * Rolls a table and collects every exclusive item it yields. A table whose context the rule cannot build is
	 * reported as not rolled, so the caller can count what it really covered.
	 */
	public static Probe probe(final ServerLevel level, final LootTable table, final int rolls) {
		LootParams params = paramsFor(level, table.getParamSet());
		if (params == null) {
			return new Probe(false, Set.of());
		}
		Set<Identifier> found = new LinkedHashSet<>();
		try {
			for (int i = 0; i < rolls; i++) {
				for (ItemStack stack : table.getRandomItems(params, level.getRandom().nextLong())) {
					if (isExclusive(stack)) {
						found.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
					}
				}
			}
		} catch (RuntimeException e) {
			return new Probe(false, found);
		}
		return new Probe(true, found);
	}

	/** A context with every allowed key the rule can fill, or null when a required key is out of reach. */
	private static LootParams paramsFor(final ServerLevel level, final ContextKeySet set) {
		Entity victim = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
		if (victim == null) {
			return null;
		}
		DamageSource source = level.damageSources().generic();
		Vec3 origin = Vec3.ZERO;
		LootParams.Builder builder = new LootParams.Builder(level);
		offer(builder, set, LootContextParams.ORIGIN, origin);
		offer(builder, set, LootContextParams.THIS_ENTITY, victim);
		offer(builder, set, LootContextParams.DAMAGE_SOURCE, source);
		offer(builder, set, LootContextParams.BLOCK_STATE, Blocks.STONE.defaultBlockState());
		offer(builder, set, LootContextParams.TOOL, new ItemStack(Items.IRON_PICKAXE));
		offer(builder, set, LootContextParams.EXPLOSION_RADIUS, 4.0F);
		for (ContextKey<?> required : set.required()) {
			if (builder.getOptionalParameter(required) == null) {
				return null;
			}
		}
		try {
			return builder.create(set);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static <T> void offer(final LootParams.Builder builder, final ContextKeySet set, final ContextKey<T> key, final T value) {
		if (set.allowed().contains(key)) {
			builder.withParameter(key, value);
		}
	}
}
