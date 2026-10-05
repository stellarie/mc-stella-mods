package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The Widow's Fang. Every number lives in {@link #TUNING}. */
public final class WidowsFang {
	public static final String ID = "widows_fang";
	private static final int SLOW_AMPLIFIER = 3;
	private static final int CLIMB_GRACE_TICKS = 40;
	private static final double CLIMB_PUSH = 0.2;
	private static final Map<UUID, Long> CLIMBERS = new ConcurrentHashMap<>();

	public record Tuning(int baseTicks, int plus7Ticks, int plus12Ticks, double plus10Bonus) {
	}

	public static final Tuning TUNING = new Tuning(20, 30, 40, 0.25);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && target.hasEffect(MobEffects.SLOWNESS)
				? (float) (amount * (1.0 + TUNING.plus10Bonus())) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (crit) {
				Af.curse(target, MobEffects.SLOWNESS, slowTicks(c.refine()), SLOW_AMPLIFIER);
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_15)) {
				CLIMBERS.put(c.holder().getUUID(), (long) c.holder().level().getServer().getTickCount());
			}
		}
	};

	private WidowsFang() {
	}

	/** How long a critical hit slows its target at the given refine. */
	public static int slowTicks(final int refine) {
		return Af.tier(refine, TUNING.baseTicks(), TUNING.plus7Ticks(), TUNING.plus12Ticks());
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Widow's Fang", UniqueJob.FIGHTER, UniqueKind.SWORD, List.of(Identifier.parse("minecraft:spider")),
			"A critical hit gives Slowness IV, " + Af.seconds(TUNING.baseTicks()),
			Af.seconds(TUNING.plus7Ticks()),
			"Targets with Slowness take +" + Af.percent(TUNING.plus10Bonus()),
			Af.seconds(TUNING.plus12Ticks()),
			"While held, you climb walls like a spider", null, EFFECT));
		ServerTickEvents.END_SERVER_TICK.register(WidowsFang::climb);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLIMBERS.clear());
	}

	/** A wearer that held the fang a moment ago climbs: a wall in front, and a push into it. */
	private static void climb(final MinecraftServer server) {
		if (CLIMBERS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long seen = CLIMBERS.get(player.getUUID());
			if (seen == null) {
				continue;
			}
			if (now - seen > CLIMB_GRACE_TICKS) {
				CLIMBERS.remove(player.getUUID());
				continue;
			}
			if (!player.horizontalCollision || player.isPassenger() || player.isInWater()) {
				continue;
			}
			if (player.zza <= 0.0F && player.xxa == 0.0F) {
				continue;
			}
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, CLIMB_PUSH, motion.z);
			player.fallDistance = 0.0F;
		}
	}
}
