package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** A hostile mob a player kills with a Shakedown weapon drops 0 to 2 x level emeralds. */
public final class Shakedown {
	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "weapon/shakedown"));
	public static final int EMERALDS_PER_LEVEL = 2;

	private Shakedown() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Shakedown::onDeath);
	}

	private static void onDeath(final LivingEntity victim, final DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer killer)
			|| !(victim instanceof Enemy)) {
			return;
		}
		ItemStack weapon = source.getWeaponItem() != null ? source.getWeaponItem() : killer.getMainHandItem();
		int count = drop(level(level, weapon), victim.getRandom());
		if (count > 0) {
			victim.spawnAtLocation(level, new ItemStack(Items.EMERALD, count));
		}
	}

	public static int level(final ServerLevel level, final ItemStack weapon) {
		Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(KEY).orElse(null);
		return holder == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, weapon);
	}

	/** Uniform from 0 to 2 x level. */
	public static int drop(final int level, final RandomSource random) {
		return level <= 0 ? 0 : random.nextInt(EMERALDS_PER_LEVEL * level + 1);
	}
}
