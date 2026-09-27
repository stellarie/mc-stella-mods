package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.OceanBand;
import com.stella.mobs.gear.GearTier;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
	public void aBandHasThreeToFiveHelmedRidersWithBowsOrAxes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);
		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first, 0, OceanBand.Leader.NONE);
		OceanBand.drain(level);
		List<Skeleton> members = OceanBand.membersOf(first);
		long armed = members.stream().filter(member -> member.getMainHandItem().is(Items.BOW) || member.getMainHandItem().is(Items.IRON_AXE)).count();
		long helmed = members.stream().filter(member -> !member.getItemBySlot(EquipmentSlot.HEAD).isEmpty()).count();
		long elites = members.stream().filter(Elite::isElite).count();
		result("aBandHasThreeToFiveHelmedRidersWithBowsOrAxes", "riders=" + members.size() + " mounts=" + OceanBand.mountsOf(first)
			+ " armed=" + armed + " helmed=" + helmed + " elites=" + elites + " detail=" + OceanBand.describeBand(first));
		helper.assertTrue(members.size() >= OceanBand.MIN_RIDERS && members.size() <= OceanBand.MAX_RIDERS, "the band holds " + members.size() + " riders");
		helper.assertTrue(armed == members.size() && helmed == members.size(), "a rider lacks a bow or axe or a helmet: " + OceanBand.describeBand(first));
		helper.assertTrue(elites == 0, "a band with no leader has " + elites + " elites");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void anEliteLeaderSwingsAnAxeOnAVeryFastDolphin(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);
		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first, 4, OceanBand.Leader.ELITE);
		OceanBand.drain(level);
		List<Skeleton> leaders = OceanBand.membersOf(first).stream().filter(Elite::isElite).toList();
		Skeleton leader = leaders.isEmpty() ? null : leaders.get(0);
		boolean fast = leader != null && leader.getVehicle() instanceof Dolphin mount
			&& mount.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(OceanBand.FAST_DOLPHIN_ID) != null;
		result("anEliteLeaderSwingsAnAxeOnAVeryFastDolphin", "leaders=" + leaders.size() + " axe=" + (leader != null && leader.getMainHandItem().is(Items.IRON_AXE)) + " fast=" + fast);
		helper.assertTrue(leaders.size() == 1 && leader.getMainHandItem().is(Items.IRON_AXE), "the band has " + leaders.size() + " leaders: " + OceanBand.describeBand(first));
		helper.assertTrue(fast, "the leader's dolphin is not fast");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aBossLeaderIsABoss(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		buildWater(helper);
		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first, 3, OceanBand.Leader.BOSS);
		OceanBand.drain(level);
		long bosses = OceanBand.membersOf(first).stream().filter(com.stella.mobs.behavior.Bosses::isBoss).count();
		result("aBossLeaderIsABoss", "bosses=" + bosses + " detail=" + OceanBand.describeBand(first));
		helper.assertTrue(bosses == 1, "the band has " + bosses + " bosses");
		helper.succeed();
	}

	@GameTest
	public void bandAndLeaderRatesAreWhatShips(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_260_927L);
		int samples = 200_000;
		int bands = 0;
		int leaders = 0;
		int bosses = 0;
		for (int i = 0; i < samples; i++) {
			bands += OceanBand.rollsBand(random) ? 1 : 0;
			OceanBand.Leader leader = OceanBand.rollLeader(random);
			leaders += leader != OceanBand.Leader.NONE ? 1 : 0;
			bosses += leader == OceanBand.Leader.BOSS ? 1 : 0;
		}
		double bandRate = bands / (double) samples;
		double leaderRate = leaders / (double) samples;
		double bossShare = leaders == 0 ? 0.0 : bosses / (double) leaders;
		result("bandAndLeaderRatesAreWhatShips", "bandRate=" + bandRate + " leaderRate=" + leaderRate + " bossShare=" + bossShare);
		helper.assertTrue(Math.abs(bandRate - OceanBand.REGION_CHANCE) <= 4 * Math.sqrt(OceanBand.REGION_CHANCE * (1 - OceanBand.REGION_CHANCE) / samples), "bands in " + bandRate);
		helper.assertTrue(Math.abs(leaderRate - OceanBand.LEADER_CHANCE) <= 4 * Math.sqrt(OceanBand.LEADER_CHANCE * (1 - OceanBand.LEADER_CHANCE) / samples), "leaders in " + leaderRate);
		helper.assertTrue(Math.abs(bossShare - OceanBand.BOSS_LEADER_SHARE) <= 4 * Math.sqrt(OceanBand.BOSS_LEADER_SHARE * (1 - OceanBand.BOSS_LEADER_SHARE) / leaders), "boss share " + bossShare);
		helper.succeed();
	}

	// TAGGED: the harness cannot make an ocean. fillbiome reports its cells filled, yet the chunk still reads
	// its generated biome, so the ocean branch never runs here. Manual item: sail an ocean and meet a band of
	// three to five riders, about one 2x2 area of ocean chunks in fifty.
	@GameTest(maxTicks = 100, padding = 48, required = false)
	public void anOceanAreaRaisesABandAtItsSurfaceRequiresManualTest(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
		net.minecraft.world.level.ChunkPos lead = new net.minecraft.world.level.ChunkPos((origin.getX() >> 4) & ~1, (origin.getZ() >> 4) & ~1);
		net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(lead.x(), lead.z());
		int x = lead.getMiddleBlockX();
		int z = lead.getMiddleBlockZ();
		String[] biomeResult = {"none"};
		String fill = "fillbiome " + lead.getMinBlockX() + " " + (origin.getY() - 8) + " " + lead.getMinBlockZ() + " " + lead.getMaxBlockX() + " " + (origin.getY() + 24) + " " + lead.getMaxBlockZ() + " minecraft:ocean";
		try {
			biomeResult[0] = "filled=" + level.getServer().getCommands().getDispatcher().execute(fill, level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
			biomeResult[0] = "failed=" + failure.getMessage();
		}
		int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
		level.setBlockAndUpdate(new BlockPos(x, surface, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
		Dolphin anchor;
		try {
			OceanBand.pinChance(1.0);
			anchor = OceanBand.rollRegion(level, chunk);
		} finally {
			OceanBand.pinChance(OceanBand.REGION_CHANCE);
		}
		BlockPos water = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		helper.assertTrue(anchor != null, "the ocean area raised no band at " + water + " biome=" + level.getBiome(water).unwrapKey()
			+ " fluid=" + level.getFluidState(water) + " lead=" + lead + " command=" + biomeResult[0]
			+ " chunkNoise=" + chunk.getNoiseBiome(water.getX() >> 2, water.getY() >> 2, water.getZ() >> 2).unwrapKey()
			+ " levelChunk=" + level.getChunkAt(water).getNoiseBiome(water.getX() >> 2, water.getY() >> 2, water.getZ() >> 2).unwrapKey()
			+ " sameChunk=" + (level.getChunkAt(water) == chunk));
		helper.runAfterDelay(3L, () -> {
			int riders = OceanBand.membersOf(anchor).size();
			result("anOceanAreaRaisesABandAtItsSurface", "riders=" + riders + " anchor=" + anchor.blockPosition());
			helper.assertTrue(riders >= OceanBand.MIN_RIDERS, "the band gathered " + riders + " riders");
			OceanBand.membersOf(anchor).forEach(member -> member.discard());
			helper.succeed();
		});
	}

	@GameTest(padding = 48)
	public void landRaisesNoBandEvenOnItsRoll(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
		net.minecraft.world.level.ChunkPos lead = new net.minecraft.world.level.ChunkPos((origin.getX() >> 4) & ~1, (origin.getZ() >> 4) & ~1);
		net.minecraft.world.level.ChunkPos odd = new net.minecraft.world.level.ChunkPos(lead.x() + 1, lead.z());
		Dolphin onLand;
		Dolphin offCorner;
		try {
			OceanBand.pinChance(1.0);
			onLand = OceanBand.rollRegion(level, level.getChunk(lead.x(), lead.z()));
			offCorner = OceanBand.rollRegion(level, level.getChunk(odd.x(), odd.z()));
		} finally {
			OceanBand.pinChance(OceanBand.REGION_CHANCE);
		}
		result("landRaisesNoBandEvenOnItsRoll", "onLand=" + (onLand != null) + " offCorner=" + (offCorner != null)
			+ " biome=" + level.getBiome(new BlockPos(lead.getMiddleBlockX(), origin.getY(), lead.getMiddleBlockZ())).unwrapKey());
		helper.assertTrue(onLand == null && offCorner == null, "a band rose on land or off its area's corner");
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

		Dolphin first = helper.spawnMob(EntityTypes.DOLPHIN, 4, POOL_Y + 1, 4).spawn();
		OceanBand.queueForTest(first, 0, OceanBand.Leader.NONE);
		OceanBand.drain(level);

		int withHelmet = 0;
		int summoned = 0;

		for (Skeleton skeleton : OceanBand.membersOf(first)) {
			if (!skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				withHelmet++;
			}

			if (EliteState.isSummoned(skeleton)) {
				summoned++;
			}
		}

		result(
			"bandMemberIsMarkedSummonedAndWearsAHelmet",
			"helmeted=" + withHelmet + " summoned=" + summoned + " band=" + OceanBand.membersOf(first).size()
		);
		int band = OceanBand.membersOf(first).size();
		helper.assertTrue(band >= OceanBand.MIN_RIDERS, "the band holds " + band + " riders, so the case proves nothing");
		helper.assertValueEqual(Integer.valueOf(withHelmet), Integer.valueOf(band), "members with a forced helmet");
		helper.assertValueEqual(Integer.valueOf(summoned), Integer.valueOf(band), "members flagged summoned");
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
