package com.stella.tower;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The crying obsidian portal: it lights blue, it leaves obsidian to vanilla, and it leads up the tower. */
public class TowerPortalGameTest {
	@GameTest
	public void aCryingObsidianFrameLitWithFireBecomesATowerPortal(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		int portals = 0;
		for (int x = 2; x <= 3; x++) {
			for (int y = 2; y <= 4; y++) {
				BlockState state = helper.getBlockState(new BlockPos(x, y, 2));
				if (state.is(TowerBlocks.TOWER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.X) {
					portals++;
				}
			}
		}
		TowerTestSupport.result("towerPortalLights", "portals=" + portals);
		helper.assertTrue(portals == 6, "the lit frame holds " + portals + " of 6 tower portal blocks");
		helper.succeed();
	}

	@GameTest
	public void anObsidianFrameStillMakesANetherPortal(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		BlockState state = helper.getBlockState(inside);
		TowerTestSupport.result("obsidianStaysVanilla", "block=" + state.getBlock());
		helper.assertTrue(state.is(Blocks.NETHER_PORTAL), "an obsidian frame lit to " + state.getBlock());
		helper.succeed();
	}

	@GameTest
	public void aFrameWithAGapLightsNothing(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(4, 3, 2, Blocks.AIR);
		helper.setBlock(inside, Blocks.FIRE);
		BlockState state = helper.getBlockState(inside);
		TowerTestSupport.result("gapLightsNothing", "block=" + state.getBlock());
		helper.assertFalse(state.is(TowerBlocks.TOWER_PORTAL), "a frame with a gap lit a portal");
		helper.succeed();
	}

	@GameTest
	public void breakingTheFrameClosesThePortal(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		helper.assertTrue(helper.getBlockState(inside).is(TowerBlocks.TOWER_PORTAL), "no portal lit, so the case proves nothing");
		helper.setBlock(1, 3, 2, Blocks.AIR);
		int left = 0;
		for (int x = 2; x <= 3; x++) {
			for (int y = 2; y <= 4; y++) {
				left += helper.getBlockState(new BlockPos(x, y, 2)).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
			}
		}
		TowerTestSupport.result("brokenFrameCloses", "left=" + left);
		helper.assertTrue(left == 0, left + " portal blocks outlived their broken frame");
		helper.succeed();
	}

	/** A pig that walks into a portal outside the tower arrives on the first floor. */
	@GameTest(maxTicks = 100)
	public void aPigInTheTowerPortalArrivesOnFloorOne(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		// Floor 1 outlives a run, so its record is cleared: the pig's trip must build it again.
		tower.setBlock(TowerFloors.marker(1), Blocks.AIR.defaultBlockState(), 2);
		Pig pig = helper.spawnMob(EntityTypes.PIG, new Vec3(2.5, 2.0, 2.5)).spawn();
		// A traveller is re-created on the far side; its tags travel with it, so the tag finds this pig and no other.
		String tag = "stella_tower_test_" + UUID.randomUUID();
		pig.addTag(tag);
		Vec3 spot = TowerLayout.arrival(tower, 1);
		helper.succeedWhen(() -> {
			var found = tower.getEntitiesOfClass(Pig.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(4.0), candidate -> candidate.entityTags().contains(tag));
			var arrived = found.isEmpty() ? null : found.get(0);
			helper.assertTrue(arrived != null, "the pig has not reached the tower: removed=" + pig.getRemovalReason() + " ticks=" + pig.tickCount
				+ " at=" + pig.position() + " block=" + pig.level().getBlockState(pig.blockPosition()).getBlock() + " cooldown=" + pig.getPortalCooldown() + " anywhere=" + where(helper, tag));
			double off = arrived.position().distanceTo(TowerLayout.arrival(tower, 1));
			helper.assertTrue(off < 2.0, "the pig arrived " + off + " from floor 1's arrival spot");
			helper.assertTrue(TowerFloors.isBuilt(tower, 1), "floor 1 was never built");
			TowerTestSupport.result("pigEntersTower", "floor=" + TowerLayout.floorAt(arrived.blockPosition()) + " off=" + off);
			arrived.discard();
		});
	}

	private static String where(final GameTestHelper helper, final String tag) {
		StringBuilder out = new StringBuilder();
		for (ServerLevel level : helper.getLevel().getServer().getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity.entityTags().contains(tag)) {
					out.append(level.dimension().identifier()).append('@').append(entity.position()).append(' ');
				}
			}
		}
		return out.toString();
	}

	/** Inside the tower, a floor's own portal leads to the floor above. */
	@GameTest
	public void aFloorsPortalLeadsToTheFloorAbove(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos inside = TowerLayout.portalInside(tower, floor).get(0);
		tower.setBlock(inside, Blocks.FIRE.defaultBlockState(), 3);
		int lit = 0;
		for (BlockPos pos : TowerLayout.portalInside(tower, floor)) {
			lit += tower.getBlockState(pos).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
		}
		// Only players climb, so a player stands in the portal.
		var climber = TowerTestSupport.join(helper, 1.5, 1.5);
		climber.teleportTo(tower, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		TeleportTransition next = TowerBlocks.TOWER_PORTAL.getPortalDestination(tower, climber, inside);
		TowerTestSupport.leave(helper, climber);
		double off = next == null ? -1.0 : next.position().distanceTo(TowerLayout.arrival(tower, floor + 1));
		TowerTestSupport.result("floorPortalLeadsUp", "lit=" + lit + " off=" + off + " builtAbove=" + TowerFloors.isBuilt(tower, floor + 1));
		helper.assertTrue(lit == TowerLayout.portalInside(tower, floor).size(), "floor " + floor + "'s frame lit " + lit + " portal blocks");
		helper.assertTrue(next != null && off < 0.01 && next.newLevel() == tower, "floor " + floor + "'s portal leads " + off + " from floor " + (floor + 1));
		helper.assertTrue(TowerFloors.isBuilt(tower, floor + 1), "the floor above was never built");
		helper.succeed();
	}

	/**
	 * Not required: a test player has no live connection, so the server never ticks it and it never counts
	 * down a portal. Manual item: walk into a lit crying obsidian portal in survival and arrive on floor 1.
	 */
	@GameTest(maxTicks = 200, required = false)
	public void aPlayerWalkingIntoThePortalArrivesRequiresManualTest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		var player = TowerTestSupport.join(helper, 2.5, 2.5);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		// The case fails every run, so the player leaves before the time runs out rather than on success.
		helper.runAfterDelay(199L, () -> TowerTestSupport.leave(helper, player));
		helper.succeedWhen(() -> {
			helper.assertTrue(player.level() == tower, "the player is still outside the tower");
			TowerTestSupport.result("playerWalksIn", "arrived=true");
		});
	}
}
