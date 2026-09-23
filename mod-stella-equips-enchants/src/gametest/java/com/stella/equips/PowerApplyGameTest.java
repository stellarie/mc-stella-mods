package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerState;
import com.stella.equips.power.PowerTracker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

public class PowerApplyGameTest {
	private static final long SURVIVABILITY_POINTS = 1000L;
	private static final int OUTPUT_LEVEL = 50;
	private static final float STARTING_HEALTH = 10.0F;
	private static final double TOLERANCE = 1.0E-9;
	private static final int SAVED_LEVEL = 30;
	private static final double SAVED_COMBAT_POINTS = 123.5;
	private static final double RUNAWAY_COMBAT_POINTS = 1_000_000.0;
	private static final Identifier MAX_HEALTH_MODIFIER = modifier("max_health");
	private static final Identifier ARMOR_MODIFIER = modifier("armor");
	private static final Identifier ATTACK_DAMAGE_MODIFIER = modifier("attack_damage");
	private static final Identifier ATTACK_SPEED_MODIFIER = modifier("attack_speed");

	@GameTest
	public void applyingTwiceLeavesEachAttributeAtItsSingleApplicationValue(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double[] vanilla = values(player);
		PowerAttributes.apply(player, SURVIVABILITY_POINTS, OUTPUT_LEVEL, config);
		double[] once = values(player);
		PowerAttributes.apply(player, SURVIVABILITY_POINTS, OUTPUT_LEVEL, config);
		double[] twice = values(player);
		int changed = differences(once, twice);
		int applied = differences(vanilla, once);
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
		PowerAttributes.apply(player, SURVIVABILITY_POINTS, OUTPUT_LEVEL, config);
		double[] applied = values(player);
		PowerAttributes.remove(player);
		double[] after = values(player);
		int appliedCount = differences(vanilla, applied);
		int changed = differences(vanilla, after);
		int basesChanged = differences(baseValues, baseValues(player));
		helper.assertTrue(
			appliedCount == 5, "the application moved only " + appliedCount + " attributes before removal"
		);
		helper.assertTrue(
			changed == 0,
			"removal left " + changed + " attributes moved: vanilla=" + describe(vanilla) + " after=" + describe(after)
		);
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

	@GameTest
	public void theOutputAxisStopsAtItsStatedMaximum(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double vanillaDamage = value(player, Attributes.ATTACK_DAMAGE);
		double vanillaSpeed = value(player, Attributes.ATTACK_SPEED);
		PowerState.set(player, new PowerState(RUNAWAY_COMBAT_POINTS, 1));
		PowerTracker.scanPlayer(player, config);
		int level = PowerState.get(player).level();
		double damageAtCap = value(player, Attributes.ATTACK_DAMAGE) - vanillaDamage;
		double speedAtCap = value(player, Attributes.ATTACK_SPEED) - vanillaSpeed;
		PowerState.set(player, new PowerState(RUNAWAY_COMBAT_POINTS * 9.0, level));
		PowerTracker.scanPlayer(player, config);
		double damageAfterMore = value(player, Attributes.ATTACK_DAMAGE) - vanillaDamage;
		double speedAfterMore = value(player, Attributes.ATTACK_SPEED) - vanillaSpeed;
		helper.assertTrue(
			level == config.levelCap(),
			RUNAWAY_COMBAT_POINTS + " combat points reached level " + level + " instead of the cap " + config.levelCap()
		);
		helper.assertTrue(
			Math.abs(damageAtCap - PowerFixtures.ATTACK_DAMAGE_MAX) < TOLERANCE,
			"at the cap the attack damage bonus was " + damageAtCap + " instead of " + PowerFixtures.ATTACK_DAMAGE_MAX
		);
		helper.assertTrue(
			Math.abs(speedAtCap - PowerFixtures.ATTACK_SPEED_MAX) < TOLERANCE,
			"at the cap the attack speed bonus was " + speedAtCap + " instead of " + PowerFixtures.ATTACK_SPEED_MAX
		);
		helper.assertTrue(
			Math.abs(damageAfterMore - damageAtCap) < TOLERANCE && Math.abs(speedAfterMore - speedAtCap) < TOLERANCE,
			"nine times the combat points bought damage " + damageAfterMore + " and speed " + speedAfterMore
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=outputCeiling RESULT combatPoints={} level={} damageBonus={} speedBonus={} damageAfterNineTimes={} speedAfterNineTimes={}",
			RUNAWAY_COMBAT_POINTS, level, damageAtCap, speedAtCap, damageAfterMore, speedAfterMore
		);
		helper.succeed();
	}

	@GameTest
	public void theArmorTermPlateausAtTheVanillaCeiling(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double vanillaArmor = value(player, Attributes.ARMOR);
		PowerAttributes.apply(player, (long) PowerFixtures.ARMOR_PLATEAU_POINTS, 1, config);
		double armorValueAtPlateau = value(player, Attributes.ARMOR) - vanillaArmor;
		double modifierAtPlateau = modifierAmount(player, ARMOR_MODIFIER);
		PowerAttributes.apply(player, PowerFixtures.DISTANCE_CAP_POINTS + PowerFixtures.DEPTH_CAP_POINTS, 1, config);
		double armorValueAtTravelCeiling = value(player, Attributes.ARMOR) - vanillaArmor;
		double modifierAtTravelCeiling = modifierAmount(player, ARMOR_MODIFIER);
		helper.assertTrue(
			Math.abs(armorValueAtPlateau - PowerFixtures.ARMOR_CEILING) < TOLERANCE,
			"at " + PowerFixtures.ARMOR_PLATEAU_POINTS + " points the armor value was " + armorValueAtPlateau
		);
		helper.assertTrue(
			Math.abs(modifierAtPlateau - PowerFixtures.ARMOR_CEILING) < TOLERANCE,
			"at " + PowerFixtures.ARMOR_PLATEAU_POINTS + " points the armor modifier was " + modifierAtPlateau
		);
		helper.assertTrue(
			Math.abs(modifierAtTravelCeiling - PowerFixtures.ARMOR_CEILING) < TOLERANCE,
			"the armor modifier at the travel ceiling was " + modifierAtTravelCeiling + " instead of the ceiling "
				+ PowerFixtures.ARMOR_CEILING
		);
		helper.assertTrue(
			Math.abs(armorValueAtTravelCeiling - armorValueAtPlateau) < TOLERANCE,
			"the armor value moved from " + armorValueAtPlateau + " to " + armorValueAtTravelCeiling + " past the plateau"
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=armorPlateau RESULT plateauPoints={} modifierAtPlateau={} modifierAtTravelCeiling={} valueAtPlateau={} valueAtTravelCeiling={}",
			PowerFixtures.ARMOR_PLATEAU_POINTS, modifierAtPlateau, modifierAtTravelCeiling,
			armorValueAtPlateau, armorValueAtTravelCeiling
		);
		helper.succeed();
	}

	@GameTest
	public void theSurvivabilityAxisLeavesOutputAlone(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double[] vanilla = values(player);
		PowerAttributes.apply(player, SURVIVABILITY_POINTS, 1, config);
		double[] after = values(player);
		helper.assertTrue(after[0] > vanilla[0], "the survivability axis did not raise max health");
		helper.assertTrue(
			Math.abs(after[3] - vanilla[3]) < TOLERANCE && Math.abs(after[4] - vanilla[4]) < TOLERANCE,
			"travel changed the output axis: damage " + vanilla[3] + " to " + after[3] + ", speed " + vanilla[4] + " to " + after[4]
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=survivabilityAxisOnly RESULT healthGained={} armorGained={} damageGained={} speedGained={}",
			after[0] - vanilla[0], after[1] - vanilla[1], after[3] - vanilla[3], after[4] - vanilla[4]
		);
		helper.succeed();
	}

	@GameTest
	public void theCombatAxisLeavesSurvivabilityAlone(final GameTestHelper helper) {
		PowerConfig config = PowerFixtures.acceptanceConfig();
		ServerPlayer player = mockPlayer(helper);
		double[] vanilla = values(player);
		PowerAttributes.apply(player, 0L, OUTPUT_LEVEL, config);
		double[] after = values(player);
		helper.assertTrue(
			after[3] > vanilla[3] && after[4] > vanilla[4],
			"the combat axis did not raise the output: damage " + after[3] + ", speed " + after[4]
		);
		helper.assertTrue(
			Math.abs(after[0] - vanilla[0]) < TOLERANCE && Math.abs(after[1] - vanilla[1]) < TOLERANCE,
			"kills changed survivability: health " + vanilla[0] + " to " + after[0] + ", armor " + vanilla[1] + " to " + after[1]
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=combatAxisOnly RESULT damageGained={} speedGained={} healthGained={} armorGained={}",
			after[3] - vanilla[3], after[4] - vanilla[4], after[0] - vanilla[0], after[1] - vanilla[1]
		);
		helper.succeed();
	}

	@GameTest
	public void aPlayerLevelSurvivesSaveAndLoadOfTheAttachment(final GameTestHelper helper) {
		ServerPlayer saved = mockPlayer(helper);
		PowerState.set(saved, new PowerState(SAVED_COMBAT_POINTS, SAVED_LEVEL));
		TagValueOutput output = TagValueOutput.createWithContext(
			ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
		);
		saved.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		ServerPlayer loaded = mockPlayer(helper);
		ValueInput input = TagValueInput.create(
			ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag
		);
		loaded.load(input);
		PowerState restored = PowerState.get(loaded);
		helper.assertTrue(
			restored.level() == SAVED_LEVEL,
			"the saved level " + SAVED_LEVEL + " loaded as " + restored.level()
		);
		helper.assertTrue(
			Math.abs(restored.combatPoints() - SAVED_COMBAT_POINTS) < TOLERANCE,
			"the saved combat points " + SAVED_COMBAT_POINTS + " loaded as " + restored.combatPoints()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=saveLoadLevel RESULT savedLevel={} loadedLevel={} savedPoints={} loadedPoints={} tagBytes={}",
			SAVED_LEVEL, restored.level(), SAVED_COMBAT_POINTS, restored.combatPoints(), tag.size()
		);
		helper.succeed();
	}

	@GameTest
	public void theAttachmentIsPersistentAndCopiesOnDeath(final GameTestHelper helper) {
		boolean persistent = PowerState.TYPE.isPersistent();
		boolean copyOnDeath = PowerState.TYPE.copyOnDeath();
		boolean hasCodec = PowerState.TYPE.persistenceCodec() != null;
		helper.assertTrue(persistent, "the power state attachment is not persistent");
		helper.assertTrue(copyOnDeath, "the power state attachment does not copy on death");
		helper.assertTrue(hasCodec, "the power state attachment has no persistence codec");
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=attachmentFlags RESULT id={} persistent={} copyOnDeath={} codec={}",
			PowerState.TYPE.identifier(), persistent, copyOnDeath, hasCodec
		);
		helper.succeed();
	}

