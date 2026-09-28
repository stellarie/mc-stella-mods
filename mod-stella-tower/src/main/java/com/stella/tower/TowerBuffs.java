package com.stella.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;

/**
 * Every boss floor gives its players one buff and its mobs two, drawn from the floor and the world seed,
 * held while they are on it. A buff's level is {@code 1 + floor / 100}, within its ceiling. Boss floors
 * are also wrapped in a snowy mist: their own biome, with close pale fog and drifting snow.
 */
public final class TowerBuffs {
	public static final ResourceKey<Biome> BOSS_BIOME = ResourceKey.create(Registries.BIOME, StellaTower.id("tower_boss_floor"));
	public static final int FLOORS_PER_LEVEL = 100;
	/** Buffs are topped up this often and last a little longer, so leaving the floor lets them lapse. */
	public static final int REFRESH_TICKS = 20;
	public static final int DURATION_TICKS = 60;
	public static final double EXTRA_HEALTH_PER_LEVEL = 0.10;
	public static final double THORNS_PER_LEVEL = 0.05;
	public static final double THORNS_CAP = 0.50;
	public static final int FLOORS_PER_EXTRA_LIFE = 500;
	/** Uncapped buffs still stop at the highest amplifier the game keeps. */
	public static final int NO_CAP = 127;

	public enum PlayerBuff {
		STRENGTH(MobEffects.STRENGTH, NO_CAP),
		REGENERATION(MobEffects.REGENERATION, 4),
		SPEED(MobEffects.SPEED, 4),
		RESISTANCE(MobEffects.RESISTANCE, 2),
		HASTE(MobEffects.HASTE, NO_CAP),
		ABSORPTION(MobEffects.ABSORPTION, NO_CAP),
		LUCK(MobEffects.LUCK, NO_CAP);

		private final Holder<MobEffect> effect;
		private final int maxAmplifier;

		PlayerBuff(final Holder<MobEffect> effect, final int maxAmplifier) {
			this.effect = effect;
			this.maxAmplifier = maxAmplifier;
		}

		public Holder<MobEffect> effect() {
			return this.effect;
		}

		public int amplifier(final int floor) {
			return Math.min(this.maxAmplifier, level(floor) - 1);
		}

		public int maxAmplifier() {
			return this.maxAmplifier;
		}
	}

	public enum MobBuff {
		SPEED(MobEffects.SPEED, 4),
		STRENGTH(MobEffects.STRENGTH, NO_CAP),
		RESISTANCE(MobEffects.RESISTANCE, 2),
		REGENERATION(MobEffects.REGENERATION, 4),
		EXTRA_HEALTH(null, NO_CAP),
		THORNS(null, NO_CAP),
		EXTRA_LIFE(null, NO_CAP);

		private final Holder<MobEffect> effect;
		private final int maxAmplifier;

		MobBuff(final Holder<MobEffect> effect, final int maxAmplifier) {
			this.effect = effect;
			this.maxAmplifier = maxAmplifier;
		}

		public Holder<MobEffect> effect() {
			return this.effect;
		}

		public int amplifier(final int floor) {
			return Math.min(this.maxAmplifier, level(floor) - 1);
		}

		public int maxAmplifier() {
			return this.maxAmplifier;
		}
	}

	private static final long PLAYER_SALT = 0x6A09E667F3BCC909L;
	private static final long MOB_SALT = 0xBB67AE8584CAA73BL;
	/** The test seam: pinned mob buffs, by the mob's own floor. */
	private static final Map<Integer, List<MobBuff>> PINNED = new java.util.concurrent.ConcurrentHashMap<>();
	/** The test seam: a pinned player buff, by boss floor. */
	private static final Map<Integer, PlayerBuff> PINNED_PLAYER = new java.util.concurrent.ConcurrentHashMap<>();
	/**
	 * Which boss floor's buff a player carries, so it is taken away when they leave that floor. Kept on the
	 * player and saved with them, so a restart or a log out does not strand a buff.
	 */
	public static final AttachmentType<Integer> CARRYING = AttachmentRegistry.create(
		StellaTower.id("carrying"), builder -> builder.persistent(Codec.INT)
	);
	/** How many extra lives a mob has spent. */
	public static final AttachmentType<Integer> REVIVES = AttachmentRegistry.create(
		StellaTower.id("revives"), builder -> builder.persistent(Codec.INT).copyOnDeath()
	);
	/** Set while thorns hit back, so the returned blow is not scaled by the reflecting mob's floor again. */
	private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

