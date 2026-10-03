package com.stella.villages.mission;

import com.mojang.serialization.Codec;
import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import com.stella.villages.patrol.PatrolState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A guard gives each player one small kill quest a day. The guard keeps, per player, the day of its last quest.
 * The duke and the mage are not guards.
 */
public final class GuardQuests {
	public static final int MIN_DISTANCE = 128;
	public static final int MAX_DISTANCE = 256;
	public static final long TICKS_PER_DAY = 24000L;
	private static final AttachmentType<Map<String, Long>> LAST_DAY = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "guard_quest_days"),
		Codec.unboundedMap(Codec.STRING, Codec.LONG)
	);

	private GuardQuests() {
	}

	public static boolean isGuard(final Mob mob) {
		PatrolState.Role role = PatrolState.role(mob);
		return role != null && role != PatrolState.Role.HEAD && role != PatrolState.Role.VILLAGE_WITCH
			&& role != PatrolState.Role.TRAINER;
	}

	public static long day(final ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), TICKS_PER_DAY);
	}

	public static boolean gaveToday(final Mob guard, final UUID player, final long day) {
		Map<String, Long> days = ((AttachmentTarget) guard).getAttached(LAST_DAY);
		return days != null && days.getOrDefault(player.toString(), Long.MIN_VALUE) == day;
	}

	public static void markGiven(final Mob guard, final UUID player, final long day) {
		Map<String, Long> days = new HashMap<>(((AttachmentTarget) guard).getAttachedOrElse(LAST_DAY, Map.of()));
		days.put(player.toString(), day);
		((AttachmentTarget) guard).setAttached(LAST_DAY, days);
	}

	static InteractionResult onUseEntity(
		final Player player, final Level world, final InteractionHand hand, final Entity entity, final EntityHitResult hit
	) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level)
			|| !(entity instanceof Mob guard) || hand != InteractionHand.MAIN_HAND || !isGuard(guard)) {
			return InteractionResult.PASS;
		}
		if (serverPlayer.distanceToSqr(guard) > (double) MissionService.MISSION_MENU_RANGE * MissionService.MISSION_MENU_RANGE) {
			return InteractionResult.PASS;
		}
		offer(level, serverPlayer, guard);
		return InteractionResult.SUCCESS;
	}

	/** Gives the day's quest, or says why not. Returns true when a quest started. */
	public static boolean offer(final ServerLevel level, final ServerPlayer player, final Mob guard) {
		long day = day(level);
		if (gaveToday(guard, player.getUUID(), day)) {
			player.sendSystemMessage(Component.literal("The guard has no more work for you today.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		VillageRecord village = VillageWorldData.get(level).villageForMember(guard.getUUID()).orElse(null);
		if (village == null) {
			return false;
		}
		String refusal = MissionService.acceptGuardQuest(level, player, village);
		if (refusal != null) {
			player.sendSystemMessage(Component.literal("The guard finds no task nearby right now.").withStyle(ChatFormatting.GRAY));
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=guard_quest_rejected guard={} player={} reason={}",
				McStellaVillages.MOD_ID, guard.getUUID(), player.getUUID(), refusal);
			return false;
		}
		markGiven(guard, player.getUUID(), day);
		player.sendSystemMessage(Component.literal("The guard marks a task on your map: 32 emeralds and a haul that grows with the mob when it is done.")
			.withStyle(ChatFormatting.GOLD));
		return true;
	}
}
