package com.stella.end;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The castle skeleton archer: its level and kit, and the volley of burning arrows it looses at a target in sight. */
public class CastleArcherGameTest {
	@GameTest(environment = "mod-stella-end-test:archer_kit", maxTicks = 20)
	public void aSkeletonArcherIsLevelOneHundredTenInFullNetheriteWithABowAndNothingItWearsDrops(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 2;
		int[] cell = EndCastles.layout(end).floor(floor).spawnCells().get(0);
		BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
		Skeleton archer = (Skeleton) EndMobs.spawn(end, floor, EndMobs.Kind.ARCHER, at);
		helper.assertTrue(archer != null, "the archer was refused");
		StringBuilder bad = new StringBuilder();
		int level = LevelBridge.read(archer);
		if (level != EndLevels.ARCHER) {
			bad.append(" level ").append(level);
		}
		if (!archer.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET) || !archer.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
			|| !archer.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS) || !archer.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS)) {
			bad.append(" armor ").append(archer.getItemBySlot(EquipmentSlot.HEAD));
		}
		if (!archer.getMainHandItem().is(Items.BOW)) {
			bad.append(" main hand ").append(archer.getMainHandItem());
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (archer.getDropChances().byEquipment(slot) != 0.0F) {
				bad.append(" drops ").append(slot);
			}
		}
		boolean tagged = EndMobs.isArcher(archer) && EndMobs.floorOf(archer) == floor;
		EndTestSupport.result("archerKit", "level=" + level + " tagged=" + tagged + " bad=" + bad.length());
		EndTestSupport.fresh(helper);
		helper.assertTrue(bad.isEmpty(), "archer kit off the plan:" + bad);
		helper.assertTrue(tagged, "the archer is not tagged as an archer of floor " + floor);
		helper.succeed();
	}

	/** Whether a straight line from one point to another meets no block that stops a collider. */
	private static boolean sees(final ServerLevel end, final Vec3 from, final Vec3 to) {
		return end.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}

	@GameTest(environment = "mod-stella-end-test:archer_volley", maxTicks = 220)
	public void oneVolleyFiresTwelveBurningArrowsThreeTicksApart(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		Vec3 eye = player.getEyePosition();
		BlockPos at = null;
		for (int[] cell : plan.spawnCells()) {
			BlockPos candidate = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
			double distance = Math.hypot(candidate.getX() + 0.5 - player.getX(), candidate.getZ() + 0.5 - player.getZ());
			if (distance >= 10.0 && distance <= 16.0 && sees(end, eye, new Vec3(candidate.getX() + 0.5, candidate.getY() + 1.6, candidate.getZ() + 0.5))) {
				at = candidate;
				break;
			}
		}
		helper.assertTrue(at != null, "no spawn cell of floor " + floor + " sees the player from 10 to 16 blocks");
		Skeleton archer = (Skeleton) EndMobs.spawn(end, floor, EndMobs.Kind.ARCHER, at);
		ArcherVolleyGoal volley = CastleTeleportGameTest.goal(archer, ArcherVolleyGoal.class);
		helper.onEachTick(() -> archer.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!volley.volleys().isEmpty(), "no volley yet; sees=" + archer.hasLineOfSight(player) + " distance=" + archer.distanceTo(player)))
			.thenExecute(() -> {
				ArcherVolleyGoal.Volley first = volley.volleys().get(0);
				StringBuilder bad = new StringBuilder();
				for (int i = 1; i < first.ticks().size(); i++) {
					if (first.ticks().get(i) - first.ticks().get(i - 1) != ArcherVolleyGoal.SPACING) {
						bad.append(" gap ").append(first.ticks().get(i) - first.ticks().get(i - 1)).append(" before arrow ").append(i);
					}
				}
				long distinct = first.arrows().stream().distinct().count();
				EndTestSupport.result("archerVolley", "arrows=" + first.arrows().size() + " distinct=" + distinct + " burning=" + first.burning() + " ticks=" + first.ticks());
				EndTestSupport.fresh(helper);
				helper.assertTrue(first.arrows().size() == ArcherVolleyGoal.VOLLEY_SIZE, "the volley held " + first.arrows().size() + " arrows");
				helper.assertTrue(distinct == ArcherVolleyGoal.VOLLEY_SIZE, "the volley counted " + distinct + " different arrows");
				helper.assertTrue(first.burning() == ArcherVolleyGoal.VOLLEY_SIZE, "only " + first.burning() + " arrows burned");
				helper.assertTrue(bad.isEmpty(), "arrows not " + ArcherVolleyGoal.SPACING + " ticks apart:" + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:archer_rest", maxTicks = 300)
	public void aSecondVolleyWaitsForTheRestOfOneHundredTicksOnTheEndClock(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		Vec3 eye = player.getEyePosition();
		BlockPos at = null;
		for (int[] cell : plan.spawnCells()) {
			BlockPos candidate = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
			double distance = Math.hypot(candidate.getX() + 0.5 - player.getX(), candidate.getZ() + 0.5 - player.getZ());
			if (distance >= 10.0 && distance <= 16.0 && sees(end, eye, new Vec3(candidate.getX() + 0.5, candidate.getY() + 1.6, candidate.getZ() + 0.5))) {
				at = candidate;
				break;
			}
		}
		helper.assertTrue(at != null, "no spawn cell of floor " + floor + " sees the player from 10 to 16 blocks");
		Skeleton archer = (Skeleton) EndMobs.spawn(end, floor, EndMobs.Kind.ARCHER, at);
		ArcherVolleyGoal volley = CastleTeleportGameTest.goal(archer, ArcherVolleyGoal.class);
		helper.onEachTick(() -> archer.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(volley.volleys().size() == 1, "no first volley yet"))
			.thenIdle(ArcherVolleyGoal.COOLDOWN - 20)
			.thenExecute(() -> helper.assertTrue(volley.begun() == 1, "a second volley began inside the rest"))
			.thenExecute(() -> EndClock.advance(end, ArcherVolleyGoal.COOLDOWN))
			.thenWaitUntil(() -> helper.assertTrue(volley.begun() >= 2 && volley.volleys().size() >= 2, "no second volley after the rest"))
			.thenExecute(() -> {
				EndTestSupport.result("archerRest", "volleys=" + volley.volleys().size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(List.of(ArcherVolleyGoal.VOLLEY_SIZE).contains(volley.volleys().get(1).arrows().size()), "the second volley held "
					+ volley.volleys().get(1).arrows().size() + " arrows");
			})
			.thenSucceed();
	}
}
