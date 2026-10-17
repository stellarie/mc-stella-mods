package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The boss of floor 6 and its undead blazes: it holds still, rings itself in flame, volleys, withers, calls blazes, and its death opens the door. */
public class SovereignGameTest {
	static final Vec3 ANCHOR = Vec3.atBottomCenterOf(EndCastle.arenaAnchor());

	/** A player inside the arena, a few blocks from the Sovereign's anchor. */
	static ServerPlayer inArena(final GameTestHelper helper, final ServerLevel end, final double dx, final double dz) {
		return EndTestSupport.join(helper, end, new Vec3(0.5 + dx, EndCastle.standY(EndCastle.FLOORS), 0.5 + dz));
	}

	static Blaze raise(final GameTestHelper helper, final ServerLevel end) {
		Blaze sovereign = UndeadSovereign.spawn(end);
		helper.assertTrue(sovereign != null, "the Sovereign was not raised");
		return sovereign;
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_level", maxTicks = 60)
	public void theSovereignIsALevelOneTwentyBlazeAtTheArenaAnchorWithSixHundredBaseHealth(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = raise(helper, end);
		// The yardstick joins the level.
		// Each sibling that scales health on load
		// scales it too.
		Blaze plain = EntityTypes.BLAZE.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(plain);
		LevelBridge.apply(plain, EndLevels.SOVEREIGN);
		plain.setNoAi(true);
		plain.snapTo(8.5, EndCastle.standY(EndCastle.FLOORS) + 3, 8.5, 0.0F, 0.0F);
		end.addFreshEntity(plain);
		// A tick later every load listener has run.
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				int level = LevelBridge.read(sovereign);
				double ratio = sovereign.getMaxHealth() / plain.getMaxHealth();
				double plainBase = plain.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
				double ceiling = ((net.minecraft.world.entity.ai.attributes.RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
				double intended = plain.getMaxHealth() * (UndeadSovereign.BASE_HEALTH / plainBase);
				EndTestSupport.result("sovereignLevel", "level=" + level + " max=" + sovereign.getMaxHealth() + " plainMax=" + plain.getMaxHealth() + " ratio=" + ratio
					+ " intended=" + intended + " ceiling=" + ceiling + " at=" + sovereign.position() + " scale=" + sovereign.getAttributeValue(Attributes.SCALE));
				boolean persistent = sovereign.isPersistenceRequired();
				boolean named = sovereign.hasCustomName();
				Vec3 at = sovereign.position();
				double max = sovereign.getMaxHealth();
				plain.discard();
				EndTestSupport.fresh(helper);
				helper.assertTrue(sovereign.getType() == EntityTypes.BLAZE, "the Sovereign is no vanilla blaze");
				helper.assertTrue(level == EndLevels.SOVEREIGN, "the Sovereign reads level " + level);
				helper.assertTrue(Math.abs(max - Math.min(intended, ceiling)) < 0.01 * Math.min(intended, ceiling),
					"the Sovereign has " + max + " health; " + UndeadSovereign.BASE_HEALTH + " base at level " + EndLevels.SOVEREIGN + " is " + intended
						+ " and the attribute ceiling is " + ceiling);
				helper.assertTrue(at.distanceTo(ANCHOR) < 1.0E-6, "the Sovereign stands at " + at + ", not at the anchor " + ANCHOR);
				helper.assertTrue(persistent && named, "the Sovereign can despawn or has no name");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_still", maxTicks = 260)
	public void theSovereignDoesNotMoveInTwoHundredTicksWhateverPushesIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		inArena(helper, end, 6.0, 0.0);
		Blaze sovereign = raise(helper, end);
		double[] drift = new double[1];
		int[] samples = new int[1];
		helper.onEachTick(() -> {
			// A shove each tick.
			// Without the reset to its anchor,
			// the shove would carry it away.
			sovereign.setDeltaMovement(0.8, 0.4, 0.3);
			drift[0] = Math.max(drift[0], sovereign.position().distanceTo(ANCHOR));
			samples[0]++;
		});
		helper.startSequence()
			.thenIdle(200)
			.thenExecute(() -> {
				EndTestSupport.result("sovereignStill", "ticks=" + samples[0] + " maxDrift=" + drift[0] + " speed=" + sovereign.getAttributeValue(Attributes.MOVEMENT_SPEED)
					+ " noGravity=" + sovereign.isNoGravity());
				boolean noGravity = sovereign.isNoGravity();
				double speed = sovereign.getAttributeValue(Attributes.MOVEMENT_SPEED);
				EndTestSupport.fresh(helper);
				helper.assertTrue(samples[0] >= 200, "only " + samples[0] + " ticks were sampled");
				helper.assertTrue(drift[0] < 1.0E-6, "the Sovereign drifted " + drift[0] + " blocks");
				helper.assertTrue(speed == 0.0 && noGravity, "the Sovereign can walk or fall: speed " + speed + ", no gravity " + noGravity);
			})
			.thenSucceed();
	}

	/** The length another mod leaves an effect on this victim: it shortens harmful effects by the victim's own stats. */
	static int lengthOn(final Zombie victim, final int ticks, final int amplifier) {
		victim.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, amplifier));
		int length = victim.getEffect(MobEffects.WITHER).getDuration();
		victim.removeEffect(MobEffects.WITHER);
		return length;
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_wither", maxTicks = 40)
	public void aHitFromTheSovereignOrItsChargeAppliesWitherTwoForOneHundredTicksAndNobodyElsesDoesNot(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = raise(helper, end);
		Zombie meleeVictim = EndTestSupport.dummy(end, ANCHOR.add(2.0, 0.0, 0.0));
		Zombie chargeVictim = EndTestSupport.dummy(end, ANCHOR.add(-2.0, 0.0, 0.0));
		Zombie controlVictim = EndTestSupport.dummy(end, ANCHOR.add(0.0, 0.0, 2.0));
		Zombie stranger = EndTestSupport.dummy(end, ANCHOR.add(0.0, 0.0, -2.0));
		int meleeTicks = lengthOn(meleeVictim, UndeadSovereign.WITHER_TICKS, UndeadSovereign.WITHER_AMPLIFIER);
		int chargeTicks = lengthOn(chargeVictim, UndeadSovereign.WITHER_TICKS, UndeadSovereign.WITHER_AMPLIFIER);
		SmallFireball charge = SoulCharges.fire(end, sovereign, new Vec3(-1.0, 0.0, 0.0), SoulCharges.SOVEREIGN_FACTOR);
		helper.assertTrue(charge != null, "the Sovereign's charge was refused");
		meleeVictim.hurtServer(end, end.damageSources().mobAttack(sovereign), 1.0F);
		chargeVictim.hurtServer(end, end.damageSources().fireball(charge, sovereign), 1.0F);
		controlVictim.hurtServer(end, end.damageSources().mobAttack(stranger), 1.0F);
		MobEffectInstance melee = meleeVictim.getEffect(MobEffects.WITHER);
		MobEffectInstance thrown = chargeVictim.getEffect(MobEffects.WITHER);
		MobEffectInstance control = controlVictim.getEffect(MobEffects.WITHER);
		EndTestSupport.result("sovereignWither", "melee=" + (melee == null ? "none" : melee.getAmplifier() + "/" + melee.getDuration() + " of " + meleeTicks)
			+ " charge=" + (thrown == null ? "none" : thrown.getAmplifier() + "/" + thrown.getDuration() + " of " + chargeTicks) + " control=" + (control == null ? "none" : "wither"));
		int meleeLength = melee == null ? -1 : melee.getDuration();
		int chargeLength = thrown == null ? -1 : thrown.getDuration();
		int meleeAmplifier = melee == null ? -1 : melee.getAmplifier();
		int chargeAmplifier = thrown == null ? -1 : thrown.getAmplifier();
		EndTestSupport.fresh(helper);
		helper.assertTrue(meleeAmplifier == UndeadSovereign.WITHER_AMPLIFIER && meleeLength == meleeTicks,
			"the Sovereign's blow left amplifier " + meleeAmplifier + " for " + meleeLength + " ticks, expected " + meleeTicks);
		helper.assertTrue(chargeAmplifier == UndeadSovereign.WITHER_AMPLIFIER && chargeLength == chargeTicks,
			"the Sovereign's charge left amplifier " + chargeAmplifier + " for " + chargeLength + " ticks, expected " + chargeTicks);
		helper.assertTrue(control == null, "a blow from another mob withered the victim");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_ring", maxTicks = 330)
	public void everyTwentyTicksTheSovereignSetsEightToTwelveSoulFlamesWithinTenBlocksAndTheyBurnOutAfterOneHundred(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = raise(helper, end);
		int floorY = EndCastle.standY(EndCastle.FLOORS);
		List<String> bad = new ArrayList<>();
		int[] seen = new int[1];
		helper.onEachTick(() -> {
			List<UndeadSovereign.Pulse> pulses = UndeadSovereign.pulses();
			for (int i = seen[0]; i < pulses.size(); i++) {
				UndeadSovereign.Pulse pulse = pulses.get(i);
				if (pulse.cells().size() < UndeadSovereign.RING_MIN || pulse.cells().size() > UndeadSovereign.RING_MAX) {
					bad.add("pulse " + i + " set " + pulse.cells().size() + " flames");
				}
				for (BlockPos cell : pulse.cells()) {
					boolean flame = end.getBlockState(cell).is(EndBlocks.soulFlame());
					double reach = Math.hypot(cell.getX() + 0.5 - ANCHOR.x, cell.getZ() + 0.5 - ANCHOR.z);
					if (!flame || cell.getY() != floorY || reach > UndeadSovereign.RING_RADIUS + 0.75) {
						bad.add("pulse " + i + " cell " + cell + " flame=" + flame + " reach=" + reach);
					}
				}
			}
			seen[0] = pulses.size();
		});
		helper.startSequence()
			.thenIdle(UndeadSovereign.RING_PERIOD * 6 + 2)
			.thenExecute(() -> {
				helper.assertTrue(UndeadSovereign.pulses().size() >= 6, "only " + UndeadSovereign.pulses().size() + " pulses in " + UndeadSovereign.RING_PERIOD * 6 + " ticks");
				helper.assertTrue(UndeadSovereign.pulses().get(1).at() - UndeadSovereign.pulses().get(0).at() == UndeadSovereign.RING_PERIOD,
					"pulses " + (UndeadSovereign.pulses().get(1).at() - UndeadSovereign.pulses().get(0).at()) + " ticks apart");
				// Let the whole life pass at once.
				// Only a flame a later pulse set may still burn.
				EndClock.advance(end, UndeadSovereign.RING_LIFE + UndeadSovereign.RING_PERIOD);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				List<UndeadSovereign.Pulse> pulses = UndeadSovereign.pulses();
				int burning = 0;
				UndeadSovereign.Pulse first = pulses.get(0);
				for (BlockPos cell : first.cells()) {
					boolean again = pulses.stream().skip(1).filter(later -> later.at() > EndClock.now(end) - UndeadSovereign.RING_LIFE).anyMatch(later -> later.cells().contains(cell));
					if (end.getBlockState(cell).is(EndBlocks.soulFlame()) && !again) {
						burning++;
					}
				}
				EndTestSupport.result("sovereignRing", "pulses=" + pulses.size() + " firstCells=" + first.cells().size() + " stillBurning=" + burning + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "ring defects: " + bad);
				helper.assertTrue(burning == 0, burning + " flames of the first pulse outlived their " + UndeadSovereign.RING_LIFE + " ticks");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_volley", maxTicks = 200)
	public void everySixtyTicksTheSovereignFiresFiveSoulChargesAtPlayersInRangeAndFiresNoneAtNobody(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = raise(helper, end);
		List<String> bad = new ArrayList<>();
		int[] seen = new int[1];
		helper.onEachTick(() -> {
			List<UndeadSovereign.Volley> volleys = UndeadSovereign.volleys();
			for (int i = seen[0]; i < volleys.size(); i++) {
				for (UUID id : volleys.get(i).charges()) {
					Entity charge = end.getEntity(id);
					if (!(charge instanceof SmallFireball fireball) || fireball.getOwner() != sovereign || !SoulCharges.is(fireball)) {
						bad.add("volley " + i + " charge " + id + " is " + charge);
					}
				}
			}
			seen[0] = volleys.size();
		});
		int[] alone = new int[1];
		helper.startSequence()
			.thenIdle(UndeadSovereign.VOLLEY_PERIOD + 10)
			.thenExecute(() -> {
				alone[0] = UndeadSovereign.volleys().size();
				inArena(helper, end, 8.0, 0.0);
			})
			.thenWaitUntil(() -> helper.assertTrue(!UndeadSovereign.volleys().isEmpty(), "no volley yet"))
			.thenExecute(() -> {
				UndeadSovereign.Volley volley = UndeadSovereign.volleys().get(0);
				EndTestSupport.result("sovereignVolley", "alone=" + alone[0] + " volleys=" + UndeadSovereign.volleys().size() + " first=" + volley.charges().size() + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(alone[0] == 0, alone[0] + " volleys flew with nobody in range");
				helper.assertTrue(volley.charges().size() == UndeadSovereign.VOLLEY_SIZE, "a volley holds " + volley.charges().size() + " charges");
				helper.assertTrue(bad.isEmpty(), "volley defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_brood", maxTicks = 100)
	public void everyTwoHundredTicksTheSovereignCallsThreeUndeadBlazesUpToTwelveAliveByRecord(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		inArena(helper, end, 8.0, 0.0);
		raise(helper, end);
		List<Integer> added = new ArrayList<>();
		var sequence = helper.startSequence();
		for (int call = 0; call < 6; call++) {
			// One tick after the clock jumps,
			// the due call happens once.
			// Its blazes join the record.
			sequence = sequence
				.thenExecute(() -> EndClock.advance(end, UndeadSovereign.SUMMON_PERIOD))
				.thenIdle(2)
				.thenExecute(() -> {
					added.add(UndeadSovereign.broods().stream().mapToInt(brood -> brood.blazes().size()).sum());
					helper.assertTrue(UndeadBlazes.alive(end).size() <= UndeadSovereign.SUMMON_CAP, "more than " + UndeadSovereign.SUMMON_CAP + " undead blazes stand");
				});
		}
		sequence
			.thenExecute(() -> {
				List<String> bad = new ArrayList<>();
				for (UndeadSovereign.Brood brood : UndeadSovereign.broods()) {
					for (UUID id : brood.blazes()) {
						Entity entity = end.getEntity(id);
						if (!(entity instanceof Blaze blaze) || !UndeadBlazes.is(blaze) || LevelBridge.read(blaze) != EndLevels.UNDEAD_BLAZE || !blaze.isAlive()) {
							bad.add(id + " is " + entity);
						}
					}
				}
				int alive = UndeadBlazes.alive(end).size();
				int recorded = UndeadSovereign.broods().stream().mapToInt(brood -> brood.blazes().size()).sum();
				EndTestSupport.result("sovereignBrood", "afterEachCall=" + added + " alive=" + alive + " recorded=" + recorded + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(!added.isEmpty() && added.get(0) == UndeadSovereign.SUMMON_COUNT, "the first call added " + (added.isEmpty() ? -1 : added.get(0)));
				helper.assertTrue(alive == UndeadSovereign.SUMMON_CAP && recorded == UndeadSovereign.SUMMON_CAP, "alive " + alive + ", recorded " + recorded + ", cap " + UndeadSovereign.SUMMON_CAP);
				helper.assertTrue(bad.isEmpty(), "brood defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_door", maxTicks = 80)
	public void theFloorSixDoorOpensOnlyWhenTheSovereignDies(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		List<BlockPos> door = new ArrayList<>();
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		for (CastleLayout.Door cells : layout.floor(EndCastle.FLOORS).doors()) {
			if (cells.barrier()) {
				for (int x = cells.x0(); x <= cells.x1(); x++) {
					for (int z = cells.z0(); z <= cells.z1(); z++) {
						for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
							door.add(EndTestSupport.world(EndCastle.min(), x, y, z));
						}
					}
				}
			}
		}
		helper.assertTrue(!door.isEmpty(), "floor 6 has no barrier door");
		Blaze sovereign = raise(helper, end);
		long shut = door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
		helper.startSequence()
			.thenIdle(40)
			.thenExecute(() -> {
				long stillShut = door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
				helper.assertTrue(stillShut == door.size(), "the door opened by itself: " + stillShut + " of " + door.size() + " barrier cells stand");
				sovereign.kill(end);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				long open = door.stream().filter(pos -> end.getBlockState(pos).isAir()).count();
				EndState state = EndCastles.state(end);
				EndTestSupport.result("sovereignDoor", "cells=" + door.size() + " shutBefore=" + shut + " openAfter=" + open + " deadAt=" + state.sovereignDeadAt() + " now=" + EndClock.now(end));
				EndTestSupport.fresh(helper);
				helper.assertTrue(shut == door.size(), "the door was not shut while the Sovereign lived: " + shut + " of " + door.size());
				helper.assertTrue(open == door.size(), "the door stayed shut after the Sovereign died: " + open + " of " + door.size() + " cells are open");
				helper.assertTrue(state.sovereignDeadAt() != EndState.NEVER, "the death time was not stored");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_respawn", maxTicks = 160)
	public void theSovereignRisesAgainAfterThirtySixThousandTicksAndShutsTheDoorBehindIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		inArena(helper, end, 8.0, 0.0);
		Blaze first = raise(helper, end);
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		CastleLayout.Door barrier = EndCastles.layout(end).floor(EndCastle.FLOORS).doors().stream().filter(CastleLayout.Door::barrier).findFirst().orElseThrow();
		BlockPos probe = EndTestSupport.world(EndCastle.min(), barrier.x0(), slab + 1, barrier.z0());
		first.kill(end);
		boolean[] early = new boolean[2];
		long[] diedAt = new long[1];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				diedAt[0] = EndCastles.state(end).sovereignDeadAt();
				helper.assertTrue(diedAt[0] != EndState.NEVER, "the death was not stored");
				helper.assertTrue(end.getBlockState(probe).isAir(), "the door did not open");
				// Ten ticks short of the time.
				// The next three ticks must not raise it.
				EndClock.advance(end, UndeadSovereign.RESPAWN_TICKS - 10 - (EndClock.now(end) - diedAt[0]));
			})
			.thenIdle(3)
			.thenExecute(() -> {
				early[0] = UndeadSovereign.current(end) != null;
				EndClock.advance(end, 10);
			})
			.thenWaitUntil(() -> helper.assertTrue(UndeadSovereign.current(end) != null, "the Sovereign has not risen again"))
			.thenExecute(() -> {
				Blaze second = UndeadSovereign.current(end);
				early[1] = end.getBlockState(probe).is(Blocks.BARRIER);
				EndTestSupport.result("sovereignRespawn", "tooEarly=" + early[0] + " risen=" + (second != null) + " doorShut=" + early[1] + " newBody=" + (second != first));
				EndTestSupport.fresh(helper);
				helper.assertTrue(!early[0], "the Sovereign rose before " + UndeadSovereign.RESPAWN_TICKS + " ticks passed");
				helper.assertTrue(second != null && second != first, "no new Sovereign stands");
				helper.assertTrue(early[1], "the door stayed open behind the new Sovereign");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_bar", maxTicks = 60)
	public void theSovereignsBossBarShowsToPlayersWithinFortyEightBlocksAndNoOtherAndFollowsItsHealth(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer near = inArena(helper, end, 12.0, 0.0);
		ServerPlayer far = EndTestSupport.join(helper, end, new Vec3(ANCHOR.x + 70.0, EndCastle.standY(EndCastle.FLOORS), ANCHOR.z));
		Blaze sovereign = raise(helper, end);
		sovereign.setHealth(sovereign.getMaxHealth() * 0.5F);
		helper.startSequence()
			.thenIdle(12)
			.thenExecute(() -> {
				var bar = UndeadSovereign.bar();
				helper.assertTrue(bar != null, "the Sovereign has no boss bar");
				boolean sawNear = bar.getPlayers().contains(near);
				boolean sawFar = bar.getPlayers().contains(far);
				float progress = bar.getProgress();
				EndTestSupport.result("sovereignBar", "near=" + sawNear + " far=" + sawFar + " progress=" + progress + " range=" + UndeadSovereign.BAR_RANGE);
				EndTestSupport.fresh(helper);
				helper.assertTrue(sawNear, "a player 12 blocks away sees no bar");
				helper.assertTrue(!sawFar, "a player 70 blocks away sees the bar");
				helper.assertTrue(Math.abs(progress - 0.5F) < 0.02F, "the bar shows " + progress + " of the Sovereign's health");
			})
			.thenSucceed();
	}

	/** Everything the undead charge test builds, held for its later steps. */
	static final class Rig {
		Blaze undead;
		SmallFireball slow;
		SmallFireball vanilla;
		Zombie victim;
		Zombie control;
		int level;
		boolean marked;
		boolean charged;
		boolean plainCharged;
		int expectedTicks;
		int directAmplifier;
		int directTicks;
		final double[] slowX = new double[16];
		final double[] fastX = new double[16];
		int tick;
	}

	@GameTest(environment = "mod-stella-end-test:undead_charge", maxTicks = 80)
	public void anUndeadBlazesChargeFliesAtHalfTheVanillaSpeedAndWithersWhatItHits(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int y = EndCastle.standY(EndCastle.FLOORS) + 2;
		Rig rig = new Rig();
		helper.onEachTick(() -> {
			if (rig.slow != null && rig.tick < rig.slowX.length) {
				rig.slowX[rig.tick] = rig.slow.getX();
				rig.fastX[rig.tick] = rig.vanilla.getX();
				rig.tick++;
			}
		});
		helper.startSequence()
			// A fireball in a chunk not yet entity ticking
			// meets the load hook late.
			// The test starts where that cannot happen.
			.thenWaitUntil(() -> helper.assertTrue(end.isPositionEntityTicking(new net.minecraft.core.BlockPos(-12, y, 5)), "the arena is not entity ticking yet"))
			.thenExecute(() -> {
				rig.undead = UndeadBlazes.spawn(end, new Vec3(-13.0, y - 1, 5.0));
				helper.assertTrue(rig.undead != null, "no undead blaze was made");
				rig.undead.setNoAi(true);
				Blaze plain = EntityTypes.BLAZE.create(end, EntitySpawnReason.SPAWNER);
				LevelBridge.claim(plain);
				plain.setNoAi(true);
				plain.snapTo(-13.0, y - 1, -5.0, 0.0F, 0.0F);
				end.addFreshEntity(plain);
				rig.level = LevelBridge.read(rig.undead);
				rig.marked = rig.undead.getAttached(UndeadBlazes.MARK) != null && rig.undead.getAttached(UndeadBlazes.MARK) == UndeadBlazes.MARK_BLAZE;
				// A blaze's own goal builds its fireball so.
				// The hook must catch it on load.
				SmallFireball slow = new SmallFireball(end, rig.undead, new Vec3(1.0, 0.0, 0.0));
				slow.setPos(-12.0, y, 5.0);
				end.addFreshEntity(slow);
				SmallFireball vanilla = new SmallFireball(end, plain, new Vec3(1.0, 0.0, 0.0));
				vanilla.setPos(-12.0, y, -5.0);
				end.addFreshEntity(vanilla);
				rig.victim = EndTestSupport.dummy(end, new Vec3(-9.0, y - 1.0, 5.0));
				rig.control = EndTestSupport.dummy(end, new Vec3(-9.0, y - 1.0, -5.0));
				rig.charged = SoulCharges.is(slow);
				rig.plainCharged = SoulCharges.is(vanilla);
				// Other mods shorten harmful effects by stats.
				// So the length is judged on the same victim,
				// with the effect given by hand.
				Zombie struck = EndTestSupport.dummy(end, new Vec3(-9.0, y - 1.0, 0.0));
				rig.expectedTicks = lengthOn(struck, UndeadBlazes.WITHER_TICKS, UndeadBlazes.WITHER_AMPLIFIER);
				struck.hurtServer(end, end.damageSources().fireball(slow, rig.undead), 1.0F);
				MobEffectInstance directEffect = struck.getEffect(MobEffects.WITHER);
				rig.directAmplifier = directEffect == null ? -1 : directEffect.getAmplifier();
				rig.directTicks = directEffect == null ? -1 : directEffect.getDuration();
				rig.slow = slow;
				rig.vanilla = vanilla;
			})
			.thenIdle(20)
			.thenExecute(() -> {
				double slowStep = rig.slowX[4] - rig.slowX[1];
				double fastStep = rig.fastX[4] - rig.fastX[1];
				MobEffectInstance wither = rig.victim.getEffect(MobEffects.WITHER);
				EndTestSupport.result("undeadCharge", "level=" + rig.level + " marked=" + rig.marked + " charged=" + rig.charged + " slowStep=" + slowStep + " vanillaStep=" + fastStep
					+ " ratio=" + slowStep / fastStep + " wither=" + (wither == null ? "none" : wither.getAmplifier() + "/" + wither.getDuration()));
				boolean controlWithered = rig.control.hasEffect(MobEffects.WITHER);
				EndTestSupport.fresh(helper);
				helper.assertTrue(rig.level == EndLevels.UNDEAD_BLAZE, "the undead blaze reads level " + rig.level);
				helper.assertTrue(rig.marked, "the undead blaze carries no client mark");
				helper.assertTrue(rig.charged && !rig.plainCharged, "the hook marked the wrong charges: undead " + rig.charged + ", plain " + rig.plainCharged);
				helper.assertTrue(fastStep > 0.3 && slowStep < fastStep, "the soul charge moves " + slowStep + " a tick against the vanilla " + fastStep);
				helper.assertTrue(Math.abs(slowStep / fastStep - SoulCharges.UNDEAD_FACTOR) < 0.05, "the speed ratio is " + slowStep / fastStep);
				helper.assertTrue(wither != null && wither.getAmplifier() == UndeadBlazes.WITHER_AMPLIFIER && wither.getDuration() > 0 && wither.getDuration() <= UndeadBlazes.WITHER_TICKS,
					"the charge left " + wither);
				helper.assertTrue(!controlWithered, "a vanilla fireball withered its victim");
				helper.assertTrue(rig.directAmplifier == UndeadBlazes.WITHER_AMPLIFIER && rig.directTicks == rig.expectedTicks,
					"a blow from the undead blaze's charge left amplifier " + rig.directAmplifier + " for " + rig.directTicks + " ticks, expected " + rig.expectedTicks);
			})
			.thenSucceed();
	}
}
