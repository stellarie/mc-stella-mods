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
	private static final Identifier MAX_HEALTH_ID = id("max_health");
	private static final Identifier ARMOR_ID = id("armor");
	private static final Identifier KNOCKBACK_RESISTANCE_ID = id("knockback_resistance");
	private static final Identifier ATTACK_DAMAGE_ID = id("attack_damage");
	private static final Identifier ATTACK_SPEED_ID = id("attack_speed");

	private PowerAttributes() {
	}

	public static double apply(
		final ServerPlayer player, final long survivabilityPoints, final int level, final PowerConfig config
	) {
		PowerConfig.Survivability survivability = config.attributes().distance();
		double healthGained = replace(
			player, Attributes.MAX_HEALTH, MAX_HEALTH_ID, survivability.maxHealth() * survivabilityPoints
		);
		replace(player, Attributes.ARMOR, ARMOR_ID, survivability.armor() * survivabilityPoints);
		replace(
			player,
			Attributes.KNOCKBACK_RESISTANCE,
			KNOCKBACK_RESISTANCE_ID,
			survivability.knockbackResistance() * survivabilityPoints
		);
		replace(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID, PowerCurve.attackDamageBonus(level, config));
		replace(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, PowerCurve.attackSpeedBonus(level, config));
		clampHealth(player);
		return healthGained;
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

	private static double replace(
		final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return 0.0;
		}

		AttributeModifier previous = instance.getModifier(id);
		double before = previous == null ? 0.0 : previous.amount();
		double capped = Math.max(0.0, Math.min(amount, headroom(attribute, instance)));
		if (capped == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(modifier(id, capped));
		}

		return capped - before;
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

	private static AttributeModifier modifier(final Identifier id, final double amount) {
		return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
	}

	private static Identifier id(final String name) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "power_" + name);
	}
}
