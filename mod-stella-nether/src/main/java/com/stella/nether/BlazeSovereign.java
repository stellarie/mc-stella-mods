package com.stella.nether;

import com.mojang.serialization.Codec;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Blaze Sovereign: one per run, risen on open ground at the run's fortress the first time a player comes
 * near, and never again in that run. It stands three times a blaze's size and keeps near the ground; its
 * health is {@link #BASE_HEALTH} plus {@link #HEALTH_PER_THREAT} per threat level, and every hit it lands
 * sets its target alight.
 */
public final class BlazeSovereign {
	public static final double BASE_HEALTH = 1000.0;
	public static final double HEALTH_PER_THREAT = 50.0;
	public static final double SCALE = 3.0;
	/** Vanilla's ceiling on the health attribute; what lies past it is kept as a divisor on every hit taken. */
	public static final double MAX_HEALTH_ATTRIBUTE = 1024.0;
	/** How high above the ground below it the Sovereign may rise. */
	public static final double NEAR_GROUND = 2.0;
	/** How near a player must come to the fortress to wake it, and to see its bar. */
	public static final double WAKE_RANGE = 96.0;
	public static final double BAR_RANGE = 96.0;
	public static final int BURN_SECONDS = 5;
	/** The Flame Mantle's chance to fall with a Blaze Core. */
	public static final double MANTLE_CHANCE = 0.05;
	/** Open ground is searched this far around the fortress start. */
	static final int GROUND_SEARCH = 32;
	/** Air a spot needs above its floor to hold a Sovereign. */
	static final int HEADROOM = 7;

	/** The run a blaze is Sovereign of. */
	public static final AttachmentType<Integer> SOVEREIGN = AttachmentRegistry.create(StellaNether.id("sovereign"), builder -> builder
		.persistent(Codec.INT).syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
	/** The share of the Sovereign's health beyond the attribute's ceiling, as a divisor on hits taken. */
	public static final AttachmentType<Double> DIVISOR = AttachmentRegistry.createPersistent(StellaNether.id("sovereign_divisor"), Codec.DOUBLE);

	private static @Nullable ServerBossEvent bar;
	private static @Nullable UUID current;
	/** The risen Sovereign's object while it stays loaded; the level's lookup sees only tracked entities. */
	private static @Nullable Blaze held;

	private BlazeSovereign() {
	}

	/** What each fallen Sovereign left, by its identity, for as long as the server runs. */
	private static final java.util.Map<UUID, List<net.minecraft.world.entity.item.ItemEntity>> SPOILS = new java.util.concurrent.ConcurrentHashMap<>();

	public static void register() {
		// Whatever fire the Sovereign looses flies as soul fire: its own casts set it as they fire; this
		// catches the rest, a vanilla attack's fireballs, as they load.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.projectile.hurtingprojectile.Fireball fireball && isSovereign(fireball.getOwner())
				&& !fireball.getItem().is(NetherItems.SOUL_FIRE_CHARGE)) {
				fireball.setItem(new net.minecraft.world.item.ItemStack(NetherItems.SOUL_FIRE_CHARGE));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Blaze blaze && isSovereign(blaze) && blaze.level() instanceof ServerLevel level) {
				dropSpoils(level, blaze);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && isSovereign(source.getEntity())) {
				entity.igniteForSeconds(BURN_SECONDS);
			}
		});
	}

	public static boolean isSovereign(final @Nullable Entity entity) {
		return entity instanceof Blaze blaze && blaze.hasAttached(SOVEREIGN);
	}

	/** The divisor a hit on this entity is taken through; 1 for all but a Sovereign. */
	public static float scaleDamageTaken(final LivingEntity entity, final float amount) {
		Double divisor = entity.getAttached(DIVISOR);
		return divisor == null || divisor <= 1.0 ? amount : (float) (amount / divisor);
	}

	/** The run's living Sovereign, if one stands loaded. */
	public static @Nullable Blaze find(final MinecraftServer server) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (raid == null || current == null) {
			return null;
		}
		if (held != null && !held.isRemoved()) {
			return held.isAlive() ? held : null;
		}
		// Unloaded and loaded again: a new object stands for it.
		held = raid.getEntity(current) instanceof Blaze blaze ? blaze : null;
		return held != null && held.isAlive() ? held : null;
	}

	/** Its whole health: what the attribute holds, times the divisor. */
	public static double effectiveHealth(final Blaze blaze) {
		Double divisor = blaze.getAttached(DIVISOR);
		return blaze.getHealth() * (divisor == null ? 1.0 : divisor);
	}

	public static double totalHealth(final int threat) {
		return BASE_HEALTH + HEALTH_PER_THREAT * threat;
	}

	static void tick(final MinecraftServer server, final RaidState state) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (raid == null) {
			return;
		}
		if (!state.sovereignRisen()) {
			BlockPos fortress = RaidInstance.site(server).fortress();
			for (ServerPlayer player : raid.players()) {
				double dx = player.getX() - fortress.getX();
				double dz = player.getZ() - fortress.getZ();
				if (dx * dx + dz * dz <= WAKE_RANGE * WAKE_RANGE && raid.isLoaded(fortress)) {
					BlockPos ground = openGround(raid, fortress);
					if (ground != null) {
						rise(raid, Vec3.atBottomCenterOf(ground));
					}
					break;
				}
			}
			return;
		}
		Blaze blaze = find(server);
		if (blaze == null) {
			if (bar != null) {
				bar.removeAllPlayers();
			}
			return;
		}
		// Again each tick: a Sovereign loaded from disk, or given the mobs mod's attack on load, gets its burst back.
		SovereignBurst.install(blaze);
		// Its opening, once, holds everything else while it runs.
		if (!SovereignOpening.tick(raid, blaze)) {
			SovereignSpecials.tick(raid, blaze);
		}
		if (!SovereignSpecials.airborne(blaze) && !SovereignOpening.rushing(blaze)) {
			keepNearGround(raid, blaze);
		}
		if (bar != null && server.getTickCount() % 10 == 0) {
			bar.setProgress((float) (blaze.getHealth() / blaze.getMaxHealth()));
			for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
				if (player.level() != raid || player.distanceTo(blaze) > BAR_RANGE) {
					bar.removePlayer(player);
				}
			}
			for (ServerPlayer player : raid.players()) {
				if (player.distanceTo(blaze) <= BAR_RANGE) {
					bar.addPlayer(player);
				}
			}
		}
	}

	/** Raises the run's Sovereign at a spot, and marks the run so none rises again in it. */
	public static Blaze rise(final ServerLevel raid, final Vec3 at) {
		MinecraftServer server = raid.getServer();
		RaidState state = RaidInstance.state(server);
		Blaze blaze = EntityTypes.BLAZE.create(raid, EntitySpawnReason.EVENT);
		blaze.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		blaze.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		blaze.setAttached(SOVEREIGN, state.run());
		ThreatState roll = ThreatState.getOrRoll(blaze);
		blaze.setAttached(ThreatState.TYPE, new ThreatState(ThreatState.NETHER_MINIMUM - Math.min(0, roll.variation()), roll.variation()));
		int threat = threat(blaze);
		double total = totalHealth(threat);
		double attribute = Math.min(total, MAX_HEALTH_ATTRIBUTE);
		blaze.getAttribute(Attributes.MAX_HEALTH).setBaseValue(attribute);
		blaze.setAttached(DIVISOR, total / attribute);
		blaze.setHealth((float) attribute);
		blaze.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		blaze.refreshDimensions();
		blaze.setPersistenceRequired();
		blaze.setCustomName(Component.translatable("entity.mod-stella-nether.blaze_sovereign").withStyle(ChatFormatting.AQUA));
		raid.addFreshEntity(blaze);
		SovereignBurst.install(blaze);
		current = blaze.getUUID();
		held = blaze;
		RaidInstance.set(server, state.withSovereignRisen());
		closeBar(server);
		bar = new ServerBossEvent(blaze.getUUID(), blaze.getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
		StellaNether.LOGGER.info("[NETHER] sovereign risen run={} at={} threat={} health={}", state.run(), blaze.blockPosition(), threat, total);
		return blaze;
	}

	/** The Sovereign's threat: the raid's roll for it, plus the mobs mod's boss bonus. */
	public static int threat(final Blaze blaze) {
		return ThreatState.level(blaze) + ThreatState.BOSS_BONUS;
	}

	public static @Nullable ServerBossEvent bar() {
		return bar;
	}

	static void closeBar(final MinecraftServer server) {
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}

	/** How far the Sovereign is drawn down each tick while it is higher than {@link #NEAR_GROUND}. */
	public static final double DESCENT_PER_TICK = 0.5;

	/** A blaze rises toward a target above it; the Sovereign is drawn back down once it is more than a little off the ground. */
	static void keepNearGround(final ServerLevel raid, final Blaze blaze) {
		double height = heightAboveGround(raid, blaze);
		if (height > NEAR_GROUND) {
			Vec3 motion = blaze.getDeltaMovement();
			blaze.setDeltaMovement(motion.x, Math.min(motion.y, 0.0), motion.z);
			blaze.setPos(blaze.getX(), blaze.getY() - Math.min(DESCENT_PER_TICK, height - NEAR_GROUND), blaze.getZ());
		}
	}

	public static double heightAboveGround(final ServerLevel raid, final Entity entity) {
		BlockPos.MutableBlockPos at = entity.blockPosition().mutable();
		for (int down = 0; down < 64; down++) {
			at.move(0, -1, 0);
			if (!raid.getBlockState(at).getCollisionShape(raid, at).isEmpty()) {
				return entity.getY() - (at.getY() + 1);
			}
		}
		return 64.0;
	}

	/** The nearest spot near the fortress start with solid floor, no lava, and room overhead. */
	static @Nullable BlockPos openGround(final ServerLevel raid, final BlockPos fortress) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -GROUND_SEARCH; dx <= GROUND_SEARCH; dx += 2) {
			for (int dz = -GROUND_SEARCH; dz <= GROUND_SEARCH; dz += 2) {
				BlockPos column = fortress.offset(dx, 0, dz);
				if (!raid.isLoaded(column)) {
					continue;
				}
				for (int y = 100; y >= 32; y--) {
					BlockPos floor = new BlockPos(column.getX(), y - 1, column.getZ());
					if (!clear(raid, floor.above())) {
						continue;
					}
					if (raid.getBlockState(floor).isFaceSturdy(raid, floor, net.minecraft.core.Direction.UP)) {
						double distance = dx * dx + dz * dz + (y - 64) * (y - 64) * 0.25;
						if (distance < bestDistance) {
							bestDistance = distance;
							best = floor.above();
						}
						break;
					}
				}
			}
		}
		return best;
	}

	private static boolean clear(final ServerLevel raid, final BlockPos feet) {
		for (int up = 0; up < HEADROOM; up++) {
			BlockPos at = feet.above(up);
			if (!raid.getBlockState(at).getCollisionShape(raid, at).isEmpty() || !raid.getFluidState(at).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** A Blaze Core always, and now and then a Flame Mantle. */
	static void dropSpoils(final ServerLevel level, final Blaze blaze) {
		List<net.minecraft.world.entity.item.ItemEntity> dropped = new java.util.ArrayList<>();
		net.minecraft.world.entity.item.ItemEntity core = blaze.spawnAtLocation(level, new net.minecraft.world.item.ItemStack(NetherItems.BLAZE_CORE));
		if (core != null) {
			dropped.add(core);
		}
		if (mantleDrops(blaze.getRandom())) {
			net.minecraft.world.entity.item.ItemEntity mantle = blaze.spawnAtLocation(level, new net.minecraft.world.item.ItemStack(NetherItems.FLAME_MANTLE));
			if (mantle != null) {
				dropped.add(mantle);
			}
		}
		net.minecraft.world.entity.item.ItemEntity scrolls = RaidDrops.sovereignScrolls(level, blaze);
		if (scrolls != null) {
			dropped.add(scrolls);
		}
		SPOILS.put(blaze.getUUID(), dropped);
		StellaNether.LOGGER.info("[NETHER] sovereign slain spoils={}", dropped.stream().map(item -> item.getItem().toString()).toList());
	}

	public static boolean mantleDrops(final net.minecraft.util.RandomSource random) {
		return random.nextDouble() < MANTLE_CHANCE;
	}

	/** What a fallen blaze left as Sovereign; nothing for any other. */
	public static List<net.minecraft.world.entity.item.ItemEntity> spoilsOf(final Entity entity) {
		return SPOILS.getOrDefault(entity.getUUID(), List.of());
	}

	/** Forgets the risen Sovereign; the next run's rises afresh. Test hook and reset. */
	static void forget() {
		current = null;
		held = null;
		SovereignSpecials.forget();
		SovereignOpening.forget();
	}

	static boolean burns(final DamageSource source) {
		return isSovereign(source.getEntity());
	}
}
