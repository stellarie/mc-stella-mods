package com.stella.rpg;

import com.stella.rpg.api.HealthScaleShare;
import com.stella.rpg.api.OutgoingDamageShare;
import com.stella.rpg.net.RpgNet;
import com.stella.rpg.bag.Bag;
import com.stella.rpg.roll.WeaponRolls;
import com.stella.rpg.tomb.TombstoneBlock;
import com.stella.rpg.tomb.TombstoneBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StellaRpg implements ModInitializer {
	public static final String MOD_ID = "mod-stella-rpg";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StatSheet.EMPTY.allocated();
		RpgLevelUp.LAST_LEVEL.identifier();
		TombstoneBlock.register();
		TombstoneBlockEntity.register();
		RpgDeath.register();
		SecondWind.register();
		com.stella.rpg.tomb.GraveCompass.register();
		RpgAttributes.register();
		RpgCommand.register();
		DamageLog.register();
		DeathRecap.register();
		MobGrowth.register();
		Bag.register();
		com.stella.rpg.api.BagShare.publish();
		RpgNet.register();
		AutoPickup.register();
		ArmorSets.register();
		WeaponRolls.register();
		RpgScale.register();
		EnchantStats.register();
		LevelBonus.register();
		com.stella.rpg.job.Jobs.register();
		com.stella.rpg.job.JobItems.register();
		EffectStats.register();
		StarterSword.register();
		OutgoingDamageShare.register();
		HealthScaleShare.publish();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> reloadConfig());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> reloadConfig());
		LOGGER.info("{} initialized", MOD_ID);
	}

	private static void reloadConfig() {
		RpgConfigStore.reload(RpgConfigStore.defaultPath());
		HealthScaleShare.publish();
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
