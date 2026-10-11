package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the Blaze Guardians stand and what they do for the nether: one beside the nether side of a portal a
 * player arrives by, a record of where each stands, the quiet they keep around themselves, and the raid
 * window they open for the player who uses one.
 */
public final class GuardianHub {
	/** A guardian's place in a level: who it is and where it stands. */
	public record Post(UUID id, BlockPos pos) {
		public static final Codec<Post> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Post::id),
			BlockPos.CODEC.fieldOf("pos").forGetter(Post::pos)
		).apply(instance, Post::new));
	}

	/** Every guardian's post in a nether level, kept with the level so it survives a restart. */
	private static final AttachmentType<List<Post>> POSTS = AttachmentRegistry.createPersistent(StellaNether.id("guardian_posts"), Post.CODEC.listOf());

	/** No hostile mob spawns this near a guardian. */
	public static final double NO_SPAWN_RANGE = 16.0;
	/** A portal with a guardian this near gets no second one. */
	public static final double SPACING = 24.0;
	/** How far from a player's arrival a nether portal block is looked for, and how far up and down. */
	public static final int PORTAL_REACH = 8;
	public static final int PORTAL_REACH_VERTICAL = 4;
	/** A player may use a guardian, and pick from its window, within this many blocks of it. */
	public static final double USE_RANGE = 8.0;
	/** Blocks past the end of the portal a guardian stands, tried in turn, and blocks out of its plane. */
	private static final int[] BESIDE = {3, 2};
	private static final int[] OUT = {1, 2, 0};
	/** Where a player is set down: this far from the guardian, tried in turn, and the turns off its facing. */
	private static final double[] FRONT_RADII = {1.5, 2.0, 2.5, 3.0};
	private static final float[] FRONT_TURNS = {0.0F, 45.0F, -45.0F, 90.0F, -90.0F};
	/** The room a guardian needs, a blaze at its scale, and the room a player needs. */
	private static final double GUARDIAN_WIDTH = 0.6 * BlazeGuardian.SCALE;
	private static final double GUARDIAN_HEIGHT = 1.8 * BlazeGuardian.SCALE;
	private static final double PLAYER_WIDTH = 0.6;
	private static final double PLAYER_HEIGHT = 1.8;

	/** The players with the window open, and the guardian each one used. */
	private static final Map<UUID, UUID> WINDOWS = new ConcurrentHashMap<>();
	private static BiConsumer<ServerPlayer, GuardianWindow.Open> sender = ServerPlayNetworking::send;

	private GuardianHub() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(BlazeGuardian.TYPE, BlazeGuardian.createGuardianAttributes());
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (destination.dimension().equals(Level.NETHER) && !origin.dimension().equals(Level.NETHER)) {
				arrived(player, destination);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WINDOWS.remove(handler.player.getUUID()));
	}

	/** Test seam: how the window reaches a player. Returns the sender it replaced. */
	static BiConsumer<ServerPlayer, GuardianWindow.Open> setSender(final BiConsumer<ServerPlayer, GuardianWindow.Open> next) {
		BiConsumer<ServerPlayer, GuardianWindow.Open> previous = sender;
		sender = next;
		return previous;
	}

	// -- Placement ---------------------------------------------------------------------------------------------

	/** A player has come into the nether: a guardian stands beside the portal they came out of, if one is near. */
	static @Nullable BlazeGuardian arrived(final ServerPlayer player, final ServerLevel nether) {
		BlockPos portal = portalNear(nether, player.blockPosition());
		return portal == null ? null : ensureBeside(nether, portal, player.position());
	}

	/** The guardian beside a portal: the one that stands within {@link #SPACING}, or a new one. */
	static @Nullable BlazeGuardian ensureBeside(final ServerLevel nether, final BlockPos portal, final Vec3 from) {
		Post near = nearest(nether, Vec3.atCenterOf(portal), SPACING);
		if (near != null) {
			return nether.getEntity(near.id()) instanceof BlazeGuardian guardian ? guardian : null;
		}
		return place(nether, portal, from);
	}

	/** The nearest nether portal block to a spot, within reach, or none. */
	static @Nullable BlockPos portalNear(final ServerLevel level, final BlockPos around) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(
			around.offset(-PORTAL_REACH, -PORTAL_REACH_VERTICAL, -PORTAL_REACH), around.offset(PORTAL_REACH, PORTAL_REACH_VERTICAL, PORTAL_REACH))) {
			if (level.getBlockState(at).is(Blocks.NETHER_PORTAL)) {
				double distance = at.distSqr(around);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = at.immutable();
				}
			}
		}
		return best;
	}

	/** Raises a guardian beside a portal, facing it, and records its post. Null where no ground holds one. */
	static @Nullable BlazeGuardian place(final ServerLevel nether, final BlockPos portal, final Vec3 from) {
		Vec3 spot = spotBeside(nether, portal, from);
		if (spot == null) {
			BehaviorLog.info("[NETHER] guardian has no ground beside portal={}", portal);
			return null;
		}
		BlazeGuardian guardian = station(nether, spot, yawToward(spot, portalCentre(nether, portal)));
		if (guardian != null) {
			BehaviorLog.info("[NETHER] guardian placed at={} beside portal={}", guardian.blockPosition(), portal);
		}
		return guardian;
	}

	/** Stands a guardian at a spot, facing a yaw, and records its post. Null where it could not be added. */
	static @Nullable BlazeGuardian station(final ServerLevel nether, final Vec3 spot, final float yaw) {
		BlazeGuardian guardian = BlazeGuardian.create(nether);
		guardian.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		guardian.setYHeadRot(yaw);
		guardian.setYBodyRot(yaw);
		// Its chunk is loaded before it is added, or the entity waits on a load that may not come.
		nether.getChunk(guardian.blockPosition());
		if (!nether.addFreshEntity(guardian)) {
			return null;
		}
		remember(nether, new Post(guardian.getUUID(), guardian.blockPosition()));
		return guardian;
	}

	/** The middle of a portal's blocks, at the height of the block asked about. */
	private static Vec3 portalCentre(final ServerLevel level, final BlockPos portal) {
		Direction along = alongOf(level, portal);
		int[] extent = extent(level, portal, along);
		double mid = (extent[0] + extent[1]) / 2.0;
		return Vec3.atCenterOf(portal).add(along.getStepX() * mid, 0.0, along.getStepZ() * mid);
	}

	private static Direction alongOf(final ServerLevel level, final BlockPos portal) {
		Direction.Axis axis = level.getBlockState(portal).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
		return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
	}

	/** How far the portal's blocks run each way from one of them, along its plane: {low, high}. */
	private static int[] extent(final ServerLevel level, final BlockPos portal, final Direction along) {
		int low = 0;
		int high = 0;
		while (high < 4 && level.getBlockState(portal.relative(along, high + 1)).is(Blocks.NETHER_PORTAL)) {
			high++;
		}
		while (low > -4 && level.getBlockState(portal.relative(along, low - 1)).is(Blocks.NETHER_PORTAL)) {
			low--;
		}
		return new int[] {low, high};
	}

	/**
	 * Ground two to three blocks past either end of the portal and a block or two out of its plane, on the side
	 * the player came out; the end nearer the player comes first. The spot is the guardian's feet.
	 */
	static @Nullable Vec3 spotBeside(final ServerLevel level, final BlockPos portal, final Vec3 from) {
		Direction along = alongOf(level, portal);
		Direction across = along.getClockWise();
		int[] extent = extent(level, portal, along);
		Vec3 centre = portalCentre(level, portal);
		double fromAlong = (from.x - centre.x) * along.getStepX() + (from.z - centre.z) * along.getStepZ();
		double fromAcross = (from.x - centre.x) * across.getStepX() + (from.z - centre.z) * across.getStepZ();
		int outward = fromAcross < 0.0 ? -1 : 1;
		int nearer = fromAlong < 0.0 ? -1 : 1;
		for (int end : new int[] {nearer, -nearer}) {
			for (int beside : BESIDE) {
				for (int out : OUT) {
					int offset = end > 0 ? extent[1] + beside : extent[0] - beside;
					BlockPos column = portal.relative(along, offset).relative(across, out * outward);
					Vec3 spot = groundAt(level, column.getX() + 0.5, column.getZ() + 0.5, portal.getY(), GUARDIAN_WIDTH, GUARDIAN_HEIGHT);
					if (spot != null) {
						return spot;
					}
				}
			}
		}
		// Any other free ground within four blocks of the portal, nearest first.
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				BlockPos column = portal.offset(dx, 0, dz);
				Vec3 spot = groundAt(level, column.getX() + 0.5, column.getZ() + 0.5, portal.getY(), GUARDIAN_WIDTH, GUARDIAN_HEIGHT);
				double distance = spot == null ? Double.MAX_VALUE : spot.distanceTo(centre);
				if (spot != null && distance >= 2.0 && distance <= 4.0 && distance < bestDistance) {
					bestDistance = distance;
					best = spot;
				}
			}
		}
		return best;
	}

	/** The feet of something of a size standing on the ground at a spot near a height: the first floor with room above. */
	private static @Nullable Vec3 groundAt(final ServerLevel level, final double x, final double z, final int height, final double width, final double tall) {
		for (int y = height + 2; y >= height - 3; y--) {
			BlockPos below = BlockPos.containing(x, y - 1, z);
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			AABB box = new AABB(x - width / 2.0, y, z - width / 2.0, x + width / 2.0, y + tall, z + width / 2.0);
			if (level.noCollision(box) && !level.containsAnyLiquid(box)) {
				return new Vec3(x, y, z);
			}
		}
		return null;
	}

	/** The yaw that turns something at a spot to look at another, flat. */
	static float yawToward(final Vec3 from, final Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
	}

	// -- The spot in front -------------------------------------------------------------------------------------

	/**
	 * Where a player stands in front of a guardian: along the way it faces, a step and a half out, then further
	 * out and a little to either side where that is blocked. Null where nothing is free.
	 */
	static @Nullable Vec3 frontOf(final BlazeGuardian guardian) {
		if (!(guardian.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, guardian.getYRot());
		for (double radius : FRONT_RADII) {
			for (float turn : FRONT_TURNS) {
				Vec3 around = guardian.position().add(forward.yRot(turn * Mth.DEG_TO_RAD).scale(radius));
				Vec3 spot = groundAt(level, around.x, around.z, guardian.getBlockY(), PLAYER_WIDTH, PLAYER_HEIGHT);
				if (spot != null) {
					return spot;
				}
			}
		}
		return null;
	}

	/** The way back to a guardian: its level, the spot in front of it, and the yaw that faces it. Null where no spot is free. */
	static RaidTravel.@Nullable Entry entryFor(final BlazeGuardian guardian) {
		Vec3 spot = frontOf(guardian);
		if (spot == null) {
			return null;
		}
		return new RaidTravel.Entry(guardian.getUUID(), guardian.level().dimension(), spot, yawToward(spot, guardian.position()));
	}

	// -- The record --------------------------------------------------------------------------------------------

	private static List<Post> posts(final ServerLevel level) {
		List<Post> posts = ((AttachmentTarget) level).getAttached(POSTS);
		return posts == null ? List.of() : posts;
	}

	private static void remember(final ServerLevel level, final Post post) {
		List<Post> all = new ArrayList<>(posts(level));
		all.add(post);
		((AttachmentTarget) level).setAttached(POSTS, List.copyOf(all));
	}

	static void forget(final ServerLevel level, final UUID id) {
		List<Post> all = new ArrayList<>(posts(level));
		if (all.removeIf(post -> post.id().equals(id))) {
			((AttachmentTarget) level).setAttached(POSTS, List.copyOf(all));
		}
	}

	/** The post nearest a spot within a range, or none. */
	public static @Nullable Post nearest(final ServerLevel level, final Vec3 spot, final double range) {
		Post best = null;
		double bestDistance = range * range;
		for (Post post : posts(level)) {
			double distance = Vec3.atBottomCenterOf(post.pos()).distanceToSqr(spot);
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = post;
			}
		}
		return best;
	}

	public static List<Post> postsIn(final ServerLevel level) {
		return posts(level);
	}

	/** Test hook: takes every guardian away and forgets every post in a level. */
	static void clear(final ServerLevel level) {
		List<Entity> loaded = new ArrayList<>();
		level.getAllEntities().forEach(entity -> {
			if (entity instanceof BlazeGuardian) {
				loaded.add(entity);
			}
		});
		loaded.forEach(Entity::discard);
		((AttachmentTarget) level).removeAttached(POSTS);
	}

	// -- The quiet ---------------------------------------------------------------------------------------------

	/** Whether a hostile mob's natural, world-generation, patrol or reinforcement spawn at a spot is refused. */
	public static boolean blocksSpawn(final ServerLevelAccessor accessor, final EntityType<?> type, final EntitySpawnReason reason, final BlockPos at) {
		boolean kind = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
			|| reason == EntitySpawnReason.PATROL || reason == EntitySpawnReason.REINFORCEMENT;
		if (!kind || type.getCategory() != MobCategory.MONSTER) {
			return false;
		}
		return nearest(accessor.getLevel(), Vec3.atCenterOf(at), NO_SPAWN_RANGE) != null;
	}

	// -- The window --------------------------------------------------------------------------------------------

	/** A player used a guardian: the raid window opens on their client. */
	public static void open(final ServerPlayer player, final BlazeGuardian guardian) {
		WINDOWS.put(player.getUUID(), guardian.getUUID());
		sender.accept(player, GuardianWindow.openFor(player.level().getServer()));
		BehaviorLog.info("[NETHER] player={} OPENED the raid window at guardian={}", player.getScoreboardName(), guardian.blockPosition());
	}

	public static boolean windowOpen(final ServerPlayer player) {
		return WINDOWS.containsKey(player.getUUID());
	}

	/** The window closed without a pick: nothing happens but the window is spent. Returns whether one was open. */
	public static boolean cancel(final ServerPlayer player) {
		boolean open = WINDOWS.remove(player.getUUID()) != null;
		if (open) {
			BehaviorLog.info("[NETHER] player={} CANCELLED the raid window", player.getScoreboardName());
		}
		return open;
	}

	/**
	 * An option picked: the player's open window is spent, and if they still stand near the guardian they used,
	 * they enter that mode's raid. Returns whether they were sent.
	 */
	public static boolean choose(final ServerPlayer player, final GuardianWindow.Option option) {
		UUID used = WINDOWS.remove(player.getUUID());
		if (used == null || !(player.level() instanceof ServerLevel level) || !(level.getEntity(used) instanceof BlazeGuardian guardian)
			|| !guardian.isAlive() || player.distanceToSqr(guardian) > USE_RANGE * USE_RANGE) {
			BehaviorLog.info("[NETHER] player={} PICKED {} with no guardian in reach; refused", player.getScoreboardName(), option);
			return false;
		}
		return RaidTravel.enterRaid(player, guardian, option.mode());
	}
}
