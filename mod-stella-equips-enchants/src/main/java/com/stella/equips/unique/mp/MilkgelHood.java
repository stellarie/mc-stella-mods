package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Milkgel Hood. Every number lives in {@link #TUNING}. */
public final class MilkgelHood {
	public static final String ID = "milkgel_hood";

	public record Tuning(int everyTicks, int everyPlus7, int everyPlus12, double radius, double healShare) {
	}

	public static final Tuning TUNING = new Tuning(200, 140, 100, 6.0, 0.03);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			if (!Priests.due(ID, c.holder(), every(c.refine()))) {
				return;
			}
			ServerPlayer holder = c.holder();
			MobEffectInstance removed = Priests.removeOneHarmful(holder);
			if (removed != null && c.atLeast(Uniques.PLUS_15)) {
				MpKit.heal(holder, holder.getMaxHealth() * TUNING.healShare());
			}
			if (c.atLeast(Uniques.PLUS_10)) {
				for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
					if (ally != holder) {
						Priests.removeOneHarmful(ally);
					}
				}
			}
		}
	};

	private MilkgelHood() {
	}

	/** How often the hood clears a harmful effect at the given refine. */
	public static int every(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.everyPlus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.everyPlus7() : TUNING.everyTicks();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Milkgel Hood", UniqueJob.PRIEST, UniqueKind.HELMET, List.of(Identifier.parse("mod-stella-mobs:white_slime")),
			"Every " + MpKit.seconds(TUNING.everyTicks()) + ", the next harmful effect on you is removed at once",
			MpKit.seconds(TUNING.everyPlus7()),
			"Allies within " + (int) TUNING.radius() + " are also protected",
			MpKit.seconds(TUNING.everyPlus12()),
			"A removed effect heals its target " + MpKit.percent(TUNING.healShare()) + " of max health",
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
