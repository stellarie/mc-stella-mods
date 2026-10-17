package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The polished dragon: its health and speed, the wind-up, the airdrop, the short breath, and the perch beside the sleep. */
public class DragonPolishGameTest {
	/** What the cruise and the rush were before the polish; the new cruise is a share of the first. */
	static final double CRUISE_BEFORE = 1.2;
	static final double RUSH_BEFORE = 2.6;
	static final double DECK_TOP = Rooftop.DECK_Y + 1;

	@GameTest(environment = "mod-stella-end-test:dragon_health", maxTicks = 60)
	public void theDragonHasExactlySixHundredThousandMaximumHealth(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		double ceiling = ((RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		helper.startSequence()
			// A tick later every load listener has run, the health scale of the rpg mod included.
			.thenIdle(4)
			.thenExecute(() -> {
				float max = dragon.getMaxHealth();
				float health = dragon.getHealth();
				EndTestSupport.result("dragonHealth", "max=" + max + " health=" + health + " ceiling=" + ceiling + " constant=" + DragonRules.MAX_HEALTH);
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.MAX_HEALTH == 600000.0F, "the constant reads " + DragonRules.MAX_HEALTH);
				helper.assertTrue(ceiling >= 600000.0, "the health attribute stops at " + ceiling + ", so this run cannot hold the fixed health");
				helper.assertTrue(Math.abs(max - 600000.0F) < 1.0F, "the dragon has " + max + " maximum health");
				helper.assertTrue(Math.abs(health - max) < 1.0F, "the dragon stands at " + health + " of " + max);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_cruise", maxTicks = 120)
	public void theCruiseIsThirtyPercentOfTheOldSpeedAndTheRushIsTheOldOne(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		// Vanilla's own movement adds the dragon's motion to the controller's step,
		// so the test reads the speed the controller sets.
		double[] steady = new double[1];
		int[] tick = new int[1];
		helper.onEachTick(() -> {
			if (tick[0] > 12 && tick[0] <= 40) {
				steady[0] = Math.max(steady[0], state.velocity.length());
			}
			tick[0]++;
		});
		helper.startSequence()
			.thenIdle(8)
			.thenExecute(() -> {
				// Thrown to the edge of its leash it flies home at its cruise.
				dragon.setPos(Rooftop.hoverPoint().x + 44.0, Rooftop.hoverPoint().y, Rooftop.hoverPoint().z);
				tick[0] = 0;
			})
			.thenIdle(45)
			.thenExecute(() -> {
				EndTestSupport.result("dragonCruise", "steady=" + steady[0] + " cruise=" + DragonFlight.CRUISE + " rush=" + DragonFlight.RUSH + " expected=" + CRUISE_BEFORE * 0.3);
				EndTestSupport.fresh(helper);
				helper.assertTrue(Math.abs(DragonFlight.CRUISE - 0.3 * CRUISE_BEFORE) < 1.0E-9, "the cruise constant is " + DragonFlight.CRUISE);
				helper.assertTrue(DragonFlight.RUSH == RUSH_BEFORE, "the rush constant is " + DragonFlight.RUSH);
				helper.assertTrue(steady[0] <= 0.3 * CRUISE_BEFORE + 0.01, "the dragon cruised at " + steady[0] + " a tick");
				helper.assertTrue(steady[0] >= 0.3 * CRUISE_BEFORE * 0.9, "the dragon cruised at only " + steady[0] + " a tick");
			})
			.thenSucceed();
	}

	/** The share of the rush a flight must reach. A dive from the hover height is short: it ends before the dragon is at full speed. */
	static void rush(final GameTestHelper helper, final String environment, final DragonState.Attack attack, final double least) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		double[] peak = new double[1];
		helper.onEachTick(() -> peak[0] = Math.max(peak[0], arena.state().velocity.length()));
		EndDragons.force(arena.dragon(), attack);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!arena.state().windups().isEmpty(), "the dragon has not begun its wind-up"))
			.thenExecute(() -> EndClock.advance(arena.end(), DragonRules.WINDUP_TICKS))
			.thenWaitUntil(() -> helper.assertTrue(arena.state().windups().get(0).launchedAt() != EndState.NEVER, "the attack has not started"))
			.thenIdle(45)
			.thenExecute(() -> {
				EndTestSupport.result(environment, "peak=" + peak[0] + " rush=" + RUSH_BEFORE);
				DragonAttackGameTest.leave(helper, arena);
				helper.assertTrue(peak[0] >= RUSH_BEFORE * least, "the " + attack + " flew only " + peak[0] + " a tick");
				helper.assertTrue(peak[0] <= RUSH_BEFORE + 0.1, "the " + attack + " flew " + peak[0] + " a tick");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_rush_swoop", maxTicks = 300)
	public void aSwoopStillFliesAtTheOldRushSpeed(final GameTestHelper helper) {
		rush(helper, "dragonRushSwoop", DragonState.Attack.SWOOP, 0.8);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_rush_dive", maxTicks = 300)
	public void aDiveStillFliesAtTheOldRushSpeed(final GameTestHelper helper) {
		rush(helper, "dragonRushDive", DragonState.Attack.DIVE, 0.6);
	}

	static void windup(final GameTestHelper helper, final String environment, final DragonState.Attack attack) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		Vec3[] settled = new Vec3[1];
		double[] drift = new double[1];
		List<String> bad = new ArrayList<>();
		helper.onEachTick(() -> {
			if (arena.state().windups().isEmpty()) {
				return;
			}
			DragonState.Windup windup = arena.state().windups().get(0);
			// It brakes from its cruise in the first ticks and hangs still after.
			if (settled[0] == null && EndClock.now(arena.end()) - windup.startedAt() >= 30) {
				settled[0] = arena.dragon().position();
			}
			if (windup.launchedAt() == EndState.NEVER) {
				if (settled[0] != null) {
					drift[0] = Math.max(drift[0], arena.dragon().position().distanceTo(settled[0]));
				}
				if (!arena.state().swoops().isEmpty() || !arena.state().dives().isEmpty()) {
					bad.add("the attack struck during its wind-up");
				}
			}
		});
		EndDragons.force(arena.dragon(), attack);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!arena.state().windups().isEmpty(), "the dragon has not begun its wind-up"))
			.thenWaitUntil(() -> helper.assertTrue(arena.state().windups().get(0).launchedAt() != EndState.NEVER, "the attack has not started"))
			.thenExecute(() -> {
				DragonState.Windup windup = arena.state().windups().get(0);
				long length = windup.launchedAt() - windup.startedAt();
				EndTestSupport.result(environment, "attack=" + windup.attack() + " length=" + length + " bursts=" + windup.bursts() + " sent=" + windup.sent() + " drift=" + drift[0]);
				DragonAttackGameTest.leave(helper, arena);
				helper.assertTrue(windup.attack() == attack, "the wind-up was for " + windup.attack());
				helper.assertTrue(length == DragonRules.WINDUP_TICKS, "the " + attack + " started " + length + " ticks after its wind-up began");
				helper.assertTrue(DragonRules.WINDUP_TICKS == 100, "the wind-up constant is " + DragonRules.WINDUP_TICKS);
				helper.assertTrue(windup.bursts() > 0 && windup.sent() > 0, "the wind-up sent " + windup.bursts() + " bursts of End particles to " + windup.sent() + " players");
				helper.assertTrue(settled[0] != null && drift[0] < 1.0, "the dragon drifted " + drift[0] + " blocks while it should hang still");
				helper.assertTrue(bad.isEmpty(), "wind-up defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_windup_swoop", maxTicks = 300)
	public void aSwoopStartsOneHundredTicksAfterItsWindUpBeginsWithEndParticlesMeanwhile(final GameTestHelper helper) {
		windup(helper, "dragonWindupSwoop", DragonState.Attack.SWOOP);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_windup_dive", maxTicks = 300)
	public void aDiveStartsOneHundredTicksAfterItsWindUpBeginsWithEndParticlesMeanwhile(final GameTestHelper helper) {
		windup(helper, "dragonWindupDive", DragonState.Attack.DIVE);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_barrage_disc", maxTicks = 400)
	public void aBarrageAtAPlayerNearTheRimLandsOnTheWholeDiscAndNotOnlyInsideASquare(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		// Seventeen blocks out on the z axis is inside the disc and outside the square of 15 the barrage used.
		EndTestSupport.joinOnRoof(helper, end, 0.0, 17.0);
		List<Vec3> landings = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		int[] seen = new int[1];
		helper.onEachTick(() -> {
			List<DragonState.Shot> shots = state.shots();
			for (int i = seen[0]; i < shots.size(); i++) {
				Vec3 lands = DragonAttackGameTest.landing(end.getEntity(shots.get(i).id()));
				if (lands == null) {
					bad.add("shot " + i + " does not come down");
				} else {
					landings.add(lands);
				}
			}
			seen[0] = shots.size();
		});
		EndDragons.force(dragon, DragonState.Attack.BARRAGE);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.shots().size() >= DragonRules.BARRAGE_SHOTS, "only " + state.shots().size() + " shots have left"))
			.thenIdle(5)
			.thenExecute(() -> {
				double farthest = landings.stream().mapToDouble(at -> Math.abs(at.z - 0.5)).max().orElse(0.0);
				long outside = landings.stream().filter(at -> !Rooftop.contains(at.x, DECK_TOP, at.z)).count();
				EndTestSupport.result("dragonBarrageDisc", "landings=" + landings.size() + " farthestZ=" + farthest + " outside=" + outside + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "barrage defects: " + bad);
				helper.assertTrue(outside == 0, outside + " shots landed off the disc");
				helper.assertTrue(farthest > 15.5, "no shot landed past the old square: the farthest was " + farthest);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_airdrop", maxTicks = 400)
	public void theAirdropLandsTwoArchersAndTwoWitherSkeletonsInFullNetheriteOnTheRooftop(final GameTestHelper helper) {
		DragonAttackGameTest.Arena arena = DragonAttackGameTest.arena(helper);
		EndDragons.force(arena.dragon(), DragonState.Attack.AIRDROP);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!arena.state().airdrops().isEmpty(), "the dragon has dropped nothing"))
			.thenWaitUntil(() -> {
				DragonState.Airdrop drop = arena.state().airdrops().get(0);
				List<UUID> all = new ArrayList<>(drop.archers());
				all.addAll(drop.withers());
				for (UUID id : all) {
					Entity mob = arena.end().getEntity(id);
					helper.assertTrue(mob != null && mob.isAlive() && mob.onGround(), "a dropped mob has not landed: " + mob);
				}
			})
			.thenExecute(() -> {
				DragonState.Airdrop drop = arena.state().airdrops().get(0);
				List<String> bad = new ArrayList<>();
				for (UUID id : drop.archers()) {
					Entity mob = arena.end().getEntity(id);
					if (!(mob instanceof Skeleton skeleton) || !skeleton.getMainHandItem().is(Items.BOW)) {
						bad.add("archer " + id + " is " + mob);
					} else if (!WyvernPolishGameTest.shoots(skeleton)) {
						bad.add("archer " + id + " has no bow goal");
					}
				}
				for (UUID id : drop.withers()) {
					Entity mob = arena.end().getEntity(id);
					if (!(mob instanceof WitherSkeleton skeleton) || !skeleton.getMainHandItem().is(Items.NETHERITE_SWORD)) {
						bad.add("wither skeleton " + id + " is " + mob);
					}
				}
				List<UUID> all = new ArrayList<>(drop.archers());
				all.addAll(drop.withers());
				for (UUID id : all) {
					if (!(arena.end().getEntity(id) instanceof net.minecraft.world.entity.Mob mob)) {
						continue;
					}
					boolean armoured = mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
						&& mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
						&& mob.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
						&& mob.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
					if (!armoured) {
						bad.add(id + " is not in full netherite");
					}
					if (LevelBridge.read(mob) != 120) {
						bad.add(id + " reads level " + LevelBridge.read(mob));
					}
					if (!mob.entityTags().contains(EndDragons.SIDE_TAG)) {
						bad.add(id + " carries no side mark");
					}
					if (!Rooftop.contains(mob.getX(), mob.getY(), mob.getZ())) {
						bad.add(id + " landed outside the rooftop at " + mob.position());
					}
				}
				EndTestSupport.result("dragonAirdrop", "archers=" + drop.archers().size() + " withers=" + drop.withers().size() + " bad=" + bad.size());
				int archers = drop.archers().size();
				int withers = drop.withers().size();
				DragonAttackGameTest.leave(helper, arena);
				helper.assertTrue(archers == DragonRules.AIRDROP_ARCHERS && archers == 2, "the airdrop held " + archers + " archers");
				helper.assertTrue(withers == DragonRules.AIRDROP_WITHERS && withers == 2, "the airdrop held " + withers + " wither skeletons");
				helper.assertTrue(bad.isEmpty(), "airdrop defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_breath", maxTicks = 160)
	public void aLingeringBreathCloudOfTheDragonLastsSixtyTicksAndOtherCloudsKeepTheirLife(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		AreaEffectCloud plain = new AreaEffectCloud(end, -12.5, DECK_TOP + 1, -12.5);
		plain.setDuration(600);
		plain.setRadius(2.0F);
		end.addFreshEntity(plain);
		DragonFireball fireball = new DragonFireball(end, dragon, new Vec3(0.0, -1.0, 0.0));
		fireball.setPos(4.5, DECK_TOP + 6, 4.5);
		end.addFreshEntity(fireball);
		long[] born = {-1};
		long[] gone = {-1};
		int[] duration = {-1};
		int[] wait = {-1};
		helper.onEachTick(() -> {
			List<UUID> clouds = DragonBreath.clouds();
			if (clouds.isEmpty()) {
				return;
			}
			Entity cloud = end.getEntity(clouds.get(0));
			if (born[0] < 0 && cloud instanceof AreaEffectCloud effect) {
				born[0] = helper.getTick();
				duration[0] = effect.getDuration();
				wait[0] = effect.getWaitTime();
			} else if (born[0] >= 0 && gone[0] < 0 && (cloud == null || cloud.isRemoved())) {
				gone[0] = helper.getTick();
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(gone[0] >= 0, "the cloud has not gone: born " + born[0] + ", clouds " + DragonBreath.clouds().size()))
			.thenExecute(() -> {
				long life = gone[0] - born[0];
				int plainDuration = plain.getDuration();
				boolean plainAlive = plain.isAlive();
				EndTestSupport.result("dragonBreath", "duration=" + duration[0] + " wait=" + wait[0] + " life=" + life + " plain=" + plainDuration + "/" + plainAlive);
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.BREATH_TICKS == 60, "the breath constant is " + DragonRules.BREATH_TICKS);
				helper.assertTrue(duration[0] + wait[0] == DragonRules.BREATH_TICKS, "the cloud was set to last " + duration[0] + " ticks after a wait of " + wait[0]);
				helper.assertTrue(life >= DragonRules.BREATH_TICKS - 3 && life <= DragonRules.BREATH_TICKS + 4, "the cloud lasted " + life + " ticks");
				helper.assertTrue(plainDuration == 600 && plainAlive, "a cloud of nobody's was shortened to " + plainDuration);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:perch_draw", maxTicks = 20)
	public void theDelayToTheNextPerchIsDrawnFromSixHundredToNineHundredTicks(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int i = 0; i < 2000; i++) {
			int delay = DragonPerch.draw(random);
			low = Math.min(low, delay);
			high = Math.max(high, delay);
		}
		EndTestSupport.result("perchDraw", "low=" + low + " high=" + high);
		helper.assertTrue(DragonRules.PERCH_MIN == 600 && DragonRules.PERCH_MAX == 900 && DragonRules.PERCH_TICKS == 200, "the perch constants are off");
		helper.assertTrue(low >= 600 && high <= 900, "the delay ran from " + low + " to " + high);
		helper.assertTrue(low <= 610 && high >= 890, "the delay only spans " + low + " to " + high);
		helper.succeed();
	}

	/** Sends the dragon to its perch by skipping the clock to the moment it is due, and waits for the perch. */
	static void toPerch(final GameTestHelper helper, final DragonRoostGameTest.Fight fight) {
		EndClock.advance(fight.end(), fight.state().perchAt() - EndClock.now(fight.end()) + 1);
	}

	@GameTest(environment = "mod-stella-end-test:perch_cycle", maxTicks = 500)
	public void everySixHundredToNineHundredTicksTheDragonLandsOnThePortalForTwoHundredTicksTakingNormalDamageWhileBreathCloudsRain(final GameTestHelper helper) {
		DragonRoostGameTest.Fight fight = DragonRoostGameTest.fight(helper);
		DragonState state = fight.state();
		ServerLevel end = fight.end();
		float[] taken = new float[2];
		long[] early = new long[2];
		Map<UUID, Vec3> seen = new HashMap<>();
		Map<UUID, Integer> lives = new HashMap<>();
		List<String> bad = new ArrayList<>();
		helper.onEachTick(() -> {
			if (state.perches().isEmpty()) {
				return;
			}
			for (UUID id : state.perches().get(0).clouds()) {
				if (end.getEntity(id) instanceof AreaEffectCloud cloud && !seen.containsKey(id)) {
					seen.put(id, cloud.position());
					lives.put(id, cloud.getDuration() + cloud.getWaitTime());
					if (cloud.getOwner() != fight.dragon()) {
						bad.add("a cloud of the perch is not the dragon's");
					}
				}
			}
		});
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				early[0] = state.perchAt() - EndClock.now(end);
				helper.assertTrue(state.perchDelay() >= DragonRules.PERCH_MIN && state.perchDelay() <= DragonRules.PERCH_MAX, "the first delay is " + state.perchDelay());
				helper.assertTrue(early[0] > DragonRules.PERCH_MIN - 20 && early[0] <= DragonRules.PERCH_MAX, "the perch is due in " + early[0] + " ticks");
				taken[0] = fight.blows(0.8, 0.01);
				fight.healTo(0.8);
				// Five ticks short of the time the dragon still flies free.
				EndClock.advance(end, early[0] - 5);
			})
			.thenIdle(2)
			.thenExecute(() -> {
				helper.assertTrue(state.mode() != DragonState.Mode.PERCH, "the dragon perched before its time");
				EndClock.advance(end, 12);
			})
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.PERCH, "the dragon is " + state.mode()))
			.thenWaitUntil(() -> helper.assertTrue(!state.perches().isEmpty() && state.perches().get(0).landedAt() != EndState.NEVER,
				"the dragon has not touched down, it is at " + fight.dragon().position() + ", the portal at " + Rooftop.roostPoint(end)))
			.thenIdle(40)
			.thenExecute(() -> {
				helper.assertTrue(fight.dragon().position().distanceTo(Rooftop.roostPoint(end)) < 2.5, "the dragon sits at " + fight.dragon().position());
				taken[1] = fight.blows(0.8, 0.01);
				fight.healTo(0.8);
				int alive = 0;
				for (UUID id : seen.keySet()) {
					alive += end.getEntity(id) != null ? 1 : 0;
					Vec3 at = seen.get(id);
					if (!Rooftop.contains(at.x, DECK_TOP, at.z)) {
						bad.add("a cloud fell outside the rooftop disc at " + at);
					}
					if (lives.get(id) != DragonRules.BREATH_TICKS) {
						bad.add("a cloud of the perch lasts " + lives.get(id) + " ticks");
					}
				}
				long left = state.perches().get(0).landedAt() + DragonRules.PERCH_TICKS - EndClock.now(end);
				EndTestSupport.result("perchCycle", "delay=" + early[0] + " clouds=" + seen.size() + " alive=" + alive + " ratio=" + taken[1] / taken[0] + " left=" + left + " bad=" + bad.size());
				helper.assertTrue(seen.size() >= 12, "only " + seen.size() + " clouds rained");
				helper.assertTrue(alive >= 10, "only " + alive + " clouds hang over the deck at once");
				helper.assertTrue(Math.abs(taken[1] / taken[0] - 1.0F) < 0.05, "the perching dragon took " + taken[1] / taken[0] + " of the damage");
				EndClock.advance(end, left - 6);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(state.mode() == DragonState.Mode.PERCH, "the perch ended early: " + state.mode());
				EndClock.advance(end, 6);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				DragonState.Perch perch = state.perches().get(0);
				long next = state.perchAt() - EndClock.now(end);
				EndTestSupport.result("perchEnd", "mode=" + state.mode() + " sat=" + (perch.endedAt() - perch.landedAt()) + " next=" + next);
				DragonState.Mode mode = state.mode();
				long sat = perch.endedAt() - perch.landedAt();
				boolean clean = bad.isEmpty();
				List<String> defects = List.copyOf(bad);
				EndTestSupport.fresh(helper);
				helper.assertTrue(mode != DragonState.Mode.PERCH, "the perch did not end after " + DragonRules.PERCH_TICKS + " ticks");
				helper.assertTrue(perch.endedAt() != EndState.NEVER && sat >= DragonRules.PERCH_TICKS && sat <= DragonRules.PERCH_TICKS + 12, "the dragon sat " + sat + " ticks");
				helper.assertTrue(next >= DragonRules.PERCH_MIN - 12 && next <= DragonRules.PERCH_MAX, "the next perch is due in " + next + " ticks");
				helper.assertTrue(clean, "perch defects: " + defects);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:perch_pause", maxTicks = 200)
	public void theSleepPausesThePerchTimer(final GameTestHelper helper) {
		DragonRoostGameTest.Fight fight = DragonRoostGameTest.fight(helper);
		DragonState state = fight.state();
		long[] remaining = new long[2];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				remaining[0] = state.perchAt() - EndClock.now(fight.end());
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(state.mode() == DragonState.Mode.ROOST, "the dragon is " + state.mode());
				EndClock.advance(fight.end(), DragonRules.ROOST_TICKS);
			})
			.thenIdle(4)
			.thenExecute(() -> {
				remaining[1] = state.perchAt() - EndClock.now(fight.end());
				DragonState.Mode mode = state.mode();
				EndTestSupport.result("perchPause", "before=" + remaining[0] + " after=" + remaining[1] + " mode=" + mode);
				EndTestSupport.fresh(helper);
				helper.assertTrue(mode != DragonState.Mode.ROOST && mode != DragonState.Mode.PERCH, "the dragon is " + mode + " after the sleep");
				helper.assertTrue(remaining[0] > 500, "the perch was due in " + remaining[0]);
				helper.assertTrue(Math.abs(remaining[1] - remaining[0]) <= 15, "the perch timer ran through the sleep: " + remaining[0] + " before, " + remaining[1] + " after");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:perch_sleep", maxTicks = 300)
	public void aSleepThatBeginsOnAPerchTakesAQuarterOfTheDamageAndNotLess(final GameTestHelper helper) {
		DragonRoostGameTest.Fight fight = DragonRoostGameTest.fight(helper);
		DragonState state = fight.state();
		float[] taken = new float[2];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				taken[0] = fight.blows(0.8, 0.01);
				toPerch(helper, fight);
			})
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.PERCH, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(state.mode() == DragonState.Mode.ROOST, "the sleep did not take the perch: " + state.mode());
				taken[1] = fight.blows(0.4, 0.01);
				EndTestSupport.result("perchSleep", "plain=" + taken[0] + " asleep=" + taken[1] + " ratio=" + taken[1] / taken[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(taken[0] > 0.0F, "a plain blow did no damage");
				helper.assertTrue(Math.abs(taken[1] / taken[0] - DragonRules.ROOST_DAMAGE) < 0.03, "the sleeping dragon took " + taken[1] / taken[0] + " of the damage");
			})
			.thenSucceed();
	}
}
