package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinConfig;
import com.stella.ruins.world.RuinLevels;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Acceptance items 3 to 7, and the sketch review's rules: the gong, the lecterns, the tier record, the wave order, the
 * markers of a kind, the vault tables, the claim of each tier, the hazards, the cheer, and the failure of a run.
 */
public class ProvingRiteGameTest {
	static final int TICKS = 6000;

	private static int[] wave(final int tier, final int wave) {
		return ProvingGroundsSite.WAVES[tier - 1][wave];
	}

	private static String mix(final int[] mix) {
		return java.util.Arrays.toString(mix);
	}

	/** Plays a whole tier: every wave must start only when the last is dead and the pause has run. */
	private static void playTier(final GameTestHelper helper, final ProvingTestSupport.Arena arena, final ServerPlayer player, final ProvingTestSupport.Clock clock, final int tier) {
		TierObjective.Result started = arena.start(player, tier);
		helper.assertTrue(started == TierObjective.Result.STARTED, "tier " + tier + " did not start: " + started);
		int waves = ProvingGroundsSite.WAVES[tier - 1].length;
		for (int w = 0; w < waves; w++) {
			helper.assertTrue(java.util.Arrays.equals(arena.mix(), wave(tier, w)), "tier " + tier + " wave " + (w + 1) + " holds " + mix(arena.mix()) + ", not " + mix(wave(tier, w)));
			helper.assertTrue(arena.objective().tier() == tier && arena.objective().wave() == w, "the core records tier " + arena.objective().tier() + " wave " + arena.objective().wave());
			arena.killRoster(player);
			if (w < waves - 1) {
				helper.assertTrue(arena.roster().isEmpty() && arena.objective().wave() == w, "wave " + (w + 2) + " began at once, with no pause");
				arena.pulse();
				helper.assertTrue(arena.roster().isEmpty(), "wave " + (w + 2) + " began on a pulse with no time passed");
				clock.advance(TierObjective.WAVE_PAUSE_TICKS - 1);
				arena.pulse();
				helper.assertTrue(arena.roster().isEmpty(), "wave " + (w + 2) + " began one tick early");
				clock.advance(1);
				arena.pulse();
			}
		}
	}

