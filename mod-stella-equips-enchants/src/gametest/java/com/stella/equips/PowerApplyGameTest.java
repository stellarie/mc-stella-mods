package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerCurve;
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
	private static final Identifier MAX_HEALTH_MODIFIER = modifier("max_health");
	private static final Identifier ARMOR_MODIFIER = modifier("armor");
	private static final Identifier KNOCKBACK_MODIFIER = modifier("knockback_resistance");
	private static final Identifier ATTACK_DAMAGE_MODIFIER = modifier("attack_damage");
	private static final Identifier ATTACK_SPEED_MODIFIER = modifier("attack_speed");

	@GameTest
	public void everyAttributeReachesItsStatedValueAtTheCap(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double health = modifierAmount(player, MAX_HEALTH_MODIFIER);
		double armor = modifierAmount(player, ARMOR_MODIFIER);
		double knockback = modifierAmount(player, KNOCKBACK_MODIFIER);
		double damage = modifierAmount(player, ATTACK_DAMAGE_MODIFIER);
		double speed = modifierAmount(player, ATTACK_SPEED_MODIFIER);
		helper.assertTrue(
			Math.abs(health - PowerFixtures.HEALTH_GAIN_AT_CAP) < TOLERANCE,
			"the maximum health gain at the cap was " + health + " instead of " + PowerFixtures.HEALTH_GAIN_AT_CAP
		);
		helper.assertTrue(
			Math.abs(armor - PowerFixtures.ARMOR_AT_CAP) < TOLERANCE,
			"the armor at the cap was " + armor + " instead of " + PowerFixtures.ARMOR_AT_CAP
		);
		helper.assertTrue(
			Math.abs(knockback - PowerFixtures.KNOCKBACK_RESISTANCE_AT_CAP) < TOLERANCE,
			"the knockback resistance at the cap was " + knockback
		);
		helper.assertTrue(
			Math.abs(damage - PowerFixtures.ATTACK_DAMAGE_AT_CAP) < TOLERANCE,
			"the attack damage at the cap was " + damage + " instead of " + PowerFixtures.ATTACK_DAMAGE_AT_CAP
		);
		helper.assertTrue(
			Math.abs(speed - PowerFixtures.ATTACK_SPEED_AT_CAP) < TOLERANCE,
			"the attack speed at the cap was " + speed + " instead of " + PowerFixtures.ATTACK_SPEED_AT_CAP
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=attributeCap RESULT level={} health={} armor={} knockback={} damage={} speed={}",
			LEVEL_AT_CAP, health, armor, knockback, damage, speed
		);
		helper.succeed();
	}

	@GameTest
	public void everyAttributeIsZeroAtLevelOneAndHalfwayAtTheMiddle(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer atOne = mockPlayer(helper);
		ServerPlayer halfway = mockPlayer(helper);
		PowerAttributes.apply(atOne, 1, config);
		PowerAttributes.apply(halfway, LEVEL_HALFWAY, config);
		double fraction = PowerCurve.levelFraction(LEVEL_HALFWAY, config);
		double damageAtOne = modifierAmount(atOne, ATTACK_DAMAGE_MODIFIER);
		double armorAtOne = modifierAmount(atOne, ARMOR_MODIFIER);
		double damageHalfway = modifierAmount(halfway, ATTACK_DAMAGE_MODIFIER);
		double armorHalfway = modifierAmount(halfway, ARMOR_MODIFIER);
		helper.assertTrue(
			damageAtOne == 0.0 && armorAtOne == 0.0,
			"a level 1 player carried damage " + damageAtOne + " and armor " + armorAtOne
		);
		helper.assertTrue(
			Math.abs(damageHalfway - PowerFixtures.ATTACK_DAMAGE_AT_CAP * fraction) < TOLERANCE,
			"the damage at level " + LEVEL_HALFWAY + " was " + damageHalfway
		);
		helper.assertTrue(
			Math.abs(armorHalfway - PowerFixtures.ARMOR_AT_CAP * fraction) < TOLERANCE,
			"the armor at level " + LEVEL_HALFWAY + " was " + armorHalfway
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=attributeRamp RESULT fraction={} damageAtLevel1={} armorAtLevel1={} damageHalfway={} armorHalfway={}",
			fraction, damageAtOne, armorAtOne, damageHalfway, armorHalfway
		);
		helper.succeed();
	}

	@GameTest
	public void theArmorAndKnockbackTermsLeaveRoomForTheEnchantments(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		PowerAttributes.apply(player, LEVEL_AT_CAP, config);
		double armor = modifierAmount(player, ARMOR_MODIFIER);
		double knockback = modifierAmount(player, KNOCKBACK_MODIFIER);
		helper.assertTrue(
			armor < PowerFixtures.ARMOR_CEILING,
			"the armor modifier " + armor + " fills the vanilla ceiling of " + PowerFixtures.ARMOR_CEILING
		);
		helper.assertTrue(
			knockback < PowerFixtures.KNOCKBACK_CEILING,
			"the knockback modifier " + knockback + " fills the vanilla ceiling of " + PowerFixtures.KNOCKBACK_CEILING
		);
		helper.assertTrue(armor > 0.0 && knockback > 0.0, "a capped player carried no armor or knockback resistance");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=enchantmentHeadroom RESULT armor={} armorHeadroom={} knockback={} knockbackHeadroom={}",
			armor, PowerFixtures.ARMOR_CEILING - armor, knockback, PowerFixtures.KNOCKBACK_CEILING - knockback
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
			applied == 5,
			"the first application moved " + applied + " of the 5 attributes: " + describe(once)
		);
		helper.assertTrue(
			changed == 0,
			"a second application moved " + changed + " attributes: once=" + describe(once) + " twice=" + describe(twice)
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=applyIdempotent RESULT applied={} changed={} health={} armor={} knockback={} damage={} speed={}",
			applied, changed, twice[0], twice[1], twice[2], twice[3], twice[4]
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
		helper.assertTrue(appliedCount == 5, "the application moved only " + appliedCount + " attributes");
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
