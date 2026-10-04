package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Acceptance items 4 to 9 and the sketch review's roster, lever and vent rules. */
public class DeepForgeObjectiveGameTest {
	static final int TICKS = 12000;
	static final long SEED = 0xF0E6E5E7L;
	static final long DAY = 24_000L;

	static int dropped(final ServerLevel level, final AABB area, final Item item) {
		int total = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	static int count(final com.stella.ruins.world.RuinVaultBlockEntity vault, final Item item) {
		int total = 0;
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			if (vault.getItem(slot).is(item)) {
				total += vault.getItem(slot).getCount();
			}
		}
		return total;
	}

	static void setCores(final ForgeTestSupport.Arena arena, final ServerPlayer player, final int cores) {
		ItemStack stack = new ItemStack(DeepForgeItems.FORGE_CORE, cores);
		for (int i = 0; i < cores; i++) {
			ForgeTestSupport.use(arena.level(), player, arena.corePos(), stack);
		}
	}

	static boolean leversUnlocked(final ForgeTestSupport.Arena arena) {
		for (BlockPos at : arena.markers("lever")) {
			if (!arena.level().getBlockState(at).getValue(ForgeLeverBlock.UNLOCKED)) {
				return false;
			}
		}
		return true;
	}

	static void pull(final ForgeTestSupport.Arena arena, final ServerPlayer player, final BlockPos at) {
		arena.level().getBlockState(at).useWithoutItem(arena.level(), player, ForgeTestSupport.hit(at));
	}

	static int pulled(final ForgeTestSupport.Arena arena) {
		int total = 0;
		for (BlockPos at : arena.markers("lever")) {
			total += arena.level().getBlockState(at).getValue(ForgeLeverBlock.POWERED) ? 1 : 0;
		}
		return total;
	}

	/** Wakes the forge, freezes the roster, sets three cores and pulls three levers. The guardian then stands. */
	static Mob guardianAfterLevers(final GameTestHelper helper, final ForgeTestSupport.Arena arena, final ServerPlayer player) {
		arena.freeze();
		setCores(arena, player, ForgeObjective.CORES);
		for (BlockPos at : arena.markers("lever")) {
			pull(arena, player, at);
		}
		arena.core().pulse(arena.level());
		List<Mob> found = arena.roster(DeepForgeMobs.guardian);
		helper.assertTrue(found.size() == 1, "the guardian count after three levers is " + found.size());
		found.get(0).setNoAi(true);
		return found.get(0);
	}

