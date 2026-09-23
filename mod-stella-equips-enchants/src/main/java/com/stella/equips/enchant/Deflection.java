package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Deflection negates a hit on a level-scaled roll. No data effect can show a
 * negated hit, so the roll, the cancellation and the flash share one branch.
 */
public final class Deflection {
	private static final Identifier ID =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "armor/deflection");
	private static final float CHANCE_PER_LEVEL = 0.05F;

	private Deflection() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Deflection::allowDamage);
	}

	private static boolean allowDamage(
		final LivingEntity wearer, final DamageSource source, final float amount
	) {
		if (amount <= 0.0F || !(wearer.level() instanceof ServerLevel level)) {
			return true;
		}

		Optional<Holder.Reference<Enchantment>> found = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(ID);
		if (found.isEmpty()) {
			return true;
		}

		int rank = EnchantmentHelper.getEnchantmentLevel(found.get(), wearer);
		if (rank <= 0 || wearer.getRandom().nextFloat() >= CHANCE_PER_LEVEL * rank) {
			return true;
		}

		level.sendParticles(
			ParticleTypes.ENCHANTED_HIT,
			wearer.getX(), wearer.getY() + wearer.getBbHeight() * 0.5, wearer.getZ(),
			8, 0.4, 0.4, 0.4, 0.05
		);
		level.playSound(
			null, wearer.getX(), wearer.getY(), wearer.getZ(),
			SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.2F
		);
		return false;
	}
}
