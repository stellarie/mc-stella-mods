package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The tower's ghosts as a pack: three that rise together, drift slowly and keep to their leader, ignore the players,
 * and hunt the tower's mobs. A ghost never follows a player, and killing one costs nothing.
 */
public class TowerGhostPackGameTest {
	/** A low floor for what a ghost's spell deals, so a zombie outlives the first casts. */
	private static final int LOW = 5;

	private static int floor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
	}

	/** A biome floor with no safe zone, whose sweep would send a mob in the arena out of it. */
	private static int arenaFloor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0);
	}

	private static TowerGhost leaderOf(final List<TowerGhost> pack) {
		return TowerGhosts.leaderOf(pack.get(0));
	}

	/**
	 * Catches a ghost that rises alone, a pack that falls apart, a ghost that goes to a player, or one that turns on
	 * one. The real spawning round raises the pack; a player stands twenty blocks off, where the old ghost came for them.
	 */
	@GameTest(maxTicks = 700)
	public void aHauntedFloorRaisesOnePackOfThreeThatKeepsTogetherAndLeavesThePlayerAlone(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor();
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 1.0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		float health = player.getHealth();
		List<List<TowerGhost>> pack = new ArrayList<>();
		int[] formed = {-1};
		int[] most = {0};
		int[] aimedAtPlayer = {0};
		int[] targetedPlayer = {0};
		double[] spread = {0.0};
		float[] lowest = {health};
		helper.onEachTick(() -> {
			List<TowerGhost> ghosts = TowerGhosts.on(tower, floor);
			most[0] = Math.max(most[0], ghosts.size());
			lowest[0] = Math.min(lowest[0], player.getHealth());
			if (pack.isEmpty()) {
				if (ghosts.isEmpty()) {
					return;
				}
				pack.add(List.copyOf(ghosts));
				formed[0] = (int) helper.getTick();
				// Twenty blocks from the leader, on ground: inside the old ghost's reach of thirty-two.
				Vec3 at = leaderOf(pack.get(0)).position();
				BlockPos origin = plan.origin();
				for (int x = 2; x < TowerLayout.SIZE - 2; x++) {
					for (int z = 2; z < TowerLayout.SIZE - 2; z++) {
						double apart = Math.hypot(origin.getX() + x + 0.5 - at.x, origin.getZ() + z + 0.5 - at.z);
						int y = plan.standY(x, z);
						if (apart >= 19.0 && apart <= 25.0 && y >= TowerWalk.LOW && !plan.biome().inDais(plan, x, z)) {
							player.teleportTo(tower, origin.getX() + x + 0.5, TowerLayout.BASE_Y + y, origin.getZ() + z + 0.5, Set.of(), 0.0F, 0.0F, true);
							return;
						}
					}
				}
				return;
			}
			TowerGhost leader = leaderOf(pack.get(0));
			for (TowerGhost ghost : pack.get(0)) {
				spread[0] = Math.max(spread[0], ghost.position().distanceTo(leader.position()));
				BlockPos going = ghost.getNavigation().getTargetPos();
				// The old goal sent a ghost to the very block of the player's head; a pack drifting past a player does not.
				aimedAtPlayer[0] += going != null && (going.equals(player.blockPosition()) || going.equals(player.blockPosition().above())) ? 1 : 0;
				targetedPlayer[0] += ghost.getTarget() == player ? 1 : 0;
			}
		});
		helper.runAfterDelay(650L, () -> {
			TowerMobs.pinFloorCap(floor, null);
			TowerGhosts.pinRiseChance(floor, null);
			List<TowerGhost> ghosts = pack.isEmpty() ? List.of() : pack.get(0);
			Set<String> packs = new java.util.HashSet<>();
			ghosts.forEach(ghost -> packs.add(String.valueOf(TowerGhosts.packOf(ghost))));
			boolean alive = ghosts.stream().allMatch(TowerGhost::isAlive);
			TowerTestSupport.result("ghostPack", "formedAt=" + formed[0] + " size=" + ghosts.size() + " packs=" + packs + " most=" + most[0]
				+ " spread=" + spread[0] + " aimedAtPlayer=" + aimedAtPlayer[0] + " targetedPlayer=" + targetedPlayer[0] + " lowest=" + lowest[0]);
			List.copyOf(TowerGhosts.on(tower, floor)).forEach(TowerGhost::discard);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(!pack.isEmpty(), "no ghost rose on the occupied floor");
			helper.assertTrue(ghosts.size() == TowerGhosts.PACK_SIZE, "a roll raised " + ghosts.size() + " ghosts, not " + TowerGhosts.PACK_SIZE);
			helper.assertTrue(packs.size() == 1 && !packs.contains("null"), "the ghosts belong to packs " + packs);
			helper.assertTrue(most[0] == TowerGhosts.GHOSTS_PER_FLOOR, "the floor held " + most[0] + " ghosts at most, not " + TowerGhosts.GHOSTS_PER_FLOOR);
			helper.assertTrue(alive, "a ghost of the pack died on a floor with no mob");
			helper.assertTrue(spread[0] <= TowerGhosts.PACK_RADIUS + 2.0, "a ghost strayed " + spread[0] + " blocks from its leader");
			helper.assertTrue(aimedAtPlayer[0] == 0, "a ghost set out for the player " + aimedAtPlayer[0] + " times");
			helper.assertTrue(targetedPlayer[0] == 0 && lowest[0] >= health, "a ghost took the player for its quarry or hurt them: " + targetedPlayer[0] + " lowest=" + lowest[0]);
			helper.succeed();
		});
	}

	/** Catches a pack that does not hunt: it hurts the tower's mobs, never takes the player for a quarry, and never hurts one. */
	@GameTest(maxTicks = 500)
	public void aGhostPackHuntsTowerMobsAndNeverTargetsOrHurtsThePlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = arenaFloor();
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		Vec3 middle = TowerTestSupport.arena(tower, floor, 11);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		player.teleportTo(tower, middle.x, middle.y, middle.z, Set.of(), 0.0F, 0.0F, true);
		float health = player.getHealth();
		List<Zombie> zombies = new ArrayList<>();
		for (Vec3 offset : List.of(new Vec3(6.0, 0.0, 0.0), new Vec3(-6.0, 0.0, 2.0), new Vec3(0.0, 0.0, 7.0))) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, LOW, TowerElement.DARK);
			zombie.snapTo(middle.add(offset));
			zombie.setNoAi(true);
			tower.addFreshEntity(zombie);
			zombies.add(zombie);
		}
		List<TowerGhost> pack = TowerGhosts.raisePack(tower, LOW, middle.add(0.0, 2.0, 0.0));
		helper.assertTrue(pack.size() == TowerGhosts.PACK_SIZE, "the pack has " + pack.size() + " ghosts");
		int[] targetedPlayer = {0};
		int[] struckByGhost = {0};
		float[] lowest = {health};
		helper.onEachTick(() -> {
			lowest[0] = Math.min(lowest[0], player.getHealth());
			for (TowerGhost ghost : pack) {
				targetedPlayer[0] += ghost.getTarget() == player ? 1 : 0;
			}
			for (Zombie zombie : zombies) {
				struckByGhost[0] += zombie.getLastHurtByMob() instanceof TowerGhost ? 1 : 0;
			}
		});
		helper.runAfterDelay(400L, () -> {
			long hurt = zombies.stream().filter(zombie -> !zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth()).count();
			TowerTestSupport.result("ghostPackHunts", "hurtZombies=" + hurt + "/" + zombies.size() + " strikes=" + struckByGhost[0]
				+ " targetedPlayer=" + targetedPlayer[0] + " lowest=" + lowest[0]);
			pack.forEach(TowerGhost::discard);
			zombies.forEach(Zombie::discard);
			TowerTestSupport.leave(helper, player);
			TowerMobs.pinFloorCap(floor, null);
			TowerGhosts.pinRiseChance(floor, null);
			helper.assertTrue(hurt >= 1 && struckByGhost[0] > 0, "the pack never hurt a tower mob: hurt=" + hurt + " strikes=" + struckByGhost[0]);
			helper.assertTrue(targetedPlayer[0] == 0, "a ghost took the player for its quarry " + targetedPlayer[0] + " times");
			helper.assertTrue(lowest[0] >= health, "the player fell to " + lowest[0] + " of " + health + " beside the pack");
			helper.succeed();
		});
	}

	/**
	 * Catches a pack that flies as fast as a lone ghost did. Both are sent the same way at the same pace, the control
	 * with the speed a ghost had before the packs.
	 */
	@GameTest(maxTicks = 300)
	public void aGhostPackFliesSlowerThanAGhostDidBefore(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = arenaFloor();
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		Vec3 middle = TowerTestSupport.arena(tower, floor, 14);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.teleportTo(tower, middle.x, middle.y, middle.z + 12.0, Set.of(), 0.0F, 0.0F, true);
		TowerGhost control = TowerGhosts.summon(tower, LOW, middle.add(-9.0, 2.0, -12.0));
		helper.assertTrue(control != null, "no control ghost could be raised");
		control.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.5);
		control.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
		TowerGhost leader = leaderOf(TowerGhosts.raisePack(tower, LOW, middle.add(9.0, 2.0, -12.0)));
		Vec3[] last = {control.position(), leader.position()};
		double[] peak = {0.0, 0.0};
		helper.onEachTick(() -> {
			if (helper.getTick() == 5) {
				Vec3 a = control.position().add(0.0, 0.0, 18.0);
				Vec3 b = leader.position().add(0.0, 0.0, 18.0);
				control.getNavigation().moveTo(a.x, a.y, a.z, 1.0);
				leader.getNavigation().moveTo(b.x, b.y, b.z, 1.0);
			}
			if (helper.getTick() > 5 && helper.getTick() <= 45) {
				peak[0] = Math.max(peak[0], control.position().distanceTo(last[0]));
				peak[1] = Math.max(peak[1], leader.position().distanceTo(last[1]));
			}
			last[0] = control.position();
			last[1] = leader.position();
		});
		helper.runAfterDelay(60L, () -> {
			TowerTestSupport.result("ghostPackSpeed", "controlPeakPerTick=" + peak[0] + " leaderPeakPerTick=" + peak[1]);
			TowerGhosts.on(tower, floor).forEach(TowerGhost::discard);
			control.discard();
			TowerTestSupport.leave(helper, player);
			TowerMobs.pinFloorCap(floor, null);
			TowerGhosts.pinRiseChance(floor, null);
			helper.assertTrue(peak[0] > 0.2, "the control ghost barely moved: " + peak[0] + " per tick at most");
			helper.assertTrue(peak[1] <= peak[0] * 0.75, "the pack's leader flew " + peak[1] + " per tick at most against the old " + peak[0]);
			helper.succeed();
		});
	}
}
