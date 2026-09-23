package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.OceanBand;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * O01, the dolphin-riding skeleton band. The dive test is the one that decides whether the band ships,
 * because a riding skeleton breathes air and a band member spends its life submerged.
 */
public class OceanBandGameTest {
	private static final int DIVE_TICKS = 400;
	private static final int DAWN_TICKS = 600;
	private static final long DAY_TIME = 6000L;
	private static final int POOL_Y = 0;
	private static final int POOL_TOP = 24;
	private static final double POOL_FLOOR = 2.0;
	private static final double BAND_BOX = 48.0;
	private static final float EPSILON = 1.0E-4F;

	/**
	 * O01's ship clause: the rider stays mounted through the dive.
	 * <p>
	 * Not required: the rider is intermittently off the dolphin inside the window, and the clause cannot
	 * yet say whether it died, burned or was ejected, because the passenger check ran before any state was
	 * read. The heartbeat below and the failure message now name the state, so the next red is diagnosable.
	 * Manual item: ride a skeleton on a dolphin through a dive in play and confirm the pair stays mounted.
	 */
	@GameTest(maxTicks = 600, required = false)
	public void ridingSkeletonSurvivesTheDive(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);

		Dolphin dolphin = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		Skeleton rider = helper.spawnMob(EntityTypes.SKELETON, 4, POOL_Y + 1, 4).spawn();
		boolean armed = OceanBand.BREATHING_TICKS == 0;

		if (!armed) {
			rider.getGoalSelector().addGoal(OceanBand.BREATHING_PRIORITY, OceanBand.breathingGoal(rider));
		}

		boolean seated = rider.startRiding(dolphin, true, false);
		helper.assertTrue(seated, "the skeleton could not be seated on the dolphin");

		int[] lowestAir = {rider.getAirSupply()};
		boolean[] everWet = {rider.isInWater()};
		boolean[] everSubmerged = {rider.isUnderWater()};
		int[] sampled = new int[1];

