package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Rotgut Cleaver. */
public final class RotgutCleaver {
	public static final String ID = "rotgut_cleaver";
	public record Tuning(double healShare, double healPlus7, double healPlus12, double absorptionCap, double lowThreshold, double lowMultiplier) {}
	public static final Tuning TUNING = new Tuning(0.02, 0.03, 0.04, 0.20, 0.30, 2.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F) {
				return;
			}
			double heal = dealt * Af.tier(c.refine(), TUNING.healShare(), TUNING.healPlus7(), TUNING.healPlus12());
			if (c.atLeast(Uniques.PLUS_15) && c.holder().getHealth() < c.holder().getMaxHealth() * TUNING.lowThreshold()) {
				heal *= TUNING.lowMultiplier();
			}
			if (!c.atLeast(Uniques.PLUS_10)) {
				c.holder().heal((float) heal);
				return;
			}
			double room = c.holder().getMaxHealth() - c.holder().getHealth();
			double healed = Math.min(room, heal);
			c.holder().heal((float) healed);
			float overflow = (float) (heal - healed);
			if (overflow > 0.0F) {
				float cap = (float) (c.holder().getMaxHealth() * TUNING.absorptionCap());
				// Golden hearts have their own ceiling, and it clamps them to zero by default.
				AttributeInstance limit = c.holder().getAttribute(Attributes.MAX_ABSORPTION);
				if (limit != null && limit.getBaseValue() < cap) {
					limit.setBaseValue(cap);
				}
				c.holder().setAbsorptionAmount(Math.min(cap, c.holder().getAbsorptionAmount() + overflow));
			}
		}
	};

	private RotgutCleaver() {}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Rotgut Cleaver", UniqueJob.FIGHTER, UniqueKind.AXE,
			List.of(Identifier.parse("minecraft:zombie")),
			"Melee hits heal you 2% of the hit", "3%", "Healing above max health becomes Absorption, up to 20% of max health", "4%", "Healing doubles below 30% health", null, EFFECT));
	}
}
