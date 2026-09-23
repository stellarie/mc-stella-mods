package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.WitchPotions;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.Loadout;
import com.stella.mobs.gear.EnchantRoller;
import com.stella.mobs.gear.GearApplier;
import com.stella.mobs.gear.GearRoller;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StellaMobs implements ModInitializer {
	public static final String MOD_ID = "mod-stella-mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ReactiveState.register();
		ServerEntityEvents.ENTITY_LOAD.register(StellaMobs::onEntityLoad);
		ServerLivingEntityEvents.AFTER_DEATH.register(StellaMobs::onEntityDeath);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> GearConfigStore.load());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				GearConfigStore.load();
				EnchantRoller.clearCache();
			}
		});
		LOGGER.info("{} initialized", MOD_ID);
	}

	static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob)) {
			return;
		}

		boolean loadedFromDisk = entity instanceof EntityLoadData loadData && loadData.isLoadedFromDisk();

		if (!loadedFromDisk) {
			Identifier entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
			Loadout loadout = GearConfigStore.get().loadoutFor(entityType);
			ReactiveState state = ReactiveState.get(mob);

			if (loadout != null) {
				if (!state.isGearApplied()) {
					GearApplier.apply(mob, loadout, level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
					state.setGearApplied(true);
				}
			} else if (GearRoller.canRoll(mob) && !state.isGearApplied()) {
				GearRoller.roll(
					mob,
					GearConfigStore.get().rollFor(entityType),
					mob.getRandom(),
					level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				);
				state.setGearApplied(true);
			}
		}

		BehaviorInstaller.install(mob);
	}

	/** W10: a witch drops one of every potion it carries, read from the one potion list. */
	private static void onEntityDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Witch witch) || !(witch.level() instanceof ServerLevel level)) {
			return;
		}

		for (WitchPotions.Carried carried : WitchPotions.CARRIED) {
			witch.spawnAtLocation(level, WitchPotions.stack(carried));
		}
	}
}
