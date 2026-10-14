package com.stella.learn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One mob that sees a player tells every mob that sees that mob, and so on down a chain of mobs that see each other.
 * Each of them knows where the player is: it takes the player as its target when it has none, and keeps the spot it was
 * told. The telling is a callout of the {@link #TYPE} type, sent to the mobs that the chain reached.
 *
 * <p>A mob sees a player when it could target the player now. Two mobs see each other within {@link #RANGE} blocks and with
 * a clear line. A mob that holds a live target of its own, or has no AI, keeps what it has.
 */
public final class SharedSight {
	public static final Identifier TYPE = StellaLearn.id("sight");
	/** Blocks within which a mob sees a player, and within which two mobs pass sight on. */
	public static final double RANGE = Formations.RADIUS;
	/** The spot where a mob last learned the player stood. */
	public static final AttachmentType<Vec3> SEEN = AttachmentRegistry.create(StellaLearn.id("sight_seen"));

	private SharedSight() {
	}

	public static void register() {
		CalloutTypes.register(TYPE, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				LivingEntity target = mob.getTarget();
				return !mob.isNoAi() && (target == null || !target.isAlive() || target == player);
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				((AttachmentTarget) mob).setAttached(SEEN, callout.position() == null ? player.position() : callout.position());
				if (mob.getTarget() != player) {
					mob.setTarget(player);
				}
			}

			@Override
			public boolean holds() {
				return false;
			}
		});
	}

	public static @Nullable Vec3 seenSpot(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(SEEN);
	}

	/** Runs the chain over one group of mobs that stand near each other. */
	static void share(final ServerLevel level, final List<Monster> group) {
		Map<Monster, ServerPlayer> knows = new IdentityHashMap<>();
		ArrayDeque<Monster> queue = new ArrayDeque<>();
		for (Monster mob : group) {
			ServerPlayer player = playerSeenBy(level, mob);
			if (player != null) {
				knows.put(mob, player);
				queue.add(mob);
			}
		}

		Map<Monster, List<Monster>> told = new IdentityHashMap<>();
		while (!queue.isEmpty()) {
			Monster from = queue.poll();
			ServerPlayer player = knows.get(from);
			for (Monster to : group) {
				if (!knows.containsKey(to) && from.distanceToSqr(to) <= RANGE * RANGE && from.getSensing().hasLineOfSight(to)) {
					knows.put(to, player);
					queue.add(to);
					told.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
				}
			}
		}

		told.forEach((from, receivers) -> tell(from, knows.get(from), receivers));
	}

	private static void tell(final Monster from, final ServerPlayer player, final List<Monster> receivers) {
		List<Monster> hunting = receivers.stream().filter(mob -> mob.getTarget() != player).toList();
		Callout callout = new Callout(TYPE, from.getUUID(), player.getUUID(), 0, 0, player.position());
		CalloutBoard.deliver(from, player, callout, receivers);
		List<Monster> turned = hunting.stream().filter(mob -> mob.getTarget() == player).toList();
		if (!turned.isEmpty()) {
			LearnLog.info("learnSight", "TELL", LearnLog.who(from) + " " + LearnLog.who(player) + " told=" + receivers.size() + " newTargets=" + turned.size());
		}
	}

	private static @Nullable ServerPlayer playerSeenBy(final ServerLevel level, final Monster mob) {
		TargetingConditions conditions = TargetingConditions.forCombat().range(RANGE);
		ServerPlayer best = null;
		for (ServerPlayer player : level.players()) {
			if (conditions.test(level, mob, player) && (best == null || mob.distanceToSqr(player) < mob.distanceToSqr(best))) {
				best = player;
			}
		}

		return best;
	}
}
