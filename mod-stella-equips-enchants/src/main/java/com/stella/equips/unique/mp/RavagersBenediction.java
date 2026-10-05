package com.stella.equips.unique.mp;

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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The Ravager's Benediction. Every number lives in {@link #TUNING}. */
public final class RavagersBenediction {
	public static final String ID = "ravagers_benediction";
	private static final double KNOCKBACK_UP = 0.4;
	private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();

	public record Tuning(double healShare, double healSharePlus7, double healSharePlus12, double radius, int readyTicks, double knockRadius,
		int weaknessTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.03, 0.04, 0.05, 6.0, 200, 4.0, 80);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F) {
				return;
			}
			ServerPlayer holder = c.holder();
			double share = c.atLeast(Uniques.PLUS_12) ? TUNING.healSharePlus12() : c.atLeast(Uniques.PLUS_7) ? TUNING.healSharePlus7() : TUNING.healShare();
			for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
				MpKit.heal(ally, dealt * share);
			}
			if (!c.atLeast(Uniques.PLUS_10) || !(holder.level() instanceof ServerLevel level)) {
				return;
			}
			long now = holder.level().getServer().getTickCount();
			Long at = READY.get(holder.getUUID());
			if (at != null && now - at < TUNING.readyTicks()) {
				return;
			}
			READY.put(holder.getUUID(), now);
			for (LivingEntity foe : MpKit.foesAround(level, holder, target.position(), TUNING.knockRadius(), target)) {
				Vec3 away = foe.position().subtract(holder.position());
				Vec3 push = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
				foe.push(push.x, KNOCKBACK_UP, push.z);
				if (c.atLeast(Uniques.PLUS_15)) {
					MpKit.buff(foe, MobEffects.WEAKNESS, TUNING.weaknessTicks(), 0);
				}
			}
		}
	};

	private RavagersBenediction() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Ravager's Benediction", UniqueJob.PRIEST, UniqueKind.MACE, List.of(Identifier.parse("minecraft:ravager")),
			"Melee hits heal you and allies within " + (int) TUNING.radius() + " for " + MpKit.percent(TUNING.healShare()) + " of the hit",
			MpKit.percent(TUNING.healSharePlus7()),
			"Every " + MpKit.seconds(TUNING.readyTicks()) + ", the next hit knocks back foes within " + (int) TUNING.knockRadius(),
			MpKit.percent(TUNING.healSharePlus12()),
			"That knockback hit gives Weakness I, " + MpKit.seconds(TUNING.weaknessTicks()),
			null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}
}
