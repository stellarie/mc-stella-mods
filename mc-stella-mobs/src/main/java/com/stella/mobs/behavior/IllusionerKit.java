package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * M9: every Illusioner freezes, burns and blinds the players around it, and splits off decoys. Vanilla's
 * ignite clears frost, so the aura alternates: a frost pulse, then a fire pulse, each with Blindness. A decoy
 * is a real one-health Illusioner the server spawns and tracks, because the vanilla mirror images exist
 * only on the client; killing one slows its killer, and it vanishes with its caster or after its lifetime.
 */
public final class IllusionerKit {
	public static final double AURA_RADIUS = 6.0;
	public static final int AURA_INTERVAL_TICKS = 40;
	public static final int BURN_TICKS = 60;
	public static final int BLINDNESS_TICKS = 60;
	public static final int DECOY_COUNT = 3;
	public static final int DECOY_LIFETIME_TICKS = 400;
	public static final int DECOY_REARM_TICKS = 300;
	public static final double DECOY_SPREAD = 3.0;
	private static final double DECOY_STROLL_SPEED = 0.6;
	public static final int KILLER_SLOW_TICKS = 100;
	public static final int KILLER_SLOW_AMPLIFIER = 1;
	public static final String DECOY_TAG = "mod-stella-mobs.decoy";
	public static final String FROST = "frost";
	public static final String FIRE = "fire";

	/** Test seam: what each Illusioner did, keyed by the Illusioner. */
	public interface Recorder {
		default void aura(final Mob illusioner, final Player player, final String element) {
		}

		default void decoys(final Mob caster, final List<UUID> decoys) {
		}

		default void slowed(final LivingEntity killer, final UUID decoy) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	// The live decoys and their casters: a watch list, so cleanup never sweeps the level.
	private static final Map<UUID, Decoy> DECOYS = new HashMap<>();

	private record Decoy(Mob decoy, Mob caster, int[] age) {
	}

	private IllusionerKit() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static boolean isDecoy(final Mob mob) {
		return mob.entityTags().contains(DECOY_TAG);
	}

	/** The decoys this caster has alive right now. */
	public static int liveDecoys(final Mob caster) {
		return (int) DECOYS.values().stream().filter(entry -> entry.caster() == caster && entry.decoy().isAlive()).count();
	}

	/** One aura pulse on every player within the radius, in survival or adventure: frost or fire, and Blindness. */
	static void pulse(final Mob illusioner, final boolean frost) {
		double radiusSqr = AURA_RADIUS * AURA_RADIUS;
		for (Player player : illusioner.level().players()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.distanceToSqr(illusioner) > radiusSqr) {
				continue;
			}

			if (frost) {
				// Held past the freeze threshold until the next pulse, since frost thaws two ticks per tick.
				player.clearFire();
				player.setTicksFrozen(Math.max(player.getTicksFrozen(), player.getTicksRequiredToFreeze() + AURA_INTERVAL_TICKS * 2));
			} else {
				player.igniteForTicks(BURN_TICKS);
			}

			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0), illusioner);
			recorder.aura(illusioner, player, frost ? FROST : FIRE);
		}
	}

	/** Splits the caster into DECOY_COUNT decoys around it; records only the decoys actually added. */
	static void split(final Illusioner caster, final ServerLevel level) {
		List<UUID> added = new ArrayList<>();
		for (int i = 0; i < DECOY_COUNT; i++) {
			Illusioner decoy = EntityTypes.ILLUSIONER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (decoy == null) {
				continue;
			}

			double angle = 2.0 * Math.PI * i / DECOY_COUNT;
			decoy.snapTo(caster.getX() + Math.cos(angle) * DECOY_SPREAD, caster.getY(), caster.getZ() + Math.sin(angle) * DECOY_SPREAD, caster.getYRot(), 0.0F);
			decoy.addTag(DECOY_TAG);
			decoy.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
			decoy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
			decoy.setHealth(1.0F);
			decoy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			decoy.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			decoy.skipDropExperience();
			// Tracked before it is added, so the load check that clears orphaned decoys lets it in.
			DECOYS.put(decoy.getUUID(), new Decoy(decoy, caster, new int[1]));
			if (!level.addFreshEntity(decoy)) {
				DECOYS.remove(decoy.getUUID());
				continue;
			}

			// A decoy only wanders: every attack, spell and target goal the load gave it is taken away.
			decoy.removeFreeWill();
			decoy.getGoalSelector().addGoal(0, new FloatGoal(decoy));
			decoy.getGoalSelector().addGoal(1, new WaterAvoidingRandomStrollGoal(decoy, DECOY_STROLL_SPEED));
			added.add(decoy.getUUID());
		}

		recorder.decoys(caster, added);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=illusionerKit DECOYS caster={} count={}", caster.getUUID(), added.size());
	}

	/** Drops every decoy whose caster is gone or whose lifetime is spent. */
	public static void tick(final ServerLevel level) {
		Iterator<Decoy> entries = DECOYS.values().iterator();
		while (entries.hasNext()) {
			Decoy entry = entries.next();
			if (entry.decoy().level() != level) {
				continue;
			}

			boolean casterGone = !entry.caster().isAlive() || entry.caster().isRemoved();
			if (!entry.decoy().isAlive() || entry.decoy().isRemoved()) {
				entries.remove();
			} else if (casterGone || ++entry.age()[0] >= DECOY_LIFETIME_TICKS) {
				entry.decoy().discard();
				entries.remove();
			}
		}
	}

	/** A decoy loaded from disk has no caster any more, so it goes. */
	public static void onLoad(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && isDecoy(mob) && !DECOYS.containsKey(mob.getUUID())) {
			mob.discard();
		}
	}

	/** A decoy's death slows whoever killed it; the real Illusioner's death does nothing here. */
	public static void afterDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !isDecoy(mob) || !(source.getEntity() instanceof LivingEntity killer)) {
			return;
		}

		killer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, KILLER_SLOW_TICKS, KILLER_SLOW_AMPLIFIER), mob);
		recorder.slowed(killer, mob.getUUID());
		StellaMobs.LOGGER.info("[BEHAVIOR] test=illusionerKit SLOWED killer={} decoy={}", killer.getUUID(), mob.getUUID());
	}
}
