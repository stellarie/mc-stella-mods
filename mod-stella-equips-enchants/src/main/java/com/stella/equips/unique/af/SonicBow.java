package com.stella.equips.unique.af;

import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueTargets;
import com.stella.equips.refine.Refine;
import com.stella.equips.mixin.AbstractArrowAccessor;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Warden's bow. */
public final class SonicBow {
	public static final String ID = "sonic_bow";

	public record Tuning(int every, int everyPlus7, int everyPlus15, double range, double rangePlus10,
		float damageShare, float damageSharePlus12) {
	}

	public static final Tuning TUNING = new Tuning(5, 4, 3, 20.0, 30.0, 1.0F, 1.5F);
	private static final Map<UUID, Integer> SHOTS = new HashMap<>();
	private static final Map<AbstractArrow, Long> CRITICAL_SAMPLES = new WeakHashMap<>();
	private static final UniqueEffect EFFECT = new UniqueEffect() {
		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			if (!Af.fullDraw(power)) {
				return;
			}
			int shot = SHOTS.merge(c.holder().getUUID(), 1, Integer::sum);
			if (shot % every(c.refine()) == 0) {
				beam(c, arrow);
			}
		}
	};

	private SonicBow() {
	}

	public static int every(final int refine) {
		return refine >= Uniques.PLUS_15 ? TUNING.everyPlus15()
			: refine >= Uniques.PLUS_7 ? TUNING.everyPlus7() : TUNING.every();
	}

	public static double range(final int refine) {
		return refine >= Uniques.PLUS_10 ? TUNING.rangePlus10() : TUNING.range();
	}

	public static float share(final int refine) {
		return refine >= Uniques.PLUS_12 ? TUNING.damageSharePlus12() : TUNING.damageShare();
	}

	public static int criticalBonus(final AbstractArrow arrow, final RandomSource random, final int bound) {
		Long sample = CRITICAL_SAMPLES.get(arrow);
		return sample == null ? random.nextInt(bound) : (int) Long.remainderUnsigned(sample, bound);
	}

	private static float arrowDamage(final UniqueContext c, final AbstractArrow arrow, final LivingEntity target) {
		ServerLevel level = c.holder().level();
		// The shot hook runs before refine's load hook.
		float base = (float) (((AbstractArrowAccessor) arrow).stella$baseDamage()
			+ c.refine() * Refine.DAMAGE_PER_LEVEL);
		ItemStack weapon = arrow.getWeaponItem();
		if (weapon != null) {
			base = EnchantmentHelper.modifyDamage(level, weapon, target, level.damageSources().arrow(arrow, c.holder()), base);
		}
		int damage = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * base, 0.0, (double) Integer.MAX_VALUE));
		if (arrow.isCritArrow()) {
			damage = (int) Math.min((long) damage + criticalBonus(arrow, arrow.getRandom(), damage / 2 + 2), Integer.MAX_VALUE);
		}
		return damage;
	}

	private static void beam(final UniqueContext c, final AbstractArrow arrow) {
		ServerLevel level = c.holder().level();
		Vec3 start = c.holder().getEyePosition();
		Vec3 direction = c.holder().getLookAngle().normalize();
		Vec3 end = start.add(direction.scale(range(c.refine())));
		if (arrow.isCritArrow()) {
			CRITICAL_SAMPLES.put(arrow, arrow.getRandom().nextLong());
		}
		AABB box = new AABB(start, end).inflate(1.0);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, box, entity -> UniqueTargets.isFoe(c.holder(), entity))) {
			Vec3 body = foe.getBoundingBox().getCenter();
			double distance = Math.clamp(body.subtract(start).dot(direction), 0.0, range(c.refine()));
			if (foe.getBoundingBox().distanceToSqr(start.add(direction.scale(distance))) <= 0.25) {
				UniqueDamage.deal(level, foe, c.holder(), arrowDamage(c, arrow, foe) * share(c.refine()), true);
			}
		}
		for (int step = 0; step <= (int) range(c.refine()); step++) {
			Vec3 point = start.add(direction.scale(step));
			level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	public static void register() {
		Uniques.register(new UniqueDef(ID, "Sonic Bow", UniqueJob.ARCHER, UniqueKind.BOW,
			List.of(Identifier.parse("minecraft:warden")),
			"Every " + Af.ordinal(TUNING.every()) + " full-draw shot also fires a sonic beam: " + Af.percent(TUNING.damageShare())
				+ " of the arrow damage to every foe in a " + (int) TUNING.range() + "-block line, ignores armor",
			"Every " + Af.ordinal(TUNING.everyPlus7()) + " shot",
			"Range " + (int) TUNING.rangePlus10(),
			Af.percent(TUNING.damageSharePlus12()),
			"Every " + Af.ordinal(TUNING.everyPlus15()) + " shot", null, EFFECT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOTS.clear();
			CRITICAL_SAMPLES.clear();
		});
	}
}
