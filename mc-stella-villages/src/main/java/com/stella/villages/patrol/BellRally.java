package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Surprise, villages: ring a village's bell and its patrol rallies to it. Every living member gets Speed
 * II and Strength I for thirty seconds and runs to the bell. A village rallies once a minute on the
 * level's own tick counter, and the bell still rings as vanilla's does.
 */
public final class BellRally {
	public static final int RALLY_TICKS = 600;
	public static final int RALLY_COOLDOWN_TICKS = 1200;
	public static final int SPEED_AMPLIFIER = 1;
	public static final int STRENGTH_AMPLIFIER = 0;
	/** How far from its village's anchor a bell still belongs to that village. */
	public static final double RALLY_RANGE = 48.0;
	private static final double RUN_SPEED = 1.2;

	/** Test seam: each rally and each refusal, keyed by the bell that was rung. */
	public interface Recorder {
		default void rallied(final BlockPos bell, final UUID villageId, final List<UUID> members) {
		}

		default void refused(final BlockPos bell, final UUID villageId, final String reason) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	private static final Map<UUID, Long> READY_AT = new ConcurrentHashMap<>();

	private BellRally() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(BellRally::onUseBlock);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	private static InteractionResult onUseBlock(final Player player, final Level world, final InteractionHand hand, final BlockHitResult hit) {
		if (!(world instanceof ServerLevel level) || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		BlockPos bell = hit.getBlockPos();
		if (!level.getBlockState(bell).is(Blocks.BELL)) {
			return InteractionResult.PASS;
		}

		// Vanilla's village distance reaches about ninety blocks, so a bell must also sit near the village's own anchor.
		VillageWorldData.get(level).findNearest(level.dimension(), bell)
			.filter(village -> village.anchor().closerThan(bell, RALLY_RANGE))
			.ifPresent(village -> rally(level, bell, village.id(), player));
		// The bell still rings: the rally rides alongside vanilla's own use.
		return InteractionResult.PASS;
	}

	static void rally(final ServerLevel level, final BlockPos bell, final UUID villageId, final Player ringer) {
		long now = PatrolService.levelTick(level);
		long readyAt = READY_AT.getOrDefault(villageId, Long.MIN_VALUE);
		if (now < readyAt) {
			recorder.refused(bell, villageId, "cooldown");
			tell(ringer, Component.literal("The patrol is still catching its breath").withStyle(ChatFormatting.GRAY));
			return;
		}

		List<Mob> members = PatrolService.members(level, villageId);
		if (members.isEmpty()) {
			recorder.refused(bell, villageId, "no_patrol");
			return;
		}

		READY_AT.put(villageId, now + RALLY_COOLDOWN_TICKS);
		Vec3 target = Vec3.atBottomCenterOf(bell.above());
		for (Mob member : members) {
			member.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, SPEED_AMPLIFIER));
			member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, STRENGTH_AMPLIFIER));
			member.getNavigation().moveTo(target.x, target.y, target.z, RUN_SPEED);
			level.sendParticles(ParticleTypes.NOTE, member.getX(), member.getY() + member.getBbHeight() + 0.5, member.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}

		recorder.rallied(bell, villageId, members.stream().map(Mob::getUUID).toList());
		tell(ringer, Component.literal("The patrol rallies to the bell").withStyle(ChatFormatting.GOLD));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=bell_rally village={} bell={} members={}",
			McStellaVillages.MOD_ID, villageId, bell, members.size()
		);
	}

	// On the action bar, so the ringer reads it without opening chat.
	private static void tell(final Player ringer, final Component message) {
		if (ringer instanceof ServerPlayer player) {
			player.sendSystemMessage(message, true);
		}
	}
}