	@GameTest(maxTicks = TICKS)
	public void activationSpawnsThreeForgeKeepersAtTheHoldersAnImpPerChamberAndTwoGolems(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core did not wake");
			List<Mob> keepers = arena.roster(DeepForgeMobs.keeper);
			List<Mob> imps = arena.roster(DeepForgeMobs.imp);
			List<Mob> golems = arena.roster(DeepForgeMobs.golem);
			helper.assertTrue(keepers.size() == 3, keepers.size() + " Forge Keepers, not 3");
			helper.assertTrue(imps.size() == arena.markers("imp").size(), imps.size() + " Cinder Imps for " + arena.markers("imp").size() + " chambers");
			helper.assertTrue(golems.size() == 2, golems.size() + " Deep Golems, not 2");
			Set<BlockPos> used = new HashSet<>();
			for (Mob keeper : keepers) {
				BlockPos nearest = null;
				for (BlockPos holder : arena.markers("holder")) {
					if (keeper.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(holder)) < 0.5) {
						nearest = holder;
					}
				}
				helper.assertTrue(nearest != null && used.add(nearest), "a Forge Keeper does not stand on its own core-holder marker");
			}
			int total = arena.entry().roster().size();
			ForgeTestSupport.result("activationRoster", "keepers=" + keepers.size() + " imps=" + imps.size() + " golems=" + golems.size() + " total=" + total);
			helper.assertTrue(total == keepers.size() + imps.size() + golems.size(), "the roster holds " + total + " mobs, which is not the three kinds only");
			helper.assertTrue(total <= 12, "the roster holds " + total + " mobs, over the sketch review's 12");
			for (UUID id : arena.entry().roster()) {
				Mob mob = (Mob) arena.level().getEntity(id);
				helper.assertTrue(mob.isPersistenceRequired(), "a roster mob can despawn");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void everyForgeKeeperKilledByAPlayerDropsOneForgeCoreAndOtherDeathsDropNone(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 1, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			List<Mob> keepers = arena.roster(DeepForgeMobs.keeper);
			helper.assertTrue(keepers.size() == 3, "expected three keepers");
			ServerLevel level = arena.level();
			AABB near = arena.area();
			arena.kill(keepers.get(0), player);
			int one = dropped(level, near, DeepForgeItems.FORGE_CORE);
			helper.assertTrue(one == 1, "the first kill dropped " + one + " Forge Cores, not 1");
			keepers.get(1).hurtServer(level, level.damageSources().generic(), 10_000.0F);
			helper.assertTrue(dropped(level, near, DeepForgeItems.FORGE_CORE) == 1, "a keeper killed by something other than a player dropped a core");
			arena.kill(keepers.get(2), player);
			int two = dropped(level, near, DeepForgeItems.FORGE_CORE);
			ForgeTestSupport.result("keeperDrops", "afterPlayerKill=" + one + " afterEnvKill=" + one + " afterSecondPlayerKill=" + two);
			helper.assertTrue(two == 2, "two player kills dropped " + two + " Forge Cores, not 2");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void threeForgeCoresAdvanceTheObjectiveAndTwoDoNot(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 2, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			helper.assertTrue(ForgeObjective.stepOf(arena.entry().objective()) == ForgeObjective.STEP_CORES, "the objective did not begin with the cores");
			ItemStack cores = new ItemStack(DeepForgeItems.FORGE_CORE, 5);
			ItemStack wrong = new ItemStack(Items.DIAMOND, 2);
			helper.assertTrue(!ForgeTestSupport.use(arena.level(), player, arena.corePos(), wrong).consumesAction() && wrong.getCount() == 2, "a diamond counted as a core");
			ForgeTestSupport.use(arena.level(), player, arena.corePos(), cores);
			ForgeTestSupport.use(arena.level(), player, arena.corePos(), cores);
			helper.assertTrue(cores.getCount() == 3, "two uses left " + cores.getCount() + " cores of 5");
			helper.assertTrue(ForgeObjective.stepOf(arena.entry().objective()) == ForgeObjective.STEP_CORES, "two cores advanced the objective");
			helper.assertTrue(!leversUnlocked(arena), "two cores unlocked the levers");
			ForgeTestSupport.use(arena.level(), player, arena.corePos(), cores);
			ForgeTestSupport.result("threeCores", "left=" + cores.getCount() + " step=" + ForgeObjective.stepOf(arena.entry().objective()) + " unlocked=" + leversUnlocked(arena));
			helper.assertTrue(cores.getCount() == 2, "the third use left " + cores.getCount() + " cores");
			helper.assertTrue(ForgeObjective.stepOf(arena.entry().objective()) == ForgeObjective.STEP_LEVERS, "three cores did not advance the objective");
			helper.assertTrue(leversUnlocked(arena), "three cores did not unlock the levers");
			helper.assertTrue(!ForgeTestSupport.use(arena.level(), player, arena.corePos(), cores).consumesAction() && cores.getCount() == 2, "a fourth core was spent");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aLeverWillNotMoveBeforeTheCoresAreSetAndStaysPulledAfterwards(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 3, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			BlockPos first = arena.markers("lever").get(0);
			pull(arena, player, first);
			helper.assertTrue(!arena.level().getBlockState(first).getValue(ForgeLeverBlock.POWERED), "a lever moved before the cores were set");
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.roster(DeepForgeMobs.guardian).isEmpty(), "a guardian stands before the levers");
			setCores(arena, player, ForgeObjective.CORES);
			pull(arena, player, first);
			helper.assertTrue(arena.level().getBlockState(first).getValue(ForgeLeverBlock.POWERED), "an unlocked lever did not move");
			pull(arena, player, first);
			helper.assertTrue(arena.level().getBlockState(first).getValue(ForgeLeverBlock.POWERED), "a pulled lever moved back");
			ForgeTestSupport.result("leverStiff", "levers=" + arena.markers("lever").size() + " pulled=" + pulled(arena));
		});
	}

