package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * The withering sword and the withering bow: netherite grade arms that wither what they hit and hit harder as they are
 * refined. Castle wither skeletons and skeleton archers may drop them.
 */
public final class WitheringArms {
	public static final String SWORD_ID = "withering_sword";
	public static final String BOW_ID = "withering_bow";
	/** Wither I for five seconds on every hit. */
	public static final int WITHER_TICKS = 100;
	public static final int WITHER_AMPLIFIER = 0;
	/** The equips mod keeps the refine level in custom data under this key. This mod copies the key and imports nothing. */
	public static final String REFINE_KEY = "stella_refine";
	public static final double REFINE_BONUS = 0.05;
	public static final int REFINE_STEP = 3;
	public static final double DROP_CHANCE = 0.01;

	/** Records keep this many entries; the oldest go first. */
	private static final int KEPT = 256;

	/** One blow an arm raised: who took it, the multiple applied, and the damage before and after. */
	public record Scaled(UUID victim, double factor, float before, float after) {
	}

	private static final List<Scaled> SCALED = new ArrayList<>();
	private static @Nullable RandomSource rollSource;

	private WitheringArms() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!blocked && victim.isAlive() && is(weaponOf(source))) {
				applyWither(victim, source.getEntity());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && EndMobs.isCastleMob(entity)) {
				Item drop = entity instanceof WitherSkeleton ? sword() : EndMobs.isArcher(entity) ? bow() : null;
				if (drop != null && roll(rollSource != null ? rollSource : entity.getRandom())) {
					entity.spawnAtLocation(level, new ItemStack(drop));
					BehaviorLog.info("[END] witheringArmDrop mob={} item={}", entity.getType().getDescriptionId(), drop);
				}
			}
		});
	}

	public static Item sword() {
		return BuiltInRegistries.ITEM.getValue(StellaEnd.id(SWORD_ID));
	}

	public static Item bow() {
		return BuiltInRegistries.ITEM.getValue(StellaEnd.id(BOW_ID));
	}

	public static boolean is(final ItemStack stack) {
		return stack.is(sword()) || stack.is(bow());
	}

	public static int refineLevel(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(REFINE_KEY, 0);
	}

	/** The damage multiple of an arm: 1 for any other item, and 5 per cent more for each full three refine levels. */
	public static double bonus(final ItemStack stack) {
		return is(stack) ? 1.0 + REFINE_BONUS * (refineLevel(stack) / REFINE_STEP) : 1.0;
	}

	/**
	 * The weapon of a melee blow or an arrow hit, or empty for any other damage. Thorns, magic and wither name the
	 * attacker too, so the damage types are listed.
	 */
	public static ItemStack weaponOf(final DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return ItemStack.EMPTY;
		}
		Entity direct = source.getDirectEntity();
		boolean melee = direct == attacker && (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
		boolean shot = direct instanceof AbstractArrow && (source.is(DamageTypes.ARROW) || source.is(DamageTypes.MOB_PROJECTILE));
		if (!melee && !shot) {
			return ItemStack.EMPTY;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon == null ? ItemStack.EMPTY : weapon;
	}

	/** The blow after the refine bonus of the arm that struck it. Any other blow passes as it is. */
	public static float scale(final LivingEntity victim, final DamageSource source, final float amount) {
		ItemStack weapon = weaponOf(source);
		if (!is(weapon)) {
			return amount;
		}
		double factor = bonus(weapon);
		float scaled = amount * (float) factor;
		SCALED.add(new Scaled(victim.getUUID(), factor, amount, scaled));
		if (SCALED.size() > KEPT) {
			SCALED.remove(0);
		}
		return scaled;
	}

	/** Every blow an arm raised since the last reset, in order. */
	public static List<Scaled> scaled() {
		return List.copyOf(SCALED);
	}

	public static void reset() {
		SCALED.clear();
		rollSource = null;
	}

	public static void applyWither(final LivingEntity victim, final @Nullable Entity cause) {
		if (victim.isAlive()) {
			victim.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), cause);
		}
	}

	public static boolean roll(final RandomSource random) {
		return random.nextDouble() < DROP_CHANCE;
	}

	/** Makes every drop roll read this source until it is cleared with null. A test seeds the roll with it. */
	public static void rollWith(final @Nullable RandomSource random) {
		rollSource = random;
	}
}
