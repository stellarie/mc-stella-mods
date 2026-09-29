package com.stella.nether;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

/**
 * The two enchantments a Blaze Core sets at an anvil. Overclock, on a weapon: each hit on the same enemy
 * strikes harder than the last, and a hit on another starts over. Magma Armor, on armor: every hit taken
 * hardens it, each hit taken through what it has built; it cools once its wearer goes untouched.
 */
public final class BlazeEnchantments {
	public static final ResourceKey<Enchantment> OVERCLOCK = ResourceKey.create(Registries.ENCHANTMENT, StellaNether.id("overclock"));
	public static final ResourceKey<Enchantment> MAGMA_ARMOR = ResourceKey.create(Registries.ENCHANTMENT, StellaNether.id("magma_armor"));

	/** Each earlier hit in a row on the same enemy adds this much to the next. */
	public static final double OVERCLOCK_STEP = 0.10;
	public static final int OVERCLOCK_MAX_STACKS = 10;
	/** Levels a Blaze Core costs at the anvil, besides the core. */
	public static final int ANVIL_LEVELS = 10;

	/** The share of each hit a Magma Armor wearer is spared: this much more per hit taken, up to the cap. */
	public static final double MAGMA_PER_HIT = 0.01;
	public static final double MAGMA_CAP = 0.30;
	/** Untouched this long, the armor cools this much a second. */
	public static final int MAGMA_COOL_AFTER_TICKS = 100;
	public static final double MAGMA_COOL_PER_SECOND = 0.02;

	public static final Holder<MobEffect> OVERCLOCK_EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
		StellaNether.id("overclock"), new MobEffect(MobEffectCategory.BENEFICIAL, 0x4FC3F7) {
		});
	public static final Holder<MobEffect> MAGMA_ARMOR_EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
		StellaNether.id("magma_armor"), new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF6A00) {
		});

	private record Chain(UUID target, int stacks) {
	}

	private record Magma(double reduction, int lastHit) {
	}

	private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Magma> MAGMA = new ConcurrentHashMap<>();

	private BlazeEnchantments() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != victim ? living : null;
			if (attacker != null && overclocked(level, attacker, source)) {
				Chain chain = CHAINS.get(attacker.getUUID());
				int stacks = chain != null && chain.target().equals(victim.getUUID()) ? Math.min(chain.stacks() + 1, OVERCLOCK_MAX_STACKS) : 1;
				CHAINS.put(attacker.getUUID(), new Chain(victim.getUUID(), stacks));
				attacker.addEffect(new MobEffectInstance(OVERCLOCK_EFFECT, MobEffectInstance.INFINITE_DURATION, stacks - 1, false, false, true));
			}
			if (magmaWorn(level, victim)) {
				Magma magma = MAGMA.get(victim.getUUID());
				double reduction = Math.min(MAGMA_CAP, (magma == null ? 0.0 : magma.reduction()) + MAGMA_PER_HIT);
				MAGMA.put(victim.getUUID(), new Magma(reduction, level.getServer().getTickCount()));
				show(victim, reduction);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(BlazeEnchantments::cool);
	}

	/** The damage a hit deals and takes, through Overclock on the striker's weapon and Magma Armor on the one struck. */
	public static float scale(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return amount;
		}
		float scaled = amount;
		if (source.getEntity() instanceof LivingEntity attacker && attacker != victim && overclocked(level, attacker, source)) {
			Chain chain = CHAINS.get(attacker.getUUID());
			int stacks = chain != null && chain.target().equals(victim.getUUID()) ? chain.stacks() : 0;
			scaled *= (float) (1.0 + OVERCLOCK_STEP * stacks);
		}
		Magma magma = MAGMA.get(victim.getUUID());
		if (magma != null && magmaWorn(level, victim)) {
			scaled *= (float) (1.0 - magma.reduction());
		}
		return scaled;
	}

	/** What Magma Armor spares its wearer of the next hit, now. */
	public static double magmaReduction(final LivingEntity entity) {
		Magma magma = MAGMA.get(entity.getUUID());
		return magma == null ? 0.0 : magma.reduction();
	}

	public static int overclockStacks(final LivingEntity attacker) {
		Chain chain = CHAINS.get(attacker.getUUID());
		return chain == null ? 0 : chain.stacks();
	}

	private static void cool(final MinecraftServer server) {
		int now = server.getTickCount();
		for (Map.Entry<UUID, Magma> entry : MAGMA.entrySet()) {
			Magma magma = entry.getValue();
			if (now - magma.lastHit() <= MAGMA_COOL_AFTER_TICKS) {
				continue;
			}
			double reduction = magma.reduction() - MAGMA_COOL_PER_SECOND / 20.0;
			LivingEntity wearer = find(server, entry.getKey());
			if (reduction <= 0.0) {
				MAGMA.remove(entry.getKey(), magma);
				if (wearer != null) {
					wearer.removeEffect(MAGMA_ARMOR_EFFECT);
				}
			} else {
				MAGMA.put(entry.getKey(), new Magma(reduction, magma.lastHit()));
				if (wearer != null && now % 20 == 0) {
					show(wearer, reduction);
				}
			}
		}
	}

	private static @Nullable LivingEntity find(final MinecraftServer server, final UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity living) {
				return living;
			}
		}
		return null;
	}

	/** The buff shows the share spared, a level per percent. */
	private static void show(final LivingEntity wearer, final double reduction) {
		int percent = (int) Math.round(reduction * 100.0);
		if (percent > 0) {
			wearer.addEffect(new MobEffectInstance(MAGMA_ARMOR_EFFECT, MobEffectInstance.INFINITE_DURATION, percent - 1, false, false, true));
		}
	}

	private static boolean overclocked(final ServerLevel level, final LivingEntity attacker, final DamageSource source) {
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			weapon = attacker.getMainHandItem();
		}
		return level(level, OVERCLOCK, weapon) > 0;
	}

	private static boolean magmaWorn(final ServerLevel level, final LivingEntity wearer) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor() && level(level, MAGMA_ARMOR, wearer.getItemBySlot(slot)) > 0) {
				return true;
			}
		}
		return false;
	}

	private static int level(final ServerLevel level, final ResourceKey<Enchantment> key, final ItemStack stack) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
			.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
	}

	/** What a Blaze Core makes of an item at an anvil: Overclock on a weapon, Magma Armor on armor, else nothing. */
	public static ItemStack imbue(final ServerLevel level, final ItemStack item) {
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (ResourceKey<Enchantment> key : new ResourceKey[] {OVERCLOCK, MAGMA_ARMOR}) {
			Holder<Enchantment> holder = enchantments.getOrThrow(key);
			if (holder.value().canEnchant(item) && EnchantmentHelper.getItemEnchantmentLevel(holder, item) == 0) {
				ItemStack result = item.copy();
				result.enchant(holder, 1);
				return result;
			}
		}
		return ItemStack.EMPTY;
	}
}
