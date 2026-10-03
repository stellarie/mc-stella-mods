package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import com.stella.villages.patrol.PatrolState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The village trainer gives a player a first job at level 10. The job rules live in the rpg mod, reached through
 * {@link RpgJobs}. A choice also gives the job's item and, for a mage or a priest, spellbooks from {@link JobKits}.
 */
public final class JobTrainer {
	/** Test seam: what the trainer told a player, and every choice and trial request with its reason. */
	public interface Recorder {
		default void said(final ServerPlayer player, final String text) {
		}

		default void chose(final ServerPlayer player, final String job, final boolean accepted, final String reason) {
		}

		default void trial(final ServerPlayer player, final int level, final boolean accepted, final String reason) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private JobTrainer() {
	}

	public static void setRecorder(final @Nullable Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static boolean isTrainer(final Mob mob) {
		return PatrolState.role(mob) == PatrolState.Role.TRAINER;
	}

	static InteractionResult onUseEntity(
		final Player player, final Level world, final InteractionHand hand, final Entity entity, final EntityHitResult hit
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel)
			|| !(entity instanceof Mob trainer) || hand != InteractionHand.MAIN_HAND || !isTrainer(trainer)) {
			return InteractionResult.PASS;
		}
		if (serverPlayer.distanceToSqr(trainer) > (double) MissionService.MISSION_MENU_RANGE * MissionService.MISSION_MENU_RANGE) {
			return InteractionResult.PASS;
		}
		if (!RpgJobs.available()) {
			say(serverPlayer, "Jobs are unavailable right now.");
			return InteractionResult.SUCCESS;
		}
		TrainerMenu.open(serverPlayer, trainer);
		return InteractionResult.SUCCESS;
	}

	/** Why the player cannot choose a job now, or null when the player can. */
	public static @Nullable String chooseRefusal(final ServerPlayer player) {
		if (!RpgJobs.available()) {
			return "unavailable";
		}
		if (!RpgJobs.NONE.equals(RpgJobs.job(player))) {
			return "has_job";
		}
		return player.experienceLevel < RpgJobs.MIN_LEVEL ? "level" : null;
	}

	/** Sets the job, then gives its item and books. Returns false, and changes nothing, on a refusal. */
	public static boolean choose(final ServerPlayer player, final String job) {
		String reason = RpgJobs.JOBS.contains(job) ? chooseRefusal(player) : "unknown_job";
		if (reason == null && !RpgJobs.choose(player, job)) {
			reason = "refused";
		}
		recorder.chose(player, job, reason == null, reason == null ? "accepted" : reason);
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=job_choice owner={} job={} accepted={} reason={}",
			McStellaVillages.MOD_ID, player.getUUID(), job, reason == null, reason == null ? "accepted" : reason);
		if (reason != null) {
			say(player, "The trainer cannot give you that job: " + reasonText(reason));
			return false;
		}
		RpgJobs.grantItems(player);
		List<ItemStack> books = JobKits.books(player.level(), job, player.getRandom());
		JobKits.give(player, books);
		say(player, "You are now a " + job + ". The trainer hands you your gear.");
		return true;
	}

	static void say(final ServerPlayer player, final String text) {
		recorder.said(player, text);
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
	}

	static void recordTrial(final ServerPlayer player, final int level, final boolean accepted, final String reason) {
		recorder.trial(player, level, accepted, reason);
	}

	static String reasonText(final String reason) {
		return switch (reason) {
			case "unavailable" -> "jobs are unavailable";
			case "level" -> "you need level " + RpgJobs.MIN_LEVEL;
			case "has_job" -> "you already have a job";
			case "no_trial" -> "you have no trial to take";
			case "no_safe_target" -> "no safe place to find a boss right now";
			case "trainer_not_registered" -> "the trainer belongs to no village";
			default -> "something went wrong (" + reason + ")";
		};
	}
}
