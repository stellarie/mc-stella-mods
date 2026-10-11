package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's ghosts: where they come from and what they do. An occupied floor keeps one pack of them about, outside its
 * cap. A pack is {@value #PACK_SIZE} ghosts that rise together and drift slowly: the pack's leader wanders, and the rest keep
 * within {@value #PACK_RADIUS} blocks of it. A ghost never follows a player. It casts at the tower's mobs, mends the
 * players it drifts past, and costs its killer nothing.
 */
public final class TowerGhosts {
	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, StellaTower.id("tower_ghost"));
	public static final EntityType<TowerGhost> GHOST = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.<TowerGhost>of(TowerGhost::new, MobCategory.MISC).sized(0.6F, 1.8F).fireImmune().clientTrackingRange(8).build(KEY));
	/** Ghosts that rise together as a pack, and the packs an occupied floor keeps. */
	public static final int PACK_SIZE = 3;
	public static final int PACKS_PER_FLOOR = 1;
	/** How many ghosts an occupied floor keeps, and the chance each spawning round raises a pack. */
	public static final int GHOSTS_PER_FLOOR = PACK_SIZE * PACKS_PER_FLOOR;
	public static final double RISE_CHANCE = 0.05;
	/** A ghost of a pack keeps within this many blocks of its leader. */
	public static final double PACK_RADIUS = 6.0;
	/** A ghost further than this from its leader closes in on it; the leader waits while a ghost is further than {@link #PACK_WAIT}. */
	public static final double PACK_CLOSE = 2.5;
	public static final double PACK_WAIT = 3.5;
	/** The most ticks a leader waits for a ghost that does not come, before it goes on. */
	public static final int PACK_PATIENCE = 60;
	/**
	 * A ghost's pace against that of a lone ghost before the packs, as the share of its flying and walking speed it
	 * keeps. A ghost's cruise rises less than its speed does, so this gives about three fifths of the old flight.
	 */
	public static final double PACE = 0.45;
	public static final double FLYING_SPEED = 0.5 * PACE;
	public static final double MOVEMENT_SPEED = 0.3 * PACE;
	/** How fast a ghost out of its place closes on its leader, as a multiple of its own pace. */
	public static final double CATCH_UP = 2.0;
	/** The tag a ghost of a pack carries, followed by the pack's id. */
	public static final String PACK_TAG = "mod-stella-tower.ghost_pack:";
	private static final double PACK_SEARCH = 48.0;
	public static final double CAST_RANGE = 16.0;
	public static final int CAST_TICKS = 40;
	public static final float SPELL_BASE = 4.0F;
	public static final double TEND_RANGE = 12.0;
	public static final int HEAL_TICKS = 100;
	public static final float HEAL_AMOUNT = 4.0F;
	public static final int BUFF_TICKS = 200;
	public static final int BUFF_DURATION = 200;
	public static final List<Holder<MobEffect>> BUFFS = List.of(MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.REGENERATION, MobEffects.SPEED);
	public static final double RUNE_CHANCE = 0.03;
	public static final String PREFIX = "Ghost";
	private static final Map<Integer, Double> PINNED_RISE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Double> PINNED_RUNE = new java.util.concurrent.ConcurrentHashMap<>();

	private TowerGhosts() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(GHOST, TowerGhost.createAttributes());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// Only a blow struck in hand leaves a rune: a shot, a blast or a spell that kills a ghost leaves none.
			if (!(entity instanceof TowerGhost ghost) || !(entity.level() instanceof ServerLevel level)
				|| !(source.getEntity() instanceof ServerPlayer killer) || source.getDirectEntity() != killer) {
				return;
			}
			TowerMobs.State state = TowerMobs.stateOf(ghost);
			double chance = PINNED_RUNE.getOrDefault(state == null ? 0 : state.floor(), RUNE_CHANCE);
			if (level.getGameRules().get(GameRules.MOB_DROPS) && ghost.getRandom().nextDouble() < chance) {
				ghost.spawnAtLocation(level, new ItemStack(TowerItems.GHOST_RUNE));
			}
			StellaTower.LOGGER.debug("[TOWER] a ghost fell to {}", killer.getName().getString());
		});
	}

	/** The test seam: pins, by floor, the chance a spawning round raises a pack. */
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

	/** The id of the pack a ghost belongs to, or null for a ghost on its own. */
	public static @Nullable String packOf(final TowerGhost ghost) {
		for (String tag : ghost.entityTags()) {
			if (tag.startsWith(PACK_TAG)) {
				return tag.substring(PACK_TAG.length());
			}
		}
		return null;
	}

	/**
	 * The leader of a ghost's pack: the living ghost of the pack with the lowest id, so every member reads the same leader
	 * and the next in line takes over when one falls. A ghost with no pack leads itself.
	 */
	public static TowerGhost leaderOf(final TowerGhost ghost) {
		String pack = packOf(ghost);
		if (pack == null) {
			return ghost;
		}
		TowerGhost leader = ghost;
		for (TowerGhost other : membersOf(ghost)) {
			if (other.getUUID().compareTo(leader.getUUID()) < 0) {
				leader = other;
			}
		}
		return leader;
	}

	/** The living ghosts of a ghost's pack, itself among them; a ghost with no pack is alone. */
	public static List<TowerGhost> membersOf(final TowerGhost ghost) {
		String pack = packOf(ghost);
		if (pack == null) {
			return List.of(ghost);
		}
		return ghost.level().getEntitiesOfClass(TowerGhost.class, ghost.getBoundingBox().inflate(PACK_SEARCH), member -> member.isAlive() && pack.equals(packOf(member)));
	}

	/** Raises a lone ghost of the floor at a spot; null if it could not be added. */
	public static @Nullable TowerGhost summon(final ServerLevel tower, final int floor, final Vec3 at) {
		TowerGhost ghost = make(tower, floor);
		if (ghost == null) {
			return null;
		}
		ghost.snapTo(at);
		return tower.addFreshEntity(ghost) ? ghost : null;
	}

	/** Raises a pack of {@value #PACK_SIZE} ghosts of the floor round a spot; the ghosts it could add, none if the first could not be. */
	public static List<TowerGhost> raisePack(final ServerLevel tower, final int floor, final Vec3 at) {
		TowerGhost first = make(tower, floor);
		if (first == null) {
			return List.of();
		}
		first.snapTo(at);
		return raise(tower, floor, first);
	}

	/** Adds the placed ghost and its fellows beside it, all of one new pack. */
	private static List<TowerGhost> raise(final ServerLevel tower, final int floor, final TowerGhost first) {
		String tag = PACK_TAG + UUID.randomUUID();
		List<TowerGhost> pack = new ArrayList<>();
		first.addTag(tag);
		if (!tower.addFreshEntity(first)) {
			first.discard();
			return pack;
		}
		pack.add(first);
		RandomSource random = tower.getRandom();
		for (int i = 1; i < PACK_SIZE; i++) {
			TowerGhost ghost = make(tower, floor);
			if (ghost == null) {
				continue;
			}
			ghost.addTag(tag);
			ghost.snapTo(first.position());
			// A free spot within a few blocks of the first, level with it or a little above; where none is free, on it.
			for (int attempt = 0; attempt < 24; attempt++) {
				double x = first.getX() + (random.nextDouble() - 0.5) * 2.0 * PACK_CLOSE;
				double y = first.getY() + random.nextInt(3);
				double z = first.getZ() + (random.nextDouble() - 0.5) * 2.0 * PACK_CLOSE;
				ghost.snapTo(x, y, z);
				if (tower.noCollision(ghost, ghost.getBoundingBox())) {
					break;
				}
				ghost.snapTo(first.position());
			}
			if (tower.addFreshEntity(ghost)) {
				pack.add(ghost);
			} else {
				ghost.discard();
			}
		}
		return pack;
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

	/** Keeps an occupied floor's ghosts: a spawning round may raise a pack while the floor has room for one more. */
	public static void haunt(final ServerLevel tower, final int floor) {
		double chance = PINNED_RISE.getOrDefault(floor, RISE_CHANCE);
		// Ghosts are kept with the floor: until its own have loaded, it cannot be told how many it holds.
		if (!TowerElites.roomLoaded(tower, floor) || on(tower, floor).size() + PACK_SIZE > GHOSTS_PER_FLOOR || tower.getRandom().nextDouble() >= chance) {
			return;
		}
		TowerGhost leader = make(tower, floor);
		if (leader == null) {
			return;
		}
		if (!TowerMobs.placeInRoom(tower, floor, leader, tower.getRandom())) {
			leader.discard();
			return;
		}
		List<TowerGhost> pack = raise(tower, floor, leader);
		StellaTower.LOGGER.debug("[TOWER] floor={} a pack of {} ghosts rises at {}", floor, pack.size(), leader.blockPosition());
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
			// Out of its bolt's reach: let it go, and keep with the pack.
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

	/** Keeps a ghost that is not its pack's leader within {@value #PACK_CLOSE} blocks of it. */
	static final class KeepWithPackGoal extends Goal {
		private final TowerGhost ghost;
		private @Nullable TowerGhost leader;
		private int recalc;

		KeepWithPackGoal(final TowerGhost ghost) {
			this.ghost = ghost;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		/** It keeps with its pack even while casting: its bolt reaches from beside the others. */
		@Override
		public boolean canUse() {
			TowerGhost lead = leaderOf(this.ghost);
			if (lead == this.ghost || this.ghost.distanceToSqr(lead) <= PACK_CLOSE * PACK_CLOSE) {
				return false;
			}
			this.leader = lead;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.leader != null && this.leader.isAlive() && this.ghost.distanceToSqr(this.leader) > PACK_CLOSE * PACK_CLOSE / 4.0;
		}

		@Override
		public void start() {
			this.recalc = 0;
		}

		@Override
		public void tick() {
			if (this.leader != null && --this.recalc <= 0) {
				this.recalc = 2;
				this.ghost.getNavigation().moveTo(this.leader.getX(), this.leader.getY(), this.leader.getZ(), CATCH_UP);
			}
		}

		@Override
		public void stop() {
			this.leader = null;
			this.ghost.getNavigation().stop();
		}
	}

	/**
	 * The pack's leader, or a ghost on its own, wanders; the rest of the pack goes where the leader goes. A leader waits
	 * while a ghost of its pack is further than {@value #PACK_WAIT} blocks behind, for {@value #PACK_PATIENCE} ticks at most.
	 */
	static final class PackWanderGoal extends WaterAvoidingRandomFlyingGoal {
		private final TowerGhost ghost;
		private int patience = PACK_PATIENCE;

		PackWanderGoal(final TowerGhost ghost, final double speed) {
			super(ghost, speed);
			this.ghost = ghost;
		}

		@Override
		public boolean canUse() {
			return leaderOf(this.ghost) == this.ghost && !holdUp() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return !holdUp() && super.canContinueToUse();
		}

		private boolean holdUp() {
			boolean behind = false;
			for (TowerGhost member : membersOf(this.ghost)) {
				behind |= member != this.ghost && member.distanceToSqr(this.ghost) > PACK_WAIT * PACK_WAIT;
			}
			if (!behind) {
				this.patience = PACK_PATIENCE;
				return false;
			}
			return this.patience-- > 0;
		}
	}
}
