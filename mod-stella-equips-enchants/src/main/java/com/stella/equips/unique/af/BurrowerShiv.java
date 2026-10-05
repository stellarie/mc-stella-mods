package com.stella.equips.unique.af;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** The Burrower's Shiv. */
public final class BurrowerShiv {
	public static final String ID = "burrower_shiv";
	public record Tuning(double chance, double chancePlus7, double chancePlus12, int delayTicks, float strikeShare, int maxExtraStrikes) {}
	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 20, 0.50F, 2);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F || !target.isAlive() || !(target.level() instanceof ServerLevel level)) {
				return;
			}
			double chance = Af.tier(c.refine(), TUNING.chance(), TUNING.chancePlus7(), TUNING.chancePlus12());
			if (!AfChance.roll(c.holder(), ID, chance)) {
				return;
			}
			strike(level, c, target, dealt * TUNING.strikeShare(), 1);
		}
	};

	private BurrowerShiv() {}

	/** Queues one extra strike and, at +15, the one repeat the tier allows. */
	private static void strike(final ServerLevel level, final UniqueContext c, final LivingEntity target, final float amount, final int count) {
		UniqueScheduler.after(level.getServer(), TUNING.delayTicks(), () -> {
			if (!target.isAlive()) {
				return;
			}
			UniqueDamage.deal(level, target, c.holder(), amount, c.atLeast(Uniques.PLUS_10));
			if (count < TUNING.maxExtraStrikes() && c.atLeast(Uniques.PLUS_15)) {
				strike(level, c, target, amount * TUNING.strikeShare(), count + 1);
			}
		});
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Burrower's Shiv", UniqueJob.FIGHTER, UniqueKind.SWORD,
			List.of(Identifier.parse("minecraft:silverfish")),
			"10% chance a hit strikes again 1 s later for 50% of the hit", "15%", "The second strike ignores armor", "20%", "The second strike can trigger once more", null, EFFECT));
	}
}
