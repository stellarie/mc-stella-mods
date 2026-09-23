package com.stella.equips;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * The measurement harness. It reads every catalogue enchantment's effects and
 * reports the level 3 value, the level 5 value, and the delta, so a level that
 * does nothing cannot pass. A walk over the effects object only sees
 * {@link LevelBasedValue}, so an enchantment whose lever lives elsewhere is
 * named in {@link #CAPPED} rather than read as a zero delta.
 */
public class EnchantmentLevelGameTest {
	private static final List<Integer> LEVELS = List.of(1, 2, 3, 4, 5);
	private static final int MAX_DEPTH = 8;
	private static final int LEVEL_THREE = 3;
	private static final int LEVEL_FIVE = 5;

	/**
	 * Enchantments with no lever a level can reach: thirteen whose effect is
	 * `run_function`, three whose effect is a `damage_immunity` flag, and
	 * Deflection, whose negation moved to a listener so the cancel and its flash
	 * share one branch. second_wind is one of the thirteen, and its only number is
	 * `periodic_tick` inside a predicate, which no level reaches either. A data
	 * particle adds no lever: its `speed` is a FloatProvider, not a LevelBasedValue.
	 */
	private static final Set<String> CAPPED = Set.of(
		"armor/cloudstep",
		"armor/deflection",
		"armor/dread_aura",
		"armor/ender_ward",
		"armor/guarding_ward",
		"armor/insulated",
		"armor/reactive_plating",
		"armor/second_wind",
		"curse/berserker",
		"tool/echolocation",
		"tool/enchanted_yield",
		"tool/harvester",
		"tool/lumberjack",
		"tool/prospector",
		"tool/veinminer",
		"weapon/momentum",
		"weapon/stormcaller",
		"weapon/tidecaller"
	);

	/** The level 3 value of every scaling enchantment, recorded before any edit. */
	private static final Map<String, String> LEVEL_THREE_BASELINE = new LinkedHashMap<>();

	static {
		LEVEL_THREE_BASELINE.put("armor/bulwark", "0.3");
		LEVEL_THREE_BASELINE.put("armor/farsight", "3.0");
		LEVEL_THREE_BASELINE.put("armor/fleetfoot", "0.3");
		LEVEL_THREE_BASELINE.put("bow/arrow_shower", "1.0,10.0,6.0");
		LEVEL_THREE_BASELINE.put("bow/bomb_arrows", "2.0");
		LEVEL_THREE_BASELINE.put("bow/longshot", "-1.0,0.4,1.0");
		LEVEL_THREE_BASELINE.put("bow/marksman", "1.0,3.0");
		LEVEL_THREE_BASELINE.put("crossbow/repeater", "-0.75,1.0");
		LEVEL_THREE_BASELINE.put("crossbow/siege_bolt", "0.75,1.0,3.0");
		LEVEL_THREE_BASELINE.put("elytra/glider", "6.0");
		LEVEL_THREE_BASELINE.put("shield/aegis", "0.3");
		LEVEL_THREE_BASELINE.put("shield/shield_bash", "1.2");
		LEVEL_THREE_BASELINE.put("tool/blockfeast", "3.0");
		LEVEL_THREE_BASELINE.put("tool/burrow", "1.0,2.0");
		LEVEL_THREE_BASELINE.put("tool/green_thumb", "1.0,2.0");
		LEVEL_THREE_BASELINE.put("weapon/aegis_breaker", "2.0");
		LEVEL_THREE_BASELINE.put("weapon/feathered_edge", "-1.5,3.0");
		LEVEL_THREE_BASELINE.put("weapon/hooked", "3.0,4.0");
		LEVEL_THREE_BASELINE.put("weapon/reach", "1.5");
		LEVEL_THREE_BASELINE.put("weapon/shockwave", "3.0,4.0");
		LEVEL_THREE_BASELINE.put("weapon/stunning_blow", "0.0,1.0,3.0,4.0");
		LEVEL_THREE_BASELINE.put("weapon/wounding", "6.0");
	}

	@GameTest(maxTicks = 200)
	public void everyEnchantmentEitherScalesOrIsCapped(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Map<String, String> unmeasurable = new LinkedHashMap<>();
		Map<String, String> cappedButMeasurable = new LinkedHashMap<>();
		Map<String, String> inert = new LinkedHashMap<>();
		Map<String, String> baselineDrift = new LinkedHashMap<>();
		List<String> reported = new ArrayList<>();
		int capped = 0;

		for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
			Identifier id = holder.key().identifier();
			if (!id.getNamespace().equals(StellaEquips.MOD_ID) || id.getPath().equals("test_marker")) {
				continue;
			}

			String path = id.getPath();
			List<LevelBasedValue> values = levelBasedValues(holder.value().effects());
			boolean isCapped = CAPPED.contains(path);

			if (values.isEmpty()) {
				if (!isCapped) {
					unmeasurable.put(path, "no LevelBasedValue and not capped");
				} else {
					capped++;
					reported.add(path + " status=capped values=0");
				}

				continue;
			}

			if (isCapped) {
				cappedButMeasurable.put(path, "capped but carries " + values.size() + " level value(s)");
			}

			String signature = signatureAt(values, LEVEL_THREE);
			String expected = LEVEL_THREE_BASELINE.get(path);
			if (expected != null && !expected.equals(signature)) {
				baselineDrift.put(path, "level 3 was " + expected + " and is " + signature);
			}

			LevelBasedValue widest = widestMover(values);
			float atThree = widest.calculate(LEVEL_THREE);
			float atFive = widest.calculate(LEVEL_FIVE);
			if (atThree == atFive) {
				inert.put(path, "level 3 and level 5 are both " + atThree);
			}

			reported.add(
				path + " status=scaled maxLevel=" + holder.value().getMaxLevel()
					+ " level3=" + atThree + " level5=" + atFive + " delta=" + Math.abs(atFive - atThree)
					+ " signature=" + signature
			);
		}

		for (String line : reported) {
			StellaEquips.LOGGER.info("[BEHAVIOR] test=enchantmentLevel RESULT {}", line);
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=enchantmentLevels RESULT measured={} capped={} unmeasurable={} inert={} "
				+ "cappedButMeasurable={} baselineDrift={}",
			reported.size(), capped, unmeasurable.size(), inert.size(),
			cappedButMeasurable.size(), baselineDrift.size()
		);

		helper.assertTrue(unmeasurable.isEmpty(), "no lever and not capped: " + unmeasurable);
		helper.assertTrue(cappedButMeasurable.isEmpty(), "capped but measurable: " + cappedButMeasurable);
		helper.assertTrue(inert.isEmpty(), "level 5 does nothing: " + inert);
		helper.assertTrue(baselineDrift.isEmpty(), "the offered levels moved: " + baselineDrift);
		helper.assertTrue(
			capped == CAPPED.size(),
			"the capped allowlist holds " + CAPPED.size() + " names and " + capped + " were seen"
		);
		helper.succeed();
	}

	private static String signatureAt(final List<LevelBasedValue> values, final int level) {
		Set<String> seen = new TreeSet<>();
		for (LevelBasedValue value : values) {
			seen.add(String.valueOf(value.calculate(level)));
		}

		return String.join(",", seen);
	}

	private static LevelBasedValue widestMover(final List<LevelBasedValue> values) {
		LevelBasedValue widest = values.get(0);
		float widestDelta = -1.0F;
		for (LevelBasedValue value : values) {
			float delta = Math.abs(value.calculate(LEVEL_FIVE) - value.calculate(LEVEL_THREE));
			if (delta > widestDelta) {
				widestDelta = delta;
				widest = value;
			}
		}

		return widest;
	}

	/** Every LevelBasedValue the effects object holds, found by walking it. */
	private static List<LevelBasedValue> levelBasedValues(final DataComponentMap effects) {
		List<LevelBasedValue> values = new ArrayList<>();
		for (TypedDataComponent<?> component : effects) {
			collect(component.value(), values, 0);
		}

		return values;
	}

	private static void collect(final Object object, final List<LevelBasedValue> values, final int depth) {
		if (object == null || depth > MAX_DEPTH) {
			return;
		}

		if (object instanceof LevelBasedValue value) {
			values.add(value);
			return;
		}

		if (object instanceof Optional<?> optional) {
			optional.ifPresent(inner -> collect(inner, values, depth + 1));
			return;
		}

		if (object instanceof Iterable<?> iterable) {
			for (Object element : iterable) {
				collect(element, values, depth + 1);
			}

			return;
		}

		if (object instanceof Map<?, ?> map) {
			for (Object element : map.values()) {
				collect(element, values, depth + 1);
			}

			return;
		}

		if (!isWalkable(object.getClass())) {
			return;
		}

		for (Field field : object.getClass().getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			try {
				field.setAccessible(true);
				collect(field.get(object), values, depth + 1);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// a field the harness cannot read is reported by the caller as no lever
			}
		}
	}

	/** Walk the mod's own payloads and the Minecraft value holders, nothing else. */
	private static boolean isWalkable(final Class<?> type) {
		if (type.isPrimitive() || type.isEnum() || type.isArray()) {
			return false;
		}

		String name = type.getName();
		if (name.startsWith("java.") || name.startsWith("com.mojang.") || name.startsWith("it.unimi")) {
			return false;
		}

		return name.startsWith("net.minecraft.world.item.enchantment")
			|| name.startsWith("net.minecraft.world.entity.ai.attributes")
			|| name.startsWith("net.minecraft.util.valueproviders");
	}

	private static Registry<Enchantment> registry(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}
}
