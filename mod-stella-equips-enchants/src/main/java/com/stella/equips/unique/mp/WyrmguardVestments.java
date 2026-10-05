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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Wyrmguard Vestments. Every number lives in {@link #TUNING}. */
public final class WyrmguardVestments {
	public static final String ID = "wyrmguard_vestments";
	private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();

	public record Tuning(double radius, double radiusPlus7, double healShare, double healSharePlus12, int pulseTicks, int effectTicks,
		int cooldownTicks, int resistAmplifier, int powerTicks) {
	}

	public static final Tuning TUNING = new Tuning(8.0, 12.0, 0.01, 0.02, 40, 60, 1200, 3, 60);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			ServerPlayer holder = c.holder();
			double radius = c.atLeast(Uniques.PLUS_7) ? TUNING.radiusPlus7() : TUNING.radius();
			for (ServerPlayer ally : Priests.allies(holder, radius)) {
				MpKit.buff(ally, MobEffects.RESISTANCE, TUNING.powerTicks(), 0);
			}
			if (Priests.due(ID, holder, TUNING.pulseTicks())) {
				pulse(c);
			}
		}
	};

	private WyrmguardVestments() {
	}

	/** One pulse: every ally in reach mends a little. */
	public static void pulse(final UniqueContext c) {
		if (!c.atLeast(Uniques.PLUS_10)) {
			return;
		}
		double radius = c.atLeast(Uniques.PLUS_7) ? TUNING.radiusPlus7() : TUNING.radius();
		double share = c.atLeast(Uniques.PLUS_12) ? TUNING.healSharePlus12() : TUNING.healShare();
		for (ServerPlayer ally : Priests.allies(c.holder(), radius)) {
			MpKit.heal(ally, ally.getMaxHealth() * share);
		}
	}

	/** The death a warded ally is saved from, once per rest. Returns true when the blow is spent. */
	public static boolean save(final LivingEntity victim, final float amount) {
		if (!(victim instanceof ServerPlayer ally) || !ally.isAlive() || !(ally.level() instanceof ServerLevel level)) {
			return false;
		}
		if (amount < ally.getHealth() + ally.getAbsorptionAmount()) {
			return false;
		}
		boolean warded = false;
		for (UniqueContext wearer : PriestWards.wearers(level, ally.position(), TUNING.radiusPlus7(), ID)) {
			if (!wearer.atLeast(Uniques.PLUS_15)) {
				continue;
			}
			long now = level.getServer().getTickCount();
			Long at = READY.get(wearer.holder().getUUID());
			if (at != null && now - at < TUNING.cooldownTicks()) {
				continue;
			}
			READY.put(wearer.holder().getUUID(), now);
			warded = true;
		}
		if (!warded) {
			return false;
		}
		ally.setHealth(1.0F);
		MpKit.buff(ally, MobEffects.RESISTANCE, TUNING.effectTicks(), TUNING.resistAmplifier());
		return true;
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Wyrmguard Vestments", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:ender_dragon")),
			"Allies within " + (int) TUNING.radius() + " get Resistance I",
			"Radius " + (int) TUNING.radiusPlus7(),
			"Every " + MpKit.seconds(TUNING.pulseTicks()) + ", allies within " + (int) TUNING.radius() + " heal " + MpKit.percent(TUNING.healShare())
				+ " of max health",
			MpKit.percent(TUNING.healSharePlus12()),
			"Once per " + MpKit.seconds(TUNING.cooldownTicks()) + ", an ally that would die stays at 1 health and gets Resistance IV, "
				+ MpKit.seconds(TUNING.effectTicks()),
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !save(entity, amount));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}
}
