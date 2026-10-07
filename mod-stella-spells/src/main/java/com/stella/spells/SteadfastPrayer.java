package com.stella.spells;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Steadfast Prayer: twenty seconds in which no harmful status lands on the holder, paid for with nine
 * tenths of the damage the holder deals.
 */
public final class SteadfastPrayer {
	/** The prayer lasts twenty seconds. */
	public static final int PRAYER_TICKS = 400;
	/** The share of the holder's outgoing damage that survives the prayer. */
	public static final double OUTGOING_DAMAGE_SHARE = 0.10;

	private SteadfastPrayer() {
	}

	/** True when this effect would harm a holder who stands in the prayer. */
	public static boolean refuses(final LivingEntity holder, final MobEffectInstance effect) {
		return holder.hasEffect(SpellStatus.STEADFAST_PRAYER)
			&& effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
	}

	/** The blow a holder of the prayer deals: a tenth of it. */
	public static float outgoing(final LivingEntity dealer, final float amount) {
		return dealer.hasEffect(SpellStatus.STEADFAST_PRAYER) ? (float) (amount * OUTGOING_DAMAGE_SHARE) : amount;
	}
}
