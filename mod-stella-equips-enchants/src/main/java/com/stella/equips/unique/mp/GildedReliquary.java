package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Gilded Reliquary. Every number lives in {@link #TUNING}. */
public final class GildedReliquary {
	public static final String ID = "gilded_reliquary";

	public record Tuning(double chance, double chancePlus7, double chancePlus12, double share, int absorptionTicks, double radius, double piglinRadius) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 0.10, 200, 6.0, 16.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (dealt <= 0.0F || !MpRolls.chance(ID, c.holder(), chance(c.refine()))) {
				return;
			}
			gild(c.holder());
			if (c.atLeast(Uniques.PLUS_15)) {
				for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.radius())) {
					if (ally != c.holder()) {
						gild(ally);
					}
				}
			}
		}

		@Override
		public void everySecond(final UniqueContext c) {
			ServerPlayer holder = c.holder();
			if (!c.atLeast(Uniques.PLUS_10) || !(holder.level() instanceof ServerLevel level)) {
				return;
			}
			for (Piglin piglin : level.getEntitiesOfClass(Piglin.class, holder.getBoundingBox().inflate(TUNING.piglinRadius()))) {
				piglin.setTarget(null);
				piglin.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
				piglin.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
			}
		}
	};

	private GildedReliquary() {
	}

	/** The chance of the gift at the given refine. */
	public static double chance(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.chancePlus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.chancePlus7() : TUNING.chance();
	}

	/** Golden hearts for the life of the gift, then gone with it. */
	public static void gild(final ServerPlayer target) {
		float amount = (float) (target.getMaxHealth() * TUNING.share());
		GelMantle.grant(target, TUNING.share());
		UniqueScheduler.after(target.level().getServer(), TUNING.absorptionTicks(), () -> {
			if (target.isAlive()) {
				target.setAbsorptionAmount(Math.max(0.0F, target.getAbsorptionAmount() - amount));
			}
		});
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Gilded Reliquary", UniqueJob.PRIEST, UniqueKind.MACE, List.of(Identifier.parse("minecraft:piglin")),
			MpKit.percent(TUNING.chance()) + " chance a hit gives you Absorption " + MpKit.percent(TUNING.share()) + " of max health, "
				+ MpKit.seconds(TUNING.absorptionTicks()),
			MpKit.percent(TUNING.chancePlus7()),
			"Piglins stay neutral as if you wore gold",
			MpKit.percent(TUNING.chancePlus12()),
			"The Absorption also goes to allies within " + (int) TUNING.radius(),
			null, EFFECT));
	}
}
