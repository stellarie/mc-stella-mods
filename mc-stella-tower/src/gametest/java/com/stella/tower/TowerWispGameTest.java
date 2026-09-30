package com.stella.tower;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** The wisp: crafted from a ghost rune, it lights the way to a floor's portal and enrages what stands on it. */
public class TowerWispGameTest {
	private static final int S = TowerLayout.SIZE;

	private static int ordinaryFloor(final ServerLevel tower) {
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		// Only the case's own mobs: the floor's are held off.
		TowerMobs.pinFloorCap(floor, 0);
		TowerGhosts.pinRiseChance(floor, 0.0);
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	private static ServerPlayer holder(final GameTestHelper helper, final ServerLevel tower, final int floor) {
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TowerItems.WISP));
		return player;
	}

	/** Steps from one spot of the room to another over open ground, or -1 when there is no way. */
	private static int steps(final ServerLevel tower, final int floor, final BlockPos from, final BlockPos to) {
		BlockPos o = TowerLayout.origin(floor);
		int[][] seen = new int[S][S];
		for (int[] row : seen) {
			Arrays.fill(row, -1);
		}
		Deque<int[]> queue = new ArrayDeque<>();
		seen[from.getX() - o.getX()][from.getZ() - o.getZ()] = 0;
		queue.add(new int[] {from.getX() - o.getX(), from.getZ() - o.getZ()});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x < 0 || z < 0 || x >= S || z >= S || seen[x][z] >= 0) {
					continue;
				}
				BlockPos feet = o.offset(x, 0, z);
				if (tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
					&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty()) {
					seen[x][z] = seen[at[0]][at[1]] + 1;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen[to.getX() - o.getX()][to.getZ() - o.getZ()];
	}

	@GameTest
	public void aGhostRuneTwoEchoShardsAndGlowstoneMakeAWisp(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(2, 2, List.of(
			new ItemStack(TowerItems.GHOST_RUNE), new ItemStack(Items.ECHO_SHARD),
			new ItemStack(Items.ECHO_SHARD), new ItemStack(Items.GLOWSTONE_DUST)
		));
		var match = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		ItemStack made = match.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		CraftingInput short1 = CraftingInput.of(2, 2, List.of(
			new ItemStack(TowerItems.GHOST_RUNE), new ItemStack(Items.ECHO_SHARD), ItemStack.EMPTY, new ItemStack(Items.GLOWSTONE_DUST)
		));
		boolean shortMakes = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, short1, level)
			.map(recipe -> recipe.value().assemble(short1).is(TowerItems.WISP)).orElse(false);
		TowerTestSupport.result("wispRecipe", "made=" + made + " uses=" + TowerWisps.usesOf(made) + " shortMakes=" + shortMakes);
		helper.assertTrue(made.is(TowerItems.WISP) && made.getCount() == 1, "the recipe made " + made);
		helper.assertTrue(TowerWisps.usesOf(made) == TowerWisps.USES, "a wisp has " + TowerWisps.usesOf(made) + " uses, not " + TowerWisps.USES);
		helper.assertFalse(shortMakes, "one echo shard short still made a wisp");
		helper.succeed();
	}

	/** Used on a floor, a wisp lights the shortest way from the player to the portal, for twenty minutes, and spends one use. */
	@GameTest
	public void aWispLightsTheShortestWayToThePortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		ServerPlayer player = holder(helper, tower, floor);
		int now = tower.getServer().getTickCount();
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		List<TowerWisps.Trail> trails = TowerWisps.trailsOn(floor);
		TowerWisps.Trail trail = trails.isEmpty() ? null : trails.get(0);
		BlockPos feet = player.blockPosition();
		// The portal is two blocks wide: the way ends at whichever is nearer.
		// The portal stands raised, so its own spot is not open ground: the walk is to the spot before it, and one more.
		TowerPlan plan = TowerPlan.at(tower, floor);
		int shortest = plan.portalInside().stream().filter(pos -> pos.getY() == plan.portalInside().get(0).getY())
			.mapToInt(pos -> steps(tower, floor, feet, new BlockPos(pos.getX() + plan.portalFacing(), TowerLayout.BASE_Y, pos.getZ())) + 1)
			.filter(n -> n >= 1).min().orElse(-1);
		int broken = 0;
		if (trail != null) {
			for (int i = 1; i < trail.path().size(); i++) {
				// Level steps, but for the last, up onto the portal's frame.
				BlockPos step = trail.path().get(i);
				BlockPos before = trail.path().get(i - 1);
				broken += Math.abs(step.getX() - before.getX()) + Math.abs(step.getZ() - before.getZ()) == 1 ? 0 : 1;
			}
		}
		int damage = TowerWisps.USES - TowerWisps.usesOf(player.getMainHandItem());
		TowerTestSupport.result("wispTrail", "trails=" + trails.size() + " length=" + (trail == null ? -1 : trail.path().size() - 1)
			+ " shortest=" + shortest + " broken=" + broken + " lasts=" + (trail == null ? -1 : trail.until() - now) + " damage=" + damage);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(trail != null, "the wisp lit no way");
		helper.assertTrue(trail.path().get(0).equals(feet) && TowerLayout.portalInside(tower, floor).contains(trail.path().get(trail.path().size() - 1)),
			"the way runs from " + trail.path().get(0) + " to " + trail.path().get(trail.path().size() - 1) + ", not from the player to the portal");
		helper.assertTrue(broken == 0 && trail.path().size() - 1 == shortest, "the way is " + (trail.path().size() - 1) + " steps with " + broken + " gaps; the shortest is " + shortest);
		helper.assertTrue(trail.until() - now == TowerWisps.LIGHT_TICKS, "the way lasts " + (trail.until() - now) + " ticks");
		helper.assertTrue(damage == 1, "the wisp spent " + damage + " uses");
		helper.succeed();
	}

	/** A lit way fades when its time is up. */
	@GameTest(maxTicks = 100)
	public void aLitWayFades(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		ServerPlayer player = holder(helper, tower, floor);
		TowerWisps.pinLightTicks(floor, 20);
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		int lit = TowerWisps.trailsOn(floor).size();
		helper.runAfterDelay(40L, () -> {
			TowerWisps.pinLightTicks(floor, null);
			int after = TowerWisps.trailsOn(floor).size();
			TowerTestSupport.result("wispFades", "lit=" + lit + " after=" + after);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(lit == 1 && after == 0, "a way lit for 20 ticks: lit=" + lit + ", still lit after 40: " + after);
			helper.succeed();
		});
	}

	/** Mobs standing on a lit way are enraged, faster and stronger; a mob away from it is not. */
	@GameTest(maxTicks = 100)
	public void mobsOnALitWayAreEnraged(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		ServerPlayer player = holder(helper, tower, floor);
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		TowerWisps.Trail trail = TowerWisps.trailsOn(floor).get(0);
		// A few steps along, by the player, where the room is surely loaded, but beyond the arrival's safe zone:
		// a tower mob put inside a zone is sent out of it within a sweep, and would leave the way.
		BlockPos o = TowerLayout.origin(floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos onWay = trail.path().get(trail.path().size() - 1);
		for (BlockPos step : trail.path()) {
			boolean clearOfZones = true;
			for (TowerMaze.Zone zone : plan.safeZones()) {
				int dx = Math.max(Math.max(zone.x() - (step.getX() - o.getX()), 0), (step.getX() - o.getX()) - (zone.x() + TowerMaze.SAFE - 1));
				int dz = Math.max(Math.max(zone.z() - (step.getZ() - o.getZ()), 0), (step.getZ() - o.getZ()) - (zone.z() + TowerMaze.SAFE - 1));
				clearOfZones &= Math.max(dx, dz) >= 2;
			}
			if (clearOfZones) {
				onWay = step;
				break;
			}
		}
		final BlockPos wayStep = onWay;
		// An open spot far from every step of the way.
		BlockPos away = null;
		for (int x = 1; x < S - 1 && away == null; x += 3) {
			for (int z = 1; z < S - 1 && away == null; z += 3) {
				BlockPos spot = o.offset(x, 0, z);
				boolean open = tower.getBlockState(spot).isAir() && tower.getBlockState(spot.above()).isAir();
				// Twice the reach from every step, so no rounding of a mob's place brings it within.
				double clear = 2.0 * TowerWisps.RAGE_REACH + 1.0;
				boolean far = trail.path().stream().allMatch(step -> step.distSqr(spot) > clear * clear);
				if (open && far) {
					away = spot;
				}
			}
		}
		helper.assertTrue(away != null, "no spot lies away from the way");
		TowerTestSupport.holdRoom(tower, Vec3.atCenterOf(away));
		Zombie on = zombieAt(tower, floor, wayStep);
		Zombie off = zombieAt(tower, floor, away);
		helper.runAfterDelay(30L, () -> {
			helper.assertTrue(tower.getEntity(on.getUUID()) == on && tower.getEntity(off.getUUID()) == off,
				"a case zombie is not in the world, so the case proves nothing");
			boolean onRaged = on.hasEffect(MobEffects.SPEED) && on.hasEffect(MobEffects.STRENGTH);
			boolean offRaged = off.hasEffect(MobEffects.SPEED) || off.hasEffect(MobEffects.STRENGTH);
			TowerTestSupport.result("wispRage", "onRaged=" + onRaged + " offRaged=" + offRaged);
			on.discard();
			off.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(onRaged && !offRaged, "on the way: enraged=" + onRaged + "; away: enraged=" + offRaged);
			helper.succeed();
		});
	}

	private static Zombie zombieAt(final ServerLevel tower, final int floor, final BlockPos at) {
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.WIND);
		zombie.snapTo(Vec3.atBottomCenterOf(at));
		zombie.setNoAi(true);
		tower.addFreshEntity(zombie);
		return zombie;
	}

	/** A wisp's uses are its own count, not wear: no crafting grid, grindstone or anvil mends them back. */
	@GameTest
	public void aWispsUsesCannotBeMended(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack a = new ItemStack(TowerItems.WISP);
		ItemStack b = new ItemStack(TowerItems.WISP);
		TowerWisps.setUses(a, 1);
		TowerWisps.setUses(b, 1);
		CraftingInput input = CraftingInput.of(2, 1, List.of(a, b));
		var match = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		ItemStack mended = match.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		boolean wears = a.isDamageableItem();
		TowerTestSupport.result("wispMend", "fresh=" + TowerWisps.usesOf(new ItemStack(TowerItems.WISP)) + " mended=" + mended + " wears=" + wears);
		helper.assertTrue(TowerWisps.usesOf(new ItemStack(TowerItems.WISP)) == TowerWisps.USES, "a fresh wisp has " + TowerWisps.usesOf(new ItemStack(TowerItems.WISP)) + " uses");
		helper.assertTrue(mended.isEmpty() && !wears, "two spent wisps mended into " + mended + "; a wisp wears=" + wears);
		helper.succeed();
	}

	/** A player keeps one lit way: a second use replaces the first, and a use within the wisp's rest spends nothing. */
	@GameTest
	public void aSecondUseReplacesTheWayAndRestsFirst(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = ordinaryFloor(tower);
		ServerPlayer player = holder(helper, tower, floor);
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		int restingSpent = TowerWisps.USES - TowerWisps.usesOf(player.getMainHandItem());
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
		player.getMainHandItem().use(tower, player, InteractionHand.MAIN_HAND);
		int spent = TowerWisps.USES - TowerWisps.usesOf(player.getMainHandItem());
		int trails = TowerWisps.trailsOn(floor).size();
		TowerTestSupport.result("wispReuse", "restingSpent=" + restingSpent + " spent=" + spent + " trails=" + trails);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(restingSpent == 1, "a use within the rest spent " + restingSpent + " in all");
		helper.assertTrue(spent == 2 && trails == 1, "two uses spent " + spent + " and left " + trails + " ways lit");
		helper.succeed();
	}

	/** Out of the tower a wisp has no portal to find, and spends nothing. */
	@GameTest
	public void aWispOutOfTheTowerSpendsNothing(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TowerItems.WISP));
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		int damage = TowerWisps.USES - TowerWisps.usesOf(player.getMainHandItem());
		TowerTestSupport.result("wispOutside", "damage=" + damage);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(damage == 0, "a wisp used out of the tower spent " + damage);
		helper.succeed();
	}
}
