package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** What each kind of castle mob carries and does: kits, the spawn mix, the shield goal, the roster. */
public class CastleMobsGameTest {
	@GameTest(environment = "mod-stella-end-test:mobs_mix", maxTicks = 20)
	public void theSpawnMixGivesFloorsOneAndTwoNoShulkersAndTheRestFifteenPercent(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int[] counts = new int[EndMobs.Kind.values().length];
			for (int roll = 0; roll < EndMobs.ROLLS; roll++) {
				counts[EndMobs.pick(floor, roll).ordinal()]++;
			}
			int skeletons = counts[EndMobs.Kind.WITHER_SKELETON.ordinal()];
			int archers = counts[EndMobs.Kind.ARCHER.ordinal()];
			int shulkers = counts[EndMobs.Kind.SHULKER.ordinal()];
			boolean low = floor <= 2;
			if (skeletons != (low ? 750 : 600) || archers != 250 || shulkers != (low ? 0 : 150)) {
				bad.append(" floor ").append(floor).append(": ").append(skeletons).append('/').append(archers).append('/').append(shulkers);
			}
			if (EndMobs.share(floor, EndMobs.Kind.SHULKER) != shulkers) {
				bad.append(" floor ").append(floor).append(" share disagrees with the roll");
			}
		}
		EndTestSupport.result("mobsMix", "floorsChecked=" + EndCastle.FLOORS + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "spawn mix off the plan (skeleton/archer/shulker per 1000):" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:mobs_shulkers_low", maxTicks = FloorSpawnerGameTest.FILL_TICKS + 100)
	public void floorsOneAndTwoNeverHoldAShulkerInPlay(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 1);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 2).size() == FloorSpawner.CAP, "floor 2 is not full yet"))
			.thenExecute(() -> {
				int shulkers = 0;
				int skeletons = 0;
				for (int floor = 1; floor <= 2; floor++) {
					for (Mob mob : FloorSpawner.hostiles(end, floor)) {
						shulkers += mob.getType() == EntityTypes.SHULKER ? 1 : 0;
						skeletons += mob.getType() == EntityTypes.WITHER_SKELETON ? 1 : 0;
					}
				}
				EndTestSupport.result("mobsShulkersLow", "shulkers=" + shulkers + " witherSkeletons=" + skeletons);
				EndTestSupport.fresh(helper);
				helper.assertTrue(shulkers == 0, shulkers + " shulkers spawned on floors 1 and 2");
				helper.assertTrue(skeletons > 0, "no wither skeleton spawned at all");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mobs_kit", maxTicks = FloorSpawnerGameTest.FILL_TICKS + 100)
	public void witherSkeletonsSpawnWithASwordAShieldAndNetheriteArmor(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 2);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 2).size() == FloorSpawner.CAP, "floor 2 is not full yet"))
			.thenExecute(() -> {
				int skeletons = 0;
				StringBuilder bad = new StringBuilder();
				for (Mob mob : FloorSpawner.hostiles(end, 2)) {
					if (!(mob instanceof WitherSkeleton)) {
						continue;
					}
					skeletons++;
					if (!mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_SWORD)) {
						bad.append(" main hand ").append(mob.getItemBySlot(EquipmentSlot.MAINHAND));
					}
					if (!mob.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD) || !mob.getItemBySlot(EquipmentSlot.OFFHAND).has(DataComponents.BLOCKS_ATTACKS)) {
						bad.append(" off hand ").append(mob.getItemBySlot(EquipmentSlot.OFFHAND));
					}
					if (!mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET) || !mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
						|| !mob.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS) || !mob.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS)) {
						bad.append(" armor ").append(mob.getItemBySlot(EquipmentSlot.HEAD));
					}
				}
				EndTestSupport.result("mobsKit", "witherSkeletons=" + skeletons + " bad=" + bad.length());
				EndTestSupport.fresh(helper);
				helper.assertTrue(skeletons >= 8, "only " + skeletons + " wither skeletons to inspect");
				helper.assertTrue(bad.isEmpty(), "kits off the plan:" + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mobs_shield_goal", maxTicks = 200)
	public void theLocalShieldGoalRaisesTheShieldWhenATargetClosesIn(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(2);
		BlockPos at = EndTestSupport.world(EndCastle.min(), plan.landingX(), EndCastle.standY(2), plan.landingZ());
		BlockPos near = EndTestSupport.cellNear(helper, plan, 2, plan.landingX(), plan.landingZ(), 4.0, 6.0);
		ServerPlayer player = EndTestSupport.join(helper, end, Vec3.atBottomCenterOf(near));
		WitherSkeleton guard = EntityTypes.WITHER_SKELETON.create(end, net.minecraft.world.entity.EntitySpawnReason.SPAWNER);
		// Another mod's guard goal would answer too; a mob it leaves alone shows this goal's own effect.
		guard.addTag("mod-stella-mobs.no_behavior");
		guard.addTag(LevelBridge.NO_ROLL_TAG);
		guard.addTag(EndMobs.FLOOR_TAG + "0");
		guard.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.NETHERITE_SWORD));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(Items.SHIELD));
		guard.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.0F, 0.0F);
		guard.getGoalSelector().addGoal(1, new CastleShieldGoal(guard));
		guard.setTarget(player);
		guard.setPersistenceRequired();
		helper.assertTrue(end.addFreshEntity(guard), "the guard was refused");
		int[] raisedFor = new int[1];
		helper.onEachTick(() -> {
			if (guard.isUsingItem() && guard.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
				raisedFor[0]++;
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(raisedFor[0] >= CastleShieldGoal.HOLD_TICKS / 2, "the shield has been up for " + raisedFor[0] + " ticks; target "
				+ guard.getTarget() + " distance " + guard.distanceTo(player) + " alive=" + guard.isAlive() + " offhand=" + guard.getOffhandItem()))
			.thenExecute(() -> {
				EndTestSupport.result("mobsShieldGoal", "raisedTicks=" + raisedFor[0]);
				guard.discard();
				EndTestSupport.fresh(helper);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mobs_natural", maxTicks = 100)
	public void vanillaNaturalSpawningIsRefusedInsideTheCastleVolume(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 1);
		// The floor of the rest room: solid slab below, air above, and far from the player.
		BlockPos hollow = EndTestSupport.world(EndCastle.min(), 14, EndCastle.REST_Y + 1, 10);
		boolean hollowOpen = end.getBlockState(hollow).isAir() && end.getBlockState(hollow.above()).isAir() && end.getBlockState(hollow.below()).isSolid();
		helper.assertTrue(hollowOpen, "the test spot is not an open dark floor: " + end.getBlockState(hollow) + " over " + end.getBlockState(hollow.below()));
		java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
		for (net.minecraft.world.entity.Entity entity : EndTestSupport.entities(end)) {
			before.add(entity.getUUID());
		}
		int inside = 0;
		for (int attempt = 0; attempt < 300; attempt++) {
			NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, end, hollow);
		}
		for (net.minecraft.world.entity.Entity entity : EndTestSupport.entities(end)) {
			inside += entity instanceof Mob && !before.contains(entity.getUUID()) && EndCastle.volumeContains(entity.blockPosition()) ? 1 : 0;
		}
		EndTestSupport.result("mobsNatural", "naturalMobsInsideTheCastle=" + inside);
		EndTestSupport.fresh(helper);
		helper.assertTrue(inside == 0, inside + " mobs spawned naturally inside the castle volume");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:mobs_roster", maxTicks = 100)
	public void aCastleMobIsRecordedByFloorAndNeverDespawns(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int[] cell = EndCastles.layout(end).floor(4).spawnCells().get(0);
		BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(4), cell[1]);
		Mob mob = EndMobs.spawn(end, 4, EndMobs.Kind.WITHER_SKELETON, at, false);
		helper.assertTrue(mob != null, "the skeleton was refused");
		List<Mob> recorded = new ArrayList<>(FloorSpawner.hostiles(end, 4));
		boolean persistent = mob.isPersistenceRequired();
		boolean tagged = EndMobs.floorOf(mob) == 4;
		EndTestSupport.result("mobsRoster", "recorded=" + recorded.size() + " persistent=" + persistent + " floor=" + EndMobs.floorOf(mob));
		EndTestSupport.fresh(helper);
		helper.assertTrue(recorded.contains(mob), "the spawner did not record the mob it added");
		helper.assertTrue(persistent, "the mob can despawn");
		helper.assertTrue(tagged, "the mob carries the wrong floor");
		helper.succeed();
	}
}
