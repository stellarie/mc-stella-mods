package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.RuinClock;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** The lookout: a living lookout that sees a player for 600 ticks lights the signal fire and calls a late squad. */
public class WatchtowerLookoutGameTest {
	static final long SEED = 0xF1A5C0DEL;
	static final int TICKS = 6000;

	private static Mob lookout(final TowerTestSupport.Arena arena) {
		return arena.roster().stream().filter(mob -> mob.entityTags().contains(GarrisonObjective.LOOKOUT_TAG)).findFirst().orElse(null);
	}

	private static boolean signalLit(final TowerTestSupport.Arena arena) {
		BlockPos captain = arena.markers(WatchtowerPlan.CAPTAIN).get(0);
		return arena.level().getBlockState(captain).is(Blocks.SOUL_CAMPFIRE);
	}

	@GameTest(maxTicks = TICKS)
	public void aLivingLookoutThatSeesAPlayerForSixHundredTicksLightsTheSignalAndCallsALateSquad(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED, arena -> {
			AtomicLong clock = new AtomicLong(50_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				arena.wake();
				arena.freeze();
				helper.assertTrue(lookout(arena) != null, "the garrison has no lookout");
				int start = arena.roster().size();
				arena.core().pulse(arena.level());
				clock.addAndGet(GarrisonObjective.SIGNAL_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.roster().size() == start && !signalLit(arena), "the signal came one tick early");
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				int late = arena.roster().size() - start;
				TowerTestSupport.result("lookoutSignal", "start=" + start + " late=" + late + " fire=" + signalLit(arena));
				helper.assertTrue(late == GarrisonObjective.LATE_SQUAD, "the late squad is " + late + " mobs, not " + GarrisonObjective.LATE_SQUAD);
				helper.assertTrue(signalLit(arena), "the signal fire is not lit");
				clock.addAndGet(GarrisonObjective.SIGNAL_TICKS * 2);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.roster().size() == start + late, "the lookout called a second squad");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aDeadLookoutCallsNoSquad(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED, arena -> {
			AtomicLong clock = new AtomicLong(51_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				Mob lookout = lookout(arena);
				helper.assertTrue(lookout != null, "the garrison has no lookout");
				arena.core().pulse(arena.level());
				lookout.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
				helper.assertTrue(!lookout.isAlive(), "the lookout survived");
				int start = arena.roster().size();
				clock.addAndGet(GarrisonObjective.SIGNAL_TICKS * 2);
				arena.core().pulse(arena.level());
				int after = arena.roster().size();
				helper.assertTrue(after == start && !signalLit(arena), "a dead lookout lit the signal or called " + (after - start) + " mobs");
			} finally {
				RuinClock.replace(old);
			}
		});
	}
}
