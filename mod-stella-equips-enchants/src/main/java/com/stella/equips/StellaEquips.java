package com.stella.equips;

import com.stella.equips.enchant.Soulbound;
import com.stella.equips.mastery.MasteryPerks;
import com.stella.equips.mastery.WeaponMastery;
import com.stella.equips.enchant.Deflection;
import com.stella.equips.enchant.OreMender;
import com.stella.equips.enchant.OreRegeneration;
import com.stella.equips.enchant.UndertowPull;
import com.stella.equips.loot.ChestLoot;
import com.stella.equips.loot.HeadDrops;
import com.stella.equips.loot.RandomEnchantmentsFunction;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerTracker;
import com.stella.equips.readout.ReadoutCommand;
import com.stella.equips.readout.ReadoutService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StellaEquips implements ModInitializer {
	public static final String MOD_ID = "mod-stella-equips-enchants";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Deflection.register();
		com.stella.equips.enchant.Shakedown.register();
		com.stella.equips.bag.EmeraldBag.register();
		com.stella.equips.heart.SlimeHeart.register();
		com.stella.equips.unique.Uniques.bootstrap();
		com.stella.equips.guardian.GuardianGear.register();
		com.stella.equips.bows.CraftedBows.register();
		com.stella.equips.merchant.MerchantWeapons.register();
		com.stella.equips.staff.Staves.register();
		com.stella.equips.anvil.EmeraldPurse.register();
		com.stella.equips.enchant.Auras.register();
		com.stella.equips.enchant.Waveform.register();
		OreRegeneration.register();
		OreMender.register();
		com.stella.equips.enchant.ProspectorStacks.register();
		UndertowPull.register();
		PowerTracker.register();
		ReadoutService.register();
		com.stella.equips.numbers.DamageNumbers.register();
		ReadoutCommand.register();
		RandomEnchantmentsFunction.register();
		ChestLoot.register();
		com.stella.equips.loot.ContainerLoot.register();
		com.stella.equips.loot.EnchantedBooks.register();
		com.stella.equips.imbue.NetherImbue.register();
		HeadDrops.register();
		WeaponMastery.register();
		MasteryPerks.register();
		Soulbound.register();
		com.stella.equips.refine.RefineBlocks.register();
		com.stella.equips.refine.RefineStation.register();
		com.stella.equips.refine.RefineEffects.register();
		com.stella.equips.shield.ShieldDurability.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PowerConfigStore.load());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				PowerConfigStore.load();
			}
		});
		LOGGER.info("{} initialized", MOD_ID);
	}
}
