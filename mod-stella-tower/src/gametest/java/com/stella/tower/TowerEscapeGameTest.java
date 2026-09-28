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
		BlockState frame = tower.getBlockState(TowerLayout.portalFrame(floor).get(0));
		List<BlockState> states = List.of(
			TowerBlocks.TOWER_WALL.defaultBlockState(), TowerBlocks.TOWER_LAMP.defaultBlockState(),
			TowerBlocks.TOWER_PORTAL.defaultBlockState(), frame
		);
		long breakable = states.stream().filter(WitherBoss::canDestroy).count();
		TowerTestSupport.result("witherProof", "breakable=" + breakable + " frame=" + frame.getBlock());
		helper.assertTrue(breakable == 0, breakable + " tower blocks can be broken by a wither");
		helper.succeed();
	}

	@GameTest
	public void chorusFruitCannotLandOnTheRoof(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		Vec3 arrival = TowerLayout.arrival(floor);
		Pig pig = EntityTypes.PIG.create(tower, EntitySpawnReason.COMMAND);
		pig.snapTo(arrival);
		tower.addFreshEntity(pig);
		boolean roof = pig.randomTeleport(arrival.x + 2.0, TowerLayout.BASE_Y + TowerLayout.HEIGHT + 1.0, arrival.z, false,
			BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
		double yAfterRoof = pig.getY();
		boolean inside = pig.randomTeleport(arrival.x + 2.0, TowerLayout.BASE_Y + 3.0, arrival.z, false, BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
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
		player.teleport(new TeleportTransition(tower, TowerLayout.arrival(floor), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
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
		player.teleport(new TeleportTransition(tower, TowerLayout.arrival(floor), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		boolean given = player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY));
		TowerTestSupport.result("enderChestKey", "given=" + given);
		TowerTestSupport.leave(helper, player);
		helper.assertFalse(given, "a key stashed in the ender chest was given again");
		helper.succeed();
	}
}
