package com.stella.equips.power;

import com.stella.equips.StellaEquips;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class PowerAttributes {
	/** The vanilla base of `minecraft:max_health`, which the modifier is measured against. */
	private static final double VANILLA_MAX_HEALTH = 20.0;
	private static final Identifier MAX_HEALTH_ID = id("max_health");
	private static final Identifier ARMOR_ID = id("armor");
	private static final Identifier KNOCKBACK_RESISTANCE_ID = id("knockback_resistance");
	private static final Identifier ATTACK_DAMAGE_ID = id("attack_damage");
	private static final Identifier ATTACK_SPEED_ID = id("attack_speed");

	private PowerAttributes() {
	}

	public static void apply(final ServerPlayer player, final int level, final PowerConfig config) {
		PowerConfig.Attributes attributes = config.attributes();
		double fraction = PowerCurve.levelFraction(level, config);
		double health = attributes.healthBase() + attributes.healthPerLevel() * Math.max(0, level - 1);
		replace(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID, health - VANILLA_MAX_HEALTH);
		replace(player, Attributes.ARMOR, ARMOR_ID, attributes.armorAtCap() * fraction);
		replace(
			player,
			Attributes.KNOCKBACK_RESISTANCE,
			KNOCKBACK_RESISTANCE_ID,
			attributes.knockbackResistanceAtCap() * fraction
		);
		replace(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID, attributes.attackDamageAtCap() * fraction);
		replace(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, attributes.attackSpeedAtCap() * fraction);
		clampHealth(player);
	}

	public static void remove(final ServerPlayer player) {
		remove(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID);
		remove(player, Attributes.ARMOR, ARMOR_ID);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_ID);
		remove(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID);
		remove(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
		clampHealth(player);
	}

	private static void clampHealth(final ServerPlayer player) {
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void replace(
		final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		double capped = Math.max(0.0, Math.min(amount, headroom(attribute, instance)));
		if (capped == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(
				id, capped, AttributeModifier.Operation.ADD_VALUE
			));
		}
	}

	private static double headroom(final Holder<Attribute> attribute, final AttributeInstance instance) {
		if (attribute.value() instanceof RangedAttribute ranged) {
			return ranged.getMaxValue() - instance.getBaseValue();
		}

		return Double.MAX_VALUE;
	}

	private static void remove(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static Identifier id(final String name) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_" + name);
	}
}
