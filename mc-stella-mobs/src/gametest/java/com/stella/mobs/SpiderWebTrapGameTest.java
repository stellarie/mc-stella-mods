package com.stella.mobs;

import com.stella.mobs.behavior.SpiderWebTrapGoal;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;

public class SpiderWebTrapGameTest {
	private static final String NO_GEAR_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"roles": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}
		""";

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void spiderTrapsThePlayerWithAWeb(final GameTestHelper helper) {
		Arena arena = build(helper, "spiderTrapsThePlayerWithAWeb", EntityTypes.SPIDER, 2, 2);
		Spider spider = (Spider)arena.subject();
		ServerPlayer player = arena.playerThreat();
		helper.assertFalse(player.isCreative(), "the web trap target is not a Survival Player");
		helper.assertTrue(player.isAlive(), "the web trap target is not alive");
		setPosition(helper, player, 3.5, 1.0, 2.5);
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		int[] webs = {0};
		helper.onEachTick(() -> {
			webs[0] = webCount(helper);
			arena.log().customState("R08_SAMPLE", "species=spider webs=" + webs[0] + " inReach=" + (spider.distanceTo(player) <= 3.5));
		});
		helper.runBeforeTestEnd(() -> arena.log().customResult("R08", webs[0] > 0 ? "WEB_PLACED" : "MISSING",
			"species=spider webs=" + webs[0] + " playerBlock=" + BlockPos.containing(player.position())));
		helper.succeedWhen(() -> {
			BlockPos foot = player.blockPosition();
			helper.assertTrue(helper.getLevel().getBlockState(foot).is(Blocks.COBWEB),
				"the Spider did not place a web at the Player's foot block; webs=" + webCount(helper));
			helper.assertTrue(spider.distanceTo(player) <= 3.5, "the Spider placed a web outside its reach");
			arena.log().customResult("R08", "WEB_PLACED", "species=spider webs=" + webCount(helper)
				+ " playerBlock=" + foot);
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void spiderWebTrapSkipsAnOutOfReachPlayer(final GameTestHelper helper) {
		Arena arena = build(helper, "spiderWebTrapSkipsAnOutOfReachPlayer", EntityTypes.SPIDER, 2, 2);
		Spider spider = (Spider)arena.subject();
		ServerPlayer player = arena.playerThreat();
		helper.assertFalse(player.isCreative(), "the reach-gate target is not a Survival Player");
		helper.assertTrue(player.isAlive(), "the reach-gate target is not alive");
		boolean previousMobGriefing = helper.getLevel().getGameRules().get(GameRules.MOB_GRIEFING);
		setPosition(helper, player, 3.5, 1.0, 2.5);
		spider.setTarget(player);
		SpiderWebTrapGoal mobGriefingGuard = new SpiderWebTrapGoal(spider);
		try {
			helper.getLevel().getGameRules().set(GameRules.MOB_GRIEFING, false, helper.getLevel().getServer());
			helper.assertTrue(mobGriefingGuard.canUse(), "the near Player did not satisfy the web trap gates");
			mobGriefingGuard.start();
			BlockPos griefingFoot = player.blockPosition();
			helper.assertFalse(helper.getLevel().getBlockState(griefingFoot).is(Blocks.COBWEB),
				"the Spider placed a web with MobGriefing disabled");
			arena.log().customResult("R08", "SKIP_MOB_GRIEFING",
				"species=spider webs=" + webCount(helper) + " playerBlock=" + griefingFoot);
		} finally {
			helper.getLevel().getGameRules().set(GameRules.MOB_GRIEFING, previousMobGriefing, helper.getLevel().getServer());
		}
		setPosition(helper, player, 7.5, 1.0, 7.5);
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		int[] webs = {0};
		helper.onEachTick(() -> {
			webs[0] = webCount(helper);
			arena.log().customState("R08_SAMPLE", "species=spider webs=" + webs[0] + " inReach=" + (spider.distanceTo(player) <= 3.5));
		});
		helper.runBeforeTestEnd(() -> arena.log().customResult("R08", webs[0] == 0 ? "SKIP_OUT_OF_REACH" : "UNEXPECTED_WEB",
			"species=spider webs=" + webs[0] + " distance=" + String.format(Locale.ROOT, "%.2f", spider.distanceTo(player))));
		helper.runAfterDelay(40L, () -> {
			helper.assertTrue(spider.distanceTo(player) > 3.5, "the fixture Player entered Spider reach");
			helper.assertTrue(webCount(helper) == 0, "the Spider webbed an out-of-reach Player");
			arena.log().customResult("R08", "SKIP_OUT_OF_REACH", "species=spider webs=0");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = Arena.MAX_TICKS)
	public void caveSpiderUsesWebTrap(final GameTestHelper helper) {
		Arena arena = build(helper, "caveSpiderUsesWebTrap", EntityTypes.CAVE_SPIDER, 2, 2);
		CaveSpider spider = (CaveSpider)arena.subject();
		ServerPlayer player = arena.playerThreat();
		helper.assertFalse(player.isCreative(), "the web trap target is not a Survival Player");
		helper.assertTrue(player.isAlive(), "the web trap target is not alive");
		setPosition(helper, player, 3.5, 1.0, 2.5);
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		boolean registered = spider.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("SpiderWebTrapGoal")
				&& wrapped.getPriority() == 5 && wrapped.getFlags().isEmpty());
		int[] webs = {0};
		helper.onEachTick(() -> {
			webs[0] = webCount(helper);
			arena.log().customState("R08_SAMPLE", "species=cave-spider webs=" + webs[0] + " inReach=" + (spider.distanceTo(player) <= 3.5));
		});
		helper.runBeforeTestEnd(() -> arena.log().customResult("R08", webs[0] > 0 ? "WEB_PLACED" : "MISSING",
			"species=cave-spider registered=" + registered + " webs=" + webs[0]));
		helper.succeedWhen(() -> {
			helper.assertTrue(registered, "CaveSpider has no no-flag priority-5 web goal");
			BlockPos foot = player.blockPosition();
			helper.assertTrue(helper.getLevel().getBlockState(foot).is(Blocks.COBWEB),
				"the CaveSpider did not place a web at the Player's foot block");
			arena.log().customResult("R08", "WEB_PLACED", "species=cave-spider registered=true webs=" + webCount(helper));
		});
	}

	@GameTest(maxTicks = 360)
	public void spiderWebCooldownAndCap(final GameTestHelper helper) {
		Arena arena = build(helper, "spiderWebCooldownAndCap", EntityTypes.SPIDER, 2, 2);
		Spider spider = (Spider)arena.subject();
		ServerPlayer player = arena.playerThreat();
		helper.assertFalse(player.isCreative(), "the cooldown target is not a Survival Player");
		helper.assertTrue(player.isAlive(), "the cooldown target is not alive");
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		int[][] playerBlocks = {{3, 2}, {2, 3}, {3, 3}, {4, 3}, {5, 3}};
		int probeMoveTick = SpiderWebTrapGoal.COOLDOWN_TICKS / 3;
		int firstRearmTick = SpiderWebTrapGoal.COOLDOWN_TICKS + 1;
		helper.onEachTick(() -> {
			int tick = spider.tickCount;
			int stage = tick < probeMoveTick ? 0
				: tick < firstRearmTick ? 1
				: Math.min(playerBlocks.length - 1,
					2 + (tick - firstRearmTick) / SpiderWebTrapGoal.COOLDOWN_TICKS);
			setPosition(helper, player, playerBlocks[stage][0] + 0.5, 1.0, playerBlocks[stage][1] + 0.5);
			arena.log().customState("R08_SAMPLE", "species=spider stage=" + stage + " webs=" + webCount(helper)
				+ " mobTick=" + spider.tickCount);
		});
		helper.runBeforeTestEnd(() -> arena.log().customResult("R08", "COOLDOWN_CAP",
			"species=spider webs=" + webCount(helper) + " cooldown=" + SpiderWebTrapGoal.COOLDOWN_TICKS
				+ " cap=" + SpiderWebTrapGoal.WEB_CAP + " mobTick=" + spider.tickCount));
		helper.runAfterDelay(SpiderWebTrapGoal.COOLDOWN_TICKS / 2L, () -> {
			BlockPos probe = player.blockPosition();
			helper.assertTrue(webCount(helper) == 1,
				"the Spider ignored its cooldown on a different block: webs=" + webCount(helper));
			helper.assertTrue(helper.getLevel().getBlockState(probe).canBeReplaced(),
				"the cooldown probe block cannot be replaced: " + probe);
			arena.log().customResult("R08", "COOLDOWN_HOLDS",
				"webs=" + webCount(helper) + " mobTick=" + spider.tickCount + " probe=" + probe);
		});
		helper.runAfterDelay(SpiderWebTrapGoal.COOLDOWN_TICKS * 5L, () -> {
			int webs = webCount(helper);
			helper.assertTrue(webs == 4, "the Spider did not reach the four-web encounter cap: webs=" + webs);
			arena.log().customResult("R08", "COOLDOWN_CAP", "species=spider webs=" + webs + " cooldown=60 cap=4 mobTick=" + spider.tickCount);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void webTrapRateBySpecies(final GameTestHelper helper) {
		Arena arena = build(helper, "webTrapRateBySpecies", EntityTypes.SPIDER, 2, 2);
		Spider spider = (Spider)arena.subject();
		CaveSpider caveSpider = helper.spawnMob(EntityTypes.CAVE_SPIDER, 6, 3, 6).spawn();
		ServerPlayer spiderPlayer = arena.playerThreat();
		ServerPlayer cavePlayer = Arena.addSurvivalPlayer(helper);
		helper.assertFalse(spiderPlayer.isCreative(), "the Spider rate target is not in Survival mode");
		helper.assertFalse(cavePlayer.isCreative(), "the CaveSpider rate target is not in Survival mode");
		cavePlayer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000.0);
		cavePlayer.setHealth(10000.0F);
		cavePlayer.getAbilities().instabuild = false;
		cavePlayer.getAbilities().invulnerable = false;
		setPosition(helper, spiderPlayer, 2.5, 1.0, 2.5);
		setPosition(helper, cavePlayer, 6.5, 1.0, 6.5);
		spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		caveSpider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		spider.setTarget(spiderPlayer);
		caveSpider.setTarget(cavePlayer);
		int[] samples = {0, 0};
		int[] placements = {0, 0};
		long[] responseTicks = {-1L, -1L};
		int[] previousCounts = {webCountNear(helper, spiderPlayer), webCountNear(helper, cavePlayer)};
		helper.onEachTick(() -> {
			spider.setTarget(spiderPlayer);
			caveSpider.setTarget(cavePlayer);
			Spider[] subjects = {spider, caveSpider};
			ServerPlayer[] players = {spiderPlayer, cavePlayer};
			for (int index = 0; index < subjects.length; index++) {
				if (samples[index] >= 20) {
					continue;
				}
				samples[index]++;
				int count = webCountNear(helper, players[index]);
				if (count > previousCounts[index]) {
					placements[index]++;
					if (responseTicks[index] < 0L) {
						responseTicks[index] = helper.getTick();
					}
				}
				previousCounts[index] = count;
			}
		});
		helper.runAfterDelay(22L, () -> {
			for (int index = 0; index < 2; index++) {
				helper.assertTrue(samples[index] == 20, "web rate sample count was " + samples[index]);
				double rate = placements[index] / 20.0;
				long medianResponse = responseTicks[index];
				arena.log().customResult("R08_RATE", "SAMPLED", "species=" + (index == 0 ? "spider" : "cave-spider")
					+ " placements=" + placements[index] + " samples=20 rate=" + rate
					+ " medianResponseTick=" + (medianResponse < 0L ? "none" : medianResponse));
			}
			helper.succeed();
		});
	}

	private static Arena build(
		final GameTestHelper helper,
		final String test,
		final net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type,
		final int x,
		final int z
	) {
		return Arena.build(helper, test, NO_GEAR_CONFIG, type, x, z, false, Arena.Threat.PLAYER);
	}

	private static void setPosition(final GameTestHelper helper, final ServerPlayer player, final double x, final double y, final double z) {
		Vec3 position = helper.absoluteVec(new Vec3(x, y, z));
		player.setPos(position.x, position.y, position.z);
	}

	private static int webCount(final GameTestHelper helper) {
		int count = 0;
		for (int x = 0; x < 8; x++) {
			for (int y = 1; y < 5; y++) {
				for (int z = 0; z < 8; z++) {
					if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(Blocks.COBWEB)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static int webCountNear(final GameTestHelper helper, final ServerPlayer player) {
		BlockPos center = BlockPos.containing(player.position());
		int count = 0;
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (helper.getLevel().getBlockState(pos).is(Blocks.COBWEB)) {
						count++;
					}
				}
			}
		}
		return count;
	}
}
