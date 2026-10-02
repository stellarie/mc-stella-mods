package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A badly hurt survival player draws the monsters around them. Every {@link #INTERVAL_TICKS} the server visits each
 * player, not each mob. A player at {@link #SHARE} of their health or less makes up to {@link #CAP} idle monsters within
 * {@link #RADIUS} blocks take the player as their target, nearest first. A monster is idle when it has no target or a
 * target that is not a player. A creeper, a neutral mob, a mob that another mod drives, a village friend, a mob that
 * reads its target from a Brain, and a monster at 35 percent health or below never answer. Every scent that draws at least
 * one monster tells the player in the action bar, and the player then waits {@link #COOLDOWN_TICKS} for the next. Both
 * counters run on the player's own tick count.
 */
public final class BloodScent {
	public static final double SHARE = 0.35;
	public static final double RADIUS = 32.0;
	public static final int CAP = 8;
	public static final int INTERVAL_TICKS = 20;
	public static final int COOLDOWN_TICKS = 200;
	/** The telegraph: a player is never swarmed without a warning. */
	public static final String WARNING = "Your blood draws them in";

	/** Test seam: reports each scent that drew or declined a monster. The message is null when nothing was drawn. */
	@FunctionalInterface
	public interface Recorder {
		void scented(ServerPlayer player, List<Mob> drawn, List<Mob> declined, @Nullable String message);
	}

	/** Per-player timers. Not saved. */
	private static final class Visit {
		private int nextVisitTick;
		private int cooldownUntilTick;
	}

	private static final AttachmentType<Visit> VISIT = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "blood_scent"), Visit::new
	);
	private static volatile Recorder recorder = (player, drawn, declined, message) -> {
	};

	private BloodScent() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (player, drawn, declined, message) -> {
		} : newRecorder;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodScent::tick);
	}

	private static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			visit(player);
		}
	}

	private static void visit(final ServerPlayer player) {
		Visit visit = ((AttachmentTarget) player).getAttachedOrCreate(VISIT);
		if (player.tickCount < visit.nextVisitTick) {
			return;
		}

		visit.nextVisitTick = player.tickCount + INTERVAL_TICKS;
		if (!player.isAlive()
			|| player.isCreative()
			|| player.isSpectator()
			|| player.tickCount < visit.cooldownUntilTick
			|| PlayerReadout.share(player) > SHARE
			|| !(player.level() instanceof ServerLevel level)) {
			return;
		}

		List<Monster> candidates = new ArrayList<>(level.getEntitiesOfClass(
			Monster.class, player.getBoundingBox().inflate(RADIUS), BloodScent::answers
		));
		candidates.removeIf(monster -> player.distanceToSqr(monster) > RADIUS * RADIUS);
		candidates.sort(Comparator.comparingDouble(player::distanceToSqr));

		List<Mob> drawn = new ArrayList<>();
		List<Mob> declined = new ArrayList<>();
		for (Monster candidate : candidates) {
			if (drawn.size() >= CAP) {
				break;
			}

			LivingEntity current = candidate.getTarget();
			// A monster that hunts this player needs no call.
			// One that hunts another player is not idle.
			if (current == player || current instanceof Player) {
				continue;
			}

			candidate.setTarget(player);
			// Peaceful and creative make setTarget a no-op.
			if (candidate.getTarget() == player) {
				drawn.add(candidate);
			} else {
				declined.add(candidate);
			}
		}

		if (drawn.isEmpty() && declined.isEmpty()) {
			return;
		}

		String message = null;
		if (!drawn.isEmpty()) {
			visit.cooldownUntilTick = player.tickCount + COOLDOWN_TICKS;
			message = WARNING;
			player.sendSystemMessage(Component.literal(WARNING).withStyle(ChatFormatting.DARK_RED), true);
			StellaMobs.LOGGER.debug(
				"[BEHAVIOR] test=bloodScent DRAW player={} share={} drawn={} declined={}",
				player.getScoreboardName(), String.format(Locale.ROOT, "%.2f", PlayerReadout.share(player)), drawn.size(), declined.size()
			);
		}

		recorder.scented(player, List.copyOf(drawn), List.copyOf(declined), message);
	}

	/** The monsters that may take the call. */
	private static boolean answers(final Monster monster) {
		// No spider calls here: nobody is left out.
		return SpiderSignal.eligible(null, monster) && !monster.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
	}
}
