package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** How a tier and the omens of a run make its mobs stronger. */
public final class RuinTiers {
	/** The health gain of a tier plus omens never goes above this share of the base. */
	public static final double HEALTH_GAIN_CAP = 1.0;

	private static final Identifier HEALTH_ID = StellaRuins.id("tier_health");
	private static final Identifier DAMAGE_ID = StellaRuins.id("tier_damage");
	private static final Identifier SPEED_ID = StellaRuins.id("omen_speed");
	private static final Identifier ARMOR_ID = StellaRuins.id("omen_armor");

	private RuinTiers() {
	}

	/** The health gain of a tier and its omens, as a share of the base, capped at {@link #HEALTH_GAIN_CAP}. */
	public static double healthGain(final RuinConfig.Values config, final int tier, final List<Omen> omens) {
		double gain = config.tierHealthStep() * tier;
		for (Omen omen : omens) {
			gain += omen.health();
		}
		return Math.min(HEALTH_GAIN_CAP, gain);
	}

	/** The damage gain of a tier and its omens, as a share of the base. */
	public static double damageGain(final RuinConfig.Values config, final int tier, final List<Omen> omens) {
		double gain = config.tierDamageStep() * tier;
		for (Omen omen : omens) {
			gain += omen.damage();
		}
		return gain;
	}

	public static void apply(final Mob mob, final int tier, final List<Omen> omens) {
		RuinConfig.Values config = RuinConfig.get();
		multiply(mob, Attributes.MAX_HEALTH, HEALTH_ID, healthGain(config, tier, omens));
		multiply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damageGain(config, tier, omens));
		multiply(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, omens.stream().mapToDouble(Omen::speed).sum());
		double armor = omens.stream().mapToDouble(Omen::armor).sum();
		AttributeInstance armorInstance = mob.getAttribute(Attributes.ARMOR);
		if (armorInstance != null && armor > 0.0) {
			armorInstance.addPermanentModifier(new AttributeModifier(ARMOR_ID, armor, AttributeModifier.Operation.ADD_VALUE));
		}
		mob.setHealth(mob.getMaxHealth());
	}

	private static void multiply(final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double gain) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && gain > 0.0) {
			instance.addPermanentModifier(new AttributeModifier(id, gain, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
