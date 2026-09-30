package com.stella.tower;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** No way out of a floor but the ways the tower gives, and no way in that loses the way back. */
public class TowerEscapeGameTest {
	@GameTest
	public void aWitherCannotBreakAnyTowerBlock(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockState frame = tower.getBlockState(TowerLayout.portalFrame(tower, floor).get(0));
		List<BlockState> states = List.of(
			TowerBlocks.TOWER_WALL.defaultBlockState(), TowerBlocks.TOWER_LAMP.defaultBlockState(),
			TowerBlocks.TOWER_PORTAL.defaultBlockState(), frame
		);
		long breakable = states.stream().filter(WitherBoss::canDestroy).count();
		TowerTestSupport.result("witherProof", "breakable=" + breakable + " frame=" + frame.getBlock());
		helper.assertTrue(breakable == 0, breakable + " tower blocks can be broken by a wither");
		helper.succeed();
	}

	/** No block the tower is built of is one an enderman may hold. */
	@GameTest
	public void noTowerBlockIsOneAnEndermanMayHold(final GameTestHelper helper) {
		java.util.List<BlockState> built = new java.util.ArrayList<>(List.of(
			TowerBlocks.TOWER_WALL.defaultBlockState(), TowerBlocks.TOWER_LAMP.defaultBlockState(), TowerBlocks.TOWER_MAZE_WALL.defaultBlockState(),
			TowerBlocks.TOWER_FRAME.defaultBlockState(), TowerFloors.SAFE_FLOOR.defaultBlockState(), Blocks.BEDROCK.defaultBlockState()));
		TowerBlocks.TRAPS.values().forEach(trap -> built.add(trap.defaultBlockState()));
		for (int floor : new int[] {1, 21, 31, 41}) {
			built.addAll(java.util.Arrays.asList(TowerFloors.palette(floor)));
		}
		List<String> holdable = built.stream().filter(state -> state.is(BlockTags.ENDERMAN_HOLDABLE)).map(state -> state.getBlock().toString()).toList();
		TowerTestSupport.result("endermanHoldable", "checked=" + built.size() + " holdable=" + holdable);
		helper.assertTrue(holdable.isEmpty(), "endermen may hold " + holdable);
		helper.succeed();
	}

