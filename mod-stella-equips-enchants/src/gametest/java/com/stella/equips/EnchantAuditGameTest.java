package com.stella.equips;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/**
 * Fails when a catalogue enchantment repeats the effect shape of a vanilla
 * enchantment. A shape is every hook with its effect and its requirement
 * condition, and every {@link LevelBasedValue} is dropped, so a weaker or
 * stronger copy of the same shape still counts as a duplicate. The requirement
 * condition is part of the key, because it decides what the effect acts on.
 */
public class EnchantAuditGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final String FIXTURE_PATH = "test_marker";
	private static final Set<String> COST_HOOKS = Set.of("minecraft:ammo_use", "minecraft:item_damage");
	private static final int MAX_DEPTH = 8;
	private static final int MAX_KEY = 400;

	@GameTest
	public void noCatalogueEnchantmentRepeatsAVanillaEffectShape(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder.Reference<Enchantment>> catalogue = registry.listElements()
			.filter(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()))
			.filter(holder -> !FIXTURE_PATH.equals(holder.key().identifier().getPath()))
			.toList();
		List<Holder.Reference<Enchantment>> vanilla = registry.listElements()
			.filter(holder -> "minecraft".equals(holder.key().identifier().getNamespace()))
			.toList();
		List<String> duplicates = new ArrayList<>();
		for (Holder.Reference<Enchantment> holder : catalogue) {
			Set<String> shape = shapeOf(holder.value());
			for (Holder.Reference<Enchantment> other : vanilla) {
				Set<String> vanillaShape = shapeOf(other.value());
				if (!vanillaShape.isEmpty() && repeats(shape, vanillaShape)) {
					duplicates.add(
						holder.key().identifier().getPath() + " repeats " + other.key().identifier().getPath()
					);
				}
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=catalogueAudit RESULT catalogue={} vanilla={} duplicates={}",
			catalogue.size(), vanilla.size(), duplicates.size()
		);
		helper.assertTrue(duplicates.isEmpty(), "an enchantment repeats a vanilla effect shape: " + duplicates);
		helper.succeed();
	}

	private static boolean repeats(final Set<String> shape, final Set<String> vanillaShape) {
		if (shape.equals(vanillaShape)) {
			return true;
		}
		if (!shape.containsAll(vanillaShape)) {
			return false;
		}
		Set<String> extra = new TreeSet<>(shape);
		extra.removeAll(vanillaShape);
		for (String entry : extra) {
			if (!COST_HOOKS.contains(entry.substring(0, entry.indexOf(':')))) {
				return false;
			}
		}
		return true;
	}

	private static Set<String> shapeOf(final Enchantment enchantment) {
		Set<String> shape = new TreeSet<>();
		addEntity(shape, "minecraft:post_attack", enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK));
		addEntity(shape, "minecraft:projectile_spawned",
			enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED));
		addEntity(shape, "minecraft:hit_block", enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK));
		addEntity(shape, "minecraft:tick", enchantment.getEffects(EnchantmentEffectComponents.TICK));
		addEntity(shape, "minecraft:post_piercing_attack",
			enchantment.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK));
		addValue(shape, "minecraft:damage", enchantment.getEffects(EnchantmentEffectComponents.DAMAGE));
		addValue(shape, "minecraft:damage_protection",
			enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION));
		addValue(shape, "minecraft:knockback", enchantment.getEffects(EnchantmentEffectComponents.KNOCKBACK));
		addValue(shape, "minecraft:item_damage", enchantment.getEffects(EnchantmentEffectComponents.ITEM_DAMAGE));
		addValue(shape, "minecraft:ammo_use", enchantment.getEffects(EnchantmentEffectComponents.AMMO_USE));
		addValue(shape, "minecraft:projectile_count",
			enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_COUNT));
		addValue(shape, "minecraft:projectile_spread",
			enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPREAD));
		addValue(shape, "minecraft:projectile_piercing",
			enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_PIERCING));
		addValue(shape, "minecraft:block_experience",
			enchantment.getEffects(EnchantmentEffectComponents.BLOCK_EXPERIENCE));
		addValue(shape, "minecraft:fishing_time_reduction",
			enchantment.getEffects(EnchantmentEffectComponents.FISHING_TIME_REDUCTION));
		addValue(shape, "minecraft:fishing_luck_bonus",
			enchantment.getEffects(EnchantmentEffectComponents.FISHING_LUCK_BONUS));
		addValue(shape, "minecraft:trident_return_acceleration",
			enchantment.getEffects(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION));
		addValue(shape, "minecraft:smash_damage_per_fallen_block",
			enchantment.getEffects(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK));
		EnchantmentValueEffect charge = enchantment.effects().get(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME);
		if (charge != null) {
			shape.add("minecraft:crossbow_charge_time:" + charge.getClass().getSimpleName());
		}
		for (ConditionalEffect<?> entry : enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
			shape.add("minecraft:damage_immunity:" + key(entry));
		}
		for (EnchantmentAttributeEffect entry : enchantment.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
			shape.add("minecraft:attributes:" + key(entry));
		}
		return shape;
	}

	/**
	 * One hook entry as a string. The effect is unwrapped so an `all_of` reads as
	 * its members, and the requirement is rendered beside it.
	 */
	private static String key(final Object entry) {
		StringBuilder text = new StringBuilder();
		describe(entry, 0, text);
		return text.length() > MAX_KEY ? text.substring(0, MAX_KEY) : text.toString();
	}

	private static void addValue(
		final Set<String> shape, final String hook, final List<? extends ConditionalEffect<?>> entries
	) {
		for (ConditionalEffect<?> entry : entries) {
			shape.add(hook + ":" + key(entry));
		}
	}

	private static void addEntity(final Set<String> shape, final String hook, final List<?> entries) {
		for (Object entry : entries) {
			shape.add(hook + ":" + key(entry));
		}
	}

	/**
	 * Renders every value a shape depends on and skips {@link LevelBasedValue}, so
	 * the numbers never enter the key and two copies of one shape still match.
	 */
	private static void describe(final Object object, final int depth, final StringBuilder text) {
		if (object == null || depth > MAX_DEPTH) {
			text.append('_');
			return;
		}

		if (object instanceof LevelBasedValue) {
			text.append('~');
			return;
		}

		if (object instanceof Optional<?> optional) {
			if (optional.isEmpty()) {
				text.append('_');
			} else {
				describe(optional.get(), depth + 1, text);
			}

			return;
		}

		if (object instanceof HolderSet.Named<?> named) {
			text.append('#').append(named.key().location());
			return;
		}

		if (object instanceof HolderSet<?> set) {
			List<String> ids = new ArrayList<>();
			for (Holder<?> holder : set) {
				ids.add(idOf(holder));
			}

			ids.sort(null);
			text.append(ids);
			return;
		}

		if (object instanceof Holder<?> holder) {
			text.append(idOf(holder)).append('(');
			try {
				describe(holder.value(), depth + 1, text);
			} catch (RuntimeException ignored) {
				text.append('?');
			}

			text.append(')');
			return;
		}

		if (object instanceof Identifier identifier) {
			text.append(identifier);
			return;
		}

		if (object instanceof Enum<?> constant) {
			text.append(constant.name());
			return;
		}

		if (object instanceof Number || object instanceof Boolean || object instanceof String) {
			text.append(object);
			return;
		}

		if (object instanceof Iterable<?> iterable) {
			List<String> parts = new ArrayList<>();
			for (Object element : iterable) {
				StringBuilder part = new StringBuilder();
				describe(element, depth + 1, part);
				parts.add(part.toString());
			}

			parts.sort(null);
			text.append(parts);
			return;
		}

		if (object instanceof java.util.Map<?, ?> map) {
			List<String> parts = new ArrayList<>();
			map.forEach((mapKey, mapValue) -> {
				StringBuilder part = new StringBuilder();
				describe(mapKey, depth + 1, part);
				part.append('=');
				describe(mapValue, depth + 1, part);
				parts.add(part.toString());
			});
			parts.sort(null);
			text.append(parts);
			return;
		}

		String name = object.getClass().getName();
		if (name.startsWith("java.") || name.startsWith("com.mojang.")) {
			text.append(name);
			return;
		}

		text.append(object.getClass().getSimpleName()).append('{');
		List<String> parts = new ArrayList<>();
		for (Field field : object.getClass().getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			StringBuilder part = new StringBuilder(field.getName()).append('=');
			try {
				field.setAccessible(true);
				describe(field.get(object), depth + 1, part);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				part.append('?');
			}

			parts.add(part.toString());
		}

		parts.sort(null);
		text.append(String.join(",", parts)).append('}');
	}

	private static String idOf(final Holder<?> holder) {
		return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("direct");
	}
}
