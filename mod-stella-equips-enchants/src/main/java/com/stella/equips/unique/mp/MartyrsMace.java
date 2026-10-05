package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** The Martyr's Mace. Every number lives in {@link #TUNING}. */
public final class MartyrsMace {
	public static final String ID = "martyrs_mace";
	private static final double WARD_SHARE = 0.10;
	private static final Set<UUID> CHARGED = ConcurrentHashMap.newKeySet();

	public record Tuning(double bonus, double bonusPlus7, double bonusPlus12, double healShare, double radius) {
	}

	public static final Tuning TUNING = new Tuning(0.30, 0.40, 0.55, 0.03, 8.0);

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			return CHARGED.contains(c.holder().getUUID()) ? (float) (amount * (1.0 + bonus(c.refine()))) : amount;
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			if (!CHARGED.remove(c.holder().getUUID()) || !c.atLeast(Uniques.PLUS_10) || dealt <= 0.0F) {
				return;
			}
			for (ServerPlayer ally : Priests.allies(c.holder(), TUNING.radius())) {
				MpKit.heal(ally, dealt * TUNING.healShare());
			}
		}
	};

	private MartyrsMace() {
	}

	/** The share a charged blow adds at the given refine. */
	public static double bonus(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.bonusPlus12();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.bonusPlus7() : TUNING.bonus();
	}

	/** A blow on an ally charges every wearer near it, and the top tier gives part of the blow back. */
	public static void grieve(final LivingEntity victim, final float damage) {
		if (damage <= 0.0F || !(victim instanceof ServerPlayer ally) || ally.isSpectator() || !(ally.level() instanceof ServerLevel level)) {
			return;
		}
		for (UniqueContext wearer : PriestWards.wearers(level, ally.position(), TUNING.radius(), ID)) {
			CHARGED.add(wearer.holder().getUUID());
			if (wearer.atLeast(Uniques.PLUS_15)) {
				MpKit.heal(ally, damage * WARD_SHARE);
			}
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Martyr's Mace", UniqueJob.PRIEST, UniqueKind.MACE, List.of(Identifier.parse("minecraft:zombified_piglin")),
			"When an ally within " + (int) TUNING.radius() + " is hit, your next melee hit deals +" + MpKit.percent(TUNING.bonus()),
			"+" + MpKit.percent(TUNING.bonusPlus7()),
			"That hit heals allies within " + (int) TUNING.radius() + " for " + MpKit.percent(TUNING.healShare()) + " of the hit",
			"+" + MpKit.percent(TUNING.bonusPlus12()),
			"Allies within " + (int) TUNING.radius() + " take " + MpKit.percent(WARD_SHARE) + " less damage",
			null, EFFECT));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (!blocked) {
				grieve(victim, damage);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHARGED.clear());
	}
}
