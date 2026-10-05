package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.reward.EffectHook;
import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.UniqueEffects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Hawkeye Longbow: every fifth arrow of its holder pierces 2 targets and gives each target it hits Glowing for
 * 100 ticks. The same class carries the 4-piece Hawkeye set bonus: arrows hit 20 percent harder.
 * <p>
 * The pierce level of an arrow has no public setter, and the library adds no mixin. The fifth arrow is therefore
 * replaced by one that is built from the bow with Piercing II on it, which sets the level through vanilla's own path.
 */
public final class HawkeyeEffect implements EffectHook {
	public static final int EVERY = 5;
	public static final int PIERCE = 2;
	public static final int GLOW_TICKS = 100;
	public static final String ARROW_TAG = "mod-stella-ruins.hawkeye_arrow";
	/** The 4-piece bonus: arrow damage, as a share of the damage the arrow dealt. */
	public static final float SET_ARROW_BONUS = 0.20F;

	private static final Map<UUID, Integer> COUNTS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> BONUS_RUNNING = ThreadLocal.withInitial(() -> false);

	/** Forgets one holder's arrow count. */
	public static void reset(final UUID user) {
		COUNTS.remove(user);
	}

	public static int count(final UUID user) {
		return COUNTS.getOrDefault(user, 0);
	}

	@Override
	public void onArrowFired(final LivingEntity user, final AbstractArrow arrow) {
		int shots = COUNTS.merge(user.getUUID(), 1, Integer::sum);
		if (shots % EVERY != 0 || !(user.level() instanceof ServerLevel level) || arrow.entityTags().contains(ARROW_TAG)) {
			return;
		}
		Holder<Enchantment> piercing = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PIERCING);
		ItemStack weapon = user.getMainHandItem().copy();
		weapon.enchant(piercing, PIERCE);
		Arrow sharp = new Arrow(level, user, new ItemStack(Items.ARROW), weapon);
		sharp.snapTo(arrow.getX(), arrow.getY(), arrow.getZ(), arrow.getYRot(), arrow.getXRot());
		sharp.setDeltaMovement(arrow.getDeltaMovement());
		sharp.setCritArrow(arrow.isCritArrow());
		sharp.pickup = arrow.pickup;
		sharp.addTag(ARROW_TAG);
		sharp.addTag(UniqueEffects.FIRED_TAG);
		arrow.discard();
		level.addFreshEntity(sharp);
	}

	@Override
	public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(ARROW_TAG)) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0));
		}
	}

	/** The 4-piece bonus rides on the same Fabric event as every unique hook. */
	static void registerSetBonus() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseTaken, taken, blocked) -> {
			if (blocked || taken <= 0.0F || BONUS_RUNNING.get() || !source.is(DamageTypeTags.IS_PROJECTILE)
				|| !(source.getDirectEntity() instanceof AbstractArrow) || !(source.getEntity() instanceof LivingEntity shooter)
				|| shooter == target || !(target.level() instanceof ServerLevel level)
				|| RuinSets.wornPieces(shooter, WatchtowerItems.SET_ID) < 4) {
				return;
			}
			BONUS_RUNNING.set(true);
			try {
				target.damageCooldownTime = 0;
				target.hurtServer(level, source, baseTaken * SET_ARROW_BONUS);
			} finally {
				BONUS_RUNNING.set(false);
			}
		});
	}
}