	/**
	 * In the tower an enderman never sets out to take a block, so no wall or floor can be lifted even if a
	 * data pack tags one holdable. Out of it the same enderman does.
	 */
	@GameTest
	public void anEndermanTakesNothingInTheTower(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerLevel world = helper.getLevel();
		int inTower = takingTries(tower, TowerLayout.arrival(tower, TowerTestSupport.freshFloor()));
		int outside = takingTries(world, helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
		TowerTestSupport.result("endermanTakes", "inTower=" + inTower + " outside=" + outside);
		helper.assertTrue(outside > 0, "an enderman outside the tower never sets out to take a block, so the case proves nothing");
		helper.assertTrue(inTower == 0, "an enderman in the tower set out to take a block " + inTower + " times");
		helper.succeed();
	}

	/** How often an enderman standing here sets out to take a block, over many tries. */
	private static int takingTries(final ServerLevel level, final Vec3 at) {
		net.minecraft.world.entity.monster.Enderman enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
		enderman.snapTo(at);
		level.addFreshEntity(enderman);
		net.minecraft.world.entity.ai.goal.GoalSelector selector;
		try {
			java.lang.reflect.Field field = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
			field.setAccessible(true);
			selector = (net.minecraft.world.entity.ai.goal.GoalSelector) field.get(enderman);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		net.minecraft.world.entity.ai.goal.Goal take = selector.getAvailableGoals().stream()
			.map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
			.filter(goal -> goal.getClass().getName().endsWith("EndermanTakeBlockGoal")).findFirst().orElseThrow();
		int tries = 0;
		for (int i = 0; i < 4000; i++) {
			tries += take.canUse() ? 1 : 0;
		}
		enderman.discard();
		return tries;
	}

	@GameTest
	public void chorusFruitCannotLandOnTheRoof(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		Pig pig = EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND);
		pig.snapTo(arrival);
		tower.addFreshEntity(pig);
		boolean roof = pig.randomTeleport(arrival.x + 2.0, TowerLayout.BASE_Y + TowerLayout.HEIGHT + 1.0, arrival.z, false,
			BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
		double yAfterRoof = pig.getY();
		// Within the arrival's own maze cell.
		boolean inside = pig.randomTeleport(arrival.x + 1.0, TowerLayout.BASE_Y + 3.0, arrival.z, false, BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
		boolean stillInRoom = pig.getY() < TowerLayout.BASE_Y + TowerLayout.HEIGHT && TowerLayout.floorAt(pig.blockPosition()) == floor;
		TowerTestSupport.result("chorusRoof", "roof=" + roof + " yAfterRoof=" + yAfterRoof + " inside=" + inside + " stillInRoom=" + stillInRoom);
		pig.discard();
		helper.assertFalse(roof, "a random teleport landed the pig at y " + yAfterRoof + ", above the room");
		helper.assertTrue(inside && stillInRoom, "a random teleport inside the room was refused: inside=" + inside);
		helper.succeed();
	}

	/** An ender pearl thrown into the portal carries its owner in; the way back is where they threw it. */
	@GameTest
	public void aPlayerCarriedInByAPearlKeepsTheirWayBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = TowerTestSupport.join(helper, 5.5, 5.5);
		player.removeAttached(TowerTravel.ENTRY);
		Vec3 thrownFrom = player.position();
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		player.teleport(new TeleportTransition(tower, TowerLayout.arrival(tower, floor), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		TowerTravel.Entry entry = player.getAttached(TowerTravel.ENTRY);
		boolean key = player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY));
		double off = entry == null ? -1.0 : entry.position().distanceTo(thrownFrom);
		TowerTestSupport.result("pearlEntry", "entry=" + (entry != null) + " off=" + off + " key=" + key);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(entry != null && entry.dimension().equals(helper.getLevel().dimension()) && off < 0.01,
			"a pearl's owner arrived with the way back " + (entry == null ? "missing" : off + " from where they stood"));
		helper.assertTrue(key, "a pearl's owner arrived with no Void Key");
		helper.succeed();
	}

	/** A player riding through the portal is remembered beside it, not inside it. */
	@GameTest(maxTicks = 100)
	public void aRiderIsRememberedBesideThePortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		player.removeAttached(TowerTravel.ENTRY);
		Pig pig = helper.spawnMob(EntityTypes.PIG, new Vec3(2.5, 2.0, 2.5)).spawn();
		player.startRiding(pig, true, false);
		helper.succeedWhen(() -> {
			helper.assertTrue(player.level() == tower, "the rider has not reached the tower");
			TowerTravel.Entry entry = player.getAttached(TowerTravel.ENTRY);
			helper.assertTrue(entry != null, "the rider arrived with no way back");
			BlockPos back = BlockPos.containing(entry.position());
			boolean clear = !helper.getLevel().getBlockState(back).is(TowerBlocks.TOWER_PORTAL);
			double off = entry.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(inside)));
			TowerTestSupport.result("riderEntry", "clear=" + clear + " off=" + off);
			helper.assertTrue(clear && off < 3.0, "the rider's way back is " + off + " from the portal, clear=" + clear);
			TowerTestSupport.leave(helper, player);
			if (player.getVehicle() != null) {
				player.getVehicle().discard();
			}
		});
	}

	@GameTest
	public void aKeyInTheEnderChestIsNotGivenAgain(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = TowerTestSupport.join(helper, 5.5, 5.5);
		player.getEnderChestInventory().addItem(new ItemStack(TowerItems.VOID_KEY));
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		player.teleport(new TeleportTransition(tower, TowerLayout.arrival(tower, floor), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		boolean given = player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY));
		TowerTestSupport.result("enderChestKey", "given=" + given);
		TowerTestSupport.leave(helper, player);
		helper.assertFalse(given, "a key stashed in the ender chest was given again");
		helper.succeed();
	}
}
