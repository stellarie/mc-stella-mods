package com.stella.rpg.mixin;

import com.stella.rpg.RpgScale;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lifts the vanilla maximum of health, attack damage, and absorption. It loads on both sides. */
@Mixin(RangedAttribute.class)
public abstract class RpgAttributeCapMixin {
	private static final Set<String> UNCAPPED = Set.of(
		"attribute.name.max_health", "attribute.name.attack_damage", "attribute.name.max_absorption"
	);

	@Shadow
	@Mutable
	private double maxValue;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void stellaRpg$lift(
		final String descriptionId, final double defaultValue, final double minValue, final double maxValue,
		final CallbackInfo callback
	) {
		if (UNCAPPED.contains(descriptionId)) {
			this.maxValue = RpgScale.ATTRIBUTE_CAP;
		}
	}
}