	private TowerBuffs() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerBuffs::tick);
		// Out of the tower by portal, key, scroll or command: the floor's buff stays behind at once.
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (origin.dimension().equals(TowerTravel.TOWER)) {
				dropCarried(player, origin.getSeed());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0.0F || REFLECTING.get() || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state == null || !mobBuffsOf(entity).contains(MobBuff.THORNS)) {
				return;
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != entity && attacker.isAlive()) {
				float back = (float) (taken * Math.min(THORNS_CAP, THORNS_PER_LEVEL * level(state.floor())));
				REFLECTING.set(true);
				try {
					attacker.hurtServer(level, entity.damageSources().thorns(entity), back);
				} finally {
					REFLECTING.set(false);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !mobBuffsOf(entity).contains(MobBuff.EXTRA_LIFE)) {
				return true;
			}
			int used = entity.getAttachedOrElse(REVIVES, 0);
			if (used >= 1 + state.floor() / FLOORS_PER_EXTRA_LIFE) {
				return true;
			}
			entity.setAttached(REVIVES, used + 1);
			entity.setHealth(entity.getMaxHealth());
			return false;
		});
	}

	public static boolean reflecting() {
		return REFLECTING.get();
	}

	public static int level(final int floor) {
		return 1 + Math.max(0, floor) / FLOORS_PER_LEVEL;
	}

	public static boolean isBossFloor(final int floor) {
		return floor > 0 && floor % TowerElites.BOSS_EVERY == 0;
	}

	/** The one buff a boss floor gives its players. */
	public static PlayerBuff playerBuff(final long seed, final int floor) {
		Random random = new Random(seed ^ PLAYER_SALT ^ (floor * 0x9E3779B97F4A7C15L));
		return PlayerBuff.values()[random.nextInt(PlayerBuff.values().length)];
	}

	/** The two buffs a boss floor gives its mobs. */
	public static List<MobBuff> mobBuffs(final long seed, final int floor) {
		Random random = new Random(seed ^ MOB_SALT ^ (floor * 0x9E3779B97F4A7C15L));
		List<MobBuff> all = new ArrayList<>(List.of(MobBuff.values()));
		MobBuff first = all.remove(random.nextInt(all.size()));
		MobBuff second = all.remove(random.nextInt(all.size()));
		return List.of(first, second);
	}

	/** The test seam: a boss floor's mob buffs, pinned for mobs of that floor; null restores the drawn ones. */
	public static void pinPlayerBuff(final int floor, final @Nullable PlayerBuff buff) {
		if (buff == null) {
			PINNED_PLAYER.remove(floor);
		} else {
			PINNED_PLAYER.put(floor, buff);
		}
	}

	/** The buff a boss floor grants its players: its own draw, unless a test has pinned one. */
	public static PlayerBuff playerBuffOn(final long seed, final int floor) {
		PlayerBuff pin = PINNED_PLAYER.get(floor);
		return pin != null ? pin : playerBuff(seed, floor);
	}

	public static void pinMobBuffs(final int floor, final @Nullable List<MobBuff> buffs) {
		if (buffs == null) {
			PINNED.remove(floor);
		} else {
			PINNED.put(floor, buffs);
		}
	}

	/** A tower mob's buffs: those of its own floor when that floor is a boss floor, else none. */
	public static List<MobBuff> mobBuffsOf(final LivingEntity mob) {
		TowerMobs.State state = TowerMobs.stateOf(mob);
		if (state == null || !isBossFloor(state.floor()) || !(mob.level() instanceof ServerLevel level)) {
			return List.of();
		}
		List<MobBuff> pin = PINNED.get(state.floor());
		return pin != null ? pin : mobBuffs(level.getSeed(), state.floor());
	}

	/** How much a buffed mob's extra health divides each hit it takes. */
	public static double healthFactor(final LivingEntity mob) {
		TowerMobs.State state = TowerMobs.stateOf(mob);
		if (state == null || !mobBuffsOf(mob).contains(MobBuff.EXTRA_HEALTH)) {
			return 1.0;
		}
		return 1.0 + EXTRA_HEALTH_PER_LEVEL * level(state.floor());
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % REFRESH_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		Map<Integer, Boolean> occupied = new HashMap<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (player.isSpectator()) {
				continue;
			}
			// A player who left the boss floor loses its buff now, not when the last dose runs out.
			Integer carried = player.getAttached(CARRYING);
			if (carried != null && carried != floor) {
				dropCarried(player, tower.getSeed());
			}
			if (floor < 1) {
				continue;
			}
			occupied.put(floor, true);
			if (isBossFloor(floor)) {
				give(player, playerBuffOn(tower.getSeed(), floor), floor);
				player.setAttached(CARRYING, floor);
			}
		}
		for (int floor : occupied.keySet()) {
			// A boss floor built before the mist came gets it the first time a player stands there.
			if (isBossFloor(floor) && !tower.getBiome(TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 2, TowerLayout.SIZE / 2)).is(BOSS_BIOME)) {
				mist(tower, floor);
			}
		}
		for (int floor : occupied.keySet()) {
			for (Mob mob : tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), mob -> mob.isAlive() && TowerMobs.stateOf(mob) != null)) {
				int own = TowerMobs.stateOf(mob).floor();
				for (MobBuff buff : mobBuffsOf(mob)) {
					if (buff.effect() != null) {
						mob.addEffect(new MobEffectInstance(buff.effect(), DURATION_TICKS, buff.amplifier(own), true, true));
					}
				}
			}
		}
	}

	/**
	 * Tops up a floor's buff. Absorption is the exception: a shield given once for the stay, since each
	 * fresh dose would fill it again, and a shield that refills is a wall that heals.
	 */
	private static void give(final ServerPlayer player, final PlayerBuff buff, final int floor) {
		int amplifier = buff.amplifier(floor);
		if (buff == PlayerBuff.ABSORPTION) {
			MobEffectInstance held = player.getEffect(buff.effect());
			if (held == null || held.getAmplifier() < amplifier) {
				player.addEffect(new MobEffectInstance(buff.effect(), MobEffectInstance.INFINITE_DURATION, amplifier, true, true));
			}
			return;
		}
		player.addEffect(new MobEffectInstance(buff.effect(), DURATION_TICKS, amplifier, true, true));
	}

	/** Takes back the buff a player carries, and gives back any weaker dose of their own it covered. */
	static void dropCarried(final ServerPlayer player, final long seed) {
		Integer carried = player.getAttached(CARRYING);
		if (carried == null) {
			return;
		}
		player.removeAttached(CARRYING);
		PlayerBuff old = playerBuffOn(seed, carried);
		MobEffectInstance held = player.getEffect(old.effect());
		if (held == null || !held.isAmbient() || !(held.isInfiniteDuration() || held.getDuration() <= DURATION_TICKS)) {
			return;
		}
		MobEffectInstance under = ((com.stella.tower.mixin.MobEffectInstanceAccessor) held).stellaTower$hidden();
		player.removeEffect(old.effect());
		if (under != null) {
			player.addEffect(new MobEffectInstance(under));
		}
	}

	/** Wraps a boss floor's chunks in its mist: their biome becomes the boss floor's own. */
	public static void mist(final ServerLevel tower, final int floor) {
		Holder<Biome> biome = tower.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(BOSS_BIOME);
		BlockPos origin = TowerLayout.origin(floor);
		List<ChunkAccess> chunks = new ArrayList<>();
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				ChunkAccess chunk = tower.getChunk(cx, cz);
				chunk.fillBiomesFromNoise((x, y, z) -> biome);
				chunk.markUnsaved();
				chunks.add(chunk);
			}
		}
		tower.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
	}
}