	@GameTest
	public void theRespawnCopyCarriesTheLevelToTheNewPlayer(final GameTestHelper helper) {
		ServerPlayer died = helper.makeMockServerPlayerInLevel();
		PowerState.set(died, new PowerState(SAVED_COMBAT_POINTS, SAVED_LEVEL));
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(
			died, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED
		);
		PowerState carried = PowerState.get(reborn);
		boolean replaced = reborn != died;
		helper.assertTrue(replaced, "the respawn returned the same player object");
		helper.assertTrue(
			carried.level() == SAVED_LEVEL,
			"the respawn copy carried level " + carried.level() + " instead of " + SAVED_LEVEL
		);
		helper.assertTrue(
			Math.abs(carried.combatPoints() - SAVED_COMBAT_POINTS) < TOLERANCE,
			"the respawn copy carried " + carried.combatPoints() + " points instead of " + SAVED_COMBAT_POINTS
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=respawnCopy RESULT levelBefore={} levelAfter={} pointsAfter={} newPlayer={}",
			SAVED_LEVEL, carried.level(), carried.combatPoints(), replaced
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
		AttributeInstance instance = player.getAttribute(Attributes.ARMOR);
		if (id.equals(ATTACK_DAMAGE_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
		} else if (id.equals(ATTACK_SPEED_MODIFIER)) {
			instance = player.getAttribute(Attributes.ATTACK_SPEED);
		} else if (id.equals(MAX_HEALTH_MODIFIER)) {
			instance = player.getAttribute(Attributes.MAX_HEALTH);
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
