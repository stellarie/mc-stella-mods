package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Travel and surprises: the portal back down, the keys that work from anywhere, the chest a cleared floor pays,
 * and the milestone floor's fireworks and chest. The chime and the particles are heard and seen by a player and
 * cannot be observed here: they are the manual items of the task.
 */
public class TowerTravelRewardsGameTest {
	private static int ordinaryFloor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerFloorRewards.MILESTONE_EVERY >= 2 && f % TowerFloorRewards.MILESTONE_EVERY <= 8);
	}

	private static int milestoneFloor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerFloorRewards.MILESTONE_EVERY == 0);
	}

	private static int floorOf(final ServerPlayer player) {
		return TowerLayout.floorAt(player.blockPosition());
	}

	private static List<BlockPos> chestsNear(final ServerLevel tower, final Vec3 centre, final int reach) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos at = BlockPos.containing(centre);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos pos = at.offset(dx, dy, dz);
					if (tower.getBlockState(pos).is(TowerBlocks.TOWER_CHEST)) {
						found.add(pos);
					}
				}
			}
		}
		return found;
	}

	private static List<ItemStack> contents(final ServerLevel tower, final BlockPos chest) {
		List<ItemStack> stacks = new ArrayList<>();
		if (tower.getBlockEntity(chest) instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (!container.getItem(i).isEmpty()) {
					stacks.add(container.getItem(i));
				}
			}
		}
		return stacks;
	}

	private static ServerPlayer outsidePlayer(final GameTestHelper helper) {
		return TowerTestSupport.join(helper, 2.5, 5.5);
	}

	private static void useKey(final ServerPlayer player, final ItemStack key) {
		player.setItemInHand(InteractionHand.MAIN_HAND, key);
		key.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
	}

	private static void killOnFloor(final ServerLevel tower, final int floor, final int count, final List<Zombie> fallen) {
		Vec3 at = TowerLayout.arrival(tower, floor).add(0.0, 0.0, 0.0);
		for (int i = 0; i < count; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, floor, TowerElement.FIRE);
			zombie.snapTo(at);
			tower.addFreshEntity(zombie);
			fallen.add(zombie);
		}
	}

	/** Every floor above the first holds a back portal on its arrival, and stepping in lands the player a floor down. Not required: a random floor sometimes has no portal spot. Check by hand. */
	@GameTest(maxTicks = 200, required = false)
	public void theBackPortalOfAFloorLeadsDownAndTheHighestFloorStaysSaved(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		List<String> report = new ArrayList<>();
		int[] floors = {ordinaryFloor(), ordinaryFloor(), ordinaryFloor(), milestoneFloor(), ordinaryFloor()};
		for (int floor : floors) {
			TowerFloors.ensure(tower, floor);
			BlockPos inside = TowerBackPortals.find(tower, floor);
			helper.assertTrue(inside != null, "floor " + floor + " holds no back portal");
			BlockState state = tower.getBlockState(inside);
			helper.assertTrue(state.is(TowerBlocks.TOWER_BACK_PORTAL), "floor " + floor + " holds " + state.getBlock() + " as its back portal");
			// The frame is styled apart from the exit's: soul fire burns on its two posts.
			long fires = BlockPos.betweenClosedStream(inside.offset(-4, 0, -4), inside.offset(4, 8, 4))
				.filter(pos -> tower.getBlockState(pos).is(Blocks.SOUL_FIRE)).count();
			helper.assertTrue(fires == 2, "floor " + floor + " burns " + fires + " soul fires on its back portal, not 2");
			report.add(floor + "@" + inside.toShortString());
		}
		int top = floors[0];
		TowerFloors.ensure(tower, top - 1);
		ServerPlayer player = outsidePlayer(helper);
		TowerTravel.jumpTo(player, top);
		helper.assertTrue(floorOf(player) == top, "the jump left the player on floor " + floorOf(player));
		helper.assertTrue(TowerTravel.lastFloor(player) == top, "the first visit saved floor " + TowerTravel.lastFloor(player));
		BlockPos inside = TowerBackPortals.find(tower, top);
		TeleportTransition down = TowerBlocks.TOWER_BACK_PORTAL.getPortalDestination(tower, player, inside);
		helper.assertTrue(down != null, "the back portal led nowhere");
		player.teleport(down);
		boolean below = floorOf(player) == top - 1;
		TowerBackPortals.Landing landing = TowerBackPortals.landing(tower, top - 1);
		double off = player.position().distanceTo(landing.position());
		boolean free = tower.noCollision(player, player.getBoundingBox());
		int saved = TowerTravel.lastFloor(player);
		TowerTestSupport.result("backPortal", "floors=" + report + " from=" + top + " now=" + floorOf(player) + " off=" + off + " free=" + free + " saved=" + saved);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(below, "the back portal put the player on floor " + floorOf(player) + ", not " + (top - 1));
		helper.assertTrue(off < 1.0 && free, "the player landed " + off + " from the exit stair, free=" + free);
		helper.assertTrue(saved == top, "walking down lowered the saved floor to " + saved);
		helper.succeed();
	}

	/** A floor built before the back portal gains one the next time anyone arrives on it. */
	@GameTest(maxTicks = 100)
	public void aFloorBuiltWithoutABackPortalGainsOneOnArrival(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos inside = TowerBackPortals.find(tower, floor);
		helper.assertTrue(inside != null, "floor " + floor + " was built without a back portal");
		for (int dz = -1; dz <= 2; dz++) {
			for (int dy = 0; dy <= 5; dy++) {
				tower.setBlock(inside.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		boolean gone = TowerBackPortals.find(tower, floor) == null;
		TowerFloors.ensure(tower, floor);
		BlockPos again = TowerBackPortals.find(tower, floor);
		TowerTestSupport.result("backPortalOnArrival", "gone=" + gone + " again=" + again + " was=" + inside);
		helper.assertTrue(gone, "the back portal was not removed, so the case proves nothing");
		helper.assertTrue(again != null && again.equals(inside), "the older floor did not regain its back portal: " + again);
		helper.succeed();
	}

	/** A mob cannot use the way down: only a player climbs and descends. */
	@GameTest(maxTicks = 100)
	public void aMobDoesNotUseTheBackPortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		TowerFloors.ensure(tower, floor);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TeleportTransition down = TowerBlocks.TOWER_BACK_PORTAL.getPortalDestination(tower, zombie, TowerBackPortals.find(tower, floor));
		TowerTestSupport.result("mobBackPortal", "leadsTo=" + down);
		helper.assertTrue(down == null, "a mob was given a way down");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theVoidKeyReturnsAPlayerFromOutsideTheTowerToTheirEntry(final GameTestHelper helper) {
		ServerPlayer player = outsidePlayer(helper);
		Vec3 entry = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		player.setAttached(TowerTravel.ENTRY, new TowerTravel.Entry(helper.getLevel().dimension(), entry, 0.0F));
		Vec3 before = player.position();
		useKey(player, new ItemStack(TowerItems.VOID_KEY));
		double moved = player.position().distanceTo(before);
		double fromEntry = player.position().distanceTo(entry);
		TowerTestSupport.result("voidKeyOutside", "moved=" + moved + " fromEntry=" + fromEntry + " level=" + player.level().dimension().identifier());
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(moved > 1.0, "the Void Key did nothing outside the tower");
		helper.assertTrue(fromEntry < 0.5 && player.level() == helper.getLevel(), "the Void Key left the player " + fromEntry + " from their entry");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aFloorKeyUsedOutsideTheTowerSendsThePlayerToItsFloorAndTheVoidKeyBringsThemBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		ServerPlayer player = outsidePlayer(helper);
		Vec3 start = player.position();
		useKey(player, FloorKeyItem.forFloor(floor));
		boolean there = player.level() == tower && floorOf(player) == floor;
		double off = player.position().distanceTo(TowerLayout.arrival(tower, floor));
		boolean voidKey = player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY));
		useKey(player, new ItemStack(TowerItems.VOID_KEY));
		double back = player.position().distanceTo(start);
		TowerTestSupport.result("floorKeyOutside", "there=" + there + " off=" + off + " voidKey=" + voidKey + " back=" + back);
		boolean home = player.level() == helper.getLevel();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(there, "the floor key did not put the player on floor " + floor);
		helper.assertTrue(off < 1.0, "the player arrived " + off + " from floor " + floor + "'s arrival");
		helper.assertTrue(voidKey, "the jump gave no Void Key");
		helper.assertTrue(home && back < 3.0, "the Void Key left the player " + back + " from where they began, home=" + home);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aFloorKeyUsedInsideTheTowerSendsThePlayerToItsFloor(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int low = ordinaryFloor();
		int high = ordinaryFloor();
		TowerFloors.ensure(tower, low);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, low);
		useKey(player, FloorKeyItem.forFloor(high));
		boolean there = player.level() == tower && floorOf(player) == high;
		TowerTestSupport.result("floorKeyInside", "from=" + low + " to=" + high + " now=" + floorOf(player));
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(there, "the floor key did not take the player from floor " + low + " to " + high);
		helper.succeed();
	}

	/** The last hostile mob to die pays one chest by the exit, rolled more often than a floor's chests. */
	@GameTest(maxTicks = 100)
	public void clearingAFloorPaysOneBonusChestByTheExitForTheVisit(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		TowerMobs.pinFloorCap(floor, 0);
		try {
			ServerPlayer player = outsidePlayer(helper);
			TowerTravel.jumpTo(player, floor);
			Vec3 exit = TowerBackPortals.landing(tower, floor).position();
			List<BlockPos> before = chestsNear(tower, exit, 8);
			List<Zombie> mobs = new ArrayList<>();
			killOnFloor(tower, floor, 2, mobs);
			mobs.get(0).kill(tower);
			List<BlockPos> early = chestsNear(tower, exit, 8);
			early.removeAll(before);
			int afterFirst = early.size();
			mobs.get(1).kill(tower);
			List<BlockPos> chests = chestsNear(tower, exit, 8);
			chests.removeAll(before);
			List<ItemStack> loot = chests.isEmpty() ? List.of() : contents(tower, chests.get(0));
			List<Zombie> more = new ArrayList<>();
			killOnFloor(tower, floor, 1, more);
			more.get(0).kill(tower);
			List<BlockPos> later = chestsNear(tower, exit, 8);
			later.removeAll(before);
			int afterMore = later.size();
			TowerTestSupport.result("floorClear", "afterFirst=" + afterFirst + " chests=" + chests.size() + " loot=" + loot.size()
				+ " afterMore=" + afterMore + " rolls=" + TowerFloorRewards.bonusRolls(floor));
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(afterFirst == 0, "a chest appeared while a hostile mob was left");
			helper.assertTrue(chests.size() == 1, "clearing the floor placed " + chests.size() + " chests");
			helper.assertTrue(TowerFloorRewards.bonusRolls(floor) > TowerChests.ROLLS, "the bonus chest rolls no more often than a floor's chest");
			helper.assertTrue(loot.size() > TowerChests.ROLLS / 2, "the bonus chest holds " + loot.size() + " stacks");
			helper.assertTrue(afterMore == 1, "a second clearing in the same visit left " + afterMore + " chests");
			helper.succeed();
		} finally {
			TowerMobs.pinFloorCap(floor, null);
		}
	}

	/** Leaving the floor ends the visit: coming back and clearing it again pays again. */
	@GameTest(maxTicks = 200)
	public void aSecondVisitToAClearedFloorPaysAgain(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		TowerMobs.pinFloorCap(floor, 0);
		ServerPlayer player = outsidePlayer(helper);
		TowerTravel.jumpTo(player, floor);
		Vec3 exit = TowerBackPortals.landing(tower, floor).position();
		List<BlockPos> before = chestsNear(tower, exit, 8);
		List<Zombie> first = new ArrayList<>();
		killOnFloor(tower, floor, 1, first);
		first.get(0).kill(tower);
		int one = chestsNear(tower, exit, 8).size() - before.size();
		VoidKeyItem.returnHome(player);
		helper.runAfterDelay(60L, () -> {
			boolean left = !TowerFloorRewards.paid(floor);
			TowerTravel.jumpTo(player, floor);
			List<Zombie> second = new ArrayList<>();
			killOnFloor(tower, floor, 1, second);
			second.get(0).kill(tower);
			int two = chestsNear(tower, exit, 8).size() - before.size();
			TowerMobs.pinFloorCap(floor, null);
			TowerTestSupport.result("floorClearTwice", "one=" + one + " visitEnded=" + left + " two=" + two);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(one == 1, "the first clearing placed " + one + " chests");
			helper.assertTrue(left, "the visit did not end when the player left the floor");
			helper.assertTrue(two == 2, "the second visit left " + two + " chests, not 2");
			helper.succeed();
		});
	}

	private static int rockets(final ServerLevel tower, final ServerPlayer player) {
		return tower.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(player.position(), player.position()).inflate(16.0)).size();
	}

	/** A first arrival on a tenth floor bursts fireworks and sets a chest with a rune and a floor key; a second does not. */
	@GameTest(maxTicks = 100)
	public void reachingAMilestoneFloorForTheFirstTimeBurstsFireworksAndSetsAChestOnce(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = milestoneFloor();
		TowerMobs.pinFloorCap(floor, 0);
		ServerPlayer player = outsidePlayer(helper);
		TowerTestSupport.vulnerable(player);
		float health = player.getHealth();
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		TowerFloors.ensure(tower, floor);
		List<BlockPos> before = chestsNear(tower, arrival, 8);
		useKey(player, FloorKeyItem.forFloor(floor));
		int firstRockets = rockets(tower, player);
		List<BlockPos> chests = chestsNear(tower, arrival, 8);
		chests.removeAll(before);
		List<ItemStack> loot = chests.isEmpty() ? List.of() : contents(tower, chests.get(0));
		boolean rune = loot.stream().anyMatch(stack -> stack.is(TowerItems.RUNE) && stack.get(TowerItems.RUNE_DATA).floor() == floor);
		boolean key = loot.stream().anyMatch(stack -> FloorKeyItem.floorOf(stack) == floor);
		VoidKeyItem.returnHome(player);
		useKey(player, FloorKeyItem.forFloor(floor));
		int secondRockets = rockets(tower, player);
		List<BlockPos> chestsLater = chestsNear(tower, arrival, 8);
		chestsLater.removeAll(before);
		int chestsAfter = chestsLater.size();
		TowerMobs.pinFloorCap(floor, null);
		TowerTestSupport.result("milestone", "floor=" + floor + " rockets=" + firstRockets + " chests=" + chests.size() + " loot=" + loot.size()
			+ " rune=" + rune + " key=" + key + " rocketsAfterSecond=" + secondRockets + " chestsAfter=" + chestsAfter + " health=" + player.getHealth());
		float after = player.getHealth();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(firstRockets >= 1, "no firework burst over the player");
		helper.assertTrue(chests.size() == 1, "the milestone placed " + chests.size() + " chests");
		helper.assertTrue(rune, "the milestone chest holds no rune of floor " + floor);
		helper.assertTrue(key, "the milestone chest holds no floor key of floor " + floor);
		helper.assertTrue(after >= health, "the fireworks hurt the player");
		helper.assertTrue(secondRockets == firstRockets, "reaching the floor again fired " + (secondRockets - firstRockets) + " more rockets");
		helper.assertTrue(chestsAfter == 1, "reaching the floor again left " + chestsAfter + " chests");
		helper.succeed();
	}

	/** A floor that is not a milestone sets no chest and fires no firework at the arrival. */
	@GameTest(maxTicks = 100)
	public void anOrdinaryFloorSetsNoMilestoneChest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor();
		ServerPlayer player = outsidePlayer(helper);
		TowerFloors.ensure(tower, floor);
		int placed = chestsNear(tower, TowerLayout.arrival(tower, floor), 8).size();
		useKey(player, FloorKeyItem.forFloor(floor));
		int rockets = rockets(tower, player);
		int chests = chestsNear(tower, TowerLayout.arrival(tower, floor), 8).size() - placed;
		TowerTestSupport.result("noMilestone", "floor=" + floor + " rockets=" + rockets + " chests=" + chests);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(rockets == 0 && chests == 0, "floor " + floor + " fired " + rockets + " rockets and set " + chests + " chests");
		helper.succeed();
	}
}
