package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Spectral Circlet. Spell hits make the target glow, and glowing targets take more spell damage. Every number lives in {@link #TUNING}. */
public final class SpectralCirclet {
	public static final String ID = "spectral_circlet";

	/** The tuning. Times are in ticks. */
	public record Tuning(int glowTicks, double baseBonus, double plus7Bonus, double plus12Bonus, double plus10Heal, int echoDelayTicks, double plus15Echo) {
	}

	public static final Tuning TUNING = new Tuning(100, 0.10, 0.15, 0.20, 0.02, 20, 0.30);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return target.hasEffect(MobEffects.GLOWING) ? (float) (amount * (1.0 + bonusAt(c.refine()))) : amount;
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			boolean wasGlowing = target.hasEffect(MobEffects.GLOWING);
			ServerPlayer holder = c.holder();
			if (wasGlowing && c.atLeast(Uniques.PLUS_10)) {
				MpKit.heal(holder, dealt * TUNING.plus10Heal());
			}
			if (wasGlowing && c.atLeast(Uniques.PLUS_15)) {
				float echo = (float) (dealt * TUNING.plus15Echo());
				UniqueScheduler.after(holder.level().getServer(), TUNING.echoDelayTicks(), () -> {
					if (holder.isAlive() && target.isAlive()) {
						UniqueDamage.deal(holder.level(), target, holder, echo, false);
					}
				});
			}
			if (!killed) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, TUNING.glowTicks(), 0));
			}
		}
	};

	private SpectralCirclet() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Spectral Circlet", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:vex")),
			"Spell hits make the target glow, " + MpKit.seconds(TUNING.glowTicks()) + ". Glowing targets take +" + MpKit.percent(TUNING.baseBonus()) + " spell damage",
			"+" + MpKit.percent(TUNING.plus7Bonus()),
			"Spell hits on glowing targets heal you " + MpKit.percent(TUNING.plus10Heal()) + " of the hit",
			"+" + MpKit.percent(TUNING.plus12Bonus()),
			MpKit.seconds(TUNING.echoDelayTicks()) + " after a spell hit on a glowing target, " + MpKit.percent(TUNING.plus15Echo()) + " of the hit lands again",
			EquipmentAssets.CHAINMAIL, EFFECT));
	}

	public static double bonusAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Bonus();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Bonus() : TUNING.baseBonus();
	}
}