	@GameTest(maxTicks = TICKS)
	public void pullingAllThreeLeversSpawnsTheGuardianAndKillingItCompletesTheCoreAndLightsTheForge(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 4, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			setCores(arena, player, ForgeObjective.CORES);
			List<BlockPos> levers = arena.markers("lever");
			helper.assertTrue(levers.size() == 3, levers.size() + " levers");
			pull(arena, player, levers.get(0));
			pull(arena, player, levers.get(1));
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.roster(DeepForgeMobs.guardian).isEmpty(), "two levers summoned the guardian");
			pull(arena, player, levers.get(2));
			arena.core().pulse(arena.level());
			List<Mob> guardians = arena.roster(DeepForgeMobs.guardian);
			helper.assertTrue(guardians.size() == 1, guardians.size() + " guardians after the third lever");
			Mob guardian = guardians.get(0);
			guardian.setNoAi(true);
			BlockPos stand = arena.markers("guardian").get(0);
			helper.assertTrue(guardian.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(stand)) < 0.5, "the guardian is not on its marker");
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.vault().sealed(), "the core finished before the guardian fell");
			BlockPos forge = arena.markers("forge").get(0);
			helper.assertTrue(!arena.level().getBlockState(forge).getValue(DeepForgeBlock.LIT), "the forge was lit before the guardian fell");
			for (BlockPos gate : arena.markers("vault_gate")) {
				helper.assertTrue(arena.level().getBlockState(gate).is(Blocks.IRON_BARS), "a vault gate cell opened early");
			}
			arena.kill(guardian, player);
			ForgeTestSupport.result("guardianFalls", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed() + " lit=" + arena.level().getBlockState(forge).getValue(DeepForgeBlock.LIT)
				+ " ingots=" + count(arena.vault(), DeepForgeItems.INGOT) + " chunks=" + count(arena.vault(), DeepForgeItems.ORE_CHUNK));
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase() + " after the guardian fell");
			helper.assertTrue(!arena.vault().sealed(), "the vault is still sealed");
			helper.assertTrue(count(arena.vault(), DeepForgeItems.INGOT) >= 4 && count(arena.vault(), DeepForgeItems.ORE_CHUNK) >= 6, "the vault holds too little Deepsteel");
			helper.assertTrue(arena.level().getBlockState(forge).getValue(DeepForgeBlock.LIT), "the forge is not lit");
			for (BlockPos gate : arena.markers("vault_gate")) {
				helper.assertTrue(arena.level().getBlockState(gate).isAir(), "a vault gate cell is still shut");
			}
			helper.assertTrue(dropped(arena.level(), arena.area(), DeepForgeItems.INGOT) >= 4 && dropped(arena.level(), arena.area(), DeepForgeItems.FORGEHEART_SHARD) == 1, "the guardian's own drops are wrong");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aCompletedForgeStaysCompleteAfterThirtyDays(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 5, arena -> {
			ServerPlayer player = arena.wake();
			Mob guardian = guardianAfterLevers(helper, arena, player);
			arena.kill(guardian, player);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the forge did not complete");
			long base = RuinClock.now(arena.level());
			RuinClock.Source old = RuinClock.replace(level -> base + 30 * DAY);
			try {
				arena.core().pulse(arena.level());
				arena.core().pulse(arena.level());
			} finally {
				RuinClock.replace(old);
			}
			ForgeTestSupport.result("staysComplete", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase() + " after 30 days");
			helper.assertTrue(!arena.vault().sealed(), "the vault shut again after 30 days");
			helper.assertTrue(arena.level().getBlockState(arena.markers("forge").get(0)).getValue(DeepForgeBlock.LIT), "the forge went out");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachLavaVentReleasesACinderImpEveryFourHundredTicksUpToFourAlive(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 6, arena -> {
			AtomicLong clock = new AtomicLong(1_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				arena.wake();
				arena.freeze();
				int vents = arena.markers("imp_vent").size();
				helper.assertTrue(vents >= 1, "the forge has no lava vent");
				int base = arena.roster(DeepForgeMobs.imp).size();
				clock.addAndGet(ForgeObjective.VENT_PERIOD_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.roster(DeepForgeMobs.imp).size() == base, "a vent released an imp after 399 ticks");
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				int afterFirst = arena.roster(DeepForgeMobs.imp).size() - base;
				helper.assertTrue(afterFirst == Math.min(vents, ForgeObjective.VENT_CAP), "the first period released " + afterFirst + " imps from " + vents + " vents");
				for (int period = 0; period < 6; period++) {
					clock.addAndGet(ForgeObjective.VENT_PERIOD_TICKS);
					arena.core().pulse(arena.level());
				}
				int vented = arena.roster(DeepForgeMobs.imp).size() - base;
				helper.assertTrue(vented == ForgeObjective.VENT_CAP, vented + " vent imps alive after many periods, not the cap of " + ForgeObjective.VENT_CAP);
				List<Mob> alive = arena.roster(DeepForgeMobs.imp);
				Mob newest = alive.get(alive.size() - 1);
				newest.hurtServer(arena.level(), arena.level().damageSources().generic(), 10_000.0F);
				clock.addAndGet(ForgeObjective.VENT_PERIOD_TICKS);
				arena.core().pulse(arena.level());
				int refilled = arena.roster(DeepForgeMobs.imp).size() - base;
				ForgeTestSupport.result("ventImps", "vents=" + vents + " first=" + afterFirst + " capped=" + vented + " refilled=" + refilled);
				helper.assertTrue(refilled == ForgeObjective.VENT_CAP, "after one died the vents held " + refilled + " imps, not " + ForgeObjective.VENT_CAP);
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theSavedObjectiveRoundTripsItsStepAndItsVentImps(final GameTestHelper helper) {
		ForgeObjective objective = new ForgeObjective();
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		tag.putInt("step", ForgeObjective.STEP_GUARDIAN);
		tag.putInt("used", 3);
		tag.putLong("vent_last", 777L);
		tag.putString("vented", a + "," + b);
		tag.putString("guardian", a.toString());
		objective.load(tag);
		net.minecraft.nbt.CompoundTag back = objective.save();
		helper.assertTrue(back.equals(tag), "the saved tag changed: " + back + " against " + tag);
		ForgeObjective empty = new ForgeObjective();
		empty.load(new net.minecraft.nbt.CompoundTag());
		helper.assertTrue(ForgeObjective.stepOf(empty.save()) == ForgeObjective.STEP_CORES, "an empty tag does not load as the first step");
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void aForgeCompleteAfterReloadStillFindsItsMarkersFromADryRun(final GameTestHelper helper) {
		ForgeTestSupport.inArena(helper, SEED + 7, arena -> {
			ServerPlayer player = arena.wake();
			Mob guardian = guardianAfterLevers(helper, arena, player);
			List<BlockPos> before = new ArrayList<>(arena.core().markers().get("lever"));
			arena.kill(guardian, player);
			helper.assertTrue(before.size() == 3 && arena.core().markers().get("lever").equals(before), "the core lost its lever markers");
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the forge did not complete");
		});
	}

	@GameTest
	public void everyKindCarriesItsOwnId(final GameTestHelper helper) {
		for (RuinMobKind kind : new RuinMobKind[] {DeepForgeMobs.keeper, DeepForgeMobs.imp, DeepForgeMobs.golem, DeepForgeMobs.guardian}) {
			helper.assertTrue(RuinMobKind.get(kind.id()) == kind, kind.id() + " is not registered");
		}
		helper.assertTrue(DeepForgeMobs.guardian.boss() && !DeepForgeMobs.keeper.boss(), "boss flags are wrong");
		BlockState cold = DeepForgeBlocks.FORGE.defaultBlockState();
		helper.assertTrue(!cold.getValue(DeepForgeBlock.LIT), "the forge block starts lit");
		helper.succeed();
	}
}
