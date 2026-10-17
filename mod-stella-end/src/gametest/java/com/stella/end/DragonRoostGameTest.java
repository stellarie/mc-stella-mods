package com.stella.end;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** At half health the dragon roosts on the portal for 2400 ticks, takes a quarter of the damage, calls skeletons, and fights harder after. */
public class DragonRoostGameTest {
	record Fight(ServerLevel end, EnderDragon dragon, DragonState state, ServerPlayer player) {
		/** One blow from the player, at a share of the dragon's full health; the damage it really did, in health. */
		float hit(final double share) {
			dragon.damageCooldownTime = 0;
			float before = dragon.getHealth();
			dragon.hurt(end, dragon.head, end.damageSources().explosion(null, player), (float) (dragon.getMaxHealth() * share / HealthBridge.scale()));
			return before - dragon.getHealth();
		}

		void healTo(final double share) {
			dragon.setHealth((float) (dragon.getMaxHealth() * share));
		}

		/** Six blows from one health, summed: another mod rolls dice on the damage, so one blow varies a little. */
		float blows(final double from, final double share) {
			float total = 0.0F;
			for (int i = 0; i < 6; i++) {
				healTo(from);
				total += hit(share);
			}
			return total;
		}
	}

	/** The player stays in the castle below, so the dragon stays passive and its attacks do not cloud the count. */
	static Fight fight(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		return new Fight(end, dragon, EndDragons.state(dragon), player);
	}

