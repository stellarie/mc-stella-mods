package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/** The two themed mobs of the camp and the tables they own. */
public final class CampMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/lost_miners_camp");
	public static final ResourceKey<LootTable> MINER_DROPS = table("entities/restless_miner");
	public static final ResourceKey<LootTable> RAT_DROPS = table("entities/tunnel_rat");
	/** The Mining Fatigue I that one miner's hit leaves, in ticks. */
	public static final int FATIGUE_TICKS = 100;

	public static RuinMobKind RESTLESS_MINER;
	public static RuinMobKind TUNNEL_RAT;

	private CampMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		RESTLESS_MINER = RuinMobKind.builder("restless_miner")
			.base(EntityTypes.ZOMBIE)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.IRON_PICKAXE))
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.LEATHER_HELMET))
			.ability(new MobAbility() {
				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					fatigue(target);
				}
			})
			.drops(MINER_DROPS)
			.register();
		TUNNEL_RAT = RuinMobKind.builder("tunnel_rat")
			.base(EntityTypes.SILVERFISH)
			.scale(1.2F)
			.drops(RAT_DROPS)
			.register();
	}

	/** A miner's pick tires the one it hits. Returns true when the effect landed. */
	public static boolean fatigue(final LivingEntity target) {
		return target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, FATIGUE_TICKS, 0, false, true));
	}
}
