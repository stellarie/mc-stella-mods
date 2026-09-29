package com.stella.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** A callout reaches every mob in the player's range, each decides for itself, and the caller keeps the record. */
public class CalloutGameTest {
	static final Identifier TEST_TYPE = StellaLearn.id("test_call");
	private static final Set<UUID> PERFORMED = ConcurrentHashMap.newKeySet();
	private static final int LONG = 400;

	static {
		CalloutTypes.register(TEST_TYPE, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final Callout callout) {
				return true;
			}

			@Override
			public void perform(final Mob mob, final Callout callout) {
				PERFORMED.add(mob.getUUID());
			}
		});
	}

	private static Callout call(final Mob caller, final ServerPlayer player, final int priority, final int duration) {
		return new Callout(TEST_TYPE, caller.getUUID(), player.getUUID(), priority, duration, null);
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		return helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
	}

	private static List<UUID> ids(final List<CalloutBoard.Answer> answers) {
		return answers.stream().map(CalloutBoard.Answer::receiver).toList();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void everyMobInThePlayersRangeIsAsked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob idle = zombie(helper, 28, 20);
		Mob engaged = zombie(helper, 20, 28);
		engaged.setTarget(player);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, call(caller, player, 1, 100), LearnConfig.defaults());
		StellaLearn.LOGGER.info("[BEHAVIOR] test=everyMobInThePlayersRangeIsAsked RESULT asked={} accepted={}", answers.size(),
			answers.stream().filter(CalloutBoard.Answer::accepted).count());
		helper.assertTrue(ids(answers).contains(idle.getUUID()), "the idle mob was not asked");
		helper.assertTrue(ids(answers).contains(engaged.getUUID()), "the mob already fighting was not asked");
		helper.assertTrue(!ids(answers).contains(caller.getUUID()), "the caller asked itself");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobOutsideThePlayersRangeIsNotAsked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob far = zombie(helper, 2, 2);
		Mob near = zombie(helper, 26, 26);
		LearnConfig config = LearnConfig.defaults().withCalloutRadius(12.0);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, call(caller, player, 1, 100), config);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobOutsideThePlayersRangeIsNotAsked RESULT asked={}", answers.size());
		helper.assertTrue(ids(answers).contains(near.getUUID()), "the near mob was not asked, so the case proves nothing");
		helper.assertTrue(!ids(answers).contains(far.getUUID()), "a mob 30 blocks from the player was asked");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobHurtBelowItsSurvivalLineDeclinesAndSaysWhy(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob hurt = zombie(helper, 28, 20);
		hurt.setHealth(1.0F);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, call(caller, player, 1, 100), LearnConfig.defaults());
		CalloutBoard.Answer answer = answers.stream().filter(a -> a.receiver().equals(hurt.getUUID())).findFirst().orElseThrow();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobHurtBelowItsSurvivalLineDeclinesAndSaysWhy RESULT accepted={} reason={}", answer.accepted(), answer.reason());
		helper.assertTrue(!answer.accepted() && answer.reason().equals("survival"), "the hurt mob answered " + answer);
		helper.assertTrue(CalloutBoard.active(hurt) == null, "the hurt mob holds a request it declined");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHealthyMobAcceptsAndHoldsTheRequest(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob healthy = zombie(helper, 28, 20);
		CalloutBoard.send(caller, player, call(caller, player, 1, 100), LearnConfig.defaults());
		boolean holds = CalloutBoard.active(healthy) != null && PERFORMED.contains(healthy.getUUID());
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aHealthyMobAcceptsAndHoldsTheRequest RESULT holds={}", holds);
		helper.assertTrue(holds, "the healthy mob did not take up the request");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theResponderCapBoundsWhoAccepts(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		for (int i = 0; i < 6; i++) {
			zombie(helper, 26 + i, 28);
		}

		LearnConfig config = LearnConfig.defaults().withMaxResponders(2);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, call(caller, player, 1, 100), config);
		long accepted = answers.stream().filter(CalloutBoard.Answer::accepted).count();
		long capped = answers.stream().filter(a -> a.reason().equals("cap")).count();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theResponderCapBoundsWhoAccepts RESULT accepted={} capped={}", accepted, capped);
		helper.assertTrue(accepted == 2, "accepted " + accepted + " with a cap of 2");
		helper.assertTrue(capped == answers.size() - 2, "the rest were not declined for the cap");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aCallerMustRestBetweenCalls(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		zombie(helper, 28, 20);
		LearnConfig config = LearnConfig.defaults().withCalloutCooldownTicks(60);
		List<CalloutBoard.Answer> first = CalloutBoard.send(caller, player, call(caller, player, 1, 100), config);
		List<CalloutBoard.Answer> second = CalloutBoard.send(caller, player, call(caller, player, 1, 100), config);
		helper.runAfterDelay(80L, () -> {
			List<CalloutBoard.Answer> third = CalloutBoard.send(caller, player, call(caller, player, 1, 100), config);
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=aCallerMustRestBetweenCalls RESULT first={} second={} third={}", first.size(), second.size(), third.size()
			);
			helper.assertTrue(!first.isEmpty(), "the first call reached no one, so the case proves nothing");
			helper.assertTrue(second.isEmpty(), "a second call went out with no rest");
			helper.assertTrue(!third.isEmpty(), "the caller could not call again after its rest");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBusierMobKeepsItsHigherPriorityRequest(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob first = zombie(helper, 20, 20);
		Mob second = zombie(helper, 20, 24);
		Mob receiver = zombie(helper, 28, 20);
		LearnConfig config = LearnConfig.defaults().withCalloutCooldownTicks(1);
		CalloutBoard.send(first, player, call(first, player, 5, 200), config);
		List<CalloutBoard.Answer> lower = CalloutBoard.send(second, player, call(second, player, 2, 200), config);
		CalloutBoard.Answer answer = lower.stream().filter(a -> a.receiver().equals(receiver.getUUID())).findFirst().orElseThrow();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aBusierMobKeepsItsHigherPriorityRequest RESULT accepted={} reason={}", answer.accepted(), answer.reason());
		helper.assertTrue(!answer.accepted() && answer.reason().equals("busier"), "the receiver answered " + answer);
		helper.assertTrue(CalloutBoard.active(receiver).callout().priority() == 5, "the receiver dropped its priority 5 request");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aHigherPriorityRequestReplacesALowerOne(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob first = zombie(helper, 20, 20);
		Mob second = zombie(helper, 20, 24);
		Mob receiver = zombie(helper, 28, 20);
		LearnConfig config = LearnConfig.defaults().withCalloutCooldownTicks(1);
		CalloutBoard.send(first, player, call(first, player, 2, 200), config);
		CalloutBoard.send(second, player, call(second, player, 5, 200), config);
		int held = CalloutBoard.active(receiver).callout().priority();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aHigherPriorityRequestReplacesALowerOne RESULT held={}", held);
		helper.assertTrue(held == 5, "the receiver still held priority " + held);
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anAcceptedRequestExpires(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		CalloutBoard.send(caller, player, call(caller, player, 1, 10), LearnConfig.defaults());
		boolean heldAtOnce = CalloutBoard.active(receiver) != null;
		helper.runAfterDelay(40L, () -> {
			boolean heldLater = CalloutBoard.active(receiver) != null;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anAcceptedRequestExpires RESULT atOnce={} later={}", heldAtOnce, heldLater);
			helper.assertTrue(heldAtOnce, "the request was never held, so the case proves nothing");
			helper.assertTrue(!heldLater, "the request outlived its 10 ticks");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aTypeNobodyRegisteredIsDeclinedAsUnknown(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		Callout unknown = new Callout(StellaLearn.id("no_such_call"), caller.getUUID(), player.getUUID(), 1, 100, null);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, unknown, LearnConfig.defaults());
		CalloutBoard.Answer answer = answers.stream().filter(a -> a.receiver().equals(receiver.getUUID())).findFirst().orElseThrow();
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aTypeNobodyRegisteredIsDeclinedAsUnknown RESULT reason={}", answer.reason());
		helper.assertTrue(!answer.accepted() && answer.reason().equals("unknown_type"), "answered " + answer);
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void onlyMonstersAreAsked(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob cow = helper.spawnMob(EntityTypes.COW, 28, LearnArena.SPAWN_Y, 20).spawn();
		Mob monster = zombie(helper, 28, 24);
		List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, call(caller, player, 1, 100), LearnConfig.defaults());
		StellaLearn.LOGGER.info("[BEHAVIOR] test=onlyMonstersAreAsked RESULT asked={}", answers.size());
		helper.assertTrue(ids(answers).contains(monster.getUUID()), "the monster was not asked, so the case proves nothing");
		helper.assertTrue(!ids(answers).contains(cow.getUUID()), "a cow was asked");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theCallersRecordIsWhatTheBoardReturns(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		zombie(helper, 28, 20);
		zombie(helper, 28, 24);
		List<CalloutBoard.Answer> answers = new ArrayList<>(CalloutBoard.send(caller, player, call(caller, player, 1, 100), LearnConfig.defaults()));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theCallersRecordIsWhatTheBoardReturns RESULT recorded={}", CalloutBoard.answersOf(caller).size());
		helper.assertTrue(!answers.isEmpty() && answers.equals(CalloutBoard.answersOf(caller)), "the record differs from the answers");
		helper.succeed();
	}
}
