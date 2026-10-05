package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Soulflame Aegis. Every number lives in {@link #TUNING}. */
public final class SoulflameAegis {
	public static final String ID = "soulflame_aegis";

	public record Tuning(double radius, double radiusPlus7, double healShare, double healSharePlus12, int pulseTicks, int powerTicks, int fireTicks) {
	}

	public static final Tuning TUNING = new Tuning(8.0, 12.0, 0.02, 0.03, 100, 60, 100);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			for (ServerPlayer ally : Priests.allies(c.holder(), radius(c.refine()))) {
				MpKit.buff(ally, MobEffects.FIRE_RESISTANCE, TUNING.powerTicks(), 0);
			}
			if (Priests.due(ID, c.holder(), TUNING.pulseTicks())) {
				pulse(c);
			}
		}
	};

	private SoulflameAegis() {
	}

	/** The reach of the aegis at the given refine. */
	public static double radius(final int refine) {
		return refine >= Uniques.PLUS_7 ? TUNING.radiusPlus7() : TUNING.radius();
	}

	/** One pulse: every ally in reach mends a little. */
	public static void pulse(final UniqueContext c) {
		if (!c.atLeast(Uniques.PLUS_10)) {
			return;
		}
		double share = c.atLeast(Uniques.PLUS_12) ? TUNING.healSharePlus12() : TUNING.healShare();
		for (ServerPlayer ally : Priests.allies(c.holder(), radius(c.refine()))) {
			MpKit.heal(ally, ally.getMaxHealth() * share);
		}
	}

	/** A blow on a warded ally sets its melee attacker alight. */
	public static void avenge(final LivingEntity victim, final DamageSource source) {
		if (!(victim instanceof ServerPlayer ally) || ally.isSpectator() || !(ally.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity attacker = PriestWards.meleeAttacker(source);
		if (attacker == null || attacker == ally) {
			return;
		}
		for (UniqueContext wearer : PriestWards.wearers(level, ally.position(), TUNING.radiusPlus7(), ID)) {
			if (wearer.atLeast(Uniques.PLUS_15)) {
				MpKit.ignite(attacker, TUNING.fireTicks());
			}
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Soulflame Aegis", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("mod-stella-nether:sovereign")),
			"You and allies within " + (int) TUNING.radius() + " are immune to fire",
			"Radius " + (int) TUNING.radiusPlus7(),
			"Every " + MpKit.seconds(TUNING.pulseTicks()) + ", allies within " + (int) TUNING.radius() + " heal " + MpKit.percent(TUNING.healShare())
				+ " of max health",
			MpKit.percent(TUNING.healSharePlus12()),
			"Melee attackers of allies within " + (int) TUNING.radiusPlus7() + " are set alight, " + MpKit.seconds(TUNING.fireTicks()),
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (!blocked) {
				avenge(victim, source);
			}
		});
	}
}
