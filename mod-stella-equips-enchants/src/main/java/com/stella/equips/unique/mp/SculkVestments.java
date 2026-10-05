package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
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

/** The Sculk Vestments. Every number lives in {@link #TUNING}. */
public final class SculkVestments {
	public static final String ID = "sculk_vestments";
	private static final double WARD_SHARE = 0.10;
	private static final double MARK_BONUS = 0.15;

	public record Tuning(double radius, double radiusPlus7, int pulseTicks, int glowTicks, double allyRadius) {
	}

	public static final Tuning TUNING = new Tuning(16.0, 24.0, 40, 60, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			if (c.atLeast(Uniques.PLUS_10)) {
				for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.allyRadius())) {
					Priests.cleanse(ally, MobEffects.DARKNESS);
					Priests.cleanse(ally, MobEffects.BLINDNESS);
				}
			}
			if (Priests.due(ID, c.holder(), TUNING.pulseTicks())) {
				pulse(c);
			}
		}
	};

	private SculkVestments() {
	}

	/** One sweep of the sculk: every foe in reach is marked. */
	public static void pulse(final UniqueContext c) {
		ServerPlayer holder = c.holder();
		if (!(holder.level() instanceof ServerLevel level)) {
			return;
		}
		double radius = c.atLeast(Uniques.PLUS_7) ? TUNING.radiusPlus7() : TUNING.radius();
		for (LivingEntity foe : MpKit.foesAround(level, holder, holder.position(), radius, null)) {
			MpKit.buff(foe, MobEffects.GLOWING, TUNING.glowTicks(), 0);
		}
	}

	/** A marked foe hits softer, and takes more from the allies of its marker. */
	public static void mark(final LivingEntity victim, final DamageSource source, final float damage) {
		if (damage <= 0.0F || source.is(UniqueDamage.UNIQUE) || source.is(UniqueDamage.UNIQUE_PIERCE)) {
			return;
		}
		if (victim instanceof ServerPlayer ally) {
			if (ally.isSpectator() || !(ally.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity foe)
				|| !foe.hasEffect(MobEffects.GLOWING)) {
				return;
			}
			if (PriestWards.warded(level, ally.position(), TUNING.allyRadius(), ID, Uniques.PLUS_12)) {
				MpKit.heal(ally, damage * WARD_SHARE);
			}
			return;
		}
		if (!victim.hasEffect(MobEffects.GLOWING) || !(source.getEntity() instanceof ServerPlayer attacker)
			|| !(attacker.level() instanceof ServerLevel level) || !PriestWards.warded(level, attacker.position(), TUNING.allyRadius(), ID, Uniques.PLUS_15)) {
			return;
		}
		UniqueDamage.deal(level, victim, attacker, damage * (float) MARK_BONUS, false);
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sculk Vestments", UniqueJob.PRIEST, UniqueKind.LEGGINGS, List.of(Identifier.parse("minecraft:warden")),
			"Every " + MpKit.seconds(TUNING.pulseTicks()) + ", foes within " + (int) TUNING.radius() + " glow, " + MpKit.seconds(TUNING.glowTicks()),
			"Radius " + (int) TUNING.radiusPlus7(),
			"Allies within " + (int) TUNING.allyRadius() + " are immune to Darkness and Blindness",
			"Glowing foes deal allies within " + (int) TUNING.allyRadius() + " " + MpKit.percent(WARD_SHARE) + " less damage",
			"Glowing foes take +" + MpKit.percent(MARK_BONUS) + " damage from allies within " + (int) TUNING.allyRadius(),
			EquipmentAssets.DIAMOND, EFFECT));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (!blocked) {
				mark(victim, source, damage);
			}
		});
	}
}
