package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * Surprise, mobs: every seventh night the Blood Moon rises over the overworld. Hostiles that spawn under
 * it roll elite three times as often and carry Strength until dawn, and every player in the overworld is
 * told when it rises and when it sets. The calendar reads the overworld clock, so the moon falls on the
 * same nights for everyone and a sleep that skips the night skips the moon with it.
 */
public final class BloodMoon {
	public static final int PERIOD_NIGHTS = 7;
	public static final long DAY_TICKS = 24_000L;
	public static final long NIGHT_START = 13_000L;
	public static final long NIGHT_END = 23_000L;
	public static final double ELITE_CHANCE_MULTIPLIER = 3.0;
	public static final int STRENGTH_AMPLIFIER = 0;
	private static final int TITLE_FADE_IN = 10;
	private static final int TITLE_STAY = 60;
	private static final int TITLE_FADE_OUT = 20;
	private static final float SOUND_VOLUME = 0.6F;

	/** Test seam: each rise and set, and each hostile the moon empowered. */
	public interface Recorder {
		default void changed(final ServerLevel level, final boolean risen, final int players) {
		}

		default void empowered(final Mob mob) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	// The last answer per level, so a rise or a set is announced once, on the edge.
	private static final Map<ResourceKey<Level>, Boolean> LAST = new HashMap<>();

	private BloodMoon() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** True through the night of every seventh day: day six, thirteen, twenty, and so on. */
	public static boolean isBloodMoon(final long clockTime) {
		long day = Math.floorDiv(clockTime, DAY_TICKS);
		long time = Math.floorMod(clockTime, DAY_TICKS);
		return Math.floorMod(day, PERIOD_NIGHTS) == PERIOD_NIGHTS - 1 && time >= NIGHT_START && time < NIGHT_END;
	}

	public static long ticksUntilDawn(final long clockTime) {
		return Math.max(0L, NIGHT_END - Math.floorMod(clockTime, DAY_TICKS));
	}

	public static boolean active(final ServerLevel level) {
		return level.dimension() == Level.OVERWORLD && isBloodMoon(level.getOverworldClockTime());
	}

	/** The elite chance a spawn rolls against: tripled under the moon, never past certainty. */
	public static double eliteChance(final ServerLevel level, final double base) {
		return active(level) ? Math.min(1.0, base * ELITE_CHANCE_MULTIPLIER) : base;
	}

	/** A hostile that spawns under the moon carries Strength until dawn. */
	public static void empower(final Mob mob, final ServerLevel level) {
		if (!(mob instanceof Enemy) || !active(level)) {
			return;
		}

		int untilDawn = (int) ticksUntilDawn(level.getOverworldClockTime());
		if (untilDawn <= 0) {
			return;
		}

		mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, untilDawn, STRENGTH_AMPLIFIER));
		recorder.empowered(mob);
	}

	/** Watches the overworld's moon and announces its rise and its set to every player there. */
	public static void tick(final ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}

		boolean now = active(level);
		Boolean was = LAST.put(level.dimension(), now);
		if (was == null || was == now) {
			return;
		}

		Component title = Component.literal(now ? "The Blood Moon rises" : "The Blood Moon sets").withStyle(ChatFormatting.DARK_RED);
		Component subtitle = Component.literal(now ? "Monsters grow stronger until dawn" : "The night is quiet again").withStyle(ChatFormatting.RED);
		int players = 0;
		for (ServerPlayer player : level.players()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			if (now) {
				level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, SOUND_VOLUME, 0.6F);
			}
			players++;
		}

		recorder.changed(level, now, players);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=bloodMoon {} clock={} players={}", now ? "RISE" : "SET", level.getOverworldClockTime(), players);
	}
}
