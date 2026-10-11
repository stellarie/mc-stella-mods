package com.stella.tower;

import com.stella.mobs.StellaMobs;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

/** What every ghost of the tower shares: its pace, its wreath of soul fire, who it will not target, and how it picks a quarry. */
public final class GhostKit {
	/** A ghost's speed against that of its kind: three quarters. */
	public static final double PACE = 0.75;
	/** How far a ghost sees a quarry, in blocks. */
	public static final double FOLLOW_RANGE = 24.0;
	/** The ticks between two puffs of a ghost's wreath, as on a tower guardian. */
	public static final int EMBER_TICKS = 3;

	private static final Set<Class<?>> RAID_GOALS = Set.of(Raider.class, AbstractIllager.class, PatrollingMonster.class);

	private GhostKit() {
	}

	/** A kind's attributes with its walking and flying speed cut to {@link #PACE}, and a ghost's sight. */
	public static AttributeSupplier.Builder paced(final java.util.function.Supplier<AttributeSupplier.Builder> kinds) {
		// A builder is spent once it has built, so the kind is asked for twice.
		AttributeSupplier base = kinds.get().build();
		AttributeSupplier.Builder kind = kinds.get();
		for (Holder<Attribute> speed : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
			if (base.hasAttribute(speed)) {
				kind.add(speed, base.getBaseValue(speed) * PACE);
			}
		}
		return kind.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
	}

	/** The marks that keep the mobs mod's kits, rolls and goals off a ghost from the moment it exists. */
	public static void settle(final Mob ghost) {
		ghost.addTag(StellaMobs.NO_BEHAVIOR_TAG);
		ghost.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		ghost.setPersistenceRequired();
	}

	/** Whether a ghost may never take this for its target: a player, a fellow ghost, or the ghost merchant. */
	public static boolean refuses(final @Nullable LivingEntity target) {
		return target instanceof Player || target instanceof Ghostly || target instanceof GhostMerchant;
	}

	/** The wreath of flame and soul fire that rises off a ghost, as it does off a tower guardian. */
	public static void wreathe(final ServerLevel level, final Mob ghost) {
		if (ghost.tickCount % EMBER_TICKS != 0) {
			return;
		}
		double w = ghost.getBbWidth() * 0.45;
		double h = ghost.getBbHeight();
		level.sendParticles(ParticleTypes.FLAME, ghost.getX(), ghost.getY() + h * 0.5, ghost.getZ(), 2, w, h * 0.35, w, 0.01);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ghost.getX(), ghost.getY() + h * 0.5, ghost.getZ(), 2, w, h * 0.35, w, 0.01);
	}

	/** Drops every vanilla goal that picks a player or a villager, runs from one, or follows a raid. */
	public static void clearVanilla(final GoalSelector goals, final GoalSelector targets) {
		targets.removeAllGoals(goal -> true);
		goals.removeAllGoals(GhostKit::vanillaNoise);
	}

	private static boolean vanillaNoise(final Goal goal) {
		Class<?> enclosing = goal.getClass().getEnclosingClass();
		return goal instanceof AvoidEntityGoal<?> || goal instanceof RandomStrollGoal
			|| enclosing != null && RAID_GOALS.contains(enclosing) || goal.getClass().getSimpleName().contains("Raid");
	}

	/** Gives a ghost its quarry: whatever hurts it, then the nearest hostile mob of the tower within sight. */
	public static void hunt(final PathfinderMob ghost, final GoalSelector targets) {
		targets.addGoal(1, new HurtByTargetGoal(ghost));
		targets.addGoal(2, new NearestAttackableTargetGoal<>(ghost, Mob.class, 5, true, false,
			(target, level) -> Hostiles.isHostile(target) && target.distanceTo(ghost) <= FOLLOW_RANGE));
	}
}
