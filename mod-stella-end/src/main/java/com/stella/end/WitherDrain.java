package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The Wither Drain enchantment. A hit applies wither, and every point of wither damage that target then takes heals
 * the one who struck it, by the same amount. Wither from any other source heals nobody.
 */
public final class WitherDrain {
	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, StellaEnd.id("wither_drain"));
	/** Records keep this many entries; the oldest go first. */
	private static final int KEPT = 256;

	/** One heal: the target whose wither did it, the one healed, and the amount. */
	public record Heal(UUID victim, UUID attacker, float amount) {
	}

	/** Who last struck a target with the enchantment. The wither on that target pays this attacker. */
	private static final Map<UUID, UUID> DRAINED = new HashMap<>();
	private static final List<Heal> HEALS = new ArrayList<>();

	private WitherDrain() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamageTaken, damageTaken, blocked) -> {
			if (source.is(DamageTypes.WITHER)) {
				drain(victim);
				return;
			}
			Entity attacker = source.getEntity();
			if (!blocked && victim.isAlive() && attacker != null && has(WitheringArms.weaponOf(source))) {
				WitheringArms.applyWither(victim, attacker);
				DRAINED.put(victim.getUUID(), attacker.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> DRAINED.remove(entity.getUUID()));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> DRAINED.remove(entity.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	/**
	 * Heals the one who struck this target by the health the wither blow took from it. The blow noted that health as it
	 * began. The rpg mod multiplies every heal by its health scale, so the call divides by that scale first.
	 */
	private static void drain(final LivingEntity victim) {
		UUID attackerId = DRAINED.get(victim.getUUID());
		float taken = ((HurtMemo) victim).stellaEnd$healthBeforeHurt() - victim.getHealth();
		if (attackerId == null || taken <= 0.0F) {
			return;
		}
		if (!victim.hasEffect(MobEffects.WITHER)) {
			DRAINED.remove(victim.getUUID());
			return;
		}
		if (victim.level() instanceof ServerLevel level && level.getEntity(attackerId) instanceof LivingEntity attacker && attacker.isAlive()) {
			attacker.heal(taken / (float) HealthBridge.scale());
			HEALS.add(new Heal(victim.getUUID(), attackerId, taken));
			if (HEALS.size() > KEPT) {
				HEALS.remove(0);
			}
			BehaviorLog.info("[END] witherDrain victim={} attacker={} healed={}", victim.getType().getDescriptionId(), attacker.getType().getDescriptionId(), taken);
		}
	}

	public static boolean has(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream().anyMatch(holder -> holder.is(KEY));
	}

	/** A copy of the item with the enchantment at level one. */
	public static ItemStack enchanted(final ItemStack stack, final RegistryAccess access) {
		Holder<Enchantment> drain = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(KEY);
		ItemStack copy = stack.copy();
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mutable.set(drain, 1);
		EnchantmentHelper.setEnchantments(copy, mutable.toImmutable());
		return copy;
	}

	public static List<Heal> heals() {
		return List.copyOf(HEALS);
	}

	public static void reset() {
		DRAINED.clear();
		HEALS.clear();
	}
}
