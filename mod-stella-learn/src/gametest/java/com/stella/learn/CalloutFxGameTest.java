package com.stella.learn;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** A callout is drawn: a burst over the caller, a beam to each mob that accepted, and a soft sound, in the type's colour. */
public class CalloutFxGameTest {
	private static final int LONG = 900;

	static {
		Rally.register();
		CalloutKits.register();
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		return mob;
	}

	private static Callout rally(final Mob caller, final ServerPlayer player) {
		return new Callout(Rally.TYPE, caller.getUUID(), player.getUUID(), 1, 100, null);
	}

	private static List<LearnTestInit.FxEvent> beams(final Mob caller) {
		return LearnTestInit.fxOf(caller.getUUID()).stream().filter(event -> event.kind().equals("beam")).toList();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBeamRunsToEachMobThatAcceptedAndToNoOther(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		List<Mob> receivers = List.of(zombie(helper, 28, 20), zombie(helper, 28, 24), zombie(helper, 28, 28));
		LearnArena.whenReady(helper, List.of(player, caller, receivers.get(0), receivers.get(1), receivers.get(2)), () -> {
			List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults().withMaxResponders(2));
			Set<UUID> accepted = new HashSet<>();
			answers.stream().filter(CalloutBoard.Answer::accepted).forEach(answer -> accepted.add(answer.receiver()));
			Set<UUID> beamed = new HashSet<>();
			beams(caller).forEach(event -> beamed.add(event.receiver()));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aBeamRunsToEachMobThatAcceptedAndToNoOther RESULT accepted={} beamed={}", accepted.size(), beamed.size());
			helper.assertTrue(accepted.size() == 2, "the cap let " + accepted.size() + " accept, so the case proves nothing");
			helper.assertTrue(beamed.equals(accepted), "beams went to " + beamed + " but " + accepted + " accepted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBeamRunsFromTheCallersHeadToTheReceiversHead(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 30, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			LearnTestInit.FxEvent beam = beams(caller).get(0);
			double fromError = beam.from().distanceTo(caller.getEyePosition());
			double toError = beam.to().distanceTo(receiver.getEyePosition());
			int want = (int) Math.ceil(caller.getEyePosition().distanceTo(receiver.getEyePosition()) / CalloutFx.BEAM_STEP);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aBeamRunsFromTheCallersHeadToTheReceiversHead RESULT fromError={} toError={} steps={} want={}", fromError, toError, beam.count(), want);
			helper.assertTrue(fromError < 0.01 && toError < 0.01, "the beam ran from " + beam.from() + " to " + beam.to());
			helper.assertTrue(beam.count() == want, "the beam had " + beam.count() + " steps, not " + want);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theCallerBurstsAboveItsHeadAndSoundsOnce(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			List<LearnTestInit.FxEvent> events = LearnTestInit.fxOf(caller.getUUID());
			List<LearnTestInit.FxEvent> bursts = events.stream().filter(e -> e.kind().equals("burst") && e.receiver() == null && e.from().distanceTo(caller.getEyePosition()) < 1.0).toList();
			long sounds = events.stream().filter(e -> e.kind().equals("sound")).count();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theCallerBurstsAboveItsHeadAndSoundsOnce RESULT bursts={} sounds={}", bursts.size(), sounds);
			helper.assertTrue(bursts.size() == 1 && bursts.get(0).count() == CalloutFx.BURST_PARTICLES, "the bursts over the caller were " + bursts);
			helper.assertTrue(sounds == 1, "the call sounded " + sounds + " times");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void eachAcceptingMobGetsAPuff(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			List<LearnTestInit.FxEvent> puffs = LearnTestInit.fxOf(caller.getUUID()).stream()
				.filter(e -> e.kind().equals("burst") && e.from().distanceTo(receiver.getEyePosition()) < 1.0).toList();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=eachAcceptingMobGetsAPuff RESULT puffs={}", puffs.size());
			helper.assertTrue(puffs.size() == 1 && puffs.get(0).count() == CalloutFx.PUFF_PARTICLES, "the puffs on the receiver were " + puffs);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobThatDeclinedIsNotDrawnTo(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob hurt = zombie(helper, 28, 20);
		// A stance would let the hurt mob answer.
		// NORMAL keeps the 35 percent survival line.
		StanceFixtures.dropStance(helper, hurt);
		LearnArena.whenReady(helper, List.of(player, caller, hurt), () -> {
			hurt.setHealth(1.0F);
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobThatDeclinedIsNotDrawnTo RESULT beams={}", beams(caller).size());
			helper.assertTrue(beams(caller).isEmpty(), "a beam ran to a mob that declined");
			helper.assertTrue(!LearnTestInit.fxOf(caller.getUUID()).isEmpty(), "the caller's own burst was not drawn, so the case proves nothing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void everyTypeHasItsOwnColour(final GameTestHelper helper) {
		List<Identifier> types = List.of(Rally.TYPE, CalloutKits.FOCUS, CalloutKits.BACK_OFF, CalloutKits.HEAL, CalloutKits.BUFF, CalloutKits.DEBUFF);
		Set<Integer> colours = new HashSet<>();
		types.forEach(type -> colours.add(CalloutFx.colorOf(type)));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=everyTypeHasItsOwnColour RESULT distinct={} of={}", colours.size(), types.size());
		helper.assertTrue(colours.size() == types.size(), "the six types share " + colours.size() + " colours");
		helper.assertTrue(!colours.contains(CalloutFx.colorOf(StellaLearn.id("no_such_call"))), "an unknown type took a real type's colour");
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theAnimationCanBeSwitchedOff(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			List<CalloutBoard.Answer> answers = CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults().withCalloutFx(false));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theAnimationCanBeSwitchedOff RESULT events={}", LearnTestInit.fxOf(caller.getUUID()).size());
			helper.assertTrue(answers.stream().anyMatch(CalloutBoard.Answer::accepted), "no one accepted, so the case proves nothing");
			helper.assertTrue(LearnTestInit.fxOf(caller.getUUID()).isEmpty(), "the animation played with it switched off");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aCallerThatMustRestPlaysNothing(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			int after = LearnTestInit.fxOf(caller.getUUID()).size();
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			int again = LearnTestInit.fxOf(caller.getUUID()).size();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aCallerThatMustRestPlaysNothing RESULT first={} second={}", after, again);
			helper.assertTrue(after > 0, "the first call drew nothing, so the case proves nothing");
			helper.assertTrue(again == after, "a call that never went out drew " + (again - after) + " events");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aPlayerNearbyIsSentTheParticles(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob receiver = zombie(helper, 28, 20);
		LearnArena.whenReady(helper, List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			List<LearnTestInit.FxEvent> events = LearnTestInit.fxOf(caller.getUUID());
			long unseen = events.stream().filter(e -> !e.seen()).count();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aPlayerNearbyIsSentTheParticles RESULT events={} unseen={}", events.size(), unseen);
			helper.assertTrue(!events.isEmpty() && unseen == 0, unseen + " of " + events.size() + " events reached no player");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theAnimationIsLoggedWithItsCounts(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20);
		Mob first = zombie(helper, 28, 20);
		Mob second = zombie(helper, 28, 24);
		LearnArena.whenReady(helper, List.of(player, caller, first, second), () -> {
			CalloutBoard.send(caller, player, rally(caller, player), LearnConfig.defaults());
			List<String> lines = LearnTestInit.linesWith(caller.getUUID().toString(), "learnCallout FX");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theAnimationIsLoggedWithItsCounts RESULT lines={}", lines);
			helper.assertTrue(lines.size() == 1 && lines.get(0).contains("beams=2"), "the fx lines were " + lines);
			helper.succeed();
		});
	}
}
