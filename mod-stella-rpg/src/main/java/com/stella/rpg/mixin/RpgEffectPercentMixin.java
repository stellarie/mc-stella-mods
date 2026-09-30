package com.stella.rpg.mixin;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Strength and Weakness act as a percent in RpgCombat, so their flat attack modifiers must not register. */
@Mixin(MobEffect.class)
public abstract class RpgEffectPercentMixin {
	private static final Set<Identifier> PERCENT_EFFECTS = Set.of(
		Identifier.withDefaultNamespace("effect.strength"), Identifier.withDefaultNamespace("effect.weakness")
	);

	@Inject(method = "addAttributeModifier", at = @At("HEAD"), cancellable = true)
	private void stellaRpg$skipFlatAttack(
		final Holder<Attribute> attribute, final Identifier id,
		final double amount, final AttributeModifier.Operation operation,
		final CallbackInfoReturnable<MobEffect> callback
	) {
		if (attribute.is(Attributes.ATTACK_DAMAGE) && PERCENT_EFFECTS.contains(id)) {
			callback.setReturnValue((MobEffect) (Object) this);
		}
	}
}
