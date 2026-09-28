package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ring a village's bell to bring its loaded patrol beside it. Each member gains Speed II and Strength II
 * for five minutes. The bell still rings as vanilla's does.
 */
public final class BellRally {
	public static final int RALLY_TICKS = 6000;
	public static final int SPEED_AMPLIFIER = 1;
	public static final int STRENGTH_AMPLIFIER = 1;
	/** How far from its village's anchor a bell still belongs to that village. */
	public static final double RALLY_RANGE = 48.0;

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
		List<Mob> members = PatrolService.members(level, villageId);
		if (members.isEmpty()) {
			recorder.refused(bell, villageId, "no_patrol");
			return;
		}

		List<UUID> moved = new ArrayList<>();
		for (Mob member : members) {
			BlockPos destination = safeSpot(level, bell, member);
			if (destination == null) {
				continue;
			}
			Vec3 target = Vec3.atBottomCenterOf(destination);
			member.teleportTo(target.x, target.y, target.z);
			member.getNavigation().stop();
			member.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, SPEED_AMPLIFIER));
			member.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, STRENGTH_AMPLIFIER));
			level.sendParticles(ParticleTypes.NOTE, member.getX(), member.getY() + member.getBbHeight() + 0.5, member.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			moved.add(member.getUUID());
		}

		recorder.rallied(bell, villageId, moved);
		tell(ringer, Component.literal("The patrol rallies to the bell").withStyle(ChatFormatting.GOLD));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=bell_rally village={} bell={} members={}",
			McStellaVillages.MOD_ID, villageId, bell, moved.size()
		);
	}

	static BlockPos safeSpot(final ServerLevel level, final BlockPos bell, final Mob member) {
		for (int radius = 1; radius <= 7; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					for (int dy = 2; dy >= -2; dy--) {
						BlockPos feet = bell.offset(dx, dy, dz);
						if (Vec3.atCenterOf(feet).distanceToSqr(Vec3.atCenterOf(bell)) > 64.0) {
							continue;
						}
						if (!level.hasChunkAt(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
							continue;
						}
						AABB box = member.getDimensions(member.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(feet));
						if (level.noCollision(member, box) && !level.containsAnyLiquid(box)) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	// On the action bar, so the ringer reads it without opening chat.
	private static void tell(final Player ringer, final Component message) {
		if (ringer instanceof ServerPlayer player) {
			player.sendSystemMessage(message, true);
		}
	}
}