	@GameTest(maxTicks = TICKS)
	public void aLecternBeforeTheGongDoesNothingAndTheGongArmsAllThree(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			TierObjective.Result early = arena.pick(player, 1);
			helper.assertTrue(early == TierObjective.Result.NOT_ARMED && arena.entry().roster().isEmpty() && arena.objective().tier() == 0, "a lectern before the gong gave " + early + " with " + arena.entry().roster().size() + " mobs");
			TierObjective.Result rung = arena.ring(player);
			helper.assertTrue(rung == TierObjective.Result.ARMED && arena.objective().armed(arena.level()), "the gong gave " + rung);
			for (int tier = 1; tier <= 3; tier++) {
				helper.assertTrue(arena.objective().armed(arena.level()), "the gong did not stay armed for tier " + tier);
			}
			ProvingTestSupport.result("gong", "early=" + early + " rung=" + rung);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theGongArmingRunsOutAndAStartUsesItUp(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.ring(player);
				clock.advance(TierObjective.ARM_TICKS - 1);
				helper.assertTrue(arena.objective().armed(arena.level()), "the arming ended one tick early");
				clock.advance(1);
				helper.assertTrue(arena.pick(player, 1) == TierObjective.Result.NOT_ARMED, "an arming that ran out still started a tier");
				arena.ring(player);
				helper.assertTrue(arena.pick(player, 1) == TierObjective.Result.STARTED, "a fresh arming did not start a tier");
				helper.assertTrue(!arena.objective().armed(arena.level()), "a started tier left the gong armed");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aSleepingCoreAnswersTheGongAndTheLecternWithoutStartingAnything(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.join(arena.standAt(3, 3));
			TierObjective.Result rung = arena.ring(player);
			TierObjective.Result picked = arena.pick(player, 2);
			helper.assertTrue(rung == TierObjective.Result.SLEEPING && picked == TierObjective.Result.SLEEPING, "a sleeping core answered " + rung + " and " + picked);
			helper.assertTrue(arena.entry() == null || arena.entry().roster().isEmpty(), "a sleeping core spawned mobs");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aLecternAwayFromAnyCoreIsLeftAlone(final GameTestHelper helper) {
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 2.0, 1.5));
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.getLevel().setBlock(spot, Blocks.LECTERN.defaultBlockState(), 3);
		helper.getLevel().setBlock(spot.east(3), Blocks.BELL.defaultBlockState(), 3);
		helper.assertTrue(GroundsUse.use(helper.getLevel(), spot, player) == null, "an ordinary lectern was claimed");
		helper.assertTrue(GroundsUse.use(helper.getLevel(), spot.east(3), player) == null, "an ordinary bell was claimed");
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(spot), net.minecraft.core.Direction.UP, spot, false);
		helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS, "the callback consumed an ordinary lectern");
		ProvingTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void anExtraLecternAndAnExtraBellBesideTheCoreAreNotTierControls(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			arena.ring(player);
			BlockPos near = arena.standAt(-2, 3).equals(Vec3.ZERO) ? arena.corePos() : BlockPos.containing(arena.standAt(-2, 3));
			arena.level().setBlock(near, Blocks.LECTERN.defaultBlockState(), 3);
			arena.level().setBlock(near.east(), Blocks.BELL.defaultBlockState(), 3);
			helper.assertTrue(GroundsUse.use(arena.level(), near, player) == null, "a loose lectern beside the core was read as a tier lectern");
			helper.assertTrue(GroundsUse.use(arena.level(), near.east(), player) == null, "a loose bell beside the core was read as the gong");
			helper.assertTrue(arena.roster().isEmpty() && arena.objective().tier() == 0, "a loose lectern started a tier");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theUseCallbackRingsTheBellAndStartsATierFromALectern(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			BlockHitResult bell = new BlockHitResult(Vec3.atCenterOf(arena.gongPos()), net.minecraft.core.Direction.NORTH, arena.gongPos(), false);
			InteractionResult rang = UseBlockCallback.EVENT.invoker().interact(player, arena.level(), InteractionHand.MAIN_HAND, bell);
			helper.assertTrue(rang == InteractionResult.PASS && arena.objective().armed(arena.level()), "the bell gave " + rang + " and armed=" + arena.objective().armed(arena.level()));
			BlockPos lectern = arena.lecternPos(2);
			BlockHitResult read = new BlockHitResult(Vec3.atCenterOf(lectern), net.minecraft.core.Direction.UP, lectern, false);
			InteractionResult used = UseBlockCallback.EVENT.invoker().interact(player, arena.level(), InteractionHand.MAIN_HAND, read);
			ProvingTestSupport.result("callback", "bell=" + rang + " lectern=" + used + " tier=" + arena.objective().tier() + " mix=" + mix(arena.mix()));
			helper.assertTrue(used.consumesAction() && arena.objective().tier() == 2, "the lectern gave " + used + " and the core records tier " + arena.objective().tier());
			helper.assertTrue(java.util.Arrays.equals(arena.mix(), wave(2, 0)), "wave 1 of tier II holds " + mix(arena.mix()));
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachLecternStartsItsOwnTierAndTheCoreRecordsIt(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			Map<Integer, Integer> recorded = new HashMap<>();
			ServerPlayer player = arena.wake();
			for (int tier = 1; tier <= 3; tier++) {
				TierObjective.Result result = arena.start(player, tier);
				recorded.put(tier, arena.entry().objective().getIntOr("tier", -1));
				helper.assertTrue(result == TierObjective.Result.STARTED && recorded.get(tier) == tier, "lectern " + tier + " gave " + result + " and the core saved tier " + recorded.get(tier));
				helper.assertTrue(java.util.Arrays.equals(arena.mix(), wave(tier, 0)), "tier " + tier + " wave 1 holds " + mix(arena.mix()));
				helper.assertTrue(arena.portcullisBars() == 3 * 7, "the portcullis stands at " + arena.portcullisBars() + " bars");
				// A death ends the run, so the next lectern starts from the same state.
				failRun(arena, player);
				player = arena.join(arena.standAt(2, 2));
			}
			ProvingTestSupport.result("lecterns", "recorded=" + recorded);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void tierOneRunsFiveWavesTierTwoSevenAndTierThreeTenEachStartingAfterTheLastIsDead(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				int[] counts = new int[3];
				for (int tier = 1; tier <= 3; tier++) {
					counts[tier - 1] = TierObjective.waveCount(tier);
					playTier(helper, arena, player, clock, tier);
					helper.assertTrue(arena.objective().tier() == 0, "tier " + tier + " did not end");
				}
				ProvingTestSupport.result("waveCounts", "counts=" + java.util.Arrays.toString(counts));
				helper.assertTrue(counts[0] == 5 && counts[1] == 7 && counts[2] == 10, "the wave counts are " + java.util.Arrays.toString(counts));
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core left the active phase: " + arena.phase());
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aWaveWaitsForTheLastMobEvenWhenAllButOneAreDead(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				arena.freeze();
				List<Mob> first = new ArrayList<>(arena.roster());
				arena.kill(player, first.subList(0, first.size() - 1));
				clock.advance(TierObjective.WAVE_PAUSE_TICKS * 5);
				arena.pulse();
				helper.assertTrue(arena.roster().size() == 1 && arena.objective().wave() == 0, "wave 2 began with a mob alive: roster " + arena.roster().size() + ", wave " + arena.objective().wave());
			}
		});
	}

	@GameTest
	public void waveStrengthRisesOnEveryWaveOfEveryTier(final GameTestHelper helper) {
		for (int tier = 1; tier <= 3; tier++) {
			double last = -1.0;
			for (int w = 0; w < ProvingGroundsSite.WAVES[tier - 1].length; w++) {
				double strength = ProvingGroundsSite.strength(tier, w);
				helper.assertTrue(strength > last, "tier " + tier + " wave " + (w + 1) + " has strength " + strength + ", not more than " + last);
				last = strength;
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void thePitBeastComesOnlyInTheLastWaveOfTierThree(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				int beasts = 0;
				arena.start(player, 3);
				int waves = ProvingGroundsSite.WAVES[2].length;
				for (int w = 0; w < waves; w++) {
					int here = arena.mix()[3];
					beasts += here;
					helper.assertTrue(here == (w == waves - 1 ? 1 : 0), "wave " + (w + 1) + " of tier III holds " + here + " beasts");
					if (here == 1) {
						Mob beast = arena.roster().stream().filter(mob -> mob.entityTags().contains("mod-stella-ruins.kind." + GroundsMobs.PIT_BEAST)).findFirst().orElseThrow();
						BlockPos hatch = arena.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.PIT_BEAST).get(0);
						helper.assertTrue(beast.position().distanceTo(Vec3.atBottomCenterOf(hatch)) < 1.5, "the beast stands at " + beast.position() + ", not on the hatch " + hatch);
					}
					arena.killRoster(player);
					clock.advance(TierObjective.WAVE_PAUSE_TICKS);
					arena.pulse();
				}
				ProvingTestSupport.result("pitBeast", "beasts=" + beasts + " waves=" + waves);
				helper.assertTrue(beasts == 1, "tier III held " + beasts + " beasts");
				arena.start(player, 1);
				helper.assertTrue(arena.mix()[3] == 0, "tier I held a beast");
			}
		});
	}

