package com.stella.nether;

import com.mojang.serialization.Codec;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SpawnScaling;
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
 * The Blaze Sovereign: one per run of each raid, risen on open ground at the run's fortress the first time a
 * player comes near, and never again in that run. It stands three times a blaze's size and keeps near the ground; its
 * health follows the raid's best player: {@link #BASE_HEALTH} times the mobs mod's health curve at its threat level,
 * and every hit it lands sets its target alight.
 */
public final class BlazeSovereign {
	/** The health at threat level 0; the level multiplies it. */
	public static final double BASE_HEALTH = 300.0;
	/** The Sovereign stands this many levels above the raid mobs. */
	public static final int LEVEL_BONUS = 10;
	public static final double SCALE = 3.0;
	/** Vanilla's ceiling on the health attribute; what lies past it is kept as a divisor on every hit taken. */
	/** How high above the ground below it the Sovereign may rise. */
	public static final double NEAR_GROUND = 2.0;
	/** A run opens 48 blocks out, so a player must enter the fortress to wake it. */
	public static final double WAKE_RANGE = 24.0;
	public static final double BAR_RANGE = 96.0;
	public static final int BURN_SECONDS = 5;
	/** The Flame Mantle's chance to fall with a Blaze Core. */
	public static final double MANTLE_CHANCE = 0.05;
	/** Open ground is searched this far around the fortress start. */
	static final int GROUND_SEARCH = 32;
	/** Air a spot needs above its floor to hold a Sovereign. */
	static final int HEADROOM = 7;

	/** The run value of a Sovereign built outside a raid; real runs count up from 1. */
	public static final int FREE_RUN = -1;

	/** The run a blaze is Sovereign of. */
	public static final AttachmentType<Integer> SOVEREIGN = AttachmentRegistry.create(StellaNether.id("sovereign"), builder -> builder
		.persistent(Codec.INT).syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
	/** The share of the Sovereign's health beyond the attribute's ceiling, as a divisor on hits taken. */
	public static final AttachmentType<Double> DIVISOR = AttachmentRegistry.createPersistent(StellaNether.id("sovereign_divisor"), Codec.DOUBLE);

	/** Each raid's Sovereign bar, identity and object, by mode. */
	private static final @Nullable ServerBossEvent[] BARS = new ServerBossEvent[RaidMode.values().length];
	private static final @Nullable UUID[] CURRENT = new UUID[RaidMode.values().length];
	/** The risen Sovereign's object while it stays loaded; the level's lookup sees only tracked entities. */
	private static final @Nullable Blaze[] HELD = new Blaze[RaidMode.values().length];

	private BlazeSovereign() {
	}

	/** What each fallen Sovereign left, by its identity, for as long as the server runs. */
	private static final java.util.Map<UUID, List<net.minecraft.world.entity.item.ItemEntity>> SPOILS = new java.util.concurrent.ConcurrentHashMap<>();

	public static void register() {
		// A debug command: a Sovereign where it is run, in the raid nether, for game masters.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, selection) -> dispatcher.register(
			net.minecraft.commands.Commands.literal(RaidTravel.COMMAND).then(net.minecraft.commands.Commands.literal("sovereign")
				.requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
				.then(net.minecraft.commands.Commands.literal("summon").executes(context -> summonByCommand(context.getSource()))))));
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

