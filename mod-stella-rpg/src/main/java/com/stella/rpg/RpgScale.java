package com.stella.rpg;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The health scale K. Every damage formula and constant stays in vanilla units.
 * Max health, max absorption, the damage that reaches health, and every heal are multiplied by K once.
 */
public final class RpgScale {
	/**
	 * Read at class init, before any config exists.
	 * A config value could differ between client and server.
	 */
	public static final double ATTRIBUTE_CAP = 1_000_000.0;
	public static final Identifier MODIFIER_ID = StellaRpg.id("health_scale");

	private RpgScale() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				apply(living);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> apply(newPlayer));
	}

	public static double k() {
		return RpgConfigStore.current().healthScale();
	}

	public static float kf() {
		return (float) k();
	}

	/** Adds the K modifier once. A first apply keeps the health and absorption fraction. */
	public static void apply(final LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}
		double amount = k() - 1.0;
		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && stale(health, amount)) {
			float fraction = fraction(entity.getHealth(), entity.getMaxHealth());
			health.addOrReplacePermanentModifier(modifier(amount));
			entity.setHealth(fraction * entity.getMaxHealth());
		}
		AttributeInstance absorption = entity.getAttribute(Attributes.MAX_ABSORPTION);
		if (absorption != null && stale(absorption, amount)) {
			float fraction = fraction(entity.getAbsorptionAmount(), entity.getMaxAbsorption());
			absorption.addOrReplacePermanentModifier(modifier(amount));
			entity.setAbsorptionAmount(fraction * entity.getMaxAbsorption());
		}
	}

	public static boolean hasModifier(final LivingEntity entity, final Holder<Attribute> attribute) {
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance != null && instance.getModifier(MODIFIER_ID) != null;
	}

	private static boolean stale(final AttributeInstance instance, final double amount) {
		AttributeModifier existing = instance.getModifier(MODIFIER_ID);
		return existing == null || existing.amount() != amount;
	}

	private static AttributeModifier modifier(final double amount) {
		return new AttributeModifier(MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static float fraction(final float value, final float max) {
		return max > 0.0F ? value / max : 0.0F;
	}
}
