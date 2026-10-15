package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** Each callout type asks something a mob can do, and a mob that cannot says so. */
public class CalloutKitsGameTest {
	private static final int LONG = 700;
	private static final Identifier STRENGTH = Identifier.withDefaultNamespace("strength");
	private static final Identifier SLOWNESS = Identifier.withDefaultNamespace("slowness");

	static {
		CalloutKits.register();
	}

	private static Callout call(final Identifier type, final Mob caller, final ServerPlayer player, final Identifier detail) {
		return new Callout(type, caller.getUUID(), player.getUUID(), 1, 200, null, detail);
	}

	private static Mob spawn(final GameTestHelper helper, final EntityType<? extends Mob> type, final int x, final int z) {
		Mob mob = helper.spawnMob(type, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		return mob;
	}

	private static CalloutBoard.Answer answerOf(final List<CalloutBoard.Answer> answers, final Mob mob) {
		return answers.stream().filter(a -> a.receiver().equals(mob.getUUID())).findFirst().orElseThrow();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void focusFireSwitchesAnEngagedMobToThePlayer(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob decoy = spawn(helper, EntityTypes.ZOMBIE, 30, 30);
		Mob receiver = spawn(helper, EntityTypes.ZOMBIE, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, decoy, receiver), () -> {
			receiver.setTarget(decoy);
			CalloutBoard.send(caller, player, call(CalloutKits.FOCUS, caller, player, null), LearnConfig.defaults().withCalloutRadius(24.0));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=focusFireSwitchesAnEngagedMobToThePlayer RESULT target={}", receiver.getTarget());
			helper.assertTrue(receiver.getTarget() == player, "the receiver's target is " + receiver.getTarget());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void backOffMovesAMobAwayFromThePlayerThatItWasAttacking(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = helper.spawnMob(EntityTypes.ZOMBIE, 20, LearnArena.SPAWN_Y, 16).spawn();
		Mob receiver = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 28).spawn();
		LearnArena.keepTargeting(helper, receiver, player);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			double before = receiver.distanceTo(player);
			CalloutBoard.send(caller, player, call(CalloutKits.BACK_OFF, caller, player, null), LearnConfig.defaults().withCalloutRadius(24.0));
			LearnArena.pollUntil(helper, () -> receiver.distanceTo(player) >= CalloutKits.BACK_OFF_DISTANCE - AnswerRallyGoal.ARRIVED - 0.1, 300, () -> {
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=backOffMovesAMobAwayFromThePlayerThatItWasAttacking RESULT before={} after={}", before, receiver.distanceTo(player)
				);
				helper.assertTrue(before < CalloutKits.BACK_OFF_DISTANCE - AnswerRallyGoal.ARRIVED - 0.1, "the receiver began too far away, so the case proves nothing");
				helper.succeed();
			}, () -> "the receiver never backed off from " + before);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aWitchHealsTheCaller(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob witch = spawn(helper, EntityTypes.WITCH, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, witch), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			float before = caller.getHealth();
			CalloutBoard.send(caller, player, call(CalloutKits.HEAL, caller, player, null), LearnConfig.defaults().withCalloutRadius(24.0));
			float gained = caller.getHealth() - before;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aWitchHealsTheCaller RESULT gained={}", gained);
			helper.assertTrue(Math.abs(gained - CalloutKits.HEAL_AMOUNT) < 0.01F, "the caller gained " + gained);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aZombieCannotHealAndSaysSo(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob zombie = spawn(helper, EntityTypes.ZOMBIE, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, zombie), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			float before = caller.getHealth();
			CalloutBoard.Answer answer = answerOf(
				CalloutBoard.send(caller, player, call(CalloutKits.HEAL, caller, player, null), LearnConfig.defaults().withCalloutRadius(24.0)), zombie
			);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aZombieCannotHealAndSaysSo RESULT reason={} gained={}", answer.reason(), caller.getHealth() - before);
			helper.assertTrue(!answer.accepted() && answer.reason().equals("cannot"), "the zombie answered " + answer);
			helper.assertTrue(caller.getHealth() == before, "the caller was healed anyway");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anEvokerBuffsTheCallerWithTheNamedEffect(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob evoker = spawn(helper, EntityTypes.EVOKER, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, evoker), () -> {
			helper.assertTrue(!caller.hasEffect(MobEffects.STRENGTH), "the caller was strong already, so the case proves nothing");
			CalloutBoard.send(caller, player, call(CalloutKits.BUFF, caller, player, STRENGTH), LearnConfig.defaults().withCalloutRadius(24.0));
			boolean strong = caller.hasEffect(MobEffects.STRENGTH);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anEvokerBuffsTheCallerWithTheNamedEffect RESULT strong={} weak={}", strong, caller.hasEffect(MobEffects.WEAKNESS));
			helper.assertTrue(strong && !caller.hasEffect(MobEffects.WEAKNESS), "the caller's effects are wrong");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBuffNamingNoRealEffectIsDeclined(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob evoker = spawn(helper, EntityTypes.EVOKER, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, evoker), () -> {
			CalloutBoard.Answer answer = answerOf(CalloutBoard.send(
				caller, player, call(CalloutKits.BUFF, caller, player, StellaLearn.id("no_such_effect")), LearnConfig.defaults().withCalloutRadius(24.0)
			), evoker);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aBuffNamingNoRealEffectIsDeclined RESULT reason={}", answer.reason());
			helper.assertTrue(!answer.accepted() && answer.reason().equals("cannot"), "the evoker answered " + answer);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aWitchDebuffsThePlayerWithinReach(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob witch = spawn(helper, EntityTypes.WITCH, 32, 24);
		LearnArena.whenReady(helper, List.of(player, caller, witch), () -> {
			helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "the player was slow already, so the case proves nothing");
			CalloutBoard.send(caller, player, call(CalloutKits.DEBUFF, caller, player, SLOWNESS), LearnConfig.defaults().withCalloutRadius(24.0));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aWitchDebuffsThePlayerWithinReach RESULT slow={}", player.hasEffect(MobEffects.SLOWNESS));
			helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "the player was not slowed");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aWitchOutOfReachDoesNotDebuff(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = spawn(helper, EntityTypes.ZOMBIE, 20, 20);
		Mob witch = spawn(helper, EntityTypes.WITCH, 24, 44);
		LearnArena.whenReady(helper, List.of(player, caller, witch), () -> {
			CalloutBoard.Answer answer = answerOf(
				CalloutBoard.send(caller, player, call(CalloutKits.DEBUFF, caller, player, SLOWNESS), LearnConfig.defaults().withCalloutRadius(24.0)), witch
			);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aWitchOutOfReachDoesNotDebuff RESULT reason={} slow={}", answer.reason(), player.hasEffect(MobEffects.SLOWNESS));
			helper.assertTrue(!answer.accepted() && answer.reason().equals("cannot"), "the witch answered " + answer);
			helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "a witch out of reach slowed the player");
			helper.succeed();
		});
	}
}
