package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.mixin.ArrowPotionInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * M2: the plain skeleton's volley arrows and the cloud each one leaves where it lands. A tipped arrow's
 * cloud carries the arrow's own potion, the way a lingering potion would; a plain arrow's cloud slows.
 */
public final class SkeletonVolley {
	public static final String VOLLEY_TAG = "mod-stella-mobs.volley";
	public static final float CLOUD_RADIUS = 1.5F;
	public static final int CLOUD_TICKS = 60;
	/** A lingering potion's own scale, so a tipped cloud lasts as long as the same potion would linger. */
	public static final float TIPPED_DURATION_SCALE = 0.25F;

	/** Test seam: what each volley spawned, keyed by the skeleton that loosed it. */
	public interface Recorder {
		default void volley(final Mob shooter, final List<UUID> arrows, final List<Vec3> velocities) {
		}

		default void cloud(final Mob shooter, final UUID arrow, final UUID cloud, final Vec3 at, final List<MobEffectInstance> effects) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;

	private SkeletonVolley() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	static Recorder recorder() {
		return recorder;
	}

	/** Called when any arrow hits, before vanilla's own hit; only a volley arrow leaves a cloud, and only once. */
	public static void onHit(final AbstractArrow arrow, final Vec3 at) {
		if (!(arrow.level() instanceof ServerLevel level) || !arrow.entityTags().contains(VOLLEY_TAG)
			|| !(arrow.getOwner() instanceof Mob shooter)) {
			return;
		}

		arrow.removeTag(VOLLEY_TAG);
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setOwner(shooter);
		cloud.setRadius(CLOUD_RADIUS);
		cloud.setDuration(CLOUD_TICKS);
		cloud.setWaitTime(0);
		cloud.setRadiusPerTick(0.0F);
		List<MobEffectInstance> effects = new ArrayList<>();
		PotionContents contents = arrow instanceof Arrow tipped ? ((ArrowPotionInvoker) tipped).stella$potionContents() : PotionContents.EMPTY;
		if (contents.hasEffects()) {
			cloud.setPotionContents(contents);
			cloud.setPotionDurationScale(TIPPED_DURATION_SCALE);
			contents.getAllEffects().forEach(effects::add);
		} else {
			MobEffectInstance slow = new MobEffectInstance(MobEffects.SLOWNESS, CLOUD_TICKS, 0);
			cloud.addEffect(slow);
			effects.add(slow);
		}

		if (!level.addFreshEntity(cloud)) {
			return;
		}

		recorder.cloud(shooter, arrow.getUUID(), cloud.getUUID(), cloud.position(), effects);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=skeletonVolley CLOUD at={} effect={}",
			cloud.position(), effects.stream().map(effect -> effect.getEffect().getRegisteredName()).toList()
		);
	}
}
