package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueRank;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The Johnny's Axe. */
public final class JohnnysAxe {
	public static final String ID = "johnnys_axe";
	public record Tuning(double armorShare, double armorSharePlus7, double cap, double capPlus12, int weaknessTicks, double executeThreshold) {}
	public static final Tuning TUNING = new Tuning(0.02, 0.03, 0.40, 0.60, 60, 0.15);
	private static final float EXECUTE_HEADROOM = 20.0F;

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			double share = c.atLeast(Uniques.PLUS_7) ? TUNING.armorSharePlus7() : TUNING.armorShare();
			double cap = c.atLeast(Uniques.PLUS_12) ? TUNING.capPlus12() : TUNING.cap();
			float out = (float) (amount * (1.0 + Math.min(cap, target.getArmorValue() * share)));
			// The gate is read before the hit lands, so a target on the line survives it.
			if (c.atLeast(Uniques.PLUS_15) && UniqueRank.of(target) != UniqueRank.BOSS
				&& target.getHealth() < target.getMaxHealth() * TUNING.executeThreshold()) {
				// An execute covers the target's armor many times over.
				return (out + target.getHealth() + target.getAbsorptionAmount() + 1.0F) * EXECUTE_HEADROOM;
			}
			return out;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (c.atLeast(Uniques.PLUS_7)) {
				Af.curse(target, MobEffects.WEAKNESS, TUNING.weaknessTicks(), 0);
			}
		}
	};

	private JohnnysAxe() {}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Johnny's Axe", UniqueJob.FIGHTER, UniqueKind.AXE,
			List.of(Identifier.parse("minecraft:vindicator")),
			"+2% damage per armor point of the target, max +40%", "+3% per point", "Hits give Weakness I, 3 s", "Max +60%", "A hit on a target below 15% health kills it outright", null, EFFECT));
	}
}