	@GameTest(environment = "mod-stella-end-test:roost_start", maxTicks = 400)
	public void atHalfHealthTheDragonLandsOnThePortalAndRoostsOnlyThen(final GameTestHelper helper) {
		Fight fight = fight(helper);
		DragonState.Mode[] early = new DragonState.Mode[1];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				fight.healTo(0.8);
				fight.hit(0.02);
			})
			.thenIdle(5)
			.thenExecute(() -> {
				early[0] = fight.state().mode();
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> helper.assertTrue(fight.state().mode() == DragonState.Mode.ROOST, "the dragon is " + fight.state().mode() + " at "
				+ fight.dragon().getHealth() / fight.dragon().getMaxHealth()))
			.thenWaitUntil(() -> helper.assertTrue(fight.dragon().position().distanceTo(Rooftop.roostPoint(fight.end())) < 2.0, "the dragon is at " + fight.dragon().position()
				+ ", the portal at " + Rooftop.roostPoint(fight.end())))
			.thenExecute(() -> {
				EndTestSupport.result("roostStart", "before=" + early[0] + " roosted=" + fight.state().roosted() + " startedAt=" + fight.state().roostStartedAt()
					+ " at=" + fight.dragon().position());
				boolean roosted = fight.state().roosted();
				long startedAt = fight.state().roostStartedAt();
				EndTestSupport.fresh(helper);
				helper.assertTrue(early[0] != DragonState.Mode.ROOST, "the dragon roosted at 78 percent");
				helper.assertTrue(roosted && startedAt != EndState.NEVER, "the roost was not recorded");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:roost_damage", maxTicks = 400)
	public void whileItRoostsTheDragonTakesAQuarterOfTheDamageAndNotBeforeOrAfter(final GameTestHelper helper) {
		Fight fight = fight(helper);
		float[] taken = new float[3];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				taken[0] = fight.blows(0.8, 0.01);
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(fight.state().mode() == DragonState.Mode.ROOST, "the dragon is " + fight.state().mode());
				taken[1] = fight.blows(0.4, 0.01);
				EndClock.advance(fight.end(), DragonRules.ROOST_TICKS);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(fight.state().mode() != DragonState.Mode.ROOST, "the dragon still roosts after " + DragonRules.ROOST_TICKS + " ticks");
				taken[2] = fight.blows(0.4, 0.01);
				EndTestSupport.result("roostDamage", "before=" + taken[0] + " during=" + taken[1] + " after=" + taken[2] + " ratio=" + taken[1] / taken[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(taken[0] > 0.0F, "a blow before the roost did no damage");
				helper.assertTrue(Math.abs(taken[1] / taken[0] - DragonRules.ROOST_DAMAGE) < 0.03, "the roost took " + taken[1] / taken[0] + " of the damage");
				helper.assertTrue(Math.abs(taken[2] / taken[0] - 1.0F) < 0.05, "after the roost the dragon took " + taken[2] / taken[0] + " of the damage");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:roost_end", maxTicks = 300)
	public void theRoostEndsAfterTwentyFourHundredTicksAndDoesNotStartAgain(final GameTestHelper helper) {
		Fight fight = fight(helper);
		DragonState.Mode[] modes = new DragonState.Mode[3];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(fight.state().mode() == DragonState.Mode.ROOST, "the dragon is " + fight.state().mode());
				long left = fight.state().roostStartedAt() + DragonRules.ROOST_TICKS - EndClock.now(fight.end());
				EndClock.advance(fight.end(), left - 6);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				modes[0] = fight.state().mode();
				EndClock.advance(fight.end(), 6);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				modes[1] = fight.state().mode();
				fight.healTo(0.3);
			})
			.thenIdle(40)
			.thenExecute(() -> {
				modes[2] = fight.state().mode();
				EndTestSupport.result("roostEnd", "justBefore=" + modes[0] + " justAfter=" + modes[1] + " later=" + modes[2]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(modes[0] == DragonState.Mode.ROOST, "the roost ended early: " + modes[0]);
				helper.assertTrue(modes[1] != DragonState.Mode.ROOST, "the roost did not end: " + modes[1]);
				helper.assertTrue(modes[2] != DragonState.Mode.ROOST, "the dragon roosted a second time");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:roost_summons", maxTicks = 300)
	public void whileItRoostsTheDragonCallsTwoWitherSkeletonsAndTwoArchersEveryHundredTicksUpToTwelve(final GameTestHelper helper) {
		Fight fight = fight(helper);
		List<Integer> alive = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		int[] seen = new int[1];
		var sequence = helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3);
		for (int wave = 0; wave < 6; wave++) {
			sequence = sequence
				.thenExecute(() -> EndClock.advance(fight.end(), DragonRules.ROOST_SUMMON_PERIOD))
				.thenIdle(2)
				.thenExecute(() -> {
					alive.add((int) fight.state().summons().stream().filter(id -> fight.end().getEntity(id) != null && fight.end().getEntity(id).isAlive()).count());
					Vec3 portal = Rooftop.roostPoint(fight.end());
					List<UUID> recorded = fight.state().summons();
					for (UUID id : recorded.subList(seen[0], recorded.size())) {
						Entity summon = fight.end().getEntity(id);
						if (summon == null || summon.position().distanceTo(portal) > 10.0) {
							bad.add("a summon stands at " + (summon == null ? "nowhere" : summon.position()) + ", the portal at " + portal);
						}
					}
					seen[0] = recorded.size();
				});
		}
		sequence
			.thenExecute(() -> {
				int skeletons = 0;
				int archers = 0;
				for (UUID id : fight.state().summons()) {
					Entity summon = fight.end().getEntity(id);
					if (summon == null) {
						continue;
					}
					skeletons += summon instanceof WitherSkeleton ? 1 : 0;
					archers += summon instanceof Skeleton skeleton && skeleton.getMainHandItem().is(Items.BOW) ? 1 : 0;
					if (LevelBridge.read(summon) != EndLevels.ROOFTOP || !summon.entityTags().contains(EndDragons.SIDE_TAG)) {
						bad.add(id + " level " + LevelBridge.read(summon));
					}
				}
				EndTestSupport.result("roostSummons", "aliveAfterEachWave=" + alive + " skeletons=" + skeletons + " archers=" + archers + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(alive.get(0) == DragonRules.ROOST_SKELETONS + DragonRules.ROOST_ARCHERS, "the first wave added " + alive.get(0));
				helper.assertTrue(alive.stream().allMatch(count -> count <= DragonRules.ROOST_CAP), "the summons passed the cap: " + alive);
				helper.assertTrue(alive.get(alive.size() - 1) == DragonRules.ROOST_CAP, "the summons settled at " + alive.get(alive.size() - 1));
				helper.assertTrue(skeletons == DragonRules.ROOST_CAP / 2 && archers == DragonRules.ROOST_CAP / 2, skeletons + " wither skeletons and " + archers + " archers");
				helper.assertTrue(bad.isEmpty(), "summon defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:roost_weights", maxTicks = 300)
	public void afterTheRoostTheSwoopAndTheDiveCountDouble(final GameTestHelper helper) {
		Fight fight = fight(helper);
		Map<DragonState.Attack, Integer> before = new EnumMap<>(DragonState.Attack.class);
		Map<DragonState.Attack, Integer> after = new EnumMap<>(DragonState.Attack.class);
		int[] totals = new int[2];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				totals[0] = fight.state().totalWeight();
				for (int roll = 0; roll < totals[0]; roll++) {
					before.merge(fight.state().pick(roll), 1, Integer::sum);
				}
				fight.healTo(0.51);
				fight.hit(0.02);
			})
			.thenIdle(3)
			.thenExecute(() -> EndClock.advance(fight.end(), DragonRules.ROOST_TICKS))
			.thenIdle(3)
			.thenExecute(() -> {
				totals[1] = fight.state().totalWeight();
				for (int roll = 0; roll < totals[1]; roll++) {
					after.merge(fight.state().pick(roll), 1, Integer::sum);
				}
				EndTestSupport.result("roostWeights", "before=" + before + " after=" + after);
				EndTestSupport.fresh(helper);
				helper.assertTrue(before.get(DragonState.Attack.SWOOP) == 1 && before.get(DragonState.Attack.BARRAGE) == 1 && before.get(DragonState.Attack.DIVE) == 1,
					"the weights before the roost are " + before);
				helper.assertTrue(after.get(DragonState.Attack.SWOOP) == 2 && after.get(DragonState.Attack.DIVE) == 2 && after.get(DragonState.Attack.BARRAGE) == 1,
					"the weights after the roost are " + after);
			})
			.thenSucceed();
	}
}
