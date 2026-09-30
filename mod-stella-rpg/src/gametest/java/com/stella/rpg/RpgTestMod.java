package com.stella.rpg;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class RpgTestMod implements ModInitializer {
	/** A stand-in for a food effect, so the stat lookup by id is tested with the food mod absent. */
	private static final class StandIn extends MobEffect {
		private StandIn() {
			super(MobEffectCategory.BENEFICIAL, 0x55AA55);
		}
	}

	@Override
	public void onInitialize() {
		for (String name : new String[] {"surefooted", "seasoned_str"}) {
			Registry.register(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("mod-stella-food", name), new StandIn());
		}
	}
}
