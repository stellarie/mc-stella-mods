package com.stella.tower;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's ghosts: where they come from, what they do for the players, and the price of killing one.
 * An occupied floor keeps a couple of them about, outside its cap.
 */
public final class TowerGhosts {
	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, StellaTower.id("tower_ghost"));
	public static final EntityType<TowerGhost> GHOST = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.<TowerGhost>of(TowerGhost::new, MobCategory.MISC).sized(0.6F, 1.8F).fireImmune().clientTrackingRange(8).build(KEY));
	/** How many ghosts an occupied floor keeps, and the chance each spawning round raises one. */
	public static final int GHOSTS_PER_FLOOR = 2;
	public static final double RISE_CHANCE = 0.05;
	public static final double CAST_RANGE = 16.0;
	public static final int CAST_TICKS = 40;
	public static final float SPELL_BASE = 4.0F;
	public static final double TEND_RANGE = 12.0;
	public static final int HEAL_TICKS = 100;
	public static final float HEAL_AMOUNT = 4.0F;
	public static final int BUFF_TICKS = 200;
	public static final int BUFF_DURATION = 200;
	public static final List<Holder<MobEffect>> BUFFS = List.of(MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.REGENERATION, MobEffects.SPEED);
	/** Killing a ghost blinds its killer this long. */
	public static final int CURSE_TICKS = 600;
	public static final double RUNE_CHANCE = 0.03;
	public static final String PREFIX = "Ghost";
	/** A ghost drifts toward the nearest player within this reach, until this near. */
	public static final double FOLLOW_RANGE = 32.0;
	public static final double KEEP_NEAR = 6.0;
	private static final Map<Integer, Double> PINNED_RISE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Double> PINNED_RUNE = new java.util.concurrent.ConcurrentHashMap<>();

	private TowerGhosts() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(GHOST, TowerGhost.createAttributes());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// Only a blow struck in hand: a shot, a blast or a spell that kills a ghost lays no curse.
			if (!(entity instanceof TowerGhost ghost) || !(entity.level() instanceof ServerLevel level)
				|| !(source.getEntity() instanceof ServerPlayer killer) || source.getDirectEntity() != killer) {
				return;
			}
			// The price of a ghost's death is its killer's sight.
			killer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CURSE_TICKS, 0));
			TowerMobs.State state = TowerMobs.stateOf(ghost);
			double chance = PINNED_RUNE.getOrDefault(state == null ? 0 : state.floor(), RUNE_CHANCE);
			if (level.getGameRules().get(GameRules.MOB_DROPS) && ghost.getRandom().nextDouble() < chance) {
				ghost.spawnAtLocation(level, new ItemStack(TowerItems.GHOST_RUNE));
			}
			StellaTower.LOGGER.debug("[TOWER] a ghost fell to {}", killer.getName().getString());
		});
	}

	/** The test seam: pins, by floor, the chance a spawning round raises a ghost. */
	public static void pinRiseChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_RISE.remove(floor);
		} else {
			PINNED_RISE.put(floor, chance);
		}
	}

	/** The test seam: pins, by floor, the chance a slain ghost leaves a rune. */
	public static void pinRuneChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_RUNE.remove(floor);
		} else {
			PINNED_RUNE.put(floor, chance);
		}
	}

	/** Raises a ghost of the floor at a spot; null if it could not be added. */
	public static @Nullable TowerGhost summon(final ServerLevel tower, final int floor, final Vec3 at) {
		TowerGhost ghost = make(tower, floor);
		if (ghost == null) {
			return null;
		}
		ghost.snapTo(at);
		return tower.addFreshEntity(ghost) ? ghost : null;
	}

	/** A ghost of the floor, as strong as its floor, named as a ghost; not yet in the world. */
	private static @Nullable TowerGhost make(final ServerLevel tower, final int floor) {
		TowerGhost ghost = GHOST.create(tower, EntitySpawnReason.EVENT);
		if (ghost == null) {
			return null;
		}
		RandomSource random = tower.getRandom();
		TowerMobs.makeTowerMob(ghost, floor, TowerElement.values()[random.nextInt(TowerElement.values().length)]);
		Component name = ghost.getCustomName();
		ghost.setCustomName(Component.literal(PREFIX + " ").withStyle(ChatFormatting.GRAY).append(name == null ? Component.empty() : name));
		return ghost;
	}

	/** Keeps an occupied floor's ghosts: a spawning round may raise one while it has fewer than its share. */
	public static void haunt(final ServerLevel tower, final int floor) {
		double chance = PINNED_RISE.getOrDefault(floor, RISE_CHANCE);
		// Ghosts are kept with the floor: until its own have loaded, it cannot be told how many it holds.
		if (!TowerElites.roomLoaded(tower, floor) || on(tower, floor).size() >= GHOSTS_PER_FLOOR || tower.getRandom().nextDouble() >= chance) {
			return;
		}
		TowerGhost ghost = make(tower, floor);
		if (ghost == null) {
			return;
		}
		if (!TowerMobs.placeInRoom(tower, floor, ghost, tower.getRandom()) || !tower.addFreshEntity(ghost)) {
			ghost.discard();
			return;
		}
		StellaTower.LOGGER.debug("[TOWER] floor={} a ghost rises at {}", floor, ghost.blockPosition());
	}

	/** The ghosts on a floor. */
	public static List<TowerGhost> on(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(TowerGhost.class, TowerMobs.room(floor), TowerGhost::isAlive);
	}

	/** Each tick of a ghost: it casts at its quarry, mends a hurt player, and now and then strengthens one. */
	static void tend(final ServerLevel level, final TowerGhost ghost) {
		int age = ghost.tickCount;
		LivingEntity quarry = ghost.getTarget();
		if (quarry != null && ghost.distanceTo(quarry) > CAST_RANGE) {
			// Out of its bolt's reach: let it go, and keep with the players.
			ghost.setTarget(null);
			quarry = null;
		}
		if (age >= ghost.nextCast && quarry != null && quarry.isAlive() && ghost.distanceTo(quarry) <= CAST_RANGE && ghost.hasLineOfSight(quarry)) {
			ghost.nextCast = age + CAST_TICKS;
			cast(level, ghost, quarry);
		}
		if (age >= ghost.nextMend) {
			ServerPlayer hurt = nearestPlayer(level, ghost, player -> player.getHealth() < player.getMaxHealth());
			if (hurt != null) {
				ghost.nextMend = age + HEAL_TICKS;
				hurt.heal(HEAL_AMOUNT);
				level.sendParticles(ParticleTypes.HEART, hurt.getX(), hurt.getY() + 1.8, hurt.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
			}
		}
		if (age >= ghost.nextStrengthen) {
			ServerPlayer near = nearestPlayer(level, ghost, player -> true);
			if (near != null) {
				ghost.nextStrengthen = age + BUFF_TICKS;
				near.addEffect(new MobEffectInstance(BUFFS.get(ghost.getRandom().nextInt(BUFFS.size())), BUFF_DURATION, 0));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, near.getX(), near.getY() + 1.0, near.getZ(), 8, 0.4, 0.6, 0.4, 0.0);
				level.playSound(null, near.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
			}
		}
	}

	/** A bolt of the ghost's own: magic, grown with its floor as a tower mob's blow is. */
	private static void cast(final ServerLevel level, final TowerGhost ghost, final LivingEntity quarry) {
		Vec3 from = ghost.getEyePosition();
		Vec3 to = quarry.getEyePosition();
		for (int step = 0; step <= 10; step++) {
			Vec3 at = from.lerp(to, step / 10.0);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, ghost.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 0.8F, 1.4F);
		// The kill is the players': the nearest is credited, so the floor's experience and loot still fall.
		ServerPlayer credited = nearestPlayer(level, ghost, player -> true);
		if (credited != null) {
			quarry.setLastHurtByPlayer(credited, 100);
		}
		quarry.hurtServer(level, level.damageSources().indirectMagic(ghost, ghost), SPELL_BASE);
	}

	/** The nearest player in reach, in survival or adventure, who passes the test. */
	private static @Nullable ServerPlayer nearestPlayer(final ServerLevel level, final TowerGhost ghost, final java.util.function.Predicate<ServerPlayer> wanted) {
		ServerPlayer nearest = null;
		double best = TEND_RANGE * TEND_RANGE;
		for (ServerPlayer player : level.players()) {
			double distance = player.distanceToSqr(ghost);
			if (player.isAlive() && !player.isSpectator() && distance <= best && wanted.test(player)) {
				best = distance;
				nearest = player;
			}
		}
		return nearest;
	}

	/** Keeps a ghost near the closest player. */
	static final class StayNearPlayerGoal extends Goal {
		private final TowerGhost ghost;

		StayNearPlayerGoal(final TowerGhost ghost) {
			this.ghost = ghost;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		/** It keeps with the players even while casting: its bolt reaches from beside them. */
		@Override
		public boolean canUse() {
			net.minecraft.world.entity.player.Player player = this.ghost.level().getNearestPlayer(this.ghost, FOLLOW_RANGE);
			return player != null && !player.isSpectator() && this.ghost.distanceTo(player) > KEEP_NEAR;
		}

		@Override
		public boolean canContinueToUse() {
			return !this.ghost.getNavigation().isDone();
		}

		@Override
		public void start() {
			net.minecraft.world.entity.player.Player player = this.ghost.level().getNearestPlayer(this.ghost, FOLLOW_RANGE);
			if (player != null) {
				this.ghost.getNavigation().moveTo(player.getX(), player.getY() + 1.0, player.getZ(), 1.0);
			}
		}
	}
}
