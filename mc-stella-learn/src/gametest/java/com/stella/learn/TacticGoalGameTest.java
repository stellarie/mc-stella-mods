package com.stella.learn;

import com.stella.mobs.behavior.ShieldUseGoal;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

/** A mob picks what to do when a player swings at it from what has worked against that player, and learns from how it goes. */
public class TacticGoalGameTest {
	private static final int LONG = 900;
	private static final String ZOMBIE = "minecraft:zombie";
	private static final double ALPHA = LearnConfig.DEFAULT_RL_ALPHA;

	/** A policy in which every tactic of the swing has been tried ten times, `best` at 1.0 and the others at 0.0. */
	private static Policy preferring(final Tactic best) {
		return preferring(ZOMBIE, Situation.SWING, List.of(Tactic.SHIELD, Tactic.BACK_OFF, Tactic.TRADE), best);
	}

	private static Policy preferring(final String kind, final Situation situation, final List<Tactic> tactics, final Tactic best) {
		java.util.Map<String, Policy.Entry> entries = new java.util.HashMap<>();
		for (Tactic tactic : tactics) {
			entries.put(Policy.key(kind, situation, tactic), new Policy.Entry(tactic == best ? 1.0 : 0.0, 10));
		}

		return new Policy(entries);
	}

	private static Mob shieldedZombie(final GameTestHelper helper, final ServerPlayer player, final int x, final int z) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		// The mobs mod's own shield goal raises it at any threat, which would hide what this goal decided.
		zombie.getGoalSelector().removeAllGoals(goal -> goal instanceof ShieldUseGoal);
		LearnArena.keepTargeting(helper, zombie, player);
		return zombie;
	}

	private static void steady(final Mob mob) {
		mob.getGoalSelector().getAvailableGoals().stream().filter(w -> w.getGoal() instanceof TacticGoal)
			.forEach(w -> ((TacticGoal) w.getGoal()).setExploring(false));
	}

	/** The player keeps swinging every tick from the moment the test starts to hit, so the goal sees a swing whenever it looks. */
	private static void swingEveryTick(final GameTestHelper helper, final ServerPlayer player, final boolean[] swinging) {
		helper.onEachTick(() -> {
			if (swinging[0]) {
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			}
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSwingMakesAMobRaiseItsShieldWhenThatIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.SHIELD));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> zombie.isUsingItem() && zombie.getUseItem().is(Items.SHIELD), 80, () -> {
				Episode episode = Episodes.active(zombie);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aSwingMakesAMobRaiseItsShieldWhenThatIsWhatWorked RESULT tactic={}", episode == null ? null : episode.tactic());
				helper.assertTrue(episode != null && episode.tactic() == Tactic.SHIELD && episode.situation() == Situation.SWING, "the episode was " + episode);
				helper.succeed();
			}, () -> "the mob never raised its shield");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSwingMakesAMobBackOffWhenThatIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.BACK_OFF));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			double before = zombie.distanceTo(player);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> zombie.distanceTo(player) > before + 2.0, 100, () -> {
				helper.assertTrue(!zombie.isUsingItem(), "the mob raised its shield as well");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aSwingMakesAMobBackOffWhenThatIsWhatWorked RESULT before={} after={}", before, zombie.distanceTo(player));
				helper.succeed();
			}, () -> "the mob never backed off from " + before);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSwingLeavesAMobFightingWhenTradingIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.TRADE));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		double[] farthest = {0.0};
		helper.onEachTick(() -> farthest[0] = Math.max(farthest[0], zombie.distanceTo(player)));
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> Episodes.active(zombie) != null, 60, () -> {
				Episode episode = Episodes.active(zombie);
				helper.runAfterDelay(40L, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aSwingLeavesAMobFightingWhenTradingIsWhatWorked RESULT tactic={} farthest={}", episode.tactic(), farthest[0]);
					helper.assertTrue(episode.tactic() == Tactic.TRADE, "the mob chose " + episode.tactic());
					helper.assertTrue(!zombie.isUsingItem() && farthest[0] < 4.0, "the mob shielded or backed off to " + farthest[0]);
					helper.succeed();
				});
			}, () -> "no decision was ever made");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobDecidesOnceAndNotAgainWhileItsEpisodeRuns(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.TRADE));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		String id = zombie.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			helper.runAfterDelay(20L, () -> {
				int chosen = LearnTestInit.linesWith(id, "learnPolicy CHOOSE").size();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobDecidesOnceAndNotAgainWhileItsEpisodeRuns RESULT chosen={}", chosen);
				helper.assertTrue(chosen == 1, "the mob made " + chosen + " decisions inside one window");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aGoodOutcomeRaisesTheValueOfTheTacticThatEarnedIt(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.TRADE));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		String key = Policy.key(ZOMBIE, Situation.SWING, Tactic.TRADE);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> Episodes.active(zombie) != null, 60, () -> {
				swinging[0] = false;
				Policy.Entry before = Policies.get(player).get(key);
				double dealt = 3.0;
				// The mock player is invulnerable until its client loads, so the hit is handed to the handler as the event would.
				Episodes.afterDamage(player, helper.getLevel().damageSources().mobAttack(zombie), (float) dealt, (float) dealt, false);
				LearnArena.pollUntil(helper, () -> Policies.get(player).get(key).tries() > before.tries(), 120, () -> {
					Policy.Entry after = Policies.get(player).get(key);
					double want = before.value() + ALPHA * (dealt - before.value());
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aGoodOutcomeRaisesTheValueOfTheTacticThatEarnedIt RESULT before={} after={} want={}", before.value(), after.value(), want);
					helper.assertTrue(Math.abs(after.value() - want) < 0.01, "the value went from " + before.value() + " to " + after.value() + ", not " + want);
					helper.succeed();
				}, () -> "the episode never ended in a reward");
			}, () -> "no decision was ever made");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBadOutcomeLowersTheValueOfTheTacticThatEarnedIt(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.TRADE));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		String key = Policy.key(ZOMBIE, Situation.SWING, Tactic.TRADE);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> Episodes.active(zombie) != null, 60, () -> {
				swinging[0] = false;
				Policy.Entry before = Policies.get(player).get(key);
				double taken = 4.0;
				zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), (float) taken);
				LearnArena.pollUntil(helper, () -> Policies.get(player).get(key).tries() > before.tries(), 120, () -> {
					Policy.Entry after = Policies.get(player).get(key);
					StellaLearn.LOGGER.info("[BEHAVIOR] test=aBadOutcomeLowersTheValueOfTheTacticThatEarnedIt RESULT before={} after={}", before.value(), after.value());
					double want = before.value() + ALPHA * (-taken - before.value());
					helper.assertTrue(Math.abs(after.value() - want) < 0.01, "the value went from " + before.value() + " to " + after.value() + ", not " + want);
					helper.succeed();
				}, () -> "the episode never ended in a reward");
			}, () -> "no decision was ever made");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aDecisionAndItsRewardAreLogged(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.TRADE));
		Mob zombie = shieldedZombie(helper, player, 24, 27);
		boolean[] swinging = {false};
		swingEveryTick(helper, player, swinging);
		String id = zombie.getUUID().toString();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			swinging[0] = true;
			LearnArena.pollUntil(helper, () -> !LearnTestInit.linesWith(id, "learnPolicy REWARD").isEmpty(), 200, () -> {
				String choose = LearnTestInit.linesWith(id, "learnPolicy CHOOSE").get(0);
				String reward = LearnTestInit.linesWith(id, "learnPolicy REWARD").get(0);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aDecisionAndItsRewardAreLogged RESULT choose={} reward={}", choose, reward);
				helper.assertTrue(choose.contains("situation=SWING") && choose.contains("tactic=TRADE") && choose.contains("why=greedy") && choose.contains("SHIELD="), "the choose line reads " + choose);
				helper.assertTrue(reward.contains("tactic=TRADE") && reward.contains("reward=") && reward.contains("value=") && reward.contains("tries=11"), "the reward line reads " + reward);
				helper.succeed();
			}, () -> "the reward was never logged");
		});
	}

	private static final List<Tactic> INCOMING = List.of(Tactic.EVADE, Tactic.SHIELD, Tactic.TANK);

	/** An arrow of the player's, in slow flight along +x toward the mob, with no gravity so it takes its time. */
	private static net.minecraft.world.entity.projectile.arrow.Arrow slowArrowAt(final GameTestHelper helper, final ServerPlayer player, final Mob mob) {
		net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
		arrow.setNoGravity(true);
		arrow.setPos(mob.getX() - 8.0, mob.getY() + 1.0, mob.getZ());
		arrow.setDeltaMovement(0.2, 0.0, 0.0);
		helper.getLevel().addFreshEntity(arrow);
		return arrow;
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anIncomingArrowMakesAMobSidestepWhenThatIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 6.5, 24.5);
		Policies.set(player, preferring(ZOMBIE, Situation.PROJECTILE, INCOMING, Tactic.EVADE));
		Mob zombie = shieldedZombie(helper, player, 30, 24);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(helper, zombie::onGround, 60, () -> {
			steady(zombie);
			double lineZ = zombie.getZ();
			slowArrowAt(helper, player, zombie);
			LearnArena.pollUntil(helper, () -> Math.abs(zombie.getZ() - lineZ) >= 1.5, 120, () -> {
				Episode episode = Episodes.active(zombie);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anIncomingArrowMakesAMobSidestepWhenThatIsWhatWorked RESULT offset={} tactic={}", zombie.getZ() - lineZ, episode == null ? null : episode.tactic());
				helper.assertTrue(episode != null && episode.situation() == Situation.PROJECTILE && episode.tactic() == Tactic.EVADE, "the episode was " + episode);
				helper.succeed();
			}, () -> "the mob never left the arrow's line; it stood at " + zombie.position() + " and the line was z=" + lineZ);
		}, () -> "the zombie never landed"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anIncomingArrowMakesAMobRaiseItsShieldWhenThatIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 6.5, 24.5);
		Policies.set(player, preferring(ZOMBIE, Situation.PROJECTILE, INCOMING, Tactic.SHIELD));
		Mob zombie = shieldedZombie(helper, player, 30, 24);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			steady(zombie);
			slowArrowAt(helper, player, zombie);
			LearnArena.pollUntil(helper, () -> zombie.isUsingItem() && zombie.getUseItem().is(Items.SHIELD), 120, () -> {
				Episode episode = Episodes.active(zombie);
				helper.assertTrue(episode != null && episode.situation() == Situation.PROJECTILE && episode.tactic() == Tactic.SHIELD, "the episode was " + episode);
				helper.succeed();
			}, () -> "the mob never raised its shield at the arrow");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anIncomingArrowLeavesAMobWhereItIsWhenTakingItIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 6.5, 24.5);
		Policies.set(player, preferring(ZOMBIE, Situation.PROJECTILE, INCOMING, Tactic.TANK));
		Mob zombie = shieldedZombie(helper, player, 30, 24);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(helper, zombie::onGround, 60, () -> {
			steady(zombie);
			double lineZ = zombie.getZ();
			slowArrowAt(helper, player, zombie);
			LearnArena.pollUntil(helper, () -> Episodes.active(zombie) != null, 120, () -> {
				Episode episode = Episodes.active(zombie);
				helper.runAfterDelay(20L, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=anIncomingArrowLeavesAMobWhereItIsWhenTakingItIsWhatWorked RESULT tactic={} shield={} offset={}", episode.tactic(), zombie.isUsingItem(), zombie.getZ() - lineZ);
					helper.assertTrue(episode.tactic() == Tactic.TANK, "the mob chose " + episode.tactic());
					helper.assertTrue(!zombie.isUsingItem() && Math.abs(zombie.getZ() - lineZ) < 1.5, "the mob shielded or sidestepped to " + (zombie.getZ() - lineZ));
					helper.succeed();
				});
			}, () -> "no decision was ever made");
		}, () -> "the zombie never landed"));
	}

	private static final String SKELETON = "minecraft:skeleton";
	private static final List<Tactic> AFTER_SHOT = List.of(Tactic.HIDE, Tactic.HOLD);
	private static final String ARCHER_CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": 5.0, "far": 15.0 } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	/** A stone pillar off the line between archer and player, with a spot behind it that is nearer to the player than the archer stands. */
	private static void pillarBesideTheArcher(final GameTestHelper helper) {
		for (int dz = 0; dz < 3; dz++) {
			for (int dx = 0; dx < 3; dx++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(30 + dx, y, 20 + dz, net.minecraft.world.level.block.Blocks.STONE);
				}
			}
		}
	}

	private static Mob archer(final GameTestHelper helper, final ServerPlayer player) {
		com.stella.mobs.config.GearConfig previous = com.stella.mobs.config.GearConfigStore.get();
		com.stella.mobs.config.GearConfigStore.set(com.stella.mobs.config.GearConfigParser.parse(ARCHER_CONFIG));
		helper.runBeforeTestEnd(() -> com.stella.mobs.config.GearConfigStore.set(previous));
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 24, LearnArena.SPAWN_Y, 24).spawn();
		LearnArena.keepTargeting(helper, skeleton, player);
		return skeleton;
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anArcherHidesAfterAShotWhenThatIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		pillarBesideTheArcher(helper);
		ServerPlayer player = LearnArena.player(helper, 34.5, 24.5);
		Policies.set(player, preferring(SKELETON, Situation.AFTER_SHOT, AFTER_SHOT, Tactic.HIDE));
		Mob skeleton = archer(helper, player);
		LearnArena.whenReady(helper, List.of(player, skeleton), () -> {
			steady(skeleton);
			LearnArena.pollUntil(helper, () -> !Cover.exposed(helper.getLevel(), player, skeleton.getEyePosition()), 400, () -> {
				Episode episode = Episodes.active(skeleton);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherHidesAfterAShotWhenThatIsWhatWorked RESULT tactic={}", episode == null ? null : episode.tactic());
				helper.assertTrue(LearnTestInit.loosedBy(skeleton.getUUID()) > 0, "the archer had not shot when it was found hidden, so it hid for another reason");
				helper.assertTrue(episode != null && episode.situation() == Situation.AFTER_SHOT && episode.tactic() == Tactic.HIDE, "the episode was " + episode);
				helper.succeed();
			}, () -> "the archer never got out of the player's sight; it stood at " + skeleton.position() + " with cover at " + Cover.find(skeleton, player) + " and had loosed " + LearnTestInit.loosedBy(skeleton.getUUID()));
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anArcherStaysPutAfterAShotWhenHoldingIsWhatWorked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		pillarBesideTheArcher(helper);
		ServerPlayer player = LearnArena.player(helper, 34.5, 24.5);
		Policies.set(player, preferring(SKELETON, Situation.AFTER_SHOT, AFTER_SHOT, Tactic.HOLD));
		Mob skeleton = archer(helper, player);
		boolean[] everHidden = {false};
		helper.onEachTick(() -> everHidden[0] |= !Cover.exposed(helper.getLevel(), player, skeleton.getEyePosition()));
		LearnArena.whenReady(helper, List.of(player, skeleton), () -> {
			steady(skeleton);
			LearnArena.pollUntil(helper, () -> Episodes.active(skeleton) != null, 300, () -> {
				helper.runAfterDelay(60L, () -> {
					Episode episode = Episodes.active(skeleton);
					StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherStaysPutAfterAShotWhenHoldingIsWhatWorked RESULT everHidden={}", everHidden[0]);
					helper.assertTrue(!everHidden[0], "the archer hid when holding was what worked");
					helper.succeed();
				});
			}, () -> "the archer never made a decision after a shot");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void hidingIsNotOfferedWhereThereIsNothingToHideBehind(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 34.5, 24.5);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 24, 1, 24).spawn();
		skeleton.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, skeleton), () -> {
			List<Tactic> open = Tactics.available(skeleton, player, Situation.AFTER_SHOT);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=hidingIsNotOfferedWhereThereIsNothingToHideBehind RESULT open={}", open);
			helper.assertTrue(open.equals(List.of(Tactic.HOLD)), "the open tactics were " + open);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20, padding = LearnArena.PADDING)
	public void theProfileReportRanksWhatMobsHaveLearnedToDo(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Policies.set(player, preferring(Tactic.BACK_OFF));
		List<String> lines = ProfileReport.policy(player);
		String summary = ProfileReport.line(player);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theProfileReportRanksWhatMobsHaveLearnedToDo RESULT lines={} summary={}", lines, summary);
		helper.assertTrue(lines.size() == 2 && lines.get(1).startsWith("minecraft:zombie SWING: BACK_OFF 1.00 (10), "), "the report read " + lines);
		helper.assertTrue(summary.contains("policy_entries=3") && summary.contains("policy_tries=30"), "the summary read " + summary);
		helper.succeed();
	}
}
