package com.stella.learn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Stance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Sends callouts and records who answered. Every monster within the player's range is asked, whether idle or
 * already fighting, nearest to the caller first, and each one decides for itself. The caller's cooldown, the cap on
 * accepting responders and a receiver's own survival rule bound what one call can do. The survival rule follows the
 * stance of the mobs mod. A mob in RETREAT declines. A mob in FIGHT, PURSUE or KITE answers at any health. A mob in
 * NORMAL, which has no stance, declines at 35 percent health or less.
 */
public final class CalloutBoard {
	/** One receiver's answer, with the reason a decline gives: cap, unknown_type, survival, cannot or busier. */
	public record Answer(UUID receiver, boolean accepted, String reason) {
	}

	/** A request a mob has accepted and still holds, until the mob's own tick count passes `untilTick`. */
	public record Active(Callout callout, int untilTick) {
	}

	/** One answer as it happens, for a log or a test to watch. */
	public record AnswerEvent(UUID caller, Answer answer) {
	}

	public static final String ACCEPTED = "accepted";
	public static final AttachmentType<List<Answer>> ANSWERS = AttachmentRegistry.create(StellaLearn.id("callout_answers"));
	public static final AttachmentType<Active> ACTIVE = AttachmentRegistry.create(StellaLearn.id("callout_active"));
	public static final AttachmentType<Integer> NEXT_CALL = AttachmentRegistry.create(StellaLearn.id("callout_next_call"));
	private static final List<Consumer<AnswerEvent>> OBSERVERS = new CopyOnWriteArrayList<>();

	private CalloutBoard() {
	}

	public static void observe(final Consumer<AnswerEvent> observer) {
		OBSERVERS.add(observer);
	}

	/** Sends one callout, and returns each answer in the order the receivers were asked; empty while the caller rests. */
	public static List<Answer> send(final Mob caller, final ServerPlayer player, final Callout callout, final LearnConfig config) {
		Integer resting = ((AttachmentTarget) caller).getAttached(NEXT_CALL);
		if (resting != null && caller.tickCount < resting) {
			LearnLog.debug("learnCallout", "REST", LearnLog.who(caller) + " ticks_left=" + (resting - caller.tickCount));
			return List.of();
		}

		((AttachmentTarget) caller).setAttached(NEXT_CALL, caller.tickCount + config.calloutCooldownTicks());
		AABB box = player.getBoundingBox().inflate(config.calloutRadius());
		List<Monster> receivers = caller.level().getEntitiesOfClass(
			Monster.class, box, monster -> monster != caller && monster.isAlive() && !monster.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)
				&& monster.distanceTo(player) <= config.calloutRadius()
		);
		receivers.sort(Comparator.comparingDouble(caller::distanceToSqr));
		List<Answer> answers = new ArrayList<>();
		List<Mob> acceptedMobs = new ArrayList<>();
		int accepted = 0;

		for (Monster receiver : receivers) {
			Answer answer = accepted >= config.maxResponders()
				? new Answer(receiver.getUUID(), false, "cap")
				: decide(receiver, player, callout, accepted);
			if (answer.accepted()) {
				accepted++;
				acceptedMobs.add(receiver);
			}

			answers.add(answer);
			LearnLog.info("learnCallout", "ANSWER", LearnLog.who(caller) + " receiver=" + receiver.getUUID() + " accepted=" + answer.accepted() + " reason=" + answer.reason());
			OBSERVERS.forEach(observer -> observer.accept(new AnswerEvent(caller.getUUID(), answer)));
		}

		((AttachmentTarget) caller).setAttached(ANSWERS, List.copyOf(answers));
		logSend(caller, player, callout, config, answers, accepted);
		CalloutFx.play(caller, callout, config, acceptedMobs);
		return answers;
	}

	private static void logSend(
		final Mob caller, final ServerPlayer player, final Callout callout, final LearnConfig config, final List<Answer> answers, final int accepted
	) {
		java.util.Map<String, Integer> declined = new java.util.TreeMap<>();
		answers.stream().filter(answer -> !answer.accepted()).forEach(answer -> declined.merge(answer.reason(), 1, Integer::sum));
		String reasons = declined.isEmpty() ? "none" : declined.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(","));
		LearnLog.info(config, "learnCallout", "SEND", LearnLog.who(caller) + " " + LearnLog.who(player) + " type=" + callout.type() + " priority=" + callout.priority()
			+ (callout.detail() == null ? "" : " detail=" + callout.detail()) + " duration=" + callout.durationTicks()
			+ " asked=" + answers.size() + " accepted=" + accepted + " declined=" + reasons
			+ " radius=" + LearnLog.num(config.calloutRadius()) + " max_responders=" + config.maxResponders() + " cooldown=" + config.calloutCooldownTicks());
	}

	private static Answer decide(final Mob receiver, final ServerPlayer player, final Callout callout, final int index) {
		CalloutHandler handler = CalloutTypes.get(callout.type());
		if (handler == null) {
			return new Answer(receiver.getUUID(), false, "unknown_type");
		}

		if (Stance.suppresses(receiver)) {
			return new Answer(receiver.getUUID(), false, "survival");
		}

		if (!handler.canPerform(receiver, player, callout)) {
			return new Answer(receiver.getUUID(), false, "cannot");
		}

		Active held = active(receiver);
		if (held != null && held.callout().priority() >= callout.priority()) {
			return new Answer(receiver.getUUID(), false, "busier");
		}

		((AttachmentTarget) receiver).setAttached(ACTIVE, new Active(callout, receiver.tickCount + callout.durationTicks()));
		handler.perform(receiver, player, callout, index);
		return new Answer(receiver.getUUID(), true, ACCEPTED);
	}

	public static List<Answer> answersOf(final Mob caller) {
		List<Answer> answers = ((AttachmentTarget) caller).getAttached(ANSWERS);
		return answers == null ? List.of() : answers;
	}

	/** The request this mob holds, or null once it has expired. */
	public static @Nullable Active active(final Mob receiver) {
		Active active = ((AttachmentTarget) receiver).getAttached(ACTIVE);
		return active == null || receiver.tickCount >= active.untilTick() ? null : active;
	}
}
