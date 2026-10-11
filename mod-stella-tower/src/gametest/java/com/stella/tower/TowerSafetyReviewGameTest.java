package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The F3 review's follow-ups to the safe zones: a shaman's quarry, what {@link TowerSafety#sheltered} costs, and
 * where a mob that is sent out lands.
 */
public class TowerSafetyReviewGameTest {

	/** A spells mod that records what it is asked to cast. */
	private static final class Fake implements TowerSpells.Caster {
		final List<String> strikes = new ArrayList<>();

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public List<String> offensive() {
			return List.of("spark_shot");
		}

		@Override
		public List<String> support() {
			return List.of("sanctuary");
		}

		@Override
		public int cooldownOf(final String spell) {
			return 20;
		}

		@Override
		public boolean castAt(final Mob caster, final String spell, final LivingEntity target) {
			this.strikes.add(spell);
			return true;
		}

		@Override
		public boolean support(final Mob caster, final String spell) {
			return true;
		}
	}

	/** A shaman beside the entrance zone never casts at a player inside it, and casts once the player steps out. */
	@GameTest(maxTicks = 200)
	public void aShamanBesideTheZoneDoesNotCastAtAPlayerInsideIt(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		Fake fake = new Fake();
		TowerSpells.install(floor, fake);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(plan);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.teleportTo(tower, spots.inside().x, spots.inside().y, spots.inside().z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, spots.mob());
		Skeleton shaman = TowerShamans.summon(tower, floor, spots.mob());
		helper.assertTrue(shaman != null, "no shaman could be raised");
		shaman.setNoAi(true);
		shaman.snapTo(spots.mob());
		// Two thinks and more, with the player inside: no cast may come.
		long inside = 3L * TowerShamans.THINK_TICKS + 2L;
		int[] castsInside = {-1};
		long[] outAt = {-1};
		boolean[] logged = {false};
		helper.onEachTick(() -> {
			if (castsInside[0] < 0 && helper.getTick() >= inside) {
				castsInside[0] = fake.strikes.size();
				outAt[0] = helper.getTick();
				player.teleportTo(tower, spots.outside().x, spots.outside().y, spots.outside().z, java.util.Set.of(), 0.0F, 0.0F, true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(castsInside[0] >= 0, "the player has not yet stepped out");
			helper.assertTrue(fake.strikes.size() > castsInside[0], "the shaman never cast at the player once they stepped out");
			if (!logged[0]) {
				logged[0] = true;
				TowerTestSupport.result("shamanQuarry", "castsInside=" + castsInside[0] + " castsAfterStepOut=" + (fake.strikes.size() - castsInside[0])
					+ " steppedOutAt=" + outAt[0] + " insideWindow=" + inside);
				shaman.discard();
				TowerTestSupport.leave(helper, player);
				TowerMobs.pinFloorCap(floor, null);
				TowerGhosts.pinRiseChance(floor, null);
				TowerSpells.install(floor, null);
			}
			helper.assertTrue(castsInside[0] == 0, "the shaman cast " + castsInside[0] + " times at a player inside the safe zone");
		});
	}

	/** Asking whether a player is sheltered never loads the chunk that holds their floor's markers. */
	@GameTest
	public void shelteredNeverLoadsAChunkToAnswer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		BlockPos origin = TowerLayout.origin(floor);
		BlockPos marker = TowerFloors.marker(floor);
		int cx = marker.getX() >> 4;
		int cz = marker.getZ() >> 4;
		helper.assertTrue(tower.getChunkSource().getChunkNow(cx, cz) == null, "the marker's chunk was already loaded, so the case proves nothing");
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.teleportTo(tower, origin.getX() + TowerLayout.SIZE - 3.5, TowerLayout.BASE_Y + 1.0, origin.getZ() + TowerLayout.SIZE - 3.5, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean answer = TowerSafety.sheltered(player);
		boolean loaded = tower.getChunkSource().getChunkNow(cx, cz) != null;
		TowerTestSupport.result("shelteredNoLoad", "sheltered=" + answer + " markerChunkLoaded=" + loaded);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!loaded, "asking whether a player is sheltered loaded the marker's chunk");
		helper.succeed();
	}

	/**
	 * A busy floor asks about the same player again and again in a tick: one plan lookup answers them all.
	 * Not required: a random floor with cover and hills can leave no open spot beside a safe zone, and the case then
	 * cannot place its player. Manual check: stand beside a safe zone on a busy floor and see no tick lag.
	 */
	@GameTest(required = false)
	public void shelteredIsLookedUpOncePerPlayerPerTickRequiresManualTest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(plan);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.teleportTo(tower, spots.outside().x, spots.outside().y, spots.outside().z, java.util.Set.of(), 0.0F, 0.0F, true);
		int before = TowerSafety.lookups();
		boolean out = false;
		for (int i = 0; i < 50; i++) {
			out |= TowerSafety.sheltered(player);
		}
		int spent = TowerSafety.lookups() - before;
		// Moved into the zone within the same tick, the answer must follow the player, not the cache.
		player.teleportTo(tower, spots.inside().x, spots.inside().y, spots.inside().z, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean in = TowerSafety.sheltered(player);
		TowerTestSupport.result("shelteredCost", "callsAtOnePlace=50 lookups=" + spent + " outsideAnswer=" + out + " insideAnswerAfterMove=" + in);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(spent == 1, "50 asks in a tick cost " + spent + " lookups, not 1");
		helper.assertTrue(!out && in, "the answers were outside=" + out + " inside=" + in);
		helper.succeed();
	}

	/**
	 * A mob is sent back to where it was last seen outside only where that spot is still a place to stand. With the
	 * block under the sighting gone, it is sent to the nearest supported spot instead, never hung over the hole.
	 */
	@GameTest
	public void aMobSentBackIsNeverPutOverAHole(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(plan);
		TowerTestSupport.holdRoom(tower, spots.mob());
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.DARK);
		// A mob with no AI does not fall or settle, so it is stood on the ground by hand.
		zombie.setNoAi(true);
		zombie.snapTo(spots.mob().x, spots.mob().y, spots.mob().z, 0.0F, 0.0F);
		zombie.setOnGround(true);
		tower.addFreshEntity(zombie);
		TowerMaze.Zone zone = plan.safeZones().get(0);
		BlockPos o = plan.origin();
		AABB box = TowerSafety.zoneBox(plan, zone);
		BlockPos seenAt = BlockPos.containing(spots.mob());
		// Seen outside, on the ground.
		TowerSafety.sweep(tower, floor);
		List<BlockPos> holes = new ArrayList<>();
		List<BlockState> kept = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos under = seenAt.offset(dx, -1, dz);
				holes.add(under);
				kept.add(tower.getBlockState(under));
				tower.setBlock(under, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		try {
			// Driven into the zone, then swept.
			zombie.snapTo(o.getX() + zone.x() + 3.5, TowerLayout.BASE_Y, o.getZ() + zone.z() + 3.5, 0.0F, 0.0F);
			zombie.setOnGround(true);
			TowerSafety.sweep(tower, floor);
			Vec3 at = zombie.position();
			BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
			boolean supported = tower.getBlockState(below).isFaceSturdy(tower, below, Direction.UP);
			boolean outside = !zombie.getBoundingBox().intersects(box);
			boolean overHole = holes.contains(below);
			TowerTestSupport.result("sentBackSupport", "outside=" + outside + " supported=" + supported + " overHole=" + overHole + " at=" + at.subtract(Vec3.atLowerCornerOf(o)));
			helper.assertTrue(outside, "the mob was not sent out of the zone");
			helper.assertTrue(supported && !overHole, "the mob was put at " + below + " with no block to stand on");
		} finally {
			for (int i = 0; i < holes.size(); i++) {
				tower.setBlock(holes.get(i), kept.get(i), 2);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/** The boss room's zone can be targeted too: the mob outside its ring, a player inside, and one outside near the mob. */
	@GameTest
	public void spotsBesideTheBossRoomSitOutsideAndInsideIt(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		AABB room = TowerSafety.zoneBox(plan, plan.safeZones().get(1));
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(plan, 1);
		boolean mobOut = !room.contains(spots.mob());
		boolean insideIn = room.contains(spots.inside());
		boolean outsideOut = !room.contains(spots.outside());
		TowerTestSupport.result("bossRoomSpots", "mobOut=" + mobOut + " insideIn=" + insideIn + " outsideOut=" + outsideOut);
		helper.assertTrue(mobOut && insideIn && outsideOut, "the boss room's spots were mobOut=" + mobOut + " insideIn=" + insideIn + " outsideOut=" + outsideOut);
		helper.succeed();
	}
}