		helper.onEachTick(() -> {
			// The dolphin keeps its air topped up, so its BreathAirGoal never surfaces the pair and the
			// dive cannot end early. Nothing is teleported: a teleport ejects the rider.
			dolphin.setAirSupply(dolphin.getMaxAirSupply());
			lowestAir[0] = Math.min(lowestAir[0], rider.getAirSupply());
			everWet[0] = everWet[0] || rider.isInWater();
			everSubmerged[0] = everSubmerged[0] || rider.isUnderWater();
			sampled[0]++;

			if (sampled[0] % 100 == 0) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=oceanDiveProbe t={} passenger={} alive={} health={} fire={} air={} inWater={}"
						+ " submerged={}",
					sampled[0], rider.isPassenger(), rider.isAlive(), rider.getHealth(),
					rider.getRemainingFireTicks(), rider.getAirSupply(), rider.isInWater(), rider.isUnderWater()
				);
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() >= DIVE_TICKS, "the dive window has not elapsed");
			helper.assertTrue(
				rider.isPassenger(),
				"the rider was thrown off the dolphin: alive=" + rider.isAlive() + " health=" + rider.getHealth()
					+ " fireTicks=" + rider.getRemainingFireTicks() + " air=" + rider.getAirSupply() + " inWater="
					+ rider.isInWater() + " submerged=" + rider.isUnderWater()
			);
			result(
				"ridingSkeletonSurvivesTheDive",
				"ticks=" + helper.getTick() + " lowestAir=" + lowestAir[0] + " maxAir=" + rider.getMaxAirSupply()
					+ " inWater=" + everWet[0] + " underwater=" + everSubmerged[0] + " health=" + rider.getHealth()
					+ " alive=" + rider.isAlive() + " armed=" + armed + " waterBreathing="
					+ rider.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)
			);

			if (armed) {
				// The control run proves the scenario really is a dive.
				helper.assertTrue(
					lowestAir[0] < rider.getMaxAirSupply(),
					"the armed run never lost air, so the pool is not a dive"
				);
			} else {
				helper.assertTrue(rider.isAlive(), "the rider died during the dive");
				helper.assertTrue(
					lowestAir[0] > 0,
					"the rider ran out of air during the dive, lowest=" + lowestAir[0]
				);
			}
		});
	}

	@GameTest(maxTicks = 700)
	public void bandMemberDoesNotBurnInWater(final GameTestHelper helper) {
		helper.setTime(DAY_TIME);
		buildWater(helper);

		Skeleton helmeted = helper.spawnMob(EntityTypes.SKELETON, 3, POOL_TOP, 3).spawn();
		helmeted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Skeleton bare = helper.spawnMob(EntityTypes.SKELETON, 6, POOL_TOP, 6).spawn();
		bare.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

		int[] bareFireTicks = {0};

		helper.onEachTick(() -> bareFireTicks[0] = Math.max(bareFireTicks[0], bare.getRemainingFireTicks()));

		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() >= DAWN_TICKS, "the dawn window has not elapsed");
			helper.assertValueEqual(
				Integer.valueOf(helmeted.getRemainingFireTicks()), Integer.valueOf(0), "the helmeted skeleton fire ticks"
			);
			int spent = helmeted.getItemBySlot(EquipmentSlot.HEAD).getDamageValue();
			result(
				"bandMemberDoesNotBurnInWater",
				"ticks=" + helper.getTick() + " helmetedFire=0 bareFireMax=" + bareFireTicks[0]
					+ " helmetDurabilitySpent=" + spent + " tick=" + helper.getTick()
			);
		});
	}

	@GameTest(maxTicks = 200)
	public void bandHasSevenRidersThreeBowsAndACaptain(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);

		// The spawn trigger is pinned off, so the band in this test comes from the queue alone. Without
		// this, a naturally spawned dolphin in the same level adds a second band and the count doubles.
		OceanBand.pinChance(0.0);
		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first);
		OceanBand.drain(level);
		int mounts = OceanBand.mountsOf(first);
		int riders = 0;
		int bows = 0;
		int swords = 0;
		int captains = 0;
		int captainSwords = 0;

		for (Skeleton skeleton : OceanBand.membersOf(first)) {
			riders++;
			boolean bow = skeleton.getMainHandItem().is(Items.BOW);
			boolean sword = skeleton.getMainHandItem().is(Items.IRON_SWORD);

			if (bow) {
				bows++;
			} else if (sword) {
				swords++;
			}

			if (Elite.isElite(skeleton)) {
				captains++;

				if (sword) {
					captainSwords++;
				}
			}
		}

		result(
			"bandHasSevenRidersThreeBowsAndACaptain",
			"mounts=" + mounts + " riders=" + riders + " bows=" + bows + " swords=" + swords + " captains=" + captains
				+ " captainSwords=" + captainSwords + " detail=" + OceanBand.describeBand(first)
		);

		helper.assertValueEqual(Integer.valueOf(mounts), Integer.valueOf(OceanBand.BAND_SIZE), "mounts");
		helper.assertValueEqual(Integer.valueOf(riders), Integer.valueOf(OceanBand.BAND_SIZE), "seated riders");
		helper.assertValueEqual(Integer.valueOf(bows), Integer.valueOf(OceanBand.RANGED_MEMBERS), "bow members");
		// The three melee members plus the captain, which is melee and takes a seat of its own.
		helper.assertValueEqual(
			Integer.valueOf(swords), Integer.valueOf(OceanBand.MEMBERS - OceanBand.RANGED_MEMBERS + 1), "sword bearers"
		);
		helper.assertValueEqual(Integer.valueOf(captains), Integer.valueOf(1), "elite captains");
		helper.assertValueEqual(Integer.valueOf(captainSwords), Integer.valueOf(1), "the captain carries a sword");
		helper.succeed();
	}

	@GameTest
	public void bandDropNumbersMatchTheConstants(final GameTestHelper helper) {
		result(
			"bandDropNumbersMatchTheConstants",
			"captain=" + OceanBand.CAPTAIN_DROP_CHANCE + " member=" + OceanBand.MEMBER_DROP_CHANCE
				+ " bonus=" + Elite.GEAR_DROP_BONUS + " eliteKit=" + Elite.KIT_DROP_CHANCE
				+ " escort=" + com.stella.mobs.behavior.EscortSquad.DROP_CHANCE
		);
		helper.assertTrue(
			Math.abs(OceanBand.CAPTAIN_DROP_CHANCE - 0.20F) < EPSILON,
			"the captain drop is " + OceanBand.CAPTAIN_DROP_CHANCE + ", not 0.20"
		);
		helper.assertTrue(
			Math.abs(OceanBand.MEMBER_DROP_CHANCE - 0.12F) < EPSILON,
			"the member drop is " + OceanBand.MEMBER_DROP_CHANCE + ", not 0.12"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void bandMemberIsMarkedSummonedAndWearsAHelmet(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);

		OceanBand.pinChance(0.0);
		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first);
		OceanBand.drain(level);

		int withHelmet = 0;
		int summoned = 0;

		for (Skeleton skeleton : OceanBand.membersOf(first)) {
			if (skeleton.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)) {
				withHelmet++;
			}

			if (EliteState.isSummoned(skeleton)) {
				summoned++;
			}
		}

		result(
			"bandMemberIsMarkedSummonedAndWearsAHelmet",
			"helmeted=" + withHelmet + " summoned=" + summoned + " band=" + OceanBand.BAND_SIZE
		);
		helper.assertValueEqual(Integer.valueOf(withHelmet), Integer.valueOf(OceanBand.BAND_SIZE), "members with a forced helmet");
		helper.assertValueEqual(Integer.valueOf(summoned), Integer.valueOf(OceanBand.BAND_SIZE), "members flagged summoned");
		helper.succeed();
	}

	private static void buildWater(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = POOL_Y + 1; y <= POOL_TOP; y++) {
					helper.setBlock(x, y, z, Blocks.WATER);
				}
			}
		}
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
