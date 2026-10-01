package com.stella.mobs.behavior;

import com.stella.mobs.scaling.ThreatState;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * A player learns when a much stronger mob starts hunting them: an elite, a boss, or a mob at least {@link #LEVEL_GAP}
 * levels above the player's experience level. The warning is an action bar line and a low note only that player hears,
 * at most once per mob every {@link #COOLDOWN_TICKS}.
 */
public final class DangerSense {
	public static final int LEVEL_GAP = 10;
	public static final int COOLDOWN_TICKS = 600;

	/** Test seam: each warning sent. */
	@FunctionalInterface
	public interface Recorder {
		void warned(ServerPlayer player, Mob mob, String text);
	}

	private static final Map<Mob, Long> LAST = new WeakHashMap<>();
	private static volatile Recorder recorder = (player, mob, text) -> { };

	private DangerSense() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (player, mob, text) -> { } : newRecorder;
	}

	public static void check(final Mob mob, final LivingEntity target) {
		if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator() || !(mob instanceof Enemy)) {
			return;
		}
		boolean boss = EliteState.isBoss(mob);
		boolean elite = !boss && EliteState.isElite(mob);
		int level = ThreatState.level(mob);
		if (!boss && !elite && level < player.experienceLevel + LEVEL_GAP) {
			return;
		}
		long now = player.level().getGameTime();
		synchronized (LAST) {
			Long last = LAST.get(mob);
			if (last != null && now - last < COOLDOWN_TICKS) {
				return;
			}
			LAST.put(mob, now);
		}
		String rank = boss ? "Boss " : elite ? "Elite " : "";
		String text = "! " + rank + mob.getDisplayName().getString() + " (Lv " + level + ") is hunting you";
		player.sendSystemMessage(Component.literal(text).withStyle(boss ? ChatFormatting.DARK_RED : ChatFormatting.GOLD), true);
		player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BASS, SoundSource.HOSTILE,
			player.getX(), player.getY(), player.getZ(), 1.0F, 0.5F, player.getRandom().nextLong()));
		recorder.warned(player, mob, text);
	}
}
