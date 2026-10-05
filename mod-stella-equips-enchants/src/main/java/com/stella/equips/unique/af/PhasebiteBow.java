package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueCooldowns;
import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** The Endermite's bow. */
public final class PhasebiteBow {
	public static final String ID = "phasebite_bow";

	public record Tuning(int cooldownTicks, int cooldownPlus7, int cooldownPlus12, int fallSafeTicks,
		int speedTicks, int speedAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(200, 140, 100, 60, 60, 1);
	private static final String ARMED = "stella_phasebite_sneaking";
	private static final Map<UUID, Integer> SAFE_UNTIL = new HashMap<>();
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			if (c.holder().isShiftKeyDown()) {
				arrow.addTag(ARMED);
			}
		}
	};

	private PhasebiteBow() {
	}

	public static int cooldown(final int refine) {
		return Af.tier(refine, TUNING.cooldownTicks(), TUNING.cooldownPlus7(), TUNING.cooldownPlus12());
	}

	public static void land(final AbstractArrow arrow, final Vec3 landing) {
		if (!arrow.entityTags().remove(ARMED) || !(arrow.getOwner() instanceof ServerPlayer holder)
			|| arrow.level() != holder.level() || !holder.isAlive()) {
			return;
		}
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		if (mark == null || !ID.equals(mark.id())) {
			return;
		}
		Vec3 offset = landing.subtract(holder.position());
		if (!holder.level().noCollision(holder, holder.getBoundingBox().move(offset))
			|| !UniqueCooldowns.ready(holder, ID, cooldown(mark.refine()))) {
			return;
		}
		holder.teleportTo(landing.x, landing.y, landing.z);
		holder.resetFallDistance();
		if (mark.refine() >= Uniques.PLUS_10) {
			SAFE_UNTIL.put(holder.getUUID(), holder.level().getServer().getTickCount() + TUNING.fallSafeTicks());
		}
		if (mark.refine() >= Uniques.PLUS_15) {
			Af.bless(holder, MobEffects.SPEED, TUNING.speedTicks(), TUNING.speedAmplifier());
		}
	}

	public static boolean protects(final ServerPlayer holder, final DamageSource source) {
		Integer until = SAFE_UNTIL.get(holder.getUUID());
		if (until != null && holder.level().getServer().getTickCount() >= until) {
			SAFE_UNTIL.remove(holder.getUUID());
			until = null;
		}
		return until != null && source.is(DamageTypes.FALL);
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Phasebite Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:endermite")),
			"A shot while sneaking teleports you to where the arrow lands. Cooldown 10 s",
			"7 s", "No fall damage for 3 s after the teleport", "5 s",
			"The teleport gives you Speed II, 3 s", null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SAFE_UNTIL.clear());
	}
}
