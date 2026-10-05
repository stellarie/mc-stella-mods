package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinVaultBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

/** Acceptance items 3 to 5: the garrison spawns on the floors and the roof, clearing it completes the core, and it stays complete. */
public class WatchtowerObjectiveGameTest {
	static final int TICKS = TowerTestSupport.ARENA_TICKS;
	static final long SEED = 0x6A22150AL;

	static int count(final RuinVaultBlockEntity vault, final Item item) {
		int total = 0;
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			if (vault.getItem(slot).is(item)) {
				total += vault.getItem(slot).getCount();
			}
		}
		return total;
	}

	@GameTest(maxTicks = TICKS)
	public void activationSpawnsOneMobAtEveryMarkerArchersOnTheFloorsAndTheCaptainOnTheRoof(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED, arena -> {
			WatchtowerPlan plan = arena.plan();
			arena.wake();
			arena.freeze();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core did not wake: " + arena.phase());
			Map<String, Integer> kinds = new HashMap<>();
			Map<Integer, Integer> floors = new HashMap<>();
			int roofY = arena.at(0, plan.floorY(plan.floors) + 1, 0).getY();
			int captains = 0;
			for (Mob mob : arena.roster()) {
				RuinMobKind kind = RuinMobs.kindOf(mob);
				helper.assertTrue(kind != null && mob.entityTags().contains(RuinMobKind.TAG_MOB), "a roster mob is no ruin mob");
				kinds.merge(kind.id(), 1, Integer::sum);
				int local = mob.blockPosition().getY() - arena.frame().min().getY();
				floors.merge(Math.floorDiv(Math.max(local - 1, 0), WatchtowerPlan.FLOOR_HEIGHT), 1, Integer::sum);
				if (kind == WatchtowerMobs.WATCH_CAPTAIN) {
					captains++;
					helper.assertTrue(mob.blockPosition().getY() == roofY, "the captain stands at y " + mob.blockPosition().getY() + ", not on the roof at " + roofY);
				}
			}
			int archers = plan.markers(WatchtowerPlan.ARCHER).size();
			int crossbows = plan.markers(WatchtowerPlan.CROSSBOW).size();
			TowerTestSupport.result("garrisonSpawns", "markers=" + plan.markers.size() + " roster=" + arena.roster().size() + " kinds=" + kinds + " floors=" + floors);
			helper.assertTrue(arena.roster().size() == plan.markers.size(), "the roster holds " + arena.roster().size() + " mobs for " + plan.markers.size() + " markers");
			helper.assertTrue(kinds.getOrDefault("watch_archer", 0) == archers && kinds.getOrDefault("tower_crossbowman", 0) == crossbows && captains == 1, "kinds " + kinds);
			for (int k = 0; k < plan.floors; k++) {
				helper.assertTrue(floors.getOrDefault(k, 0) >= 2, "floor " + k + " holds " + floors.getOrDefault(k, 0) + " mobs");
			}
			helper.assertTrue(floors.getOrDefault(plan.floors, 0) == 3, "the roof holds " + floors.getOrDefault(plan.floors, 0) + " mobs");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void everyGarrisonMobStandsAtItsOwnMarkerAndCarriesItsBoxAndPost(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED + 1, net.minecraft.world.level.block.Rotation.CLOCKWISE_90, arena -> {
			arena.wake();
			arena.freeze();
			int tagged = 0;
			for (Mob mob : arena.roster()) {
				boolean near = false;
				for (String kind : new String[] {WatchtowerPlan.ARCHER, WatchtowerPlan.CROSSBOW, WatchtowerPlan.CAPTAIN}) {
					for (BlockPos spot : arena.markers(kind)) {
						near |= mob.blockPosition().distManhattan(spot) <= 1;
					}
				}
				helper.assertTrue(near, "a garrison mob at " + mob.blockPosition() + " is at no marker");
				boolean has = mob.entityTags().stream().anyMatch(tag -> tag.startsWith(WatchtowerMobs.GARRISON_TAG));
				helper.assertTrue(has, "a garrison mob has no box and post tag: " + mob.entityTags());
				tagged++;
			}
			helper.assertTrue(tagged == arena.plan().markers.size(), "tagged " + tagged);
			TowerTestSupport.result("garrisonTags", "tagged=" + tagged + " turn=1");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void killingTheWholeGarrisonCompletesTheCoreAndTheVaultAtTheTopUnlocks(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED + 2, arena -> {
			WatchtowerPlan plan = arena.plan();
			ServerPlayer player = arena.wake();
			arena.freeze();
			helper.assertTrue(arena.vault().sealed(), "the vault was open before the garrison died");
			int killed = arena.killRoster(player);
			helper.assertTrue(killed == plan.markers.size(), "killed " + killed + " of " + plan.markers.size());
			RuinVaultBlockEntity vault = arena.vault();
			int roofY = arena.frame().min().getY() + plan.floorY(plan.floors) + 1;
			int feathers = count(vault, WatchtowerItems.HAWKFEATHER);
			TowerTestSupport.result("clearCompletes", "killed=" + killed + " phase=" + arena.phase() + " sealed=" + vault.sealed() + " vaultY=" + arena.vaultPos().getY() + " roofY=" + roofY + " feathers=" + feathers);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase());
			helper.assertTrue(!vault.sealed(), "the vault stayed sealed");
			helper.assertTrue(arena.vaultPos().getY() == roofY && arena.corePos().getY() == roofY, "the core or the vault is not on the roof");
			helper.assertTrue(feathers >= 4 && feathers <= 8, "the vault holds " + feathers + " Hawkfeathers, not 4 to 8");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aMobLeftAliveKeepsTheVaultSealed(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED + 3, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			java.util.List<Mob> roster = arena.roster();
			for (int i = 1; i < roster.size(); i++) {
				roster.get(i).hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
			}
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.vault().sealed(), "one mob alive: phase " + arena.phase() + " sealed " + arena.vault().sealed());
			roster.get(0).hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the last kill did not complete the core: " + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theCoreStaysCompleteAfterThirtyDays(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, SEED + 4, arena -> {
			AtomicLong clock = new AtomicLong(9_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				arena.killRoster(player);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core did not complete: " + arena.phase());
				long claimed = arena.entry().claimTime();
				arena.leave(player);
				clock.addAndGet(30 * Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				TowerTestSupport.result("onceStaysComplete", "claimTime=" + claimed + " days=30 phase=" + arena.phase() + " sealed=" + arena.vault().sealed() + " once=" + TowerTestSupport.site().claim().isOnce());
				helper.assertTrue(TowerTestSupport.site().claim().isOnce(), "the tower's claim is not once");
				helper.assertTrue(claimed == 9_000_000L, "the claim time is " + claimed);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the core left COMPLETE or the vault sealed after 30 days: " + arena.phase());
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.roster().isEmpty(), "a complete tower spawned mobs again");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	/** A seed that draws the tallest tower, 6 floors. */
	static long tallSeed() {
		for (long seed = SEED; ; seed++) {
			if (WatchtowerPlan.derive(seed).floors == WatchtowerPlan.MIN_FLOORS + 2) {
				return seed;
			}
		}
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerTwentyBlocksFromTheFootOfTheTallestTowerWakesIt(final GameTestHelper helper) {
		TowerTestSupport.inArena(helper, tallSeed(), arena -> {
			BlockPos core = arena.corePos();
			BlockPos out = arena.at(WatchtowerPlan.CENTER, 0, WatchtowerPlan.CENTER - 20);
			ServerPlayer player = arena.join(new net.minecraft.world.phys.Vec3(out.getX() + 0.5, out.getY(), out.getZ() + 0.5));
			double distance = Math.sqrt(player.distanceToSqr(core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5));
			arena.core().pulse(arena.level());
			TowerTestSupport.result("wakeReach", "floors=" + arena.plan().floors + " distance=" + distance + " radius=" + FallenWatchtowerSite.ACTIVATION_RADIUS + " phase=" + arena.phase());
			helper.assertTrue(distance <= FallenWatchtowerSite.ACTIVATION_RADIUS, "a player 20 blocks from the centre is " + distance + " from the core of the tallest tower");
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core is " + arena.phase() + " with a player in the radius");
		});
	}
}