	private static Map<BlockPos, Integer> spotsOf(final ProvingTestSupport.Arena arena, final String kind) {
		Map<BlockPos, Integer> spots = new HashMap<>();
		for (Mob mob : arena.roster()) {
			if (mob.entityTags().contains("mod-stella-ruins.kind." + kind)) {
				spots.merge(mob.blockPosition(), 1, Integer::sum);
			}
		}
		return spots;
	}

	private static Map<BlockPos, Integer> firstMarkers(final ProvingTestSupport.Arena arena, final String kind, final int count) {
		List<BlockPos> markers = arena.markers(ArenaPlan.SPAWN_PREFIX + kind);
		Map<BlockPos, Integer> expected = new HashMap<>();
		for (int i = 0; i < count; i++) {
			expected.merge(markers.get(i % markers.size()), 1, Integer::sum);
		}
		return expected;
	}

	@GameTest(maxTicks = TICKS)
	public void everyMobAppearsAtAMarkerOfItsOwnKindAndAnOverflowReusesTheMarkersInOrder(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 3);
				helper.assertTrue(spotsOf(arena, GroundsMobs.GLADIATOR).equals(firstMarkers(arena, GroundsMobs.GLADIATOR, 5)), "wave 1 gladiators stand at " + spotsOf(arena, GroundsMobs.GLADIATOR));
				// Walk to wave 8 of tier III, which holds ten gladiators on eight markers.
				for (int w = 0; w < 7; w++) {
					arena.killRoster(player);
					clock.advance(TierObjective.WAVE_PAUSE_TICKS);
					arena.pulse();
				}
				helper.assertTrue(arena.objective().wave() == 7, "the walk reached wave " + (arena.objective().wave() + 1));
				Map<BlockPos, Integer> gladiators = spotsOf(arena, GroundsMobs.GLADIATOR);
				Map<BlockPos, Integer> retiarii = spotsOf(arena, GroundsMobs.RETIARIUS);
				Map<BlockPos, Integer> hounds = spotsOf(arena, GroundsMobs.HOUND);
				ProvingTestSupport.result("markersOfKind", "gladiators=" + gladiators.values() + " retiarii=" + retiarii.values() + " hounds=" + hounds.values());
				helper.assertTrue(gladiators.equals(firstMarkers(arena, GroundsMobs.GLADIATOR, 10)), "gladiators stand at " + gladiators);
				helper.assertTrue(gladiators.values().stream().filter(n -> n == 2).count() == 2, "the first two markers did not take the extra gladiators: " + gladiators.values());
				helper.assertTrue(retiarii.equals(firstMarkers(arena, GroundsMobs.RETIARIUS, 5)), "retiarii stand at " + retiarii);
				helper.assertTrue(hounds.equals(firstMarkers(arena, GroundsMobs.HOUND, 5)), "hounds stand at " + hounds);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aCompletedTierFillsTheVaultFromItsOwnTableAndOpensIt(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				int rolls = RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), Vec3.atCenterOf(arena.corePos())));
				helper.assertTrue(arena.vault().sealed(), "the vault stood open before any tier");
				int[][] bands = {{2, 3}, {4, 6}, {8, 12}};
				int before = 0;
				for (int tier = 1; tier <= 3; tier++) {
					playTier(helper, arena, player, clock, tier);
					int now = arena.itemsInVault(GroundsItems.CHAMPION_TOKEN);
					int gained = now - before;
					before = now;
					ProvingTestSupport.result("vaultTier" + tier, "tokens=" + gained + " rolls=" + rolls + " sealed=" + arena.vault().sealed() + " diamonds=" + arena.itemsInVault(Items.DIAMOND));
					helper.assertTrue(!arena.vault().sealed(), "the vault stayed sealed after tier " + tier);
					helper.assertTrue(gained >= bands[tier - 1][0] * rolls && gained <= bands[tier - 1][1] * rolls, "tier " + tier + " paid " + gained + " tokens, outside " + bands[tier - 1][0] * rolls + ".." + bands[tier - 1][1] * rolls);
					helper.assertTrue((tier == 3) == (arena.itemsInVault(Items.DIAMOND) >= rolls), "diamonds: " + arena.itemsInVault(Items.DIAMOND) + " after tier " + tier);
				}
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theVaultStaysSealedWhileATierRunsAndATierThatFailsPaysNothing(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				arena.killRoster(player);
				helper.assertTrue(arena.vault().sealed() && arena.itemsInVault(GroundsItems.CHAMPION_TOKEN) == 0, "the vault opened before the last wave died");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void tierOneCannotPayTwiceInADayButTierTwoStillOpensTheSameDay(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTier(helper, arena, player, clock, 1);
				int paid = arena.itemsInVault(GroundsItems.CHAMPION_TOKEN);
				long claimed = ProvingData.get(arena.level()).claimTime(arena.corePos(), 1);
				helper.assertTrue(claimed == clock.now(), "tier I claim time is " + claimed + ", not " + clock.now());
				TierObjective.Result again = arena.start(player, 1);
				helper.assertTrue(again == TierObjective.Result.CLAIMED && arena.roster().isEmpty() && arena.objective().tier() == 0, "tier I paid twice in a day: " + again + " with " + arena.roster().size() + " mobs");
				helper.assertTrue(arena.itemsInVault(GroundsItems.CHAMPION_TOKEN) == paid, "the vault changed after a refused start");
				clock.advance(Claim.DAY_TICKS / 2);
				helper.assertTrue(arena.start(player, 1) == TierObjective.Result.CLAIMED, "tier I opened half a day later");
				TierObjective.Result other = arena.start(player, 2);
				ProvingTestSupport.result("claims", "again=" + again + " tierTwo=" + other + " claimTime=" + claimed);
				helper.assertTrue(other == TierObjective.Result.STARTED && java.util.Arrays.equals(arena.mix(), wave(2, 0)), "tier II did not open the same day: " + other);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aTierRearmsAfterExactlyOneDayThroughTheClock(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTier(helper, arena, player, clock, 1);
				clock.advance(Claim.DAY_TICKS - 1);
				helper.assertTrue(arena.start(player, 1) == TierObjective.Result.CLAIMED, "tier I opened one tick before a day");
				clock.advance(1);
				TierObjective.Result result = arena.start(player, 1);
				helper.assertTrue(result == TierObjective.Result.STARTED && java.util.Arrays.equals(arena.mix(), wave(1, 0)), "tier I did not open after a day: " + result);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void completingATierClaimsOnlyThatTier(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTier(helper, arena, player, clock, 2);
				ProvingData data = ProvingData.get(arena.level());
				helper.assertTrue(data.claimTime(arena.corePos(), 1) == ProvingData.NONE && data.claimTime(arena.corePos(), 3) == ProvingData.NONE, "a tier II win claimed another tier");
				helper.assertTrue(data.claimTime(arena.corePos(), 2) == clock.now(), "tier II is not claimed");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theConfigResetDaysEntryChangesTheClaimDelay(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withResetDays(Map.of(ProvingGroundsSite.ID, 2)));
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTier(helper, arena, player, clock, 1);
				clock.advance(Claim.DAY_TICKS + 5);
				helper.assertTrue(arena.start(player, 1) == TierObjective.Result.CLAIMED, "a two day claim opened after one day");
				clock.advance(Claim.DAY_TICKS);
				helper.assertTrue(arena.start(player, 1) == TierObjective.Result.STARTED, "a two day claim did not open after two days");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aTierRunHoldsOtherLecternsAndTheGong(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			arena.start(player, 1);
			int roster = arena.roster().size();
			helper.assertTrue(arena.pick(player, 2) == TierObjective.Result.BUSY && arena.ring(player) == TierObjective.Result.BUSY, "a lectern or the gong worked in a run");
			helper.assertTrue(arena.roster().size() == roster && arena.objective().tier() == 1, "a second pick changed the run");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerDeathFailsTheRunDespawnsTheMobsAndTheTierCanStartAgain(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 2);
				helper.assertTrue(!arena.roster().isEmpty() && arena.portcullisBars() > 0, "the run did not start");
				// One wave mob chased the player far off, and one mob of the kind stands loose: the failure must take both.
				Mob far = arena.roster().get(0);
				far.setNoAi(true);
				far.snapTo(far.getX(), far.getY(), far.getZ() + 52.0);
				Mob loose = GroundsMobs.gladiator().spawn(arena.level(), arena.standAt(4, 4));
				helper.assertTrue(far.isAlive() && loose.isAlive() && far.distanceTo(loose) > 40.0, "the setup mobs are not in place");
				ProvingTestSupport.vulnerable(player);
				player.kill(arena.level());
				helper.assertTrue(!player.isAlive(), "the player survived the kill, so the test proves nothing");
				arena.pulse();
				long strays = arena.level().getEntities((Entity) null, arena.area().inflate(46.0), GroundsMobs::isGroundsMob).size();
				ProvingTestSupport.result("failDeath", "roster=" + arena.entry().roster().size() + " strays=" + strays + " tier=" + arena.objective().tier() + " bars=" + arena.portcullisBars());
				helper.assertTrue(arena.entry().roster().isEmpty(), "the roster record stayed: " + arena.entry().roster().size());
				helper.assertTrue(!far.isAlive(), "the wave mob far from the core stayed");
				helper.assertTrue(!loose.isAlive(), "the loose mob of the kind stayed");
				helper.assertTrue(strays == 0, "the wave mobs stayed: strays " + strays);
				helper.assertTrue(arena.objective().tier() == 0 && arena.portcullisBars() == 0, "the run did not end: tier " + arena.objective().tier() + ", bars " + arena.portcullisBars());
				helper.assertTrue(ProvingData.get(arena.level()).claimTime(arena.corePos(), 2) == ProvingData.NONE, "a failed run claimed the tier");
				helper.assertTrue(arena.vault().sealed(), "a failed run opened the vault");
				ServerPlayer next = arena.join(arena.standAt(2, 2));
				helper.assertTrue(arena.pick(next, 2) == TierObjective.Result.NOT_ARMED, "the failed run left the gong armed");
				TierObjective.Result again = arena.start(next, 2);
				helper.assertTrue(again == TierObjective.Result.STARTED && java.util.Arrays.equals(arena.mix(), wave(2, 0)), "the tier could not restart: " + again);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aRunPlayerOutsideTheBoundsForSixHundredTicksFailsTheRunAndOneThatReturnsInTimeDoesNot(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				Vec3 home = player.position();
				Vec3 away = home.add(0.0, 0.0, 120.0);
				player.teleportTo(arena.level(), away.x, away.y, away.z, java.util.Set.of(), 0.0F, 0.0F, true);
				arena.pulse();
				clock.advance(TierObjective.LEAVE_TICKS - 1);
				arena.pulse();
				helper.assertTrue(arena.objective().tier() == 1, "the run failed one tick early");
				player.teleportTo(arena.level(), home.x, home.y, home.z, java.util.Set.of(), 0.0F, 0.0F, true);
				arena.pulse();
				player.teleportTo(arena.level(), away.x, away.y, away.z, java.util.Set.of(), 0.0F, 0.0F, true);
				arena.pulse();
				clock.advance(TierObjective.LEAVE_TICKS - 1);
				arena.pulse();
				helper.assertTrue(arena.objective().tier() == 1, "coming back did not reset the count");
				clock.advance(1);
				arena.pulse();
				ProvingTestSupport.result("failLeave", "tier=" + arena.objective().tier() + " roster=" + arena.entry().roster().size() + " bars=" + arena.portcullisBars());
				helper.assertTrue(arena.objective().tier() == 0 && arena.entry().roster().isEmpty() && arena.portcullisBars() == 0, "six hundred ticks outside did not fail the run: tier " + arena.objective().tier());
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aRunPlayerJustOutsideTheBoxButInsideTheMarginStaysInside(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				var bounds = TierObjective.bounds(TierObjective.Arena.of(arena.level(), arena.core()));
				double margin = TierObjective.BOUNDS_MARGIN;
				var box = arena.frame().box();
				helper.assertTrue(bounds.minX == box.minX() - margin && bounds.maxX == box.maxX() + 1 + margin && bounds.minZ == box.minZ() - margin && bounds.maxZ == box.maxZ() + 1 + margin, "the bounds are " + bounds);
				helper.assertTrue(bounds.minY == box.minY() - margin && bounds.maxY == box.maxY() + 1, "the vertical bounds are " + bounds.minY + ".." + bounds.maxY);
				Vec3 edge = new Vec3(box.minX() - 1.0, arena.frame().min().getY() + 1.0, box.minZ() - 1.0);
				player.teleportTo(arena.level(), edge.x, edge.y, edge.z, java.util.Set.of(), 0.0F, 0.0F, true);
				arena.pulse();
				clock.advance(TierObjective.LEAVE_TICKS * 3);
				arena.pulse();
				helper.assertTrue(arena.objective().tier() == 1, "a player one block outside the box failed the run");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theMainPortcullisDropsAtTheStartAndRisesAtTheEnd(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				helper.assertTrue(arena.portcullisBars() == 0, "the main gate was barred before a run");
				arena.start(player, 1);
				int down = arena.portcullisBars();
				arena.killRoster(player);
				for (int w = 1; w < TierObjective.waveCount(1); w++) {
					clock.advance(TierObjective.WAVE_PAUSE_TICKS);
					arena.pulse();
					arena.killRoster(player);
				}
				ProvingTestSupport.result("portcullis", "down=" + down + " after=" + arena.portcullisBars());
				helper.assertTrue(down == 21 && arena.portcullisBars() == 0, "the portcullis had " + down + " bars at the start and " + arena.portcullisBars() + " at the end");
			}
		});
	}

	private static boolean allBraziers(final ProvingTestSupport.Arena arena, final boolean lit) {
		List<BlockPos> spots = arena.markers(ArenaPlan.BRAZIER);
		return spots.size() == ArenaPlan.BRAZIERS && spots.stream().allMatch(spot -> {
			BlockState state = arena.level().getBlockState(spot);
			return state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) == lit;
		});
	}

	@GameTest(maxTicks = TICKS)
	public void tierTwoAndThreeLightFourBraziersAndTierOneDoesNot(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				helper.assertTrue(allBraziers(arena, false), "the braziers were lit before a run");
				playTierPartial(helper, arena, player, 1);
				helper.assertTrue(allBraziers(arena, false), "tier I lit the braziers");
				failRun(arena, player);
				helper.assertTrue(allBraziers(arena, false), "a failed run left braziers lit");
				ServerPlayer second = arena.join(arena.standAt(2, 2));
				playTierPartial(helper, arena, second, 2);
				helper.assertTrue(allBraziers(arena, true), "tier II did not light four braziers");
				ProvingTestSupport.result("braziers", "tierTwoLit=" + allBraziers(arena, true));
				failRun(arena, second);
				helper.assertTrue(allBraziers(arena, false), "a failed tier II run left braziers lit");
				ServerPlayer third = arena.join(arena.standAt(-2, 2));
				playTierPartial(helper, arena, third, 3);
				helper.assertTrue(allBraziers(arena, true), "tier III did not light the braziers");
				clearTier(helper, arena, third, clock, 3);
				helper.assertTrue(allBraziers(arena, false), "a completed tier left the braziers lit");
			}
		});
	}

	private static void playTierPartial(final GameTestHelper helper, final ProvingTestSupport.Arena arena, final ServerPlayer player, final int tier) {
		TierObjective.Result result = arena.start(player, tier);
		helper.assertTrue(result == TierObjective.Result.STARTED, "tier " + tier + " did not start: " + result);
	}

	/** Plays the rest of the tier a start began, to its win. */
	private static void clearTier(final GameTestHelper helper, final ProvingTestSupport.Arena arena, final ServerPlayer player, final ProvingTestSupport.Clock clock, final int tier) {
		int waves = ProvingGroundsSite.WAVES[tier - 1].length;
		for (int w = arena.objective().wave(); w < waves; w++) {
			arena.killRoster(player);
			if (w < waves - 1) {
				clock.advance(TierObjective.WAVE_PAUSE_TICKS);
				arena.pulse();
			}
		}
		helper.assertTrue(arena.objective().tier() == 0, "tier " + tier + " did not end");
	}

	/** Ends a run the way a death does. */
	private static void failRun(final ProvingTestSupport.Arena arena, final ServerPlayer player) {
		ProvingTestSupport.vulnerable(player);
		player.kill(arena.level());
		arena.pulse();
	}

	private static List<BlockPos> grateCells(final ProvingTestSupport.Arena arena) {
		List<BlockPos> cells = new ArrayList<>(arena.markers(ArenaPlan.GRATE + 1));
		cells.addAll(arena.markers(ArenaPlan.GRATE + 2));
		return cells;
	}

	private static boolean grates(final ProvingTestSupport.Arena arena, final boolean open) {
		List<BlockPos> cells = grateCells(arena);
		return cells.size() == 8 && cells.stream().allMatch(spot -> {
			BlockState door = arena.level().getBlockState(spot);
			boolean fire = arena.level().getBlockState(spot.below()).is(Blocks.FIRE);
			return door.is(Blocks.IRON_TRAPDOOR) && door.getValue(TrapDoorBlock.OPEN) == open && fire == open;
		});
	}

	@GameTest(maxTicks = TICKS)
	public void tierThreeOpensTwoFireGratesForSixtyTicksEveryFourHundredAndNoLowerTierDoes(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTierPartial(helper, arena, player, 2);
				clock.advance(TierObjective.GRATE_FIRST_TICKS + TierObjective.GRATE_PERIOD_TICKS * 2);
				arena.pulse();
				helper.assertTrue(grates(arena, false), "tier II opened a grate");
				failRun(arena, player);
				ServerPlayer second = arena.join(arena.standAt(2, 2));
				playTierPartial(helper, arena, second, 3);
				long start = clock.now();
				helper.assertTrue(grates(arena, false), "the grates stood open at the start");
				clock.advance(TierObjective.GRATE_FIRST_TICKS - 1);
				arena.pulse();
				helper.assertTrue(grates(arena, false), "a grate opened " + (clock.now() - start) + " ticks in");
				clock.advance(1);
				arena.pulse();
				long opened = clock.now();
				helper.assertTrue(grates(arena, true), "the grates did not open at " + (clock.now() - start) + " ticks");
				clock.advance(TierObjective.GRATE_OPEN_TICKS - 1);
				arena.pulse();
				helper.assertTrue(grates(arena, true), "the grates closed one tick early");
				clock.advance(1);
				arena.pulse();
				helper.assertTrue(grates(arena, false), "the grates stayed open " + (clock.now() - opened) + " ticks");
				clock.advance(TierObjective.GRATE_PERIOD_TICKS - TierObjective.GRATE_OPEN_TICKS - 1);
				arena.pulse();
				helper.assertTrue(grates(arena, false), "the next opening came early");
				clock.advance(1);
				arena.pulse();
				ProvingTestSupport.result("grates", "firstOpen=" + (opened - start) + " period=" + (clock.now() - opened) + " open=" + TierObjective.GRATE_OPEN_TICKS);
				helper.assertTrue(grates(arena, true) && clock.now() - opened == TierObjective.GRATE_PERIOD_TICKS, "the grates did not reopen a period after the first");
				failRun(arena, second);
				helper.assertTrue(grates(arena, false), "a failed run left the grates open");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theGrateFireStandsOnNetherrackAndGoesOutWhenTheGrateCloses(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTierPartial(helper, arena, player, 3);
				BlockPos cell = grateCells(arena).get(0);
				clock.advance(TierObjective.GRATE_FIRST_TICKS);
				arena.pulse();
				helper.assertTrue(grates(arena, true), "the grates did not open");
				BlockPos fire = cell.below();
				helper.assertTrue(arena.level().getBlockState(fire).is(Blocks.FIRE) && arena.level().getBlockState(fire.below()).is(Blocks.NETHERRACK), "the fire does not stand on netherrack");
				clock.advance(TierObjective.GRATE_OPEN_TICKS);
				arena.pulse();
				helper.assertTrue(arena.level().getBlockState(fire).isAir(), "the fire stayed after the grate closed");
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void everyClearedWaveCheersFromTheStandsAndTheLastOneToo(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			List<Integer> seats = new ArrayList<>();
			Cheer.Sink old = Cheer.replace((level, spots) -> seats.add(spots.size()));
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				playTier(helper, arena, player, clock, 1);
				ProvingTestSupport.result("cheer", "cheers=" + seats);
				helper.assertTrue(seats.size() == 5, "tier I cheered " + seats.size() + " times, not 5");
				helper.assertTrue(seats.stream().allMatch(n -> n == 8), "a cheer had " + seats + " seats, not 8 each");
				seats.clear();
				arena.start(player, 2);
				helper.assertTrue(seats.isEmpty(), "a start cheered");
				arena.killRoster(player);
				helper.assertTrue(seats.size() == 1, "one clear gave " + seats.size() + " cheers");
			} finally {
				Cheer.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aFailedRunDoesNotCheer(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			List<Integer> seats = new ArrayList<>();
			Cheer.Sink old = Cheer.replace((level, spots) -> seats.add(spots.size()));
			try {
				ServerPlayer player = arena.wake();
				arena.start(player, 1);
				failRun(arena, player);
				helper.assertTrue(seats.isEmpty(), "a failed run cheered " + seats.size() + " times");
			} finally {
				Cheer.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aRunSurvivesTheSaveAndLoadOfItsRecord(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, arena -> {
			try (ProvingTestSupport.Clock clock = new ProvingTestSupport.Clock()) {
				ServerPlayer player = arena.wake();
				arena.start(player, 3);
				arena.killRoster(player);
				clock.advance(TierObjective.WAVE_PAUSE_TICKS);
				arena.pulse();
				TierObjective reloaded = new TierObjective();
				reloaded.load(arena.entry().objective());
				helper.assertTrue(reloaded.tier() == 3 && reloaded.wave() == 1 && reloaded.running(), "the saved run reads tier " + reloaded.tier() + " wave " + reloaded.wave());
				helper.assertTrue(reloaded.save().equals(arena.entry().objective()), "the objective did not save what it loaded");
			}
		});
	}
}
