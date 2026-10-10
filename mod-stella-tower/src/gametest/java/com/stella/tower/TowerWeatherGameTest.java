package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The storms and the lava: bounded weather on a body in the open, and lava that stays off every way a body walks. */
public class TowerWeatherGameTest {
	private static final long SEED = 0x5E7B_10E5_0003L;

	private static ServerPlayer onWeatherFloor(final GameTestHelper helper, final ServerLevel tower, final FloorBiome biome) {
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		return player;
	}

	private static TowerPlan planOf(final ServerLevel tower, final ServerPlayer player) {
		return TowerPlan.at(tower, TowerLayout.floorAt(player.blockPosition()));
	}

	/** Catches lava on the road or near it, near the stair, on the way to the portal, or lava a body could stand in. */
	@GameTest(maxTicks = 100)
	public void lavaStaysOffEveryWayABodyWalks(final GameTestHelper helper) {
		int plans = 0;
		int lavaColumns = 0;
		int standing = 0;
		int nearThePath = 0;
		int nearTheRoad = 0;
		int nearTheStair = 0;
		for (int floor = 2; plans < 40 && floor < 5000; floor++) {
			if (floor % TowerElites.BOSS_EVERY == 0 || FloorBiomes.of(SEED, floor) != FloorBiome.BASALT_DELTAS) {
				continue;
			}
			plans++;
			TowerPlan plan = BiomeGen.plan(SEED, floor);
			TowerVolume volume = TowerVolume.of(plan);
			boolean[][] lava = plan.biome().lava();
			byte[][] surface = plan.biome().surface();
			List<int[]> path = TowerWalk.path(volume, new int[] {plan.arrivalX(), 0, plan.arrivalZ()}, TowerVolume.portalGoals(plan));
			for (int x = 0; x < TowerLayout.SIZE; x++) {
				for (int z = 0; z < TowerLayout.SIZE; z++) {
					if (!lava[x][z]) {
						continue;
					}
					lavaColumns++;
					standing += plan.standY(x, z) != BiomeDraft.NONE ? 1 : 0;
					for (int[] step : path) {
						nearThePath += step[0] == x && step[2] == z ? 1 : 0;
					}
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							int a = x + dx;
							int b = z + dz;
							if (a >= 0 && b >= 0 && a < TowerLayout.SIZE && b < TowerLayout.SIZE && surface[a][b] == BiomeBlocks.PATH) {
								nearTheRoad++;
							}
						}
					}
					for (int dx = -3; dx <= 3; dx++) {
						for (int dz = -3; dz <= 3; dz++) {
							nearTheStair += plan.biome().inDais(plan, x + dx, z + dz) ? 1 : 0;
						}
					}
				}
			}
			helper.assertTrue(!path.isEmpty(), "basalt floor " + floor + " has no walk to the portal");
		}
		TowerTestSupport.result("lavaOffTheWalk", "plans=" + plans + " lavaColumns=" + lavaColumns + " standing=" + standing + " nearThePath=" + nearThePath
			+ " nearTheRoad=" + nearTheRoad + " nearTheStair=" + nearTheStair);
		helper.assertTrue(plans == 40 && lavaColumns > 400, "the case proves little: " + plans + " plans, " + lavaColumns + " lava columns");
		helper.assertTrue(standing == 0, standing + " lava columns can be stood in");
		helper.assertTrue(nearThePath == 0 && nearTheRoad == 0 && nearTheStair == 0,
			"lava lies on the way: path " + nearThePath + ", road " + nearTheRoad + ", stair " + nearTheStair);
		helper.succeed();
	}

	/** Catches a built lava floor in which a body's walk to the portal is not the model's, and lava a built walk would enter. */
	@GameTest(maxTicks = 400)
	public void aBuiltBasaltFloorNeverWalksIntoLava(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = onWeatherFloor(helper, tower, FloorBiome.BASALT_DELTAS);
		TowerPlan plan = planOf(tower, player);
		List<int[]> path = TowerWalk.path(TowerWalk.world(tower, plan.floor()), new int[] {plan.arrivalX(), 0, plan.arrivalZ()}, TowerVolume.portalGoals(plan));
		BlockPos origin = plan.origin();
		int inLava = 0;
		for (int[] step : path) {
			inLava += tower.getFluidState(origin.offset(step[0], step[1], step[2])).is(net.minecraft.tags.FluidTags.LAVA) ? 1 : 0;
		}
		int lavaBlocks = 0;
		for (int x = 0; x < TowerLayout.SIZE; x++) {
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				lavaBlocks += plan.biome().lava()[x][z] && tower.getBlockState(origin.offset(x, -1, z)).is(Blocks.LAVA) ? 1 : 0;
			}
		}
		TowerTestSupport.result("basaltWalk", "floor=" + plan.floor() + " path=" + path.size() + " stepsInLava=" + inLava + " lavaBlocks=" + lavaBlocks);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!path.isEmpty() && inLava == 0, "the built walk has " + inLava + " steps in lava");
		helper.assertTrue(lavaBlocks > 0, "the floor holds no lava, so the case proves nothing");
		helper.succeed();
	}

	/** Catches a frost that builds past the cap, one that reaches a body under a roof, or one that ignores boots. */
	@GameTest(maxTicks = 400)
	public void theSnowstormFreezesOnlyInTheOpenAndNeverPastTheCap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = onWeatherFloor(helper, tower, FloorBiome.ETERNAL_SNOWSTORM);
		TowerPlan plan = planOf(tower, player);
		int most = 0;
		boolean steady = true;
		int before = 0;
		for (int round = 0; round < 60; round++) {
			TowerWeather.affect(player, plan, round * TowerWeather.INTERVAL);
			int frozen = player.getTicksFrozen();
			steady &= frozen >= before;
			before = frozen;
			most = Math.max(most, frozen);
		}
		ServerPlayer covered = TowerTestSupport.onFloor(helper, tower, plan.floor());
		tower.setBlock(covered.blockPosition().above(3), Blocks.STONE.defaultBlockState(), 3);
		for (int round = 0; round < 60; round++) {
			TowerWeather.affect(covered, plan, round * TowerWeather.INTERVAL);
		}
		ServerPlayer booted = TowerTestSupport.onFloor(helper, tower, plan.floor());
		booted.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		for (int round = 0; round < 60; round++) {
			TowerWeather.affect(booted, plan, round * TowerWeather.INTERVAL);
		}
		int under = covered.getTicksFrozen();
		int boots = booted.getTicksFrozen();
		TowerTestSupport.result("snowFrost", "open=" + most + " underARoof=" + under + " inLeatherBoots=" + boots + " steady=" + steady);
		tower.setBlock(covered.blockPosition().above(3), Blocks.AIR.defaultBlockState(), 3);
		TowerTestSupport.leave(helper, player);
		TowerTestSupport.leave(helper, covered);
		TowerTestSupport.leave(helper, booted);
		helper.assertTrue(most == TowerWeather.FREEZE_CAP && steady, "the frost in the open peaks at " + most + ", not " + TowerWeather.FREEZE_CAP);
		helper.assertTrue(under == 0, "a body under a roof froze to " + under);
		helper.assertTrue(boots == 0, "a body in leather boots froze to " + boots);
		helper.succeed();
	}

	/** Catches a push stronger than the bound, a gust that does more than Slowness I, and a gust out of its window. */
	@GameTest(maxTicks = 400)
	public void theDesertGustSlowsAndPushesLightly(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = onWeatherFloor(helper, tower, FloorBiome.DESERT_STORM);
		TowerPlan plan = planOf(tower, player);
		player.setDeltaMovement(Vec3.ZERO);
		TowerWeather.affect(player, plan, TowerWeather.GUST_LENGTH + 10);
		boolean calmSlow = player.hasEffect(MobEffects.SLOWNESS);
		double calmPush = player.getDeltaMovement().length();
		TowerWeather.affect(player, plan, 0);
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
		double push = player.getDeltaMovement().length();
		int effects = player.getActiveEffects().size();
		Vec3 wind = TowerWeather.wind(plan);
		TowerTestSupport.result("desertGust", "calmSlow=" + calmSlow + " calmPush=" + calmPush + " slowAmplifier=" + (slow == null ? -1 : slow.getAmplifier())
			+ " push=" + push + " effects=" + effects + " wind=" + wind.length());
		TowerTestSupport.leave(helper, player);
		helper.assertFalse(calmSlow, "a body was slowed outside the gust");
		helper.assertTrue(calmPush == 0.0, "a body was pushed outside the gust");
		helper.assertTrue(slow != null && slow.getAmplifier() == 0 && effects == 1, "the gust gives more than Slowness I: " + effects + " effects");
		helper.assertTrue(push > 0.0 && push <= TowerWeather.PUSH + 1.0E-9 && Math.abs(wind.length() - 1.0) < 1.0E-9, "the push is " + push);
		helper.succeed();
	}

	/** Catches weather on a biome that has none. */
	@GameTest(maxTicks = 400)
	public void aFloorWithoutWeatherIsLeftBe(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		List<FloorBiome> calm = new ArrayList<>();
		int touched = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			if (biome.weather() != FloorBiome.Weather.NONE) {
				continue;
			}
			calm.add(biome);
			ServerPlayer player = onWeatherFloor(helper, tower, biome);
			TowerPlan plan = planOf(tower, player);
			player.setDeltaMovement(Vec3.ZERO);
			for (int round = 0; round < 40; round++) {
				TowerWeather.affect(player, plan, round * TowerWeather.INTERVAL);
			}
			touched += player.getTicksFrozen() != 0 || player.getDeltaMovement().length() != 0.0 || !player.getActiveEffects().isEmpty() ? 1 : 0;
			TowerTestSupport.leave(helper, player);
		}
		TowerTestSupport.result("calmBiomes", calm + " touched=" + touched);
		helper.assertTrue(calm.size() >= 5 && touched == 0, touched + " of " + calm.size() + " calm biomes had weather");
		helper.succeed();
	}
}
