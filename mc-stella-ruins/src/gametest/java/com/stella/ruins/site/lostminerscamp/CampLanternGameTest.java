package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/** The lantern rite: the camp starts dark, a dark lantern calls help and bars the journal, and a torch lights it. */
public class CampLanternGameTest {
	static final long SEED = 0x10C0FFEEL;

	static boolean lit(final MinerTestSupport.Arena arena, final BlockPos pos) {
		return arena.level().getBlockState(pos).is(Blocks.CAMPFIRE) && arena.level().getBlockState(pos).getValue(CampfireBlock.LIT);
	}

	static int compasses(final ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			count += stack.is(CampItems.MINERS_COMPASS) ? stack.getCount() : 0;
		}
		return count;
	}

	static void light(final MinerTestSupport.Arena arena, final BlockPos pos) {
		arena.level().setBlock(pos, arena.level().getBlockState(pos).setValue(CampfireBlock.LIT, true), 3);
	}

	/** Lights every lantern of the camp, as a player with a torch would. */
	static void lightAll(final MinerTestSupport.Arena arena) {
		for (BlockPos pos : arena.markers(CampMarkers.LANTERN)) {
			light(arena, pos);
		}
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void theCampStartsWithThreeDarkLanterns(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			List<BlockPos> lanterns = arena.markers(CampMarkers.LANTERN);
			helper.assertTrue(lanterns.size() == 3, "the camp has " + lanterns.size() + " lantern markers, not 3");
			for (BlockPos pos : lanterns) {
				helper.assertTrue(arena.level().getBlockState(pos).is(Blocks.CAMPFIRE) && !lit(arena, pos), "the lantern at " + pos.toShortString() + " is " + arena.level().getBlockState(pos));
			}
			helper.assertTrue(lanterns.stream().distinct().count() == 3, "two lanterns share a spot");
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aDarkLanternBarsTheJournalUntilEveryLanternIsLit(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			MinerTestSupport.Joined joined = arena.wake();
			arena.freeze();
			ServerPlayer player = joined.player();
			arena.killRoster();
			arena.core().pulse(arena.level());
			List<BlockPos> lanterns = arena.markers(CampMarkers.LANTERN);
			helper.assertTrue(arena.entry().roster().isEmpty(), "a mob still lives, so the dark check proves nothing");
			JournalObjective.Outcome dark = JournalObjective.read(arena.level(), arena.lecternPos(), player);
			helper.assertTrue(dark == JournalObjective.Outcome.GUARDED && !arena.objective().hasRead() && compasses(player) == 0, "a read in the dark gave " + dark);
			light(arena, lanterns.get(0));
			light(arena, lanterns.get(1));
			helper.assertTrue(JournalObjective.read(arena.level(), arena.lecternPos(), player) == JournalObjective.Outcome.GUARDED, "two lit lanterns of three allowed the read");
			light(arena, lanterns.get(2));
			JournalObjective.Outcome relit = JournalObjective.read(arena.level(), arena.lecternPos(), player);
			MinerTestSupport.result("lanternRead", "dark=" + dark + " relit=" + relit + " compasses=" + compasses(player));
			helper.assertTrue(relit == JournalObjective.Outcome.READ && arena.objective().hasRead() && compasses(player) == 1, "relit lanterns gave " + relit);
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aTorchLightsALanternOfTheCampAndNothingElse(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake().player();
			BlockPos lantern = arena.markers(CampMarkers.LANTERN).get(0);
			helper.assertTrue(!JournalObjective.light(arena.level(), lantern, player, new ItemStack(Items.STICK)) && !lit(arena, lantern), "a stick lit the lantern");
			helper.assertTrue(JournalObjective.light(arena.level(), lantern, player, new ItemStack(Items.TORCH)) && lit(arena, lantern), "a torch did not light the lantern");
			BlockPos fire = arena.markers(CampMarkers.FIRE).get(0);
			helper.assertTrue(!JournalObjective.light(arena.level(), fire, player, new ItemStack(Items.TORCH)), "a torch was taken by the camp fire, which is no lantern");
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aDarkLanternCallsOneMoreMobEveryFourHundredTicksAndALitCampCallsNone(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			AtomicLong clock = new AtomicLong(40_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				arena.wake();
				arena.freeze();
				int start = arena.entry().roster().size();
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == start, "a mob came at once");
				clock.addAndGet(JournalObjective.DARK_SPAWN_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == start, "a mob came one tick early");
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				int dark = arena.entry().roster().size();
				for (BlockPos pos : arena.markers(CampMarkers.LANTERN)) {
					light(arena, pos);
				}
				clock.addAndGet(JournalObjective.DARK_SPAWN_TICKS * 3);
				arena.core().pulse(arena.level());
				int lit = arena.entry().roster().size();
				MinerTestSupport.result("darkCalls", "start=" + start + " afterDark=" + dark + " afterLit=" + lit + " phase=" + arena.phase());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core is " + arena.phase());
				helper.assertTrue(dark == start + 1, "a dark camp held " + dark + " mobs, not " + (start + 1));
				helper.assertTrue(lit == dark, "a lit camp called " + (lit - dark) + " more mobs");
			} finally {
				RuinClock.replace(old);
			}
		});
	}
}