	/** A mode's run's living Sovereign, if one stands loaded. */
	public static @Nullable Blaze find(final MinecraftServer server, final RaidMode mode) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		int at = mode.ordinal();
		if (raid == null || CURRENT[at] == null) {
			return null;
		}
		Blaze held = HELD[at];
		if (held != null && !held.isRemoved()) {
			return held.isAlive() ? held : null;
		}
		// Unloaded and loaded again: a new object stands for it.
		held = raid.getEntity(CURRENT[at]) instanceof Blaze blaze ? blaze : null;
		HELD[at] = held;
		return held != null && held.isAlive() ? held : null;
	}

	/** Its whole health: what the attribute holds, times the divisor. */
	public static double effectiveHealth(final Blaze blaze) {
		Double divisor = blaze.getAttached(DIVISOR);
		return blaze.getHealth() * (divisor == null ? 1.0 : divisor);
	}

	/** Its whole health at full: the attribute's maximum times the divisor. */
	public static double effectiveMaxHealth(final Blaze blaze) {
		Double divisor = blaze.getAttached(DIVISOR);
		return blaze.getMaxHealth() * (divisor == null ? 1.0 : divisor);
	}

	/** A Sovereign's small fireball deals this share of vanilla's damage before the level factor. */
	public static final float FIREBALL_SHARE = 0.5F;

	/** The damage of a hit taken: halved for a small fireball the Sovereign threw. */
	public static float scaleFireball(final DamageSource source, final float amount) {
		boolean thrown = source.is(net.minecraft.world.damagesource.DamageTypes.FIREBALL)
			&& source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
			&& isSovereign(source.getEntity());
		return thrown ? amount * FIREBALL_SHARE : amount;
	}

	static void tick(final MinecraftServer server, final RaidState state) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (raid == null) {
			return;
		}
		RaidMode mode = state.mode();
		if (!state.sovereignRisen()) {
			BlockPos fortress = RaidInstance.site(server, mode).fortress();
			for (ServerPlayer player : RaidInstance.playersOf(raid, mode)) {
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
		Blaze blaze = find(server, mode);
		ServerBossEvent bar = BARS[mode.ordinal()];
		if (blaze == null) {
			if (bar != null) {
				bar.removeAllPlayers();
			}
			return;
		}
		tickSovereign(raid, blaze, bar, server.getTickCount());
	}

	/** One tick of a Sovereign's own behaviour; shared by the raid's Sovereign and a free one. */
	static void tickSovereign(final ServerLevel level, final Blaze blaze, final @Nullable ServerBossEvent bossBar, final int serverTick) {
		// Again each tick: a Sovereign loaded from disk, or given the mobs mod's attack on load, gets its burst back.
		SovereignBurst.install(blaze);
		// A charge at a player keeping away holds everything else while it runs; so does its shield.
		if (!SovereignCharge.tick(level, blaze)) {
			SovereignSpecials.tick(level, blaze);
		}
		if (!SovereignSpecials.airborne(blaze) && !SovereignCharge.charging(blaze) && !SovereignSpecials.shielded(blaze)) {
			keepNearGround(level, blaze);
		}
		if (bossBar != null && serverTick % 10 == 0) {
			bossBar.setProgress((float) (blaze.getHealth() / blaze.getMaxHealth()));
			for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
				if (player.level() != level || player.distanceTo(blaze) > BAR_RANGE) {
					bossBar.removePlayer(player);
				}
			}
			for (ServerPlayer player : level.players()) {
				if (player.distanceTo(blaze) <= BAR_RANGE) {
					bossBar.addPlayer(player);
				}
			}
		}
	}

	/** The summon command: in the raid nether, a Sovereign where the command is run; anywhere else, nothing. */
	static int summonByCommand(final net.minecraft.commands.CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (!level.dimension().equals(RaidTravel.RAID)) {
			source.sendFailure(Component.translatable("message.mod-stella-nether.summon.raid_only"));
			return 0;
		}
		Blaze blaze = rise(level, source.getPosition());
		source.sendSuccess(() -> Component.translatable("message.mod-stella-nether.summon.done", blaze.blockPosition().toShortString()), true);
		return 1;
	}

	/** The Sovereign's level in a raid: the raid mob level of the mode and its best player, plus the bonus. */
	public static int threatLevel(final ServerLevel raid, final RaidMode mode) {
		return threatLevel(RaidInstance.playersOf(raid, mode), mode);
	}

	/** The Sovereign's level: the raid mob level of its mode, plus the bonus. */
	public static int threatLevel(final java.util.Collection<? extends net.minecraft.world.entity.player.Player> players, final RaidMode mode) {
		return RaidInstance.mobLevel(mode, players) + LEVEL_BONUS;
	}

	/** The rpg mod's multiplier on every hit taken; 1 when that mod is absent. */
	public static double healthScale() {
		Object scale = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get("mod-stella-rpg:health-scale");
		return scale instanceof Double value && value > 0.0 ? value : 1.0;
	}

	/** The most health an attribute may hold here: the live cap, divided by the rpg mod's health scale. */
	public static double healthCap() {
		double cap = ((net.minecraft.world.entity.ai.attributes.RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		return cap / healthScale();
	}

	/** A Sovereign of a run, not yet placed or added: its tags, level, health, size and name. */
	static Blaze create(final ServerLevel level, final int run, final int threat) {
		Blaze blaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.EVENT);
		blaze.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		blaze.addTag(StellaMobs.SELF_SCALED_TAG);
		blaze.setAttached(SOVEREIGN, run);
		blaze.setAttached(ThreatState.TYPE, new ThreatState(threat, 0));
		double total = BASE_HEALTH * SpawnScaling.healthMultiplier(threat);
		double attribute = Math.min(total, healthCap());
		blaze.getAttribute(Attributes.MAX_HEALTH).setBaseValue(attribute);
		blaze.setAttached(DIVISOR, total / attribute);
		blaze.setHealth((float) attribute);
		blaze.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		blaze.refreshDimensions();
		blaze.setPersistenceRequired();
		blaze.setCustomName(Component.translatable("entity.mod-stella-nether.blaze_sovereign").withStyle(ChatFormatting.AQUA));
		return blaze;
	}

	/** Raises the Sovereign of the raid a spot lies in, and marks the run so none rises again in it. */
	public static Blaze rise(final ServerLevel raid, final Vec3 at) {
		MinecraftServer server = raid.getServer();
		RaidMode mode = RaidInstance.modeAt(BlockPos.containing(at));
		RaidState state = RaidInstance.state(server, mode);
		int level = threatLevel(raid, mode);
		Blaze blaze = create(raid, state.run(), level);
		blaze.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		raid.addFreshEntity(blaze);
		SovereignBurst.install(blaze);
		CURRENT[mode.ordinal()] = blaze.getUUID();
		HELD[mode.ordinal()] = blaze;
		RaidInstance.set(server, state.withSovereignRisen());
		closeBar(mode);
		BARS[mode.ordinal()] = new ServerBossEvent(blaze.getUUID(), blaze.getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
		BehaviorLog.info("[NETHER] sovereign risen mode={} run={} at={} level={} health={}", mode, state.run(), blaze.blockPosition(), level, BASE_HEALTH * SpawnScaling.healthMultiplier(level));
		return blaze;
	}

	public static @Nullable ServerBossEvent bar(final RaidMode mode) {
		return BARS[mode.ordinal()];
	}

	static void closeBar(final RaidMode mode) {
		ServerBossEvent bar = BARS[mode.ordinal()];
		if (bar != null) {
			bar.removeAllPlayers();
			BARS[mode.ordinal()] = null;
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
		BehaviorLog.info("[NETHER] sovereign slain spoils={}", dropped.stream().map(item -> item.getItem().toString()).toList());
	}

	public static boolean mantleDrops(final net.minecraft.util.RandomSource random) {
		return random.nextDouble() < MANTLE_CHANCE;
	}

	/** What a fallen blaze left as Sovereign; nothing for any other. */
	public static List<net.minecraft.world.entity.item.ItemEntity> spoilsOf(final Entity entity) {
		return SPOILS.getOrDefault(entity.getUUID(), List.of());
	}

	/** Forgets a mode's risen Sovereign; the next run's rises afresh. Test hook and reset. */
	static void forget(final RaidMode mode) {
		// The raid's Sovereign only: a free one keeps its state across a raid reset.
		UUID current = CURRENT[mode.ordinal()];
		if (current != null) {
			SovereignSpecials.forget(current);
			SovereignCharge.forget(current);
		}
		CURRENT[mode.ordinal()] = null;
		HELD[mode.ordinal()] = null;
	}

	static boolean burns(final DamageSource source) {
		return isSovereign(source.getEntity());
	}
}
