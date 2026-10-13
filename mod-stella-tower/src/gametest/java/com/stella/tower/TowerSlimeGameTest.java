package com.stella.tower;

import com.stella.mobs.entity.RedSlime;
import com.stella.mobs.entity.StellaEntities;
import com.stella.mobs.entity.WhiteSlime;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** The red, white and black slimes in the tower's pools: they are raised, they take their floor's level, and a white slime's escorts leave with it. */
public class TowerSlimeGameTest {
	private static final List<EntityType<?>> SLIMES = List.of(StellaEntities.RED_SLIME, StellaEntities.WHITE_SLIME, StellaEntities.BLACK_SLIME);

	/** Catches a biome whose pool lacks a coloured slime, or holds one twice. */
	@GameTest
	public void everyBiomePoolHoldsTheRedWhiteAndBlackSlimeOnce(final GameTestHelper helper) {
		for (FloorBiome biome : FloorBiome.DRAWN) {
			List<EntityType<? extends Mob>> pool = BiomePools.of(biome);
			for (EntityType<?> slime : SLIMES) {
				helper.assertTrue(pool.stream().filter(slime::equals).count() == 1, biome + " holds " + slime + " " + pool.stream().filter(slime::equals).count() + " times");
			}
		}
		helper.succeed();
	}

	/** Catches a slime that a floor never raises, or one raised at the wrong level or health. */
	@GameTest(maxTicks = 1200)
	public void aFloorRaisesTheThreeSlimesAtItsOwnLevelAndHealth(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		Set<EntityType<?>> seen = new HashSet<>();
		List<Mob> raised = new ArrayList<>();
		for (int i = 0; i < 400 && seen.size() < SLIMES.size(); i++) {
			Mob mob = TowerMobs.spawnOne(tower, floor);
			if (mob == null) {
				continue;
			}
			raised.add(mob);
			if (SLIMES.contains(mob.getType())) {
				seen.add(mob.getType());
				TowerMobs.State state = TowerMobs.stateOf(mob);
				helper.assertTrue(state != null && state.floor() == floor, mob.getType() + " carries no tower state of its floor");
				helper.assertTrue(ThreatState.level(mob) == TowerTestSupport.levelOf(floor), mob.getType() + " is level " + ThreatState.level(mob) + ", not " + TowerTestSupport.levelOf(floor));
				double base = mob instanceof RedSlime ? RedSlime.BASE_HEALTH : WhiteSlime.BASE_HEALTH;
				double want = TowerTestSupport.floorHealth(base, floor, TowerRank.MOB);
				double effective = mob.getMaxHealth() * state.healthDivisor();
				helper.assertTrue(Math.abs(effective - want) < want * 1.0E-6, mob.getType() + " has " + effective + " health, not " + want);
			}
		}
		raised.forEach(Mob::discard);
		TowerTestSupport.result("slimes", "floor=" + floor + " level=" + TowerTestSupport.levelOf(floor) + " seen=" + seen.size() + " of " + SLIMES.size());
		helper.assertTrue(seen.size() == SLIMES.size(), "the floor raised only " + seen + " of the three slimes in 400 tries");
		helper.succeed();
	}

	/** Catches a white slime that leaves its five blue escorts behind when it dies. */
	@GameTest(maxTicks = 1200)
	public void aWhiteSlimesEscortsGoWhenItDies(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		WhiteSlime white = StellaEntities.WHITE_SLIME.create(tower, EntitySpawnReason.EVENT);
		white.snapTo(TowerTestSupport.awayFromArrival(tower, floor, 8.0));
		white.finalizeSpawn(tower, tower.getCurrentDifficultyAt(BlockPos.containing(white.position())), EntitySpawnReason.EVENT, null);
		// Its state names floor 1: a drop grows a thousandth per floor, and a kill on a test floor of millions would drop a flood.
		TowerMobs.makeTowerMob(white, 1, TowerElement.FIRE);
		tower.addFreshEntity(white);
		List<UUID> escorts = new ArrayList<>();
		boolean[] killed = {false};
		helper.succeedWhen(() -> {
			if (!killed[0]) {
				helper.assertTrue(white.livingEscorts(tower).size() == WhiteSlime.ESCORT_COUNT, "the white slime has " + white.livingEscorts(tower).size() + " escorts");
				escorts.addAll(white.recordedEscorts());
				white.kill(tower);
				killed[0] = true;
				helper.assertTrue(false, "the white slime was killed on this tick");
			}
			long left = escorts.stream().filter(id -> tower.getEntity(id) != null).count();
			TowerTestSupport.result("whiteEscorts", "escorts=" + escorts.size() + " leftAfterDeath=" + left);
			helper.assertTrue(escorts.size() == WhiteSlime.ESCORT_COUNT && left == 0, left + " of the escorts outlived the white slime");
			TowerTestSupport.leave(helper, player);
		});
	}

	/** Catches a pool that raises a slime nobody can hit or that can hit nobody: each is a hostile enemy to the tower. */
	@GameTest
	public void theThreeSlimesCountAsHostileInTheTower(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		for (EntityType<?> type : SLIMES) {
			Mob slime = (Mob) type.create(tower, EntitySpawnReason.COMMAND);
			helper.assertTrue(Hostiles.isHostile(slime), type + " does not count as hostile");
			slime.discard();
		}
		helper.succeed();
	}
}
