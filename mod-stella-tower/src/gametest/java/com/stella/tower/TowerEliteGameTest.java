package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Elites by the portal every fifth floor, a boss beside them every tenth, and the keys the bosses drop. */
public class TowerEliteGameTest {
	private static Vec3 portalFront(final int floor) {
		BlockPos inside = TowerLayout.portalInside(floor).get(0);
		return Vec3.atBottomCenterOf(inside);
	}

	@GameTest(maxTicks = 100)
	public void anEliteFloorKeepsOneEliteByItsPortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.runAfterDelay(80L, () -> {
			int elites = TowerElites.alive(tower, floor, TowerRank.ELITE);
			int bosses = TowerElites.alive(tower, floor, TowerRank.BOSS);
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			double fromPortal = elite == null ? -1.0 : elite.position().distanceTo(portalFront(floor));
			TowerTestSupport.result("eliteFloor", "elites=" + elites + " bosses=" + bosses + " fromPortal=" + fromPortal);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(elites == 1 && bosses == 0, "floor " + floor + " holds " + elites + " elites and " + bosses + " bosses");
			helper.assertTrue(fromPortal >= 0.0 && fromPortal <= TowerElites.GUARD_DISTANCE + 4.0, "the elite stands " + fromPortal + " from the portal");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aBossFloorKeepsABossAndAnElite(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.runAfterDelay(80L, () -> {
			int elites = TowerElites.alive(tower, floor, TowerRank.ELITE);
			int bosses = TowerElites.alive(tower, floor, TowerRank.BOSS);
			TowerTestSupport.result("bossFloor", "elites=" + elites + " bosses=" + bosses);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(elites == 1 && bosses == 1, "boss floor " + floor + " holds " + elites + " elites and " + bosses + " bosses");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void anOrdinaryFloorKeepsNeither(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.runAfterDelay(80L, () -> {
			int stocked = TowerMobs.count(tower, floor);
			int guards = TowerElites.alive(tower, floor, TowerRank.ELITE) + TowerElites.alive(tower, floor, TowerRank.BOSS);
			TowerTestSupport.result("ordinaryFloor", "stocked=" + stocked + " guards=" + guards);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(stocked > 0, "the floor raised nothing, so the case proves nothing");
			helper.assertTrue(guards == 0, "ordinary floor " + floor + " raised " + guards + " elites or bosses");
			helper.succeed();
		});
	}

	@GameTest
	public void elitesAndBossesAreStrongerAndOutsideTheCap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = 100;
		double[] health = new double[3];
		double[] hit = new double[3];
		TowerRank[] ranks = {TowerRank.MOB, TowerRank.ELITE, TowerRank.BOSS};
		for (int i = 0; i < ranks.length; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, floor, TowerElement.FIRE, ranks[i]);
			health[i] = zombie.getMaxHealth() * TowerMobs.stateOf(zombie).healthDivisor();
			hit[i] = TowerMobs.scaleDamage(EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND), zombie.damageSources().mobAttack(zombie), 3.0F);
			zombie.discard();
		}
		TowerTestSupport.result("rankStrength", "health=" + java.util.Arrays.toString(health) + " hit=" + java.util.Arrays.toString(hit));
		helper.assertTrue(Math.abs(health[1] - health[0] * TowerRank.ELITE.healthFactor()) < 0.1 && Math.abs(health[2] - health[0] * TowerRank.BOSS.healthFactor()) < 0.1,
			"elite and boss health are " + health[1] + " and " + health[2] + " against " + health[0]);
		helper.assertTrue(Math.abs(hit[1] - hit[0] * TowerRank.ELITE.damageFactor()) < 0.01 && Math.abs(hit[2] - hit[0] * TowerRank.BOSS.damageFactor()) < 0.01,
			"elite and boss hits are " + hit[1] + " and " + hit[2] + " against " + hit[0]);
		helper.succeed();
	}

	@GameTest
	public void elitesAndBossesStandOutsideTheFloorsCap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		Mob elite = TowerElites.rise(tower, floor, TowerRank.ELITE);
		Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
		int counted = TowerMobs.count(tower, floor);
		TowerTestSupport.result("guardsOutsideCap", "elite=" + (elite != null) + " boss=" + (boss != null) + " counted=" + counted);
		if (elite != null) {
			elite.discard();
		}
		if (boss != null) {
			boss.discard();
		}
		helper.assertTrue(elite != null && boss != null, "the elite or the boss could not rise, so the case proves nothing");
		helper.assertTrue(counted == 0, "the floor's cap counts " + counted + " of its elite and boss");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aSlainEliteReturnsAfterItsRest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		int rest = 40;
		int before = TowerElites.respawnTicks();
		TowerElites.pinRespawnTicks(rest);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		int[] slainAt = {-1};
		int[] backAt = {-1};
		helper.onEachTick(() -> {
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			if (slainAt[0] < 0 && elite != null) {
				elite.kill(tower);
				slainAt[0] = (int) helper.getTick();
			} else if (slainAt[0] >= 0 && backAt[0] < 0 && elite != null) {
				backAt[0] = (int) helper.getTick();
			}
		});
		helper.runAfterDelay(95L, () -> {
			TowerElites.pinRespawnTicks(before);
			TowerTestSupport.leave(helper, player);
			int gap = backAt[0] - slainAt[0];
			TowerTestSupport.result("eliteReturns", "slainAt=" + slainAt[0] + " backAt=" + backAt[0] + " gap=" + gap + " rest=" + rest);
			helper.assertTrue(slainAt[0] >= 0 && backAt[0] > 0, "the elite never came back: slain=" + slainAt[0] + " back=" + backAt[0]);
			helper.assertTrue(gap >= rest, "the elite came back after " + gap + " ticks, before its rest of " + rest);
			helper.succeed();
		});
	}

	@GameTest
	public void aBossDropsItsFloorsKey(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		Zombie boss = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(boss, floor, TowerElement.DARK, TowerRank.BOSS);
		Vec3 at = TowerLayout.arrival(floor).add(20.0, 0.0, 0.0);
		boss.snapTo(at);
		tower.addFreshEntity(boss);
		boss.kill(tower);
		List<ItemEntity> keys = tower.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4.0),
			item -> FloorKeyItem.floorOf(item.getItem()) == floor);
		TowerTestSupport.result("bossKey", "keys=" + keys.size() + " floor=" + floor);
		keys.forEach(ItemEntity::discard);
		helper.assertTrue(keys.size() == 1, "the boss of floor " + floor + " dropped " + keys.size() + " keys to its floor");
		helper.succeed();
	}

	@GameTest
	public void theHighestKeyHeldTakesAPlayerToItsFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 5.5);
		int low = 20;
		int high = 40;
		player.getInventory().add(FloorKeyItem.forFloor(low));
		player.getInventory().add(FloorKeyItem.forFloor(high));
		TeleportTransition way = TowerBlocks.TOWER_PORTAL.getPortalDestination(helper.getLevel(), player, helper.absolutePos(inside));
		int arrived = way == null ? 0 : TowerLayout.floorAt(BlockPos.containing(way.position()));
		player.teleport(way);
		int keys = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			keys += FloorKeyItem.floorOf(player.getInventory().getItem(i)) > 0 ? 1 : 0;
		}
		TowerTestSupport.result("keyTravel", "arrived=" + arrived + " keysAfter=" + keys);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(arrived == high, "a player holding keys to " + low + " and " + high + " arrived on floor " + arrived);
		helper.assertTrue(keys == 2, "entering spent keys: " + keys + " of 2 left");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aBossCarriesABarForThePlayersOnItsFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.runAfterDelay(80L, () -> {
			Mob boss = TowerElites.guardian(tower, floor, TowerRank.BOSS);
			var bar = boss == null ? null : TowerElites.barOf(boss);
			boolean shown = bar != null && bar.getPlayers().contains(player);
			TowerTestSupport.result("bossBar", "boss=" + (boss != null) + " shown=" + shown);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(shown, "the boss's bar is not shown to the player on its floor");
			helper.succeed();
		});
	}
}
