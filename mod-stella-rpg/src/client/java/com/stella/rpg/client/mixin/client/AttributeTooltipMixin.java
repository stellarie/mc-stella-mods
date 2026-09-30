package com.stella.rpg.client.mixin.client;

import com.stella.rpg.RpgScale;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shows attack damage and max health in health units. The item data stays in vanilla units. */
@Mixin(targets = "net.minecraft.world.item.component.ItemAttributeModifiers$Display$Default")
public abstract class AttributeTooltipMixin {
	@ModifyVariable(method = "apply", at = @At("STORE"), ordinal = 1)
	private double stellaRpg$scaleShown(
		final double shown, final Consumer<Component> consumer, final @Nullable Player player,
		final Holder<Attribute> attribute, final AttributeModifier modifier
	) {
		boolean scaled = attribute.is(Attributes.ATTACK_DAMAGE) || attribute.is(Attributes.MAX_HEALTH);
		return scaled && modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? shown * RpgScale.k() : shown;
	}
}
