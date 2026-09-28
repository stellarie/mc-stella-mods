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
		// No mob hunts a creative player, so the elite has no quarry and keeps to its post.
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		helper.runAfterDelay(80L, () -> {
			int elites = TowerElites.alive(tower, floor, TowerRank.ELITE);
			int bosses = TowerElites.alive(tower, floor, TowerRank.BOSS);
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			// Where it rose: at its post. Whether it keeps there is aGuardianWalksBackToItsPost's case.
			double fromPost = elite == null ? -1.0 : elite.position().distanceTo(Vec3.atBottomCenterOf(TowerElites.post(floor)));
			TowerTestSupport.result("eliteFloor", "elites=" + elites + " bosses=" + bosses + " fromPost=" + fromPost);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(elites == 1 && bosses == 0, "floor " + floor + " holds " + elites + " elites and " + bosses + " bosses");
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
		TowerElites.pinRespawnTicks(floor, rest);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		int[] slainAt = {-1};
		int[] backAt = {-1};
		int[] seenAt = {-1};
		helper.onEachTick(() -> {
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			if (seenAt[0] < 0 && elite != null) {
				seenAt[0] = (int) helper.getTick();
			}
			// Slain some ticks after it rose, off the check's beat, so a rest timed from the check shows.
			if (slainAt[0] < 0 && elite != null && helper.getTick() >= seenAt[0] + 7) {
				elite.kill(tower);
				slainAt[0] = (int) helper.getTick();
			} else if (slainAt[0] >= 0 && backAt[0] < 0 && elite != null) {
				backAt[0] = (int) helper.getTick();
			}
		});
		helper.runAfterDelay(95L, () -> {
			try {
				TowerTestSupport.leave(helper, player);
			} finally {
				TowerElites.pinRespawnTicks(floor, null);
			}
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
		ServerPlayer slayer = TowerTestSupport.onFloor(helper, tower, floor);
		TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.STRENGTH));
		try {
			boss.hurtServer(tower, slayer.damageSources().playerAttack(slayer), 1.0E30F);
		} finally {
			TowerBuffs.pinMobBuffs(floor, null);
		}
		TowerTestSupport.leave(helper, slayer);
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

	/** A boss slain by no player drops no key. The room is held loaded first, or the drop would vanish unseen. */
	@GameTest(maxTicks = 200)
	public void aBossNotSlainByAPlayerDropsNoKey(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		Vec3 at = TowerLayout.arrival(floor).add(20.0, 0.0, 0.0);
		TowerTestSupport.holdRoom(tower, at);
		helper.succeedWhen(() -> {
			helper.assertTrue(TowerTestSupport.roomLoaded(tower, at), "the boss's room never loaded");
			Zombie boss = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(boss, floor, TowerElement.DARK, TowerRank.BOSS);
			boss.snapTo(at);
			tower.addFreshEntity(boss);
			TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.STRENGTH));
			try {
				boss.hurtServer(tower, tower.damageSources().generic(), 1.0E30F);
			} finally {
				TowerBuffs.pinMobBuffs(floor, null);
			}
			List<ItemEntity> drops = tower.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4.0), item -> true);
			long keys = drops.stream().filter(item -> FloorKeyItem.floorOf(item.getItem()) == floor).count();
			TowerTestSupport.result("noPlayerNoKey", "dead=" + !boss.isAlive() + " drops=" + drops.size() + " keys=" + keys);
			drops.forEach(ItemEntity::discard);
			boss.discard();
			helper.assertTrue(!boss.isAlive(), "the boss did not die, so the case proves nothing");
			helper.assertTrue(keys == 0, "a boss no player slew dropped " + keys + " keys");
		});
	}

	/** A split boss's pieces are ordinary mobs: one boss, one key. A slime splits when its dead body is removed. */
	@GameTest(maxTicks = 200)
	public void aSplitBossLeavesOrdinaryPieces(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		Vec3 at = TowerLayout.arrival(floor).add(20.0, 0.0, 0.0);
		TowerTestSupport.holdRoom(tower, at);
		java.util.Set<Mob> pieces = new java.util.HashSet<>();
		helper.succeedWhen(() -> {
			helper.assertTrue(TowerTestSupport.roomLoaded(tower, at), "the boss's room never loaded");
			net.minecraft.world.entity.monster.cubemob.MagmaCube cube = EntityTypes.MAGMA_CUBE.create(tower, EntitySpawnReason.COMMAND);
			cube.setSize(2, true);
			TowerMobs.makeTowerMob(cube, floor, TowerElement.FIRE, TowerRank.BOSS);
			cube.snapTo(at);
			tower.addFreshEntity(cube);
			TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.STRENGTH));
			try {
				cube.hurtServer(tower, tower.damageSources().generic(), 1.0E30F);
			} finally {
				TowerBuffs.pinMobBuffs(floor, null);
			}
			// The death animation ends in this removal, which is where a cube splits.
			cube.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
			pieces.addAll(tower.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(4.0), mob -> mob != cube
				&& mob.getType() == EntityTypes.MAGMA_CUBE && TowerMobs.stateOf(mob) != null && TowerMobs.stateOf(mob).floor() == floor));
			long bosses = pieces.stream().filter(mob -> TowerMobs.stateOf(mob).rank() == TowerRank.BOSS).count();
			TowerTestSupport.result("splitBoss", "pieces=" + pieces.size() + " bosses=" + bosses);
			pieces.forEach(Mob::discard);
			helper.assertTrue(!pieces.isEmpty(), "the boss did not split, so the case proves nothing");
			helper.assertTrue(bosses == 0, bosses + " of the split boss's " + pieces.size() + " pieces are bosses too");
		});
	}

	@GameTest
	public void noCreeperHoldsAPost(final GameTestHelper helper) {
		boolean creeper = TowerElites.guardianPool(10).contains(EntityTypes.CREEPER) || TowerElites.guardianPool(30).contains(EntityTypes.CREEPER);
		TowerTestSupport.result("noCreeperGuard", "creeper=" + creeper);
		helper.assertFalse(creeper, "a creeper, whose blast ends it without a death, may hold a post");
		helper.succeed();
	}

	/** A boss whose bar was dropped, as by its chunk unloading, has it back at the next check. */
	@GameTest(maxTicks = 120)
	public void aBossGetsItsBarBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Mob[] boss = {null};
		helper.runAfterDelay(50L, () -> {
			boss[0] = TowerElites.guardian(tower, floor, TowerRank.BOSS);
			if (boss[0] != null) {
				TowerElites.forgetBar(boss[0]);
			}
		});
		helper.runAfterDelay(100L, () -> {
			boolean back = boss[0] != null && TowerElites.barOf(boss[0]) != null && TowerElites.barOf(boss[0]).getPlayers().contains(player);
			TowerTestSupport.result("barBack", "boss=" + (boss[0] != null) + " back=" + back);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(boss[0] != null, "no boss rose, so the case proves nothing");
			helper.assertTrue(back, "the boss's dropped bar never came back");
			helper.succeed();
		});
	}

	@GameTest
	public void aRidersKeyCountsAsMuchAsAWalkers(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 5.5);
		int high = 40;
		player.getInventory().add(FloorKeyItem.forFloor(high));
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnMob(EntityTypes.PIG, new Vec3(2.5, 2.0, 5.5)).spawn();
		player.startRiding(pig, true, false);
		TeleportTransition way = TowerBlocks.TOWER_PORTAL.getPortalDestination(helper.getLevel(), pig, helper.absolutePos(inside));
		int arrived = way == null ? 0 : TowerLayout.floorAt(BlockPos.containing(way.position()));
		TowerTestSupport.result("riderKey", "arrived=" + arrived);
		player.stopRiding();
		pig.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(arrived == high, "a rider holding a key to " + high + " arrived on floor " + arrived);
		helper.succeed();
	}

	/**
	 * A player past a guardian's post can still be fought: kept to its post, a guardian could not even take
	 * up a player who shot it from beyond, and would stand to be shot. A zombie, so its targeting is known.
	 */
	@GameTest(maxTicks = 200)
	public void aGuardianTakesOnAPlayerBeyondItsPost(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		// Beyond the post's reach, well inside a zombie's following range.
		Vec3 stand = Vec3.atBottomCenterOf(TowerElites.post(floor)).add(-(TowerElites.GUARD_RADIUS + 8.0), 0.0, 0.0);
		player.teleportTo(tower, stand.x, stand.y, stand.z, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean[] shot = {false};
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			helper.assertTrue(elite != null, "no elite rose, so the case proves nothing");
			if (!shot[0]) {
				shot[0] = true;
				elite.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
			}
			helper.assertTrue(elite.getTarget() == player, "an elite shot from " + (int) elite.distanceTo(player) + " blocks never took the player on");
			if (!done[0]) {
				done[0] = true;
				TowerElites.pinGuardianKind(floor, null);
				TowerTestSupport.result("guardianAnswers", "tick=" + helper.getTick() + " distance=" + (int) elite.distanceTo(player));
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	/**
	 * A guardian with no quarry walks back to its post: from a spot a short way into the maze, it walks, and
	 * is not snapped back. The spot is found by a walk over open ground from the post.
	 */
	@GameTest(maxTicks = 300)
	public void aGuardianWalksBackToItsPost(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		// No mob hunts a creative player, so the elite has no quarry.
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		Vec3 post = Vec3.atBottomCenterOf(TowerElites.post(floor));
		BlockPos away = nearbyOpen(tower, TowerElites.post(floor), TowerElites.GUARD_RADIUS + 3, 20);
		int[] movedAt = {-1};
		double[] biggestStep = {0.0};
		Vec3[] last = {null};
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			Mob elite = TowerElites.guardian(tower, floor, TowerRank.ELITE);
			helper.assertTrue(elite != null, "no elite rose, so the case proves nothing");
			helper.assertTrue(away != null, "no open spot a short walk from the post, so the case proves nothing");
			if (movedAt[0] < 0) {
				movedAt[0] = (int) helper.getTick();
				elite.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0F, 0.0F);
				last[0] = elite.position();
			}
			biggestStep[0] = Math.max(biggestStep[0], elite.position().distanceTo(last[0]));
			last[0] = elite.position();
			double from = elite.position().distanceTo(post);
			helper.assertTrue(from <= TowerElites.GUARD_RADIUS, "the elite stands " + (int) from + " blocks from its post with nothing to hunt");
			helper.assertTrue(biggestStep[0] < 2.0, "the elite was snapped back, not walked: a step of " + biggestStep[0]);
			if (!done[0]) {
				done[0] = true;
				TowerElites.pinGuardianKind(floor, null);
				TowerTestSupport.result("guardianReturns", "from=" + away + " movedAt=" + movedAt[0] + " backAt=" + helper.getTick() + " biggestStep=" + biggestStep[0]);
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	/** An open spot at least this far from a place and at most so many steps from it over open ground. */
	private static BlockPos nearbyOpen(final ServerLevel tower, final BlockPos from, final int atLeast, final int steps) {
		java.util.Map<BlockPos, Integer> seen = new java.util.HashMap<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		seen.put(from, 0);
		queue.add(from);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			int walked = seen.get(at);
			if (Math.sqrt(at.distSqr(from)) >= atLeast) {
				return at;
			}
			if (walked >= steps) {
				continue;
			}
			for (net.minecraft.core.Direction way : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(way);
				boolean open = tower.getBlockState(next).getCollisionShape(tower, next).isEmpty()
					&& tower.getBlockState(next.above()).getCollisionShape(tower, next.above()).isEmpty();
				if (open && TowerLayout.floorAt(next) == TowerLayout.floorAt(from) && !seen.containsKey(next)) {
					seen.put(next, walked + 1);
					queue.add(next);
				}
			}
		}
		return null;
	}

	/** A floor built before the halls keeps its portal across the room, and its guardians keep to that portal. */
	@GameTest
	public void anOldElitefloorKeepsItsGuardsByItsOwnPortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && f % TowerElites.BOSS_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		// As an older build left it: the frame across the room, none in the middle.
		for (BlockPos pos : TowerLayout.portalFrame(floor)) {
			tower.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			tower.setBlockAndUpdate(pos.offset(TowerLayout.PORTAL_X - TowerLayout.portalX(floor), 0, 0), TowerBlocks.TOWER_FRAME.defaultBlockState());
		}
		BlockPos post = TowerElites.post(tower, floor).subtract(o);
		TowerTestSupport.result("oldPost", "post=" + post);
		helper.assertTrue(post.getX() == TowerLayout.PORTAL_X - TowerElites.GUARD_DISTANCE, "an old floor's guardians keep to " + post + ", not its portal across the room");
		helper.succeed();
	}

	/**
	 * Not required: the harness cannot restart the server or load a floor's entities late. Manual item: on a
	 * boss floor, save and quit, return, and see one boss and one elite; slay the boss, restart, and see it
	 * stay away for its rest.
	 */
	@GameTest(required = false)
	public void aSavedPostHoldsAcrossARestartRequiresManualTest(final GameTestHelper helper) {
		helper.fail("a server restart cannot be run inside the game test harness");
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
