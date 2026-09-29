package com.stella.nether;

import com.stella.spells.MobSpells;
import java.util.EnumSet;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * With the spells mod, some raid mobs cast, in either mode: each strikes its target with a fire spell or Time
 * Lock and buffs itself, on the spells mod's own cooldowns. A wither skeleton brands with embers and sings a
 * battle hymn; a piglin brute rings itself in flame and turns to stone; a ghast locks time and mends itself.
 */
public final class RaidCasters {
	/** What a caster strikes with, what it aids itself with, and how near its target must be. */
	public record Kit(String strike, String aid, double range) {
	}

	public static final Map<EntityType<?>, Kit> KITS = Map.of(
		EntityTypes.WITHER_SKELETON, new Kit("ember_brand", "battle_hymn", MobSpells.CAST_RANGE),
		EntityTypes.PIGLIN_BRUTE, new Kit("flame_ring", "stoneguard", MobSpells.CAST_RANGE),
		EntityTypes.GHAST, new Kit("time_lock", "mending_word", 32.0)
	);
	/** A caster thinks this often, as the spells mod's own casters do. */
	public static final int THINK_TICKS = MobSpells.THINK_INTERVAL_TICKS;

	/** Test seam: each cast a raid caster makes. */
	public interface Recorder {
		void cast(Mob caster, String spell, LivingEntity at);
	}

	private static volatile Recorder recorder = (caster, spell, at) -> {
	};

	private RaidCasters() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && level.dimension().equals(RaidTravel.RAID)) {
				install(mob);
			}
		});
	}

	static Recorder setRecorder(final @Nullable Recorder next) {
		Recorder previous = recorder;
		recorder = next == null ? (caster, spell, at) -> {
		} : next;
		return previous;
	}

	public static @Nullable Kit kitOf(final Mob mob) {
		return KITS.get(mob.getType());
	}

	/** Gives a raid mob its casting, once; the Sovereign and mobs of other kinds cast nothing. */
	static void install(final Mob mob) {
		Kit kit = kitOf(mob);
		if (kit == null || BlazeSovereign.isSovereign(mob) || hasCaster(mob)) {
			return;
		}
		// Flagless, so it runs beside the mob's own attack and movement rather than in place of them.
		mob.getGoalSelector().addGoal(1, new Caster(mob, kit));
	}

	public static boolean hasCaster(final Mob mob) {
		return mob.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof Caster);
	}

	static final class Caster extends Goal {
		private final Mob mob;
		private final Kit kit;
		/** The goal's own clock: the level's game time barely moves in a test, and a cooldown must still pass. */
		private int age;
		private int strikeReady;
		private int aidReady;

		Caster(final Mob mob, final Kit kit) {
			this.mob = mob;
			this.kit = kit;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.mob.getTarget();
			return target != null && target.isAlive() && this.mob.distanceToSqr(target) <= this.kit.range() * this.kit.range();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.age++;
			LivingEntity target = this.mob.getTarget();
			if (this.age % THINK_TICKS != 0 || target == null || !target.isAlive()) {
				return;
			}
			if (this.age >= this.strikeReady && this.mob.hasLineOfSight(target) && MobSpells.castAt(this.mob, this.kit.strike(), target)) {
				this.strikeReady = this.age + MobSpells.cooldownOf(this.kit.strike());
				recorder.cast(this.mob, this.kit.strike(), target);
			}
			if (this.age >= this.aidReady && MobSpells.support(this.mob, this.kit.aid())) {
				this.aidReady = this.age + MobSpells.cooldownOf(this.kit.aid());
				recorder.cast(this.mob, this.kit.aid(), this.mob);
			}
		}
	}
}
