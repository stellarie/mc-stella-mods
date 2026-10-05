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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/** Echo Circlet repeats part of a spell hit after one second. */
public final class EchoCirclet {
	public static final String ID = "echo_circlet";

	public record Tuning(int delayTicks, double baseShare, double plus7Share, double plus12Share, double plus15Radius) {
	}

	public static final Tuning TUNING = new Tuning(20, 0.25, 0.35, 0.45, 3.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float dealt, final boolean killed) {
			ServerPlayer holder = c.holder();
			ServerLevel level = holder.level();
			Vec3 center = target.position();
			float echo = (float) (dealt * shareAt(c.refine()));
			boolean pierce = c.atLeast(Uniques.PLUS_10);
			boolean area = c.atLeast(Uniques.PLUS_15);
			UniqueScheduler.after(level.getServer(), TUNING.delayTicks(), () -> {
				if (!holder.isAlive() || holder.level() != level) {
					return;
				}
				if (target.isAlive()) {
					UniqueDamage.deal(level, target, holder, echo, pierce);
				}
				if (area) {
					UniqueDamage.area(level, center, TUNING.plus15Radius(), holder, echo, pierce, target);
				}
			});
		}
	};

	private EchoCirclet() {
	}

	public static double shareAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Share();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Share() : TUNING.baseShare();
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Echo Circlet", UniqueJob.MAGE, UniqueKind.HELMET, List.of(Identifier.parse("minecraft:warden")),
			MpKit.seconds(TUNING.delayTicks()) + " after a spell hit, " + MpKit.percent(TUNING.baseShare()) + " of the hit lands again",
			MpKit.percent(TUNING.plus7Share()),
			"The echo ignores armor",
			MpKit.percent(TUNING.plus12Share()),
			"The echo hits every foe within " + (int) TUNING.plus15Radius() + " of the target",
			EquipmentAssets.NETHERITE, EFFECT));
	}
}
