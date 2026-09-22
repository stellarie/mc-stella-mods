package com.stella.mobs;

import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.Loadout;
import com.stella.mobs.gear.GearApplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StellaMobs implements ModInitializer {
	public static final String MOD_ID = "mod-stella-mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register(StellaMobs::onEntityLoad);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> GearConfigStore.load());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				GearConfigStore.load();
			}
		});
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (entity instanceof EntityLoadData loadData && loadData.isLoadedFromDisk()) {
			return;
		}

		if (!(entity instanceof Mob mob)) {
			return;
		}

		Identifier entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		Loadout loadout = GearConfigStore.get().loadoutFor(entityType);
		if (loadout == null) {
			return;
		}

		GearApplier.apply(mob, loadout, level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
	}
}
