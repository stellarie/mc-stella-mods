package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.jspecify.annotations.Nullable;

/**
 * The small withers of an encounter Wither. A parent under a quarter health summons them once, in a ring around it.
 * A small wither is a Wither with a share of the health and the size, the parent's level and divisor, and the volley at
 * a share of the damage. It takes no spawn roll, fires no sphere, summons nothing, calls no minions, and drops nothing.
 * It leaves with the parent.
 * A small wither whose parent has been missing for a grace period leaves on its own, which covers an unload.
 */
public final class WitherSpawnlings {
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "small_wither_speed");
	private static final Map<WitherBoss, int[]> ORPHANED = new WeakHashMap<>();

	private WitherSpawnlings() {
	}

	/** True for a small wither. A legacy Wither and a parent are not. */
	public static boolean isSmall(final Entity entity) {
		if (!(entity instanceof WitherBoss wither)) {
			return false;
		}
		WitherState state = WitherState.find(wither);
		return state != null && state.small();
	}

	/**
	 * Adds the configured number of small withers around the parent and records them. The parent is marked spent even
	 * when none could be added, so the summon never repeats. Returns the ones it added.
	 */
	public static List<WitherBoss> summon(final ServerLevel level, final WitherBoss parent) {
		List<WitherBoss> added = new ArrayList<>();
		WitherState parentState = WitherState.find(parent);
		if (parentState == null || parentState.small()) {
			return added;
		}
		WitherConfig config = WitherConfigStore.get();
		int count = Math.max(0, config.getInt(WitherConfig.SMALL_COUNT));
		double phase = parent.getRandom().nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < count; i++) {
			WitherBoss small = create(parent, parentState, config);
			if (small == null) {
				continue;
			}
			place(level, parent, small, phase + i * Math.PI * 2.0 / count, config.get(WitherConfig.SMALL_DISTANCE));
			if (level.addFreshEntity(small)) {
				added.add(small);
			}
		}
		List<UUID> record = new ArrayList<>(parentState.spawnlings());
		added.forEach(small -> record.add(small.getUUID()));
		WitherState.set(parent, WitherState.find(parent).withSpawnlings(record));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings SUMMON uuid={} asked={} added={} each_health={}",
			parent.getUUID(), count, added.size(), added.isEmpty() ? 0.0F : added.get(0).getMaxHealth());
		return added;
	}

	private static @Nullable WitherBoss create(final WitherBoss parent, final WitherState parentState, final WitherConfig config) {
		WitherBoss small = EntityTypes.WITHER.create(parent.level(), EntitySpawnReason.EVENT);
		if (small == null) {
			return null;
		}
		// The health follows the parent's attribute, so the divisor the parent carries applies to the small one alike.
		double health = parent.getMaxHealth() * config.get(WitherConfig.SMALL_HEALTH_SHARE);
		small.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		small.getAttribute(Attributes.SCALE).setBaseValue(config.get(WitherConfig.SMALL_SCALE));
		small.setHealth((float) health);
		small.setPersistenceRequired();
		small.skipDropExperience();
		small.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		// All of these must exist before the add: ENTITY_LOAD fires inside addFreshEntity and reads them.
		((AttachmentTarget) small).setAttached(ThreatState.TYPE, new ThreatState(ThreatState.level(parent), 0, 0, 0));
		WitherState.set(small, WitherState.small(parentState.anchorY(), parentState.healthDivisor(), parent.getUUID()));
		// A Wither is immune to every effect, so Speed II cannot be applied. The same bonus goes on its flight and its walk.
		speed(small, Attributes.FLYING_SPEED, config.get(WitherConfig.SMALL_SPEED_BONUS));
		speed(small, Attributes.MOVEMENT_SPEED, config.get(WitherConfig.SMALL_SPEED_BONUS));
		return small;
	}

	private static void speed(final WitherBoss small, final Holder<Attribute> attribute, final double bonus) {
		AttributeInstance instance = small.getAttribute(attribute);
		if (instance != null) {
			instance.addPermanentModifier(new AttributeModifier(SPEED_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** The ring spot at the configured reach, or a nearer one when a block is in the way, or the parent's own. */
	private static void place(final ServerLevel level, final WitherBoss parent, final WitherBoss small, final double angle, final double reach) {
		double[] reaches = {reach, reach / 2.0, 0.0};
		for (double step : reaches) {
			small.snapTo(parent.getX() + Math.cos(angle) * step, parent.getY(), parent.getZ() + Math.sin(angle) * step, parent.getYRot(), 0.0F);
			if (level.noCollision(small)) {
				return;
			}
		}
	}

	/** Takes every small wither in the parent's record out of the world. */
	public static void discardAll(final WitherBoss parent) {
		WitherState state = WitherState.find(parent);
		if (state == null || state.spawnlings().isEmpty() || !(parent.level() instanceof ServerLevel level)) {
			return;
		}
		int removed = 0;
		for (UUID id : state.spawnlings()) {
			Entity small = level.getEntity(id);
			if (small != null) {
				small.discard();
				removed++;
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings DISCARD uuid={} removed={}", parent.getUUID(), removed);
	}

	/** One tick of a small wither: it counts the ticks its parent has been missing, and leaves when the grace is spent. */
	public static void tick(final ServerLevel level, final WitherBoss small) {
		WitherState state = WitherState.find(small);
		if (state == null || state.parent().isEmpty()) {
			return;
		}
		boolean standing = level.getEntity(state.parent().get()) instanceof WitherBoss parent && parent.isAlive() && !parent.isRemoved();
		if (standing) {
			ORPHANED.remove(small);
			return;
		}
		int[] waited = ORPHANED.computeIfAbsent(small, key -> new int[1]);
		if (++waited[0] >= WitherConfigStore.get().getInt(WitherConfig.SMALL_ORPHAN_TICKS)) {
			ORPHANED.remove(small);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnlings ORPHAN uuid={} parent={}", small.getUUID(), state.parent().get());
			small.discard();
		}
	}
}
