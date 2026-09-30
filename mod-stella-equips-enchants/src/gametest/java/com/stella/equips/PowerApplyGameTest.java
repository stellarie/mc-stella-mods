package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.refine.Refine;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

public class PowerApplyGameTest {
	private static final int LEVEL_AT_CAP = 50;
	private static final int LEVEL_HALFWAY = 25;
	private static final double TOLERANCE = 1.0E-9;
	private static final double HEALTH_GAIN_HALFWAY = 96.0;
	private static final Identifier MAX_HEALTH_MODIFIER = modifier("max_health");
	private static final Identifier ARMOR_MODIFIER = modifier("armor");
	private static final Identifier KNOCKBACK_MODIFIER = modifier("knockback_resistance");
	private static final Identifier ATTACK_DAMAGE_MODIFIER = modifier("attack_damage");
	private static final Identifier ATTACK_SPEED_MODIFIER = modifier("attack_speed");

	@GameTest
	public void onlyHealthIsGainedAtTheCap(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double health = modifierAmount(player, MAX_HEALTH_MODIFIER);
		helper.assertTrue(
			Math.abs(health - PowerFixtures.HEALTH_GAIN_AT_CAP) < TOLERANCE,
			"the maximum health gain at the cap was " + health + " instead of " + PowerFixtures.HEALTH_GAIN_AT_CAP
		);
		helper.assertTrue(retiredModifiers(player) == 0, "a capped player carried a retired Power modifier");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=attributeCap RESULT level={} health={} retired={}",
			LEVEL_AT_CAP, health, retiredModifiers(player)
		);
		helper.succeed();
	}

	@GameTest
	public void healthGainKeepsItsValueAtLevelOneHalfwayAndTheCap(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer atOne = mockPlayer(helper);
		ServerPlayer halfway = mockPlayer(helper);
		ServerPlayer atCap = mockPlayer(helper);
		PowerAttributes.apply(atOne, 1, config);
		PowerAttributes.apply(halfway, LEVEL_HALFWAY, config);
		PowerAttributes.apply(atCap, LEVEL_AT_CAP, config);
		double gainAtOne = modifierAmount(atOne, MAX_HEALTH_MODIFIER);
		double gainHalfway = modifierAmount(halfway, MAX_HEALTH_MODIFIER);
		double gainAtCap = modifierAmount(atCap, MAX_HEALTH_MODIFIER);
		helper.assertTrue(gainAtOne == 0.0, "a level 1 player carried a health gain of " + gainAtOne);
		helper.assertTrue(
			Math.abs(gainHalfway - HEALTH_GAIN_HALFWAY) < TOLERANCE,
			"the health gain at level " + LEVEL_HALFWAY + " was " + gainHalfway + " instead of " + HEALTH_GAIN_HALFWAY
		);
		helper.assertTrue(
			Math.abs(gainAtCap - PowerFixtures.HEALTH_GAIN_AT_CAP) < TOLERANCE,
			"the health gain at the cap was " + gainAtCap
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=healthCurve RESULT atLevel1={} halfway={} atCap={}", gainAtOne, gainHalfway, gainAtCap
		);
		helper.succeed();
	}

	@GameTest
	public void staleModifiersRemoved(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		addStale(player, Attributes.ARMOR, ARMOR_MODIFIER, 11.76);
		addStale(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER, 0.294);
		addStale(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER, 20.0);
		addStale(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER, 1.0);
		AttributeModifier refineArmor = new AttributeModifier(
			Refine.ARMOR_ID, 3.0, AttributeModifier.Operation.ADD_VALUE
		);
		player.getAttribute(Attributes.ARMOR).addPermanentModifier(refineArmor);
		int before = retiredModifiers(player);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		int after = retiredModifiers(player);
		double health = modifierAmount(player, MAX_HEALTH_MODIFIER);
		helper.assertTrue(before == 4, "the fixture started with " + before + " of the 4 stale modifiers");
		helper.assertTrue(after == 0, "one apply left " + after + " stale modifiers");
		helper.assertTrue(
			Math.abs(health - PowerFixtures.HEALTH_GAIN_AT_CAP) < TOLERANCE,
			"the health gain after the apply was " + health
		);
		helper.assertTrue(
			player.getAttribute(Attributes.ARMOR).getModifier(Refine.ARMOR_ID) != null,
			"the apply removed the refine armor modifier"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=staleModifiersRemoved RESULT before={} after={} health={}", before, after, health
		);
		helper.succeed();
	}

	@GameTest
	public void applyingTwiceLeavesEachAttributeAtItsSingleApplicationValue(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double[] vanilla = values(player);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double[] once = values(player);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double[] twice = values(player);
		int applied = differences(vanilla, once);
		int changed = differences(once, twice);
		helper.assertTrue(
			applied == 1,
			"the first application moved " + applied + " attributes instead of only health: " + describe(once)
		);
		helper.assertTrue(
			changed == 0,
			"a second application moved " + changed + " attributes: once=" + describe(once) + " twice=" + describe(twice)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=applyIdempotent RESULT applied={} changed={} health={}",
			applied, changed, twice[0]
		);
		helper.succeed();
	}

	@GameTest
	public void removingTheModifiersReturnsEveryAttributeToItsVanillaDefault(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double[] vanilla = values(player);
		double[] baseValues = baseValues(player);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double[] applied = values(player);
		PowerAttributes.remove(player);
		double[] after = values(player);
		int appliedCount = differences(vanilla, applied);
		int changed = differences(vanilla, after);
		int basesChanged = differences(baseValues, baseValues(player));
		helper.assertTrue(appliedCount == 1, "the application moved " + appliedCount + " attributes instead of only health");
		helper.assertTrue(changed == 0, "removal left " + changed + " attributes moved: " + describe(after));
		helper.assertTrue(basesChanged == 0, "removal changed " + basesChanged + " base values");
		helper.assertTrue(
			player.getHealth() <= player.getMaxHealth(),
			"health " + player.getHealth() + " stayed above the restored maximum " + player.getMaxHealth()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=removeRestores RESULT applied={} changed={} health={} maxHealth={} vanillaMaxHealth={}",
			appliedCount, changed, player.getHealth(), player.getMaxHealth(), vanilla[0]
		);
		helper.succeed();
	}

	private static void addStale(
		final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		player.getAttribute(attribute).addPermanentModifier(
			new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE)
		);
	}

	private static int retiredModifiers(final ServerPlayer player) {
		int count = 0;
		count += modifierAmount(player, ARMOR_MODIFIER) != 0.0 ? 1 : 0;
		count += modifierAmount(player, KNOCKBACK_MODIFIER) != 0.0 ? 1 : 0;
		count += modifierAmount(player, ATTACK_DAMAGE_MODIFIER) != 0.0 ? 1 : 0;
		count += modifierAmount(player, ATTACK_SPEED_MODIFIER) != 0.0 ? 1 : 0;
		return count;
	}

	private static Identifier modifier(final String name) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_" + name);
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static double modifierAmount(final ServerPlayer player, final Identifier id) {
		AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
		if (id.equals(ATTACK_DAMAGE_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
		} else if (id.equals(ATTACK_SPEED_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_SPEED);
		} else if (id.equals(ARMOR_MODIFIER)) {
			instance = player.getAttribute(Attributes.ARMOR);
		} else if (id.equals(KNOCKBACK_MODIFIER)) {
			instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		}

		if (instance == null) {
			return 0.0;
		}

		AttributeModifier modifier = instance.getModifier(id);
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static double[] values(final ServerPlayer player) {
		return new double[] {
			value(player, Attributes.MAX_HEALTH),
			value(player, Attributes.ARMOR),
			value(player, Attributes.KNOCKBACK_RESISTANCE),
			value(player, Attributes.ATTACK_DAMAGE),
			value(player, Attributes.ATTACK_SPEED)
		};
	}

	private static double[] baseValues(final ServerPlayer player) {
		return new double[] {
			base(player, Attributes.MAX_HEALTH),
			base(player, Attributes.ARMOR),
			base(player, Attributes.KNOCKBACK_RESISTANCE),
			base(player, Attributes.ATTACK_DAMAGE),
			base(player, Attributes.ATTACK_SPEED)
		};
	}

	private static double value(final ServerPlayer player, final Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance == null ? Double.NaN : instance.getValue();
	}

	private static double base(final ServerPlayer player, final Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance == null ? Double.NaN : instance.getBaseValue();
	}

	private static int differences(final double[] first, final double[] second) {
		int different = 0;
		for (int index = 0; index < first.length; index++) {
			if (Math.abs(first[index] - second[index]) > TOLERANCE) {
				different++;
			}
		}

		return different;
	}

	private static String describe(final double[] values) {
		return "health=" + values[0] + " armor=" + values[1] + " knockback=" + values[2]
			+ " damage=" + values[3] + " speed=" + values[4];
	}
}
