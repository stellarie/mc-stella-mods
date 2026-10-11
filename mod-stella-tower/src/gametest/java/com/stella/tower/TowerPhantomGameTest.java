package com.stella.tower;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** The tower's phantom (T18): which floors draw one, the pair the spawner raises, and how it holds above a player and shoots. */
public class TowerPhantomGameTest {
	private static final int SAMPLED = 1800;
	/** The clear air the flight test asks round a player and over the player's head, in blocks. */
	private static final int HOLD_REACH = 9;
	private static final int HOLD_UP = 17;

	private static void hold(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos middle = plan.origin().offset(plan.width() / 2, 0, plan.depth() / 2);
		tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.chunkRadius(plan));
	}

	/** A fresh floor that draws a phantom; with {@code openGround}, one that also has a spot for a player to stand clear under it. */
	private static int phantomFloor(final long seed, final boolean openGround) {
		for (int block = 0; block < 40; block++) {
			try {
				return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0
					&& FloorBiomes.of(seed, f).openRoof() && TowerPhantoms.drawn(seed, f) > 0 && TowerPhantoms.wanted(TowerPlan.of(seed, f)) > 0
					&& (!openGround || hasOpenGround(TowerPlan.of(seed, f))));
			} catch (IllegalStateException e) {
				// This block of floors holds none: the next block is tried.
			}
		}
		throw new IllegalStateException("no floor that draws a phantom in forty blocks of floors");
	}

	private static boolean hasOpenGround(final TowerPlan plan) {
		try {
			openGround(plan, HOLD_REACH, HOLD_UP);
			return true;
		} catch (IllegalStateException e) {
			return false;
		}
	}

	/**
	 * A spot on a floor's ground with open air for {@code reach} blocks all round and {@code up} blocks over it, for a test
	 * that holds a player where nothing stands in a phantom's way. The spot is the nearest such to the floor's middle.
	 */
	private static BlockPos openGround(final TowerPlan plan, final int reach, final int up) {
		TowerVolume volume = TowerVolume.of(plan);
		BlockPos origin = plan.origin();
		double best = Double.MAX_VALUE;
		BlockPos spot = null;
		for (int x = reach + 1; x < plan.width() - reach - 1; x += 2) {
			for (int z = reach + 1; z < plan.depth() - reach - 1; z += 2) {
				int y = plan.standY(x, z);
				if (y < 0 || plan.inSafeZone(x, y, z) || plan.biome().inDais(plan, x, z) || Math.hypot(x - plan.arrivalX(), z - plan.arrivalZ()) < TowerMobs.BIOME_ARRIVAL_CLEAR) {
					continue;
				}
				double away = Math.hypot(x - plan.width() / 2.0, z - plan.depth() / 2.0);
				if (away >= best || !volume.standsFlat(x, y, z)) {
					continue;
				}
				boolean open = true;
				for (int dx = -reach; dx <= reach && open; dx += 2) {
					for (int dz = -reach; dz <= reach && open; dz += 2) {
						for (int dy = 1; dy <= up && open; dy += 2) {
							open = volume.kind(x + dx, y + dy, z + dz) == TowerVolume.AIR;
						}
					}
				}
				if (open) {
					best = away;
					spot = origin.offset(x, TowerLayout.BASE_Y + y - origin.getY(), z);
				}
			}
		}
		if (spot == null) {
			throw new IllegalStateException("floor " + plan.floor() + " has no open ground " + reach + " all round and " + up + " up");
		}
		return spot;
	}

	/**
	 * Catches a draw that gives every floor a phantom, none, or one the floor cannot hold: over a run of floors some, about
	 * {@value TowerPhantoms#CHANCE_PERCENT} in a hundred of those with sky enough, are drawn with one or two, and none
	 * without an open roof or {@value TowerPhantoms#MIN_SKY} blocks of sky.
	 */
	@GameTest(maxTicks = 3000)
	public void aShareOfOpenFloorsDrawOneOrTwoPhantoms(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int eligible = 0;
		int drawn = 0;
		int ones = 0;
		int twos = 0;
		int closedWith = 0;
		int checked = 0;
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 800_000; checked < SAMPLED; floor++) {
			if (floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			checked++;
			if (!FloorBiomes.of(seed, floor).openRoof()) {
				// The draw is pure, and a closed floor has no plan walked here: its want is read from a plan for a few only.
				if (checked % 60 == 0) {
					closedWith += TowerPhantoms.wanted(TowerPlan.of(seed, floor)) > 0 ? 1 : 0;
				}
				continue;
			}
			if (checked % 12 != 0) {
				// The share is read from the draw alone on most floors, and the eligibility from plans on one in twelve.
				continue;
			}
			TowerPlan plan = TowerPlan.of(seed, floor);
			if (TowerPhantoms.eligible(plan)) {
				eligible++;
				int wanted = TowerPhantoms.wanted(plan);
				drawn += wanted > 0 ? 1 : 0;
				ones += wanted == 1 ? 1 : 0;
				twos += wanted == 2 ? 1 : 0;
			}
		}
		int pureDrawn = 0;
		int pureCounted = 0;
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 900_000; pureCounted < 20_000; floor++) {
			pureCounted++;
			pureDrawn += TowerPhantoms.drawn(seed, floor) > 0 ? 1 : 0;
		}
		double share = pureDrawn / (double) pureCounted;
		TowerTestSupport.result("phantomDraw", "eligibleSampled=" + eligible + " drawnAmongThem=" + drawn + " ones=" + ones + " twos=" + twos + " closedWith=" + closedWith
			+ " pureShare=" + share);
		helper.assertTrue(eligible >= 30, "only " + eligible + " eligible floors were read");
		helper.assertTrue(closedWith == 0, closedWith + " closed floors draw a phantom");
		helper.assertTrue(share >= (TowerPhantoms.CHANCE_PERCENT - 2) / 100.0 && share <= (TowerPhantoms.CHANCE_PERCENT + 2) / 100.0,
			"the draw gives " + share + " of floors a phantom, not about " + TowerPhantoms.CHANCE_PERCENT / 100.0);
		helper.assertTrue(drawn > 0, "none of " + eligible + " eligible floors draws a phantom");
		for (int floor = 1; floor < TowerPhantoms.MIN_FLOOR; floor++) {
			helper.assertTrue(TowerPhantoms.wanted(TowerPlan.of(seed, floor)) == 0, "floor " + floor + " draws a phantom, below the lowest floor");
		}
		helper.assertTrue(ones + twos == drawn, "a floor draws a number of phantoms other than one or two");
		helper.succeed();
	}

	/**
	 * Catches a spawner that never raises the pair, raises it on a floor that did not draw it, or raises it wrong: on a floor
	 * that draws a phantom, with a player on it, the real spawner raises a phantom with a skeleton archer riding it, a bow
	 * in the archer's hand, both tower mobs of the floor with its level, no more than the floor draws; and on a floor that
	 * draws none, with the chance pinned to certain, none is raised.
	 */
	@GameTest(maxTicks = 700)
	public void theSpawnerRaisesTheDrawnPhantomsWithAnArcherEachAndNoOthers(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int with = phantomFloor(seed, false);
		int without = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0
			&& FloorBiomes.of(seed, f).openRoof() && TowerPhantoms.drawn(seed, f) == 0);
		List<ServerPlayer> players = new java.util.ArrayList<>();
		for (int floor : new int[] {with, without}) {
			TowerMobs.pinFloorCap(floor, 0);
			TowerGhosts.pinRiseChance(floor, 0.0);
			TowerPhantoms.pinRiseChance(floor, 1.0);
			TowerFloors.ensure(tower, floor);
			hold(tower, floor);
			ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
			player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			players.add(player);
		}
		int wanted = TowerPhantoms.wanted(TowerPlan.of(seed, with));
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], TowerPhantoms.on(tower, with).size()));
		helper.runAfterDelay(400L, () -> {
			List<TowerPhantom> pairs = TowerPhantoms.on(tower, with);
			int armed = 0;
			int ridden = 0;
			int ofTheTower = 0;
			int leveled = 0;
			for (TowerPhantom phantom : pairs) {
				Entity rider = phantom.getFirstPassenger();
				if (rider instanceof AbstractSkeleton archer) {
					ridden++;
					armed += archer.getMainHandItem().is(Items.BOW) ? 1 : 0;
					TowerMobs.State phantomState = TowerMobs.stateOf(phantom);
					TowerMobs.State archerState = TowerMobs.stateOf(archer);
					ofTheTower += phantomState != null && archerState != null && phantomState.floor() == with && archerState.floor() == with ? 1 : 0;
					leveled += com.stella.mobs.scaling.ThreatState.level(phantom) == TowerTestSupport.levelOf(with)
						&& com.stella.mobs.scaling.ThreatState.level(archer) == TowerTestSupport.levelOf(with) ? 1 : 0;
				}
			}
			int none = TowerPhantoms.on(tower, without).size();
			TowerTestSupport.result("phantomRaised", "floor=" + with + " wanted=" + wanted + " raised=" + pairs.size() + " most=" + most[0] + " ridden=" + ridden + " armed=" + armed
				+ " ofTheTower=" + ofTheTower + " leveled=" + leveled + " onAFloorThatDrewNone=" + none);
			for (int floor : new int[] {with, without}) {
				TowerMobs.pinFloorCap(floor, null);
				TowerGhosts.pinRiseChance(floor, null);
				TowerPhantoms.pinRiseChance(floor, null);
			}
			pairs.forEach(phantom -> {
				phantom.getPassengers().forEach(Entity::discard);
				phantom.discard();
			});
			players.forEach(player -> TowerTestSupport.leave(helper, player));
			helper.assertTrue(pairs.size() == wanted && most[0] == wanted, pairs.size() + " phantoms stand (at most " + most[0] + ") on a floor that draws " + wanted);
			helper.assertTrue(ridden == pairs.size(), ridden + " of " + pairs.size() + " phantoms carry a skeleton");
			helper.assertTrue(armed == ridden, armed + " of " + ridden + " archers hold a bow");
			helper.assertTrue(ofTheTower == ridden, "only " + ofTheTower + " of " + ridden + " pairs are tower mobs of the floor");
			helper.assertTrue(leveled == ridden, "only " + leveled + " of " + ridden + " pairs carry the floor's level");
			helper.assertTrue(none == 0, none + " phantoms stand on a floor that draws none");
			helper.succeed();
		});
	}

	/**
	 * Catches a phantom that dives, drifts off or does not circle, and an archer that does not shoot: a pair is raised
	 * thirty blocks from a player standing on open ground, and over three hundred ticks it comes to the player's airspace,
	 * holds {@value TowerPhantoms#HOLD_MIN} to {@value TowerPhantoms#HOLD_MAX} blocks over the player's head at every tick
	 * after it arrives, within a few blocks across, goes round the player more than a whole turn, never once nearer than
	 * {@value TowerPhantoms#HOLD_MIN} blocks over, and its archer looses arrows at the player.
	 */
	@GameTest(maxTicks = 700)
	public void aPhantomHoldsTenToFourteenBlocksOverThePlayerCirclesAndNeverDivesWhileItsArcherShoots(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int floor = phantomFloor(seed, true);
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerPhantoms.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		hold(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos ground = openGround(plan, HOLD_REACH, HOLD_UP);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2048.0);
		player.setAbsorptionAmount(2048.0F);
		player.teleportTo(tower, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		// A player changing dimension takes no damage until its client has loaded: cleared after the move, not before.
		TowerTestSupport.vulnerable(player);
		// A level low enough that the arrows leave the player standing; the floor is high up the tower.
		int lowLevel = TowerMobs.levelOf(TowerPhantoms.MIN_FLOOR);
		// Twenty-odd blocks off, at the height the spawner gives, in the first direction where the air is clear.
		TowerPhantom phantom = null;
		for (int turn = 0; turn < 16 && phantom == null; turn++) {
			double angle = turn * Math.PI / 8.0;
			Vec3 at = new Vec3(ground.getX() + 0.5 + Math.cos(angle) * 24.0, ground.getY() + 12.0, ground.getZ() + 0.5 + Math.sin(angle) * 24.0);
			BlockPos cell = BlockPos.containing(at);
			boolean inRoom = cell.getX() > plan.origin().getX() + 2 && cell.getX() < plan.origin().getX() + plan.width() - 3
				&& cell.getZ() > plan.origin().getZ() + 2 && cell.getZ() < plan.origin().getZ() + plan.depth() - 3;
			phantom = inRoom ? TowerPhantoms.raise(tower, floor, lowLevel, at) : null;
		}
		helper.assertTrue(phantom != null, "no pair could be raised near the open ground");
		TowerPhantom pair = phantom;
		AbstractSkeleton archer = (AbstractSkeleton) pair.getFirstPassenger();
		helper.assertTrue(archer != null, "the pair carries no archer");
		Set<UUID> arrows = new HashSet<>();
		double[] lowest = {Double.MAX_VALUE};
		double[] highest = {-Double.MAX_VALUE};
		double[] widest = {0.0};
		int[] arrivedAt = {-1};
		double[] turned = {0.0};
		double[] lastAngle = {Double.NaN};
		float absorbed = player.getAbsorptionAmount();
		helper.onEachTick(() -> {
			if (!pair.isAlive()) {
				return;
			}
			// The player is kept at full health in a creative-like safety: absorption soaks the arrows, and is counted below.
			double over = pair.getY() - player.getY();
			double across = Math.hypot(pair.getX() - player.getX(), pair.getZ() - player.getZ());
			if (arrivedAt[0] < 0 && across < 8.0 && over > TowerPhantoms.HOLD_MIN - 1.5) {
				arrivedAt[0] = (int) helper.getTick();
			}
			if (arrivedAt[0] >= 0 && helper.getTick() >= arrivedAt[0] + 20) {
				lowest[0] = Math.min(lowest[0], over);
				highest[0] = Math.max(highest[0], over);
				widest[0] = Math.max(widest[0], across);
				double angle = Math.atan2(pair.getZ() - player.getZ(), pair.getX() - player.getX());
				if (!Double.isNaN(lastAngle[0])) {
					double step = angle - lastAngle[0];
					while (step > Math.PI) {
						step -= 2.0 * Math.PI;
					}
					while (step < -Math.PI) {
						step += 2.0 * Math.PI;
					}
					turned[0] += Math.abs(step);
				}
				lastAngle[0] = angle;
			}
			for (AbstractArrow arrow : tower.getEntitiesOfClass(AbstractArrow.class, pair.getBoundingBox().inflate(30.0, 30.0, 30.0), shot -> shot.getOwner() == archer)) {
				arrows.add(arrow.getUUID());
			}
		});
		helper.runAfterDelay(600L, () -> {
			float soaked = absorbed - player.getAbsorptionAmount();
			boolean alive = pair.isAlive();
			boolean seatedStill = archer.isPassenger() && archer.getVehicle() == pair;
			TowerTestSupport.result("phantomHold", "alive=" + alive + " arrivedAt=" + arrivedAt[0] + " lowestOver=" + lowest[0] + " highestOver=" + highest[0] + " widestAcross=" + widest[0]
				+ " turnedRad=" + turned[0] + " arrows=" + arrows.size() + " soaked=" + soaked + " seated=" + seatedStill);
			pair.getPassengers().forEach(Entity::discard);
			pair.discard();
			TowerMobs.pinFloorCap(floor, null);
			TowerGhosts.pinRiseChance(floor, null);
			TowerPhantoms.pinRiseChance(floor, null);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(alive && seatedStill, "the pair did not last: alive=" + alive + " seated=" + seatedStill);
			helper.assertTrue(arrivedAt[0] >= 0 && arrivedAt[0] <= 300, "the pair came to the player's airspace at tick " + arrivedAt[0]);
			helper.assertTrue(lowest[0] >= TowerPhantoms.HOLD_MIN - 0.5, "the pair came down to " + lowest[0] + " blocks over the player");
			helper.assertTrue(highest[0] <= TowerPhantoms.HOLD_MAX + 0.5, "the pair rose to " + highest[0] + " blocks over the player");
			helper.assertTrue(widest[0] <= 8.0, "the pair drifted " + widest[0] + " blocks across from the player");
			helper.assertTrue(turned[0] >= 2.0 * Math.PI * 1.2, "the pair went round the player only " + turned[0] / (2.0 * Math.PI) + " turns");
			helper.assertTrue(arrows.size() >= 3, "the archer loosed only " + arrows.size() + " arrows");
			helper.assertTrue(soaked > 0.0F, "no arrow struck the player");
			helper.succeed();
		});
	}
}
