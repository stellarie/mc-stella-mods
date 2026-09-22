package com.stella.mobs;

import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.GearRoller;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The shipped roll rates, measured over seeded rolls. This class pins the shield rate: a shield must be
 * visible in play, on roughly one armed melee mob in four.
 */
public class GearRateGameTest {
	private static final int ROLLS = 4000;
	private static final double RATE_FLOOR = 0.20;
	private static final double RATE_CEILING = 0.30;
	private static final String NO_ROLES_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"roles": {}
		}""";
	private static final String ALL_GEAR_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"roles": {},
			"rolls": { "roll_chance": 1.0 }
		}""";

	@GameTest(maxTicks = 200)
	public void shieldsAppearOnAboutOneArmedMeleeMobInFour(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(NO_ROLES_CONFIG));
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		int armed = 0;
		int armedMelee = 0;
		int shielded = 0;

		for (int i = 0; i < ROLLS; i++) {
			Mob mob = created(helper);
			GearRoller.roll(mob, RollSpec.DEFAULT, RandomSource.create(i), registry);

			if (!mob.getMainHandItem().isEmpty()) {
				armed++;

				if (GearRollGameTest.isMeleeWeapon(mob.getMainHandItem())) {
					armedMelee++;

					if (mob.getOffhandItem().is(Items.SHIELD)) {
						shielded++;
					}
				}
			}
		}

		double rate = armedMelee == 0 ? 0.0 : (double) shielded / armedMelee;
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=shieldRate rolls={} armed={} armedMelee={} shielded={} rate={}",
			ROLLS, armed, armedMelee, shielded, String.format(java.util.Locale.ROOT, "%.4f", rate)
		);
		helper.assertTrue(armedMelee > ROLLS / 8, "too few armed melee mobs to measure a rate: " + armedMelee);
		helper.assertTrue(
			rate >= RATE_FLOOR && rate <= RATE_CEILING,
			"a shield appeared on " + shielded + " of " + armedMelee + " armed melee mobs, a rate of " + rate
		);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest(maxTicks = 200)
	public void unarmedMonstersRollNoGear(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(ALL_GEAR_CONFIG));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		helper.makeMockServerPlayerInLevel();
		Mob creeper = helper.spawnMob(EntityTypes.CREEPER, 2, 3, 2).spawn();
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 4, 3, 4).spawn();

		helper.runAfterDelay(4L, () -> {
			helper.assertTrue(
				creeper.getMainHandItem().isEmpty(),
				"a creeper rolled a main hand item: " + describe(creeper)
			);
			helper.assertTrue(
				creeper.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "a creeper rolled a helmet: " + describe(creeper)
			);
			helper.assertFalse(zombie.getMainHandItem().isEmpty(), "the control zombie rolled no weapon");
			helper.succeed();
		});
	}

	private static String describe(final Mob mob) {
		return mob.getType() + " main=" + mob.getMainHandItem() + " off=" + mob.getOffhandItem()
			+ " head=" + mob.getItemBySlot(EquipmentSlot.HEAD);
	}

	private static Mob created(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.SPAWNER);
		helper.assertTrue(mob != null, "the test mob could not be created");
		return mob;
	}
}
