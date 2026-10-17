package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The tweaked dragon: a steady orbit, a fixed attack cycle, a quiet first ten seconds after waking, and no harm from beds. */
public class DragonTweaksGameTest {
	/** The clock the controller leaves a timer at when a test wants it never to fire. */
	static final long FAR = Long.MAX_VALUE / 4;

	/** An arena whose dragon is awake and has nothing due: no attack, no perch. */
	static void quiet(final DragonAttackGameTest.Arena arena) {
		arena.state().nextAttackAt = FAR;
		arena.state().perchAt = FAR;
	}

	@GameTest(environment = "mod-stella-end-test:dragon_orbit", maxTicks = 700)
	public void betweenAttacksTheDragonCirclesOneRingAtOneHeightAtTheCruiseAndNeverTurnsBack(final GameTestHelper helper) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		DragonState state = arena.state();
		Vec3 centre = Rooftop.hoverPoint();
		List<double[]> samples = new ArrayList<>();
		double[] fastest = new double[1];
		boolean[] sampling = new boolean[1];
		helper.onEachTick(() -> {
			if (sampling[0]) {
				Vec3 at = arena.dragon().position();
				samples.add(new double[] {Math.hypot(at.x - centre.x, at.z - centre.z), at.y, Math.atan2(at.z - centre.z, at.x - centre.x)});
				fastest[0] = Math.max(fastest[0], state.velocity.length());
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.AWAKE, "the dragon is " + state.mode()))
			.thenExecute(() -> state.nextAttackAt = FAR)
			.thenIdle(150)
			.thenExecute(() -> sampling[0] = true)
			.thenIdle(350)
			.thenExecute(() -> {
				double radiusOff = 0.0;
				double heightOff = 0.0;
				double swept = 0.0;
				int forward = 0;
				int back = 0;
				double slowest = Double.MAX_VALUE;
				double quickest = 0.0;
				for (int i = 1; i < samples.size(); i++) {
					double[] now = samples.get(i);
					double[] before = samples.get(i - 1);
					radiusOff = Math.max(radiusOff, Math.abs(now[0] - DragonFlight.ORBIT_RADIUS));
					heightOff = Math.max(heightOff, Math.abs(now[1] - centre.y));
					double step = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(now[2] - before[2])) * Math.PI / 180.0;
					swept += step;
					forward += step > 1.0E-4 ? 1 : 0;
					back += step < -1.0E-4 ? 1 : 0;
					double ground = Math.hypot(now[0] * Math.cos(now[2]) - before[0] * Math.cos(before[2]), now[0] * Math.sin(now[2]) - before[0] * Math.sin(before[2]));
					slowest = Math.min(slowest, ground);
					quickest = Math.max(quickest, ground);
				}
				int turned = Math.min(forward, back);
				EndTestSupport.result("dragonOrbit", "samples=" + samples.size() + " radiusOff=" + radiusOff + " heightOff=" + heightOff + " swept=" + swept + " turned=" + turned
					+ " controller=" + fastest[0] + " ground=" + slowest + ".." + quickest + " attacks=" + state.attacks().size());
				int attacks = state.attacks().size();
				EndTestSupport.fresh(helper);
				helper.assertTrue(samples.size() >= 300, "only " + samples.size() + " ticks were watched");
				helper.assertTrue(attacks == 0, "the dragon attacked while the test held it back");
				helper.assertTrue(radiusOff <= 3.0, "the dragon strayed " + radiusOff + " blocks off the ring of " + DragonFlight.ORBIT_RADIUS);
				helper.assertTrue(heightOff <= 0.5, "the dragon rose and sank " + heightOff + " blocks round the hover height");
				helper.assertTrue(turned == 0, "the dragon turned back " + turned + " times");
				helper.assertTrue(Math.abs(swept) >= Math.PI, "the dragon swept only " + swept + " radians");
				helper.assertTrue(fastest[0] <= DragonFlight.CRUISE + 1.0E-6, "the dragon circled at " + fastest[0] + " a tick");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_wake_perch", maxTicks = 300)
	public void theDragonDoesNotPerchInTheFirstSixHundredTicksAfterItWakesAndThenPerches(final GameTestHelper helper) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		DragonState state = arena.state();
		ServerLevel end = arena.end();
		DragonState.Mode[] seen = new DragonState.Mode[3];
		long[] due = new long[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.AWAKE, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				quiet(arena);
				due[0] = state.awokeAt();
				// The perch is due now.
				state.perchAt = 0L;
			})
			.thenIdle(5)
			.thenExecute(() -> {
				seen[0] = state.mode();
				EndClock.advance(end, due[0] + DragonRules.WAKE_GRACE - 10 - EndClock.now(end));
			})
			.thenIdle(2)
			.thenExecute(() -> {
				seen[1] = state.mode();
				EndClock.advance(end, 20);
			})
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.PERCH, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				seen[2] = state.mode();
				long began = state.perches().get(0).startedAt() - due[0];
				EndTestSupport.result("dragonWakePerch", "grace=" + DragonRules.WAKE_GRACE + " awokeAt=" + due[0] + " early=" + seen[0] + " late=" + seen[1] + " perchedAfter=" + began);
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.WAKE_GRACE == 600L, "the grace constant is " + DragonRules.WAKE_GRACE);
				helper.assertTrue(due[0] != EndState.NEVER, "the waking was not recorded");
				helper.assertTrue(seen[0] == DragonState.Mode.AWAKE && seen[1] == DragonState.Mode.AWAKE, "the dragon was " + seen[0] + " and " + seen[1] + " inside the grace");
				helper.assertTrue(began >= DragonRules.WAKE_GRACE && began <= DragonRules.WAKE_GRACE + 30, "the dragon perched " + began + " ticks after it woke");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_wake_roost", maxTicks = 300)
	public void theDragonDoesNotRoostInTheFirstSixHundredTicksAfterItWakesEvenAtHalfHealthAndThenRoosts(final GameTestHelper helper) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		DragonState state = arena.state();
		ServerLevel end = arena.end();
		DragonState.Mode[] seen = new DragonState.Mode[2];
		long[] woke = new long[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.AWAKE, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				quiet(arena);
				woke[0] = state.awokeAt();
				arena.dragon().setHealth(arena.dragon().getMaxHealth() * 0.4F);
			})
			.thenIdle(5)
			.thenExecute(() -> {
				seen[0] = state.mode();
				EndClock.advance(end, woke[0] + DragonRules.WAKE_GRACE - 10 - EndClock.now(end));
			})
			.thenIdle(2)
			.thenExecute(() -> {
				seen[1] = state.mode();
				EndClock.advance(end, 20);
			})
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.ROOST, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				long began = state.roostStartedAt() - woke[0];
				EndTestSupport.result("dragonWakeRoost", "awokeAt=" + woke[0] + " early=" + seen[0] + " late=" + seen[1] + " roostedAfter=" + began);
				EndTestSupport.fresh(helper);
				helper.assertTrue(woke[0] != EndState.NEVER, "the waking was not recorded");
				helper.assertTrue(seen[0] == DragonState.Mode.AWAKE && seen[1] == DragonState.Mode.AWAKE, "the dragon was " + seen[0] + " and " + seen[1] + " inside the grace");
				helper.assertTrue(began >= DragonRules.WAKE_GRACE && began <= DragonRules.WAKE_GRACE + 30, "the dragon roosted " + began + " ticks after it woke");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_cycle", maxTicks = 1800)
	public void theDragonFliesSwoopBarrageDiveAirdropInThatOrderEachSeparatedByTheCruiseTime(final GameTestHelper helper) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		DragonState state = arena.state();
		ServerLevel end = arena.end();
		int rounds = 5;
		helper.onEachTick(() -> {
			// The wind-up is not under test.
			if (state.launchAt != EndState.NEVER && EndClock.now(end) < state.launchAt) {
				EndClock.advance(end, state.launchAt - EndClock.now(end));
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.AWAKE, "the dragon is " + state.mode()))
			.thenExecute(() -> state.perchAt = FAR)
			.thenWaitUntil(() -> helper.assertTrue(state.attacks().size() >= rounds, "only " + state.attacks().size() + " attacks have begun"))
			.thenExecute(() -> {
				List<DragonState.Attack> order = new ArrayList<>();
				List<Long> gaps = new ArrayList<>();
				boolean ended = true;
				List<DragonState.AttackRecord> attacks = state.attacks();
				for (int i = 0; i < rounds; i++) {
					order.add(attacks.get(i).attack());
					if (i > 0) {
						gaps.add(attacks.get(i).at() - attacks.get(i - 1).endedAt());
						ended &= attacks.get(i - 1).endedAt() != EndState.NEVER;
					}
				}
				boolean steady = true;
				for (long gap : gaps) {
					steady &= gap >= DragonRules.CRUISE_TICKS && gap <= DragonRules.CRUISE_TICKS + 3;
				}
				EndTestSupport.result("dragonCycle", "order=" + order + " gaps=" + gaps + " cruise=" + DragonRules.CRUISE_TICKS);
				boolean exact = ended && steady;
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.CRUISE_TICKS == 100, "the cruise time constant is " + DragonRules.CRUISE_TICKS);
				helper.assertTrue(order.equals(List.of(DragonState.Attack.SWOOP, DragonState.Attack.BARRAGE, DragonState.Attack.DIVE, DragonState.Attack.AIRDROP, DragonState.Attack.SWOOP)),
					"the dragon flew " + order);
				helper.assertTrue(exact, "the cruise times between the attacks were " + gaps + ", ended=" + ended);
			})
			.thenSucceed();
	}

	/** A bed or an anchor that blows at the dragon's head, and the health it took. */
	interface Blast {
		void go(ServerLevel end, DragonRoostGameTest.Fight fight, BlockPos at);
	}

	static float blown(final ServerLevel end, final DragonRoostGameTest.Fight fight, final BlockPos at, final Blast blast) {
		fight.dragon().damageCooldownTime = 0;
		fight.dragon().hurtTime = 0;
		float before = fight.dragon().getHealth();
		blast.go(end, fight, at);
		return before - fight.dragon().getHealth();
	}

	static void bed(final ServerLevel end, final DragonRoostGameTest.Fight fight, final BlockPos at) {
		Direction facing = Direction.NORTH;
		BlockState foot = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
		end.setBlock(at, foot, 3);
		end.setBlock(at.relative(facing), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
		BlockState used = end.getBlockState(at);
		used.useWithoutItem(end, fight.player(), new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	static void anchor(final ServerLevel end, final DragonRoostGameTest.Fight fight, final BlockPos at) {
		BlockState charged = Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1);
		end.setBlock(at, charged, 3);
		end.getBlockState(at).useWithoutItem(end, fight.player(), new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	@GameTest(environment = "mod-stella-end-test:dragon_bed", maxTicks = 200)
	public void aBedAndARespawnAnchorBlowingAtTheDragonDoItNoHarmWhileAPlayersBlowStillDoes(final GameTestHelper helper) {
		DragonRoostGameTest.Fight fight = DragonRoostGameTest.fight(helper);
		ServerLevel end = fight.end();
		float[] lost = new float[4];
		boolean[] gone = new boolean[2];
		BlockPos[] spot = new BlockPos[1];
		helper.startSequence()
			// The blast is the game's own, fire and broken blocks included. It goes off high over the roof, far from the castle.
			.thenWaitUntil(() -> helper.assertTrue(fight.dragon().head.getY() > Rooftop.DECK_Y + 16.0, "the dragon's head is at " + fight.dragon().head.position()))
			.thenExecute(() -> {
				helper.assertTrue(end.dimension().equals(Level.END), "the test level is not the End");
				spot[0] = BlockPos.containing(fight.dragon().head.position());
				lost[0] = blown(end, fight, spot[0], DragonTweaksGameTest::bed);
				gone[0] = !(end.getBlockState(spot[0]).getBlock() instanceof BedBlock);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				spot[0] = BlockPos.containing(fight.dragon().head.position());
				lost[1] = blown(end, fight, spot[0], DragonTweaksGameTest::anchor);
				gone[1] = !end.getBlockState(spot[0]).is(Blocks.RESPAWN_ANCHOR);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				spot[0] = BlockPos.containing(fight.dragon().head.position());
				lost[2] = blown(end, fight, spot[0], (level, f, at) -> level.explode(null, level.damageSources().badRespawnPointExplosion(Vec3.atCenterOf(at)), null,
					Vec3.atCenterOf(at), 5.0F, false, Level.ExplosionInteraction.NONE));
				lost[3] = fight.hit(0.0001);
				EndTestSupport.result("dragonBed", "at=" + spot[0] + " bed=" + lost[0] + " anchor=" + lost[1] + " plain=" + lost[2] + " control=" + lost[3] + " used=" + gone[0] + "/" + gone[1]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gone[0], "the bed was not used up: it did not blow");
				helper.assertTrue(gone[1], "the anchor was not used up: it did not blow");
				helper.assertTrue(lost[0] == 0.0F, "a bed took " + lost[0] + " health from the dragon");
				helper.assertTrue(lost[1] == 0.0F, "a respawn anchor took " + lost[1] + " health from the dragon");
				helper.assertTrue(lost[2] == 0.0F, "a bare respawn point blast took " + lost[2] + " health from the dragon");
				helper.assertTrue(lost[3] > 0.0F, "a player's blow did " + lost[3] + " damage: the dragon cannot be hurt at all");
			})
			.thenSucceed();
	}
}
