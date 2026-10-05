package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Ectoplasm Veil lets some hits pass and empowers the next spell. */
public final class EctoplasmVeil {
	public static final String ID = "ectoplasm_veil";

	public record Tuning(double baseChance, double plus7Chance, double plus12Chance, double plus10Bonus,
		int plus15BuffTicks, int plus15BuffAmplifier) {
	}

	public static final Tuning TUNING = new Tuning(0.10, 0.15, 0.20, 0.30, 40, 1);
	private static final Map<UUID, Boolean> NEXT_SPELL = new HashMap<>();

	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public float incoming(final UniqueContext c, final DamageSource source, final float amount) {
			if (amount <= 0.0F || !MpRolls.chance(ID, c.holder(), chanceAt(c.refine()))) {
				return amount;
			}
			if (c.atLeast(Uniques.PLUS_10)) {
				NEXT_SPELL.put(c.holder().getUUID(), true);
			}
			if (c.atLeast(Uniques.PLUS_15)) {
				MpKit.buff(c.holder(), MobEffects.SPEED, TUNING.plus15BuffTicks(), TUNING.plus15BuffAmplifier());
				MpKit.buff(c.holder(), MobEffects.REGENERATION, TUNING.plus15BuffTicks(), TUNING.plus15BuffAmplifier());
			}
			return 0.0F;
		}

		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource source, final float amount) {
			return c.atLeast(Uniques.PLUS_10) && NEXT_SPELL.remove(c.holder().getUUID()) != null
				? (float) (amount * (1.0 + TUNING.plus10Bonus())) : amount;
		}
	};

	private EctoplasmVeil() {
	}

	public static double chanceAt(final int refine) {
		if (refine >= Uniques.PLUS_12) {
			return TUNING.plus12Chance();
		}
		return refine >= Uniques.PLUS_7 ? TUNING.plus7Chance() : TUNING.baseChance();
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_SPELL.clear());
		Uniques.register(new UniqueDef(ID, "Ectoplasm Veil", UniqueJob.MAGE, UniqueKind.CHESTPLATE,
			List.of(Identifier.parse("mod-stella-mobs:ghost")),
			MpKit.percent(TUNING.baseChance()) + " chance a hit passes through you and deals no damage",
			MpKit.percent(TUNING.plus7Chance()),
			"After a pass, your next spell hit deals +" + MpKit.percent(TUNING.plus10Bonus()),
			MpKit.percent(TUNING.plus12Chance()),
			"A pass gives you Speed II and Regeneration II, " + MpKit.seconds(TUNING.plus15BuffTicks()),
			EquipmentAssets.CHAINMAIL, EFFECT));
	}
}
