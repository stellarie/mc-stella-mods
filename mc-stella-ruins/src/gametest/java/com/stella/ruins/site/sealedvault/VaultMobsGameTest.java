package com.stella.ruins.site.sealedvault;

import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Items 5, 6 and 12 and the hunt the review asked for: the three kinds, the Warden, and what its coming feels like. */
public class VaultMobsGameTest {
	static final long SEED = 0x57A7EL;
	static final int ROLLS = 4000;

	private static Vec3 spot(final BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static void tick(final Mob warden, final ServerLevel level) {
		VaultMobs.VAULT_WARDEN.ability().tick(warden, level);
	}

	@GameTest(maxTicks = 6000)
	public void theThreeKindsHaveTheirBaseScaleHealthAndGear(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			Mob wraith = VaultMobs.VOID_WRAITH.spawn(arena.level(), spot(arena.markers(VaultMarkers.WRAITH).get(0)));
			Mob guardian = VaultMobs.VAULT_GUARDIAN.spawn(arena.level(), spot(arena.markers(VaultMarkers.GUARDIAN).get(0)));
			Mob warden = VaultMobs.VAULT_WARDEN.spawn(arena.level(), spot(arena.markers(VaultMarkers.WARDEN).get(0)));
			for (Mob mob : new Mob[] {wraith, guardian, warden}) {
				mob.setNoAi(true);
			}
			helper.assertTrue(wraith.getType() == EntityTypes.VEX && guardian.getType() == EntityTypes.HUSK && warden.getType() == EntityTypes.WITHER_SKELETON, "a base type is wrong");
			double vex = EntityTypes.VEX.create(arena.level(), EntitySpawnReason.COMMAND).getMaxHealth();
			double husk = EntityTypes.HUSK.create(arena.level(), EntitySpawnReason.COMMAND).getMaxHealth();
			double skeleton = EntityTypes.WITHER_SKELETON.create(arena.level(), EntitySpawnReason.COMMAND).getMaxHealth();
			helper.assertTrue(Math.abs(wraith.getMaxHealth() - vex * 3.0) < 1.0E-6, "wraith health " + wraith.getMaxHealth());
			helper.assertTrue(Math.abs(guardian.getMaxHealth() - husk * 3.0) < 1.0E-6, "guardian health " + guardian.getMaxHealth());
			helper.assertTrue(Math.abs(warden.getMaxHealth() - skeleton * 10.0) < 1.0E-6, "warden health " + warden.getMaxHealth());
			helper.assertTrue(Math.abs(guardian.getAttributeValue(Attributes.SCALE) - 1.2) < 1.0E-6 && Math.abs(warden.getAttributeValue(Attributes.SCALE) - 1.8) < 1.0E-6, "a scale is wrong");
			helper.assertTrue(guardian.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET), "the guardian has no netherite helmet");
			helper.assertTrue(VaultMobs.VAULT_WARDEN.boss() && !VaultMobs.VOID_WRAITH.boss() && !VaultMobs.VAULT_GUARDIAN.boss(), "the boss flag is wrong");
			helper.assertTrue(RuinMobs.kindOf(warden) == VaultMobs.VAULT_WARDEN && warden.entityTags().contains(RuinMobKind.TAG_MOB), "the warden is not a tagged ruin mob");
			VaultTestSupport.result("mobKits", "wraith=" + wraith.getMaxHealth() + " guardian=" + guardian.getMaxHealth() + " warden=" + warden.getMaxHealth() + " wardenScale=" + warden.getAttributeValue(Attributes.SCALE));
			wraith.discard();
			guardian.discard();
			warden.discard();
		});
	}

	@GameTest(maxTicks = 6000)
	public void wakingTheVaultSpawnsSixWraithsEightGuardiansAndTheWardenAtTheirMarkers(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			helper.assertTrue(arena.roster("void_wraith").size() == 6, "wraiths " + arena.roster("void_wraith").size());
			helper.assertTrue(arena.roster("vault_guardian").size() == 8, "guardians " + arena.roster("vault_guardian").size());
			helper.assertTrue(arena.roster("vault_warden").size() == 1, "wardens " + arena.roster("vault_warden").size());
			helper.assertTrue(arena.entry().roster().size() == 15, "the roster holds " + arena.entry().roster().size());
			Mob warden = arena.warden();
			BlockPos lair = arena.markers(VaultMarkers.WARDEN).get(0);
			helper.assertTrue(warden.blockPosition().distManhattan(lair) <= 1, "the warden stands at " + warden.blockPosition() + ", not at the lair " + lair);
			for (Mob guardian : arena.roster("vault_guardian")) {
				boolean atMarker = arena.markers(VaultMarkers.GUARDIAN).stream().anyMatch(marker -> guardian.blockPosition().distManhattan(marker) <= 1);
				helper.assertTrue(atMarker, "a guardian stands away from every guardian marker at " + guardian.blockPosition());
			}
			for (Mob wraith : arena.roster("void_wraith")) {
				boolean atMarker = arena.markers(VaultMarkers.WRAITH).stream().anyMatch(marker -> wraith.blockPosition().distManhattan(marker) <= 1);
				helper.assertTrue(atMarker, "a wraith stands away from every wraith marker at " + wraith.blockPosition());
			}
			VaultTestSupport.result("roster", "wraiths=6 guardians=8 warden=1 lair=" + lair.toShortString() + " wardenAt=" + warden.blockPosition().toShortString());
		});
	}

	/** Level 1 spots the Warden cannot see from its lair, nearest first. */
	private static List<Vec3> hiddenSpots(final VaultTestSupport.Arena arena, final Mob warden) {
		List<Vec3> found = new ArrayList<>();
		ServerPlayer probe = arena.join(Vec3.ZERO);
		for (int c = 0; c < VaultPlan.CELLS; c++) {
			if (c == VaultPlan.CENTER_CELL || arena.plan().isStair(c)) {
				continue;
			}
			BlockPos at = arena.local(VaultPlan.cellMinX(c % VaultPlan.GRID) + 4, 9, VaultPlan.cellMinZ(c / VaultPlan.GRID) + 4);
			probe.teleportTo(arena.level(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
			if (!warden.hasLineOfSight(probe)) {
				found.add(probe.position());
			}
		}
		arena.leave(probe);
		found.sort((a, b) -> Double.compare(a.distanceTo(warden.position()), b.distanceTo(warden.position())));
		return found;
	}

	@GameTest(maxTicks = 6000)
	public void theWardenTracksTheNearestPlayerThroughWallsAndSetsOffToward(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer first = arena.wake();
			Mob warden = arena.warden();
			List<Vec3> hidden = hiddenSpots(arena, warden);
			helper.assertTrue(hidden.size() >= 10, "only " + hidden.size() + " hidden spots on level 1");
			Vec3 nearSpot = hidden.get(0);
			Vec3 farSpot = hidden.get(hidden.size() - 1);
			ServerPlayer far = first;
			far.teleportTo(arena.level(), farSpot.x, farSpot.y, farSpot.z, java.util.Set.of(), 0.0F, 0.0F, true);
			ServerPlayer nearer = arena.join(nearSpot);
			double toFar = far.position().distanceTo(warden.position());
			double toNear = nearer.position().distanceTo(warden.position());
			helper.assertTrue(!warden.hasLineOfSight(far) && !warden.hasLineOfSight(nearer), "a player is in sight of the warden");
			helper.assertTrue(toNear + 5.0 < toFar, "the players are not clearly near and far: " + toNear + " " + toFar);
			tick(warden, arena.level());
			VaultTestSupport.result("wardenTrack", "target=" + (warden.getTarget() == null ? "none" : warden.getTarget().getName().getString()) + " toFar=" + toFar + " toNear=" + toNear
				+ " pathing=" + warden.getNavigation().isInProgress());
			helper.assertTrue(warden.getTarget() == nearer, "the warden targets " + warden.getTarget() + ", not the nearest player");
		});
	}

	@GameTest(maxTicks = 6000)
	public void theWardenIgnoresAPlayerOutsideTheBounds(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			Mob warden = arena.warden();
			BlockPos corner = arena.frame().min();
			ServerPlayer outside = arena.join(new Vec3(corner.getX() - 30.5, corner.getY() + 9, corner.getZ() - 30.5));
			ServerPlayer inside = arena.players().get(1);
			helper.assertTrue(outside.position().distanceTo(warden.position()) < inside.position().distanceTo(warden.position()) + 200.0, "the outside player is absurdly far");
			warden.setTarget(null);
			tick(warden, arena.level());
			helper.assertTrue(warden.getTarget() == inside, "the warden targets " + warden.getTarget() + " instead of the player inside");
			arena.leave(inside);
			warden.setTarget(null);
			tick(warden, arena.level());
			helper.assertTrue(warden.getTarget() == null, "the warden hunts a player outside the bounds");
		});
	}

	@GameTest(maxTicks = 6000)
	public void theWardenHealsTwoHealthASecondWhileNoPlayerIsWithinSixteenBlocks(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			Mob warden = arena.warden();
			warden.setHealth(warden.getMaxHealth() - 100.0F);
			double gap = player.position().distanceTo(warden.position());
			helper.assertTrue(gap > VaultMobs.REGEN_QUIET_RANGE, "the player is within " + VaultMobs.REGEN_QUIET_RANGE + " of the warden: " + gap);
			float before = warden.getHealth();
			for (int i = 0; i < 4; i++) {
				tick(warden, arena.level());
			}
			float healed = warden.getHealth() - before;
			VaultTestSupport.result("wardenRegen", "gap=" + gap + " healedInFourTicks=" + healed + " (one second)");
			helper.assertTrue(Math.abs(healed - VaultMobs.WARDEN_REGEN_PER_SECOND) < 1.0E-4F, "the warden healed " + healed + " in one second");
			player.teleportTo(arena.level(), warden.getX() + 6.0, warden.getY(), warden.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
			before = warden.getHealth();
			for (int i = 0; i < 4; i++) {
				tick(warden, arena.level());
			}
			helper.assertTrue(warden.getHealth() == before, "the warden healed with a player 6 blocks away");
		});
	}

	@GameTest(maxTicks = 6000)
	public void theWardenAndTheWraithsCannotLeaveTheBounds(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			Mob warden = arena.warden();
			Mob wraith = arena.roster("void_wraith").get(0);
			BoundingBox box = arena.frame().box();
			for (Mob mob : new Mob[] {warden, wraith}) {
				mob.snapTo(box.minX() - 40.5, box.maxY() + 12.0, box.maxZ() + 40.5, 0.0F, 0.0F);
				helper.assertTrue(!box.isInside(mob.blockPosition()), "the test did not put the mob outside");
			}
			tick(warden, arena.level());
			RuinMobKind kind = RuinMobs.kindOf(wraith);
			kind.ability().tick(wraith, arena.level());
			VaultTestSupport.result("bounds", "warden=" + warden.blockPosition().toShortString() + " wraith=" + wraith.blockPosition().toShortString() + " box=" + box);
			helper.assertTrue(box.isInside(warden.blockPosition()), "the warden is outside the bounds at " + warden.blockPosition());
			helper.assertTrue(box.isInside(wraith.blockPosition()), "the wraith is outside the bounds at " + wraith.blockPosition());
		});
	}

	@GameTest(maxTicks = 6000)
	public void withinSixteenBlocksThePlayerHearsAHeartbeatEveryFortyTicks(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			Mob warden = arena.warden();
			AtomicInteger beats = new AtomicInteger();
			List<Double> distances = new ArrayList<>();
			VaultMobs.setHeartbeat((heard, distance) -> {
				if (heard == player) {
					beats.incrementAndGet();
					distances.add(distance);
				}
			});
			try {
				player.teleportTo(arena.level(), warden.getX() + 12.0, warden.getY(), warden.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
				int fired = 0;
				for (int tick = 0; tick < 120; tick += 5) {
					warden.tickCount = tick;
					int before = beats.get();
					tick(warden, arena.level());
					fired += beats.get() - before;
				}
				VaultTestSupport.result("heartbeat", "beatsIn120Ticks=" + fired + " distances=" + distances);
				helper.assertTrue(fired == 3, "the player heard " + fired + " heartbeats in 120 ticks, not 3");
				player.teleportTo(arena.level(), warden.getX() + 20.0, warden.getY(), warden.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
				beats.set(0);
				for (int tick = 0; tick < 120; tick += 5) {
					warden.tickCount = tick;
					tick(warden, arena.level());
				}
				helper.assertTrue(beats.get() == 0, "the player heard " + beats.get() + " heartbeats from 20 blocks");
			} finally {
				VaultMobs.setHeartbeat(null);
			}
		});
	}

	@GameTest(maxTicks = 6000)
	public void withinEightBlocksThePlayerGetsDarknessForFortyTicks(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			Mob warden = arena.warden();
			player.teleportTo(arena.level(), warden.getX() + 10.0, warden.getY(), warden.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
			tick(warden, arena.level());
			helper.assertTrue(!player.hasEffect(MobEffects.DARKNESS), "a player 10 blocks away has Darkness");
			player.teleportTo(arena.level(), warden.getX() + 6.0, warden.getY(), warden.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
			tick(warden, arena.level());
			MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
			VaultTestSupport.result("darkness", "effect=" + (darkness == null ? "none" : darkness.getDuration() + "/" + darkness.getAmplifier()));
			helper.assertTrue(darkness != null && darkness.getDuration() > 0 && darkness.getDuration() <= VaultMobs.DARKNESS_TICKS, "darkness is " + darkness);
		});
	}

	@GameTest
	public void aWraithsHitGivesDarknessForSixtyTicks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cow cow = VaultTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		Mob wraith = VaultMobs.VOID_WRAITH.spawn(level, helper.absoluteVec(new Vec3(5.5, 2.0, 5.5)));
		wraith.setNoAi(true);
		helper.assertTrue(cow.getEffect(MobEffects.DARKNESS) == null, "the cow had Darkness before the hit");
		if (FabricLoader.getInstance().isModLoaded("mod-stella-mobs")) {
			VaultMobs.VOID_WRAITH.ability().onHurtTarget(wraith, cow);
		} else {
			cow.hurtServer(level, level.damageSources().mobAttack(wraith), 1.0F);
		}
		MobEffectInstance darkness = cow.getEffect(MobEffects.DARKNESS);
		VaultTestSupport.result("wraithDarkness", "effect=" + (darkness == null ? "none" : darkness.getDuration() + "/" + darkness.getAmplifier()));
		helper.assertTrue(darkness != null && darkness.getAmplifier() == 0 && darkness.getDuration() > 0 && darkness.getDuration() <= VaultMobs.WRAITH_DARKNESS_TICKS, "darkness is " + darkness);
		wraith.discard();
		cow.discard();
		helper.succeed();
	}

	private static List<ItemStack> roll(final ServerLevel level, final net.minecraft.resources.ResourceKey<LootTable> key, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Mob mob = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(LootContextParamSets.ENTITY);
		return table.getRandomItems(params, seed);
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261005L);
		int wraith = 0;
		int guardian = 0;
		boolean one = false;
		boolean two = false;
		int[] wardenCounts = new int[6];
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, VaultMobs.WRAITH_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(VaultItems.VOID_ESSENCE) && stack.getCount() == 1, "the wraith dropped " + stack);
				wraith++;
			}
			for (ItemStack stack : roll(level, VaultMobs.GUARDIAN_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(VaultItems.VAULT_DUST) && stack.getCount() >= 1 && stack.getCount() <= 2, "the guardian dropped " + stack);
				guardian++;
				one |= stack.getCount() == 1;
				two |= stack.getCount() == 2;
			}
			List<ItemStack> boss = roll(level, VaultMobs.WARDEN_DROPS, master.nextLong());
			helper.assertTrue(boss.size() == 1 && boss.get(0).is(VaultItems.VOID_ESSENCE) && boss.get(0).getCount() >= 3 && boss.get(0).getCount() <= 5, "the warden dropped " + boss);
			wardenCounts[boss.get(0).getCount()]++;
		}
		double wraithRate = wraith / (double) ROLLS;
		double guardianRate = guardian / (double) ROLLS;
		VaultTestSupport.result("mobDrops", "rolls=" + ROLLS + " wraithRate=" + wraithRate + " guardianRate=" + guardianRate + " wardenCounts=" + java.util.Arrays.toString(wardenCounts));
		helper.assertTrue(Math.abs(wraithRate - 0.40) <= 0.40 * 0.08, "wraith rate " + wraithRate);
		helper.assertTrue(Math.abs(guardianRate - 0.60) <= 0.60 * 0.08, "guardian rate " + guardianRate);
		helper.assertTrue(one && two && wardenCounts[3] > 0 && wardenCounts[4] > 0 && wardenCounts[5] > 0, "the drop counts did not cover their ranges");
		helper.succeed();
	}

	@GameTest
	public void theGuideRoutesFromCellToCellThroughTheStairsAndMapsWorldSpotsBackToCells(final GameTestHelper helper) {
		for (int n = 0; n < 40; n++) {
			long seed = VaultPlanGameTest.seedOf(n);
			Rotation rotation = Rotation.values()[n % 4];
			VaultPlan plan = VaultTestSupport.plan(seed);
			com.stella.ruins.world.RuinFrame frame = VaultTestSupport.frame(seed, rotation);
			VaultPlan.Cell lair = new VaultPlan.Cell(0, plan.lair());
			VaultPlan.Cell core = new VaultPlan.Cell(1, VaultPlan.CENTER_CELL);
			List<VaultPlan.Cell> route = VaultGuide.route(plan, lair, core);
			helper.assertTrue(route != null && route.get(0).equals(lair) && route.get(route.size() - 1).equals(core), "seed " + n + " has no route from the lair to the core");
			for (int k = 1; k < route.size(); k++) {
				helper.assertTrue(plan.neighbors(route.get(k - 1)).contains(route.get(k)), "seed " + n + " route step " + k + " is not a neighbour step");
			}
			helper.assertTrue(route.stream().anyMatch(cell -> cell.level() == 1) && route.stream().anyMatch(cell -> plan.isStair(cell.cell())), "seed " + n + " route does not climb a stair");
			helper.assertTrue(VaultGuide.route(plan, lair, new VaultPlan.Cell(0, VaultPlan.CENTER_CELL)).get(route.size() - 1).equals(core), "a target in the inner vault is not met at the core room");
			for (int c = 0; c < VaultPlan.CELLS; c += 5) {
				for (int level = 0; level < 2; level++) {
					int[] local = {VaultPlan.cellMinX(c % VaultPlan.GRID) + 2, VaultPlan.stand(level), VaultPlan.cellMinZ(c / VaultPlan.GRID) + 3};
					BlockPos world = frame.toWorld(local[0], local[1], local[2]);
					int[] back = VaultGuide.toLocal(frame, world);
					helper.assertTrue(java.util.Arrays.equals(back, local), "seed " + n + " " + rotation + " maps " + java.util.Arrays.toString(local) + " back to " + java.util.Arrays.toString(back));
					helper.assertTrue(VaultGuide.cellOf(back).equals(new VaultPlan.Cell(level, c)), "seed " + n + " cell " + c + " level " + level + " reads as " + VaultGuide.cellOf(back));
				}
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 2600)
	public void theWardenWalksTheMazeTowardAPlayerItCannotSee(final GameTestHelper helper) {
		VaultTestSupport.Arena arena = VaultTestSupport.arena(helper.getLevel(), Rotation.NONE, SEED);
		long begun = System.nanoTime();
		Mob[] warden = new Mob[1];
		int[] start = new int[1];
		int[] best = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> VaultTestSupport.awaitReady(helper, arena, begun))
			.thenExecute(() -> {
				ServerPlayer player = arena.wake();
				player.getAbilities().invulnerable = true;
				warden[0] = arena.warden();
				start[0] = stepsToPlayer(arena, warden[0]);
				best[0] = start[0];
				helper.assertTrue(start[0] > 20, "the lair is only " + start[0] + " cells from the core");
			})
			.thenWaitUntil(() -> {
				best[0] = Math.min(best[0], stepsToPlayer(arena, warden[0]));
				helper.assertTrue(best[0] <= start[0] - 4, "the warden is still " + best[0] + " cells away, from " + start[0]);
			})
			.thenExecute(() -> {
				VaultTestSupport.result("wardenWalk", "startCells=" + start[0] + " nearestCells=" + best[0] + " at=" + warden[0].blockPosition().toShortString());
				arena.release();
			})
			.thenSucceed();
	}

	private static int stepsToPlayer(final VaultTestSupport.Arena arena, final Mob warden) {
		ServerPlayer player = arena.players().get(1);
		VaultPlan.Cell from = VaultGuide.cellOf(VaultGuide.toLocal(arena.frame(), warden.blockPosition()));
		VaultPlan.Cell to = VaultGuide.cellOf(VaultGuide.toLocal(arena.frame(), player.blockPosition()));
		List<VaultPlan.Cell> route = VaultGuide.route(arena.plan(), from, to);
		return route == null ? Integer.MAX_VALUE : route.size() - 1;
	}
}
