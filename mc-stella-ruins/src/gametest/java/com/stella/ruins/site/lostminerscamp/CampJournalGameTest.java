package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 4, 5 and 7: the journal gate, the payout and the once claim. */
public class CampJournalGameTest {
	static final long SEED = 0x10C0FFEEL;

	private static int compasses(final ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(CampItems.MINERS_COMPASS)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Brings the camp to one living roster mob: four miners and six rats die. */
	private static void killAllButOneMiner(final MinerTestSupport.Arena arena) {
		ServerLevel level = arena.level();
		boolean kept = false;
		for (java.util.UUID id : arena.entry().roster()) {
			if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob mob) {
				if (!kept && com.stella.ruins.world.RuinMobs.kindOf(mob) == CampMobs.RESTLESS_MINER) {
					kept = true;
					continue;
				}
				mob.hurtServer(level, level.damageSources().genericKill(), 1.0E9F);
			}
		}
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void theJournalIsGuardedWhileARestlessMinerLives(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			MinerTestSupport.Joined joined = arena.wake();
			arena.freeze();
			ServerPlayer player = joined.player();
			BlockPos lectern = arena.lecternPos();
			killAllButOneMiner(arena);
			helper.assertTrue(arena.entry().roster().size() == 1, "the camp holds " + arena.entry().roster().size() + " roster mobs");
			JournalObjective.Outcome outcome = JournalObjective.read(arena.level(), lectern, player);
			MinerTestSupport.result("journalGuarded", "outcome=" + outcome + " phase=" + arena.phase() + " live=" + arena.entry().roster().size());
			helper.assertTrue(outcome == JournalObjective.Outcome.GUARDED, "the journal gave " + outcome + " with a miner alive");
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core is " + arena.phase() + " with a miner alive");
			helper.assertTrue(arena.vault().sealed(), "the cache opened with a miner alive");
			helper.assertTrue(!arena.objective().hasRead(), "the read counted with a miner alive");
			helper.assertTrue(compasses(player) == 0, "the journal paid out with a miner alive");
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aQuietReadCompletesTheCoreOpensTheCacheAndGivesTheCompass(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			MinerTestSupport.Joined joined = arena.wake();
			arena.freeze();
			ServerPlayer player = joined.player();
			BlockPos lectern = arena.lecternPos();
			int killed = arena.killRoster();
			arena.core().pulse(arena.level());
			CampLanternGameTest.lightAll(arena);
			JournalObjective.Outcome outcome = JournalObjective.read(arena.level(), lectern, player);
			helper.assertTrue(outcome == JournalObjective.Outcome.READ, "the quiet read gave " + outcome);
			helper.assertTrue(arena.objective().hasRead(), "the quiet read did not count");
			arena.core().pulse(arena.level());
			boolean sealed = arena.vault().sealed();
			MinerTestSupport.result("journalRead", "killed=" + killed + " outcome=" + outcome + " phase=" + arena.phase()
				+ " sealed=" + sealed + " compasses=" + compasses(player));
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase() + " after the read");
			helper.assertTrue(!sealed, "the cache stayed shut after the read");
			helper.assertTrue(compasses(player) == 1, "the reader holds " + compasses(player) + " compasses");
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void theCompletedCoreRearmsAfterSevenDaysAndNotBefore(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			MinerTestSupport.Joined joined = arena.wake();
			arena.freeze();
			ServerPlayer player = joined.player();
			arena.killRoster();
			arena.core().pulse(arena.level());
			CampLanternGameTest.lightAll(arena);
			JournalObjective.read(arena.level(), arena.lecternPos(), player);
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase() + " before the wait");
			long claimed = arena.entry().claimTime();
			long base = RuinClock.now(arena.level());
			RuinClock.Source old = RuinClock.replace(level -> base + 7L * Claim.DAY_TICKS - 1L);
			try {
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && arena.entry().claimTime() == claimed && !arena.vault().sealed(), "the camp reset one tick early: " + arena.phase());
				RuinClock.replace(level -> base + 7L * Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
			} finally {
				RuinClock.replace(old);
			}
			MinerTestSupport.result("sevenDays", "phase=" + arena.phase() + " claimed=" + claimed + " claimTime=" + arena.entry().claimTime() + " sealed=" + arena.vault().sealed());
			helper.assertTrue(arena.phase() != RuinPhase.COMPLETE, "the core stayed COMPLETE after 7 days");
			helper.assertTrue(arena.entry().claimTime() == -1L && arena.vault().sealed(), "the claim or the cache did not reset: " + arena.entry().claimTime() + " sealed " + arena.vault().sealed());
			helper.assertTrue(compasses(player) == 1, "the reader holds " + compasses(player) + " compasses after the reset");
		});
	}

	@GameTest(maxTicks = MinerTestSupport.ARENA_TICKS)
	public void aLecternAwayFromAnyCampCoreIsLeftAlone(final GameTestHelper helper) {
		MinerTestSupport.inArena(helper, SEED, arena -> {
			MinerTestSupport.Joined joined = arena.wake();
			ServerPlayer player = joined.player();
			BlockPos loose = arena.lecternPos().east(6);
			arena.level().setBlock(loose, Blocks.LECTERN.defaultBlockState(), 3);
			helper.assertTrue(JournalObjective.read(arena.level(), loose, player) == JournalObjective.Outcome.IGNORED, "a loose lectern was read as the camp journal");
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(loose), Direction.UP, loose, false);
			InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, arena.level(), InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(result == InteractionResult.PASS, "the callback consumed an ordinary lectern with " + result);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.vault().sealed(), "a loose lectern changed the core");
		});
	}
}
