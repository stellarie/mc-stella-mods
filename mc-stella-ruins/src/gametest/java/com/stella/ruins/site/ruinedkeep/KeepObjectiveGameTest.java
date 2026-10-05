package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.site.ruinedkeep.KeepPlan.Kind;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 3 to 7 and the staged roster: what spawns when, what drops, what opens, and what pays out. */
public class KeepObjectiveGameTest {
	static final int TICKS = 6000;

	static InteractionResult useCore(final KeepTestSupport.Arena arena, final ServerPlayer player, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos pos = arena.corePos();
		return arena.level().getBlockState(pos).useItemOn(stack, arena.level(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	/** What a floor's roster holds, from the plan: sentinels, knights, and its warden. */
	static int expected(final KeepPlan plan, final int floor) {
		return plan.markers(floor, Kind.SENTINEL).size() + plan.markers(floor, Kind.KNIGHT).size() + plan.markers(floor, Kind.WARDEN).size();
	}

	/** A spot on a floor for a player: a key hall's warden marker, or the antechamber on the throne floor. */
	static Vec3 standOn(final KeepTestSupport.Arena arena, final int floor) {
		if (floor == 3) {
			return arena.at(24, KeepPlan.STAND[3], 30);
		}
		KeepPlan.Marker warden = arena.plan().markers(floor, Kind.WARDEN).get(0);
		return arena.at(warden.x(), KeepPlan.STAND[floor], warden.z());
	}

	@GameTest(maxTicks = TICKS)
	public void activationSpawnsTheFirstFloorRosterAndItsWardenAndNothingAbove(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core did not wake for a player at the forecourt, phase " + arena.phase());
			int want = expected(arena.plan(), 0);
			int roster = arena.entry().roster().size();
			helper.assertTrue(roster == want, "the roster holds " + roster + " mobs, the plan's floor 1 holds " + want);
			helper.assertTrue(arena.roster(KeepMobs.warden).size() == 1, "floor 1 has " + arena.roster(KeepMobs.warden).size() + " wardens");
			helper.assertTrue(arena.roster(KeepMobs.sentinel).size() == arena.plan().markers(0, Kind.SENTINEL).size(), "the sentinel count is off");
			helper.assertTrue(arena.roster(KeepMobs.knight).size() == arena.plan().markers(0, Kind.KNIGHT).size(), "the knight count is off");
			double ceiling = arena.frame().min().getY() + KeepPlan.STAND[1] - 1;
			for (UUID id : arena.entry().roster()) {
				helper.assertTrue(arena.level().getEntity(id).getY() < ceiling, "a floor 1 mob stands above floor 1");
			}
			helper.assertTrue(arena.roster(KeepMobs.castellan).isEmpty(), "the Castellan spawned at activation");
			KeepTestSupport.result("activationFloorOne", "roster=" + roster + " expected=" + want + " wardens=1 phase=" + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aHigherFloorSpawnsWhenAPlayerFirstStandsOnItAndOnlyOnce(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			int total = expected(arena.plan(), 0);
			StringBuilder line = new StringBuilder("floor1=" + total);
			for (int floor = 1; floor <= 3; floor++) {
				int before = arena.entry().roster().size();
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before, "floor " + (floor + 1) + " spawned before anyone stood on it");
				arena.moveTo(player, standOn(arena, floor));
				arena.core().pulse(arena.level());
				int gained = arena.entry().roster().size() - before;
				int want = floor == 3 ? arena.plan().markers(3, Kind.SENTINEL).size() + arena.plan().markers(3, Kind.KNIGHT).size() : expected(arena.plan(), floor);
				helper.assertTrue(gained == want, "standing on floor " + (floor + 1) + " gained " + gained + " mobs, the plan holds " + want);
				helper.assertTrue(gained <= KeepPlan.FLOOR_CAP + 1, "floor " + (floor + 1) + " gained " + gained + ", over the cap of " + KeepPlan.FLOOR_CAP + " plus a warden");
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before + gained, "floor " + (floor + 1) + " spawned a second time");
				total += gained;
				line.append(" floor").append(floor + 1).append('=').append(gained);
			}
			helper.assertTrue(arena.roster(KeepMobs.warden).size() == 3, "the keep holds " + arena.roster(KeepMobs.warden).size() + " wardens, not 3");
			KeepTestSupport.result("stagedRoster", line + " total=" + total + " cap=" + KeepPlan.FLOOR_CAP);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachFloorWardenKilledByAPlayerDropsExactlyOneKeyFragment(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			StringBuilder line = new StringBuilder();
			for (int floor = 0; floor < 3; floor++) {
				if (floor > 0) {
					arena.moveTo(player, standOn(arena, floor));
					arena.core().pulse(arena.level());
				}
				arena.freeze();
				List<Mob> wardens = new ArrayList<>(arena.roster(KeepMobs.warden));
				int before = arena.itemsNear(KeepRewards.keyFragment);
				helper.assertTrue(!wardens.isEmpty(), "floor " + (floor + 1) + " has no warden to kill");
				Mob warden = wardens.get(wardens.size() - 1);
				arena.kill(player, warden);
				int gained = arena.itemsNear(KeepRewards.keyFragment) - before;
				helper.assertTrue(gained == 1, "the floor " + (floor + 1) + " warden dropped " + gained + " fragments");
				line.append(" floor").append(floor + 1).append("Fragments=").append(gained);
			}
			helper.assertTrue(arena.itemsNear(KeepRewards.keyFragment) == 3, "the keep dropped " + arena.itemsNear(KeepRewards.keyFragment) + " fragments in all");
			KeepTestSupport.result("wardenDrops", line.toString());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theThroneDoorStaysShutUntilThreeFragmentsAreUsedThenOpensAndTheCastellanSpawns(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			List<BlockPos> door = arena.markers(KeepLayout.M_THRONE_DOOR);
			helper.assertTrue(door.size() == KeepLayout.DOOR_W * KeepLayout.DOOR_H, "the door has " + door.size() + " blocks");
			for (BlockPos spot : door) {
				helper.assertTrue(arena.level().getBlockState(spot).is(KeepBlocks.THRONE_DOOR), "the throne door has a hole before any key");
			}
			ItemStack stick = new ItemStack(Items.GOLD_INGOT, 3);
			helper.assertTrue(!useCore(arena, player, stick).consumesAction() && stick.getCount() == 3, "a wrong item was spent on the core");
			ItemStack fragments = new ItemStack(KeepRewards.keyFragment, 5);
			for (int used = 1; used <= 2; used++) {
				useCore(arena, player, fragments);
				helper.assertTrue(fragments.getCount() == 5 - used, "use " + used + " spent " + (5 - fragments.getCount()) + " fragments");
				for (BlockPos spot : door) {
					helper.assertTrue(arena.level().getBlockState(spot).is(KeepBlocks.THRONE_DOOR), "the door opened after " + used + " fragments");
				}
				helper.assertTrue(arena.roster(KeepMobs.castellan).isEmpty(), "the Castellan spawned after " + used + " fragments");
			}
			useCore(arena, player, fragments);
			helper.assertTrue(fragments.getCount() == 2, "three uses left " + fragments.getCount() + " of 5 fragments");
			int open = 0;
			for (BlockPos spot : door) {
				open += arena.level().getBlockState(spot).isAir() ? 1 : 0;
			}
			helper.assertTrue(open == door.size(), "only " + open + " of " + door.size() + " door blocks opened");
			helper.assertTrue(arena.roster(KeepMobs.castellan).size() == 1, "the Castellan count is " + arena.roster(KeepMobs.castellan).size());
			Mob castellan = arena.roster(KeepMobs.castellan).get(0);
			BlockPos marker = arena.markers(KeepLayout.M_CASTELLAN).get(0);
			helper.assertTrue(castellan.blockPosition().distManhattan(marker) <= 1, "the Castellan stands at " + castellan.blockPosition() + ", not at " + marker);
			ItemStack late = new ItemStack(KeepRewards.keyFragment, 1);
			helper.assertTrue(!useCore(arena, player, late).consumesAction() && late.getCount() == 1, "a fourth fragment was spent");
			KeepTestSupport.result("throneDoor", "doorBlocks=" + door.size() + " opened=" + open + " castellans=1 fragmentsLeft=" + fragments.getCount());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void killingTheCastellanCompletesTheCoreAndTheVaultHoldsTheOathAndOneSigil(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			ItemStack fragments = new ItemStack(KeepRewards.keyFragment, 3);
			for (int i = 0; i < 3; i++) {
				useCore(arena, player, fragments);
			}
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.vault().sealed(), "the core completed before the Castellan died");
			arena.freeze();
			arena.kill(player, arena.roster(KeepMobs.castellan).get(0));
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the Castellan's death left the core " + arena.phase());
			helper.assertTrue(!arena.vault().sealed(), "the vault stayed sealed");
			int oath = 0;
			int sigil = 0;
			int crests = 0;
			for (int slot = 0; slot < arena.vault().getContainerSize(); slot++) {
				ItemStack stack = arena.vault().getItem(slot);
				oath += stack.is(KeepRewards.castellansOath) ? stack.getCount() : 0;
				sigil += stack.is(KeepRewards.sigil.template()) ? stack.getCount() : 0;
				crests += stack.is(KeepRewards.tarnishedCrest) ? stack.getCount() : 0;
			}
			helper.assertTrue(oath == 1, "the vault holds " + oath + " castellans_oath");
			helper.assertTrue(sigil == 1, "the vault holds " + sigil + " keep_sigil_affix");
			helper.assertTrue(crests >= 4 && crests <= 8, "the vault holds " + crests + " crests, the table gives 4 to 8");
			KeepTestSupport.result("castellanDeath", "phase=" + arena.phase() + " oath=" + oath + " sigil=" + sigil + " crests=" + crests + " sealed=" + arena.vault().sealed());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theCoreStaysCompleteAfterThirtyDays(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			AtomicLong clock = new AtomicLong(1_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				ItemStack fragments = new ItemStack(KeepRewards.keyFragment, 3);
				for (int i = 0; i < 3; i++) {
					useCore(arena, player, fragments);
				}
				arena.freeze();
				arena.kill(player, arena.roster(KeepMobs.castellan).get(0));
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core did not complete, phase " + arena.phase());
				clock.addAndGet(30L * 24_000L);
				arena.core().pulse(arena.level());
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "after 30 days the core is " + arena.phase());
				helper.assertTrue(!arena.vault().sealed(), "after 30 days the vault is sealed again");
				KeepTestSupport.result("thirtyDays", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theObjectiveSurvivesASaveAndLoadOfItsState(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			arena.moveTo(player, standOn(arena, 1));
			arena.core().pulse(arena.level());
			ItemStack fragments = new ItemStack(KeepRewards.keyFragment, 2);
			useCore(arena, player, fragments);
			useCore(arena, player, fragments);
			net.minecraft.nbt.CompoundTag saved = arena.entry().objective();
			KeepObjective loaded = new KeepObjective();
			loaded.load(saved);
			helper.assertTrue(loaded.save().equals(saved), "the objective changed through a save and load: " + saved + " vs " + loaded.save());
			helper.assertTrue(saved.getIntOr("used", -1) == 2 && (saved.getIntOr("spawned", 0) & 0b11) == 0b11, "the saved state is off: " + saved);
			int before = arena.entry().roster().size();
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.entry().roster().size() == before, "a pulse after the load spawned a floor again");
			helper.assertTrue(arena.level().getBlockState(arena.markers(KeepLayout.M_THRONE_DOOR).get(0)).is(KeepBlocks.THRONE_DOOR), "the door changed with two fragments");
			KeepTestSupport.result("objectiveRoundTrip", "state=" + saved);
		});
	}
}
