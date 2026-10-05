package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.UniquePassives;
import com.stella.equips.unique.Uniques;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Boarhide Vest. Every number lives in {@link #TUNING}. */
public final class BoarhideVest {
	public static final String ID = "boarhide_vest";

	public record Tuning(double knockbackResistance, double healShare, double healSharePlus7, double healSharePlus12, double radius,
		int resistTicks, int strengthTicks) {
	}

	public static final Tuning TUNING = new Tuning(0.50, 0.02, 0.03, 0.04, 8.0, 60, 100);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void everySecond(final UniqueContext c) {
			UniquePassives.hold(c.holder(), ID, Attributes.KNOCKBACK_RESISTANCE, TUNING.knockbackResistance());
		}

		@Override
		public void onKill(final UniqueContext c, final LivingEntity victim, final DamageSource s) {
			ServerPlayer holder = c.holder();
			double share = c.atLeast(Uniques.PLUS_12) ? TUNING.healSharePlus12() : c.atLeast(Uniques.PLUS_7) ? TUNING.healSharePlus7() : TUNING.healShare();
			MpKit.heal(holder, holder.getMaxHealth() * share);
			for (ServerPlayer ally : Priests.allies(holder, TUNING.radius())) {
				if (c.atLeast(Uniques.PLUS_10)) {
					MpKit.buff(ally, MobEffects.RESISTANCE, TUNING.resistTicks(), 0);
				}
				if (c.atLeast(Uniques.PLUS_15)) {
					MpKit.buff(ally, MobEffects.STRENGTH, TUNING.strengthTicks(), 0);
				}
			}
		}
	};

	private BoarhideVest() {
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Boarhide Vest", UniqueJob.PRIEST, UniqueKind.CHESTPLATE, List.of(Identifier.parse("minecraft:hoglin")),
			"+" + MpKit.percent(TUNING.knockbackResistance()) + " knockback resistance. Each kill heals you "
				+ MpKit.percent(TUNING.healShare()) + " of max health",
			MpKit.percent(TUNING.healSharePlus7()),
			"Your kills give allies within " + (int) TUNING.radius() + " Resistance I, " + MpKit.seconds(TUNING.resistTicks()),
			MpKit.percent(TUNING.healSharePlus12()),
			"Your kills give you and allies within " + (int) TUNING.radius() + " Strength I, " + MpKit.seconds(TUNING.strengthTicks()),
			EquipmentAssets.DIAMOND, EFFECT));
	}
}
