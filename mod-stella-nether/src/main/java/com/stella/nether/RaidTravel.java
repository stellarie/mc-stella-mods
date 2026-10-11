package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How players cross between the nether and the raid nether. A player enters by picking a raid in the window of
 * a Blaze Guardian, and every way out of the raid, its portal, a death, a log-in, leads back to the spot in
 * front of the guardian they used. The overworld's portals are the world's own.
 */
public final class RaidTravel {
	public static final ResourceKey<Level> RAID = ResourceKey.create(Registries.DIMENSION, StellaNether.id("raid"));
	public static final String COMMAND = "stellanether";
	/** The arrival hall around a run's origin: an obsidian floor with room above and a lit portal out at its north end. */
	private static final int HALL_HALF_WIDTH = 3;
	private static final int HALL_NORTH = -5;
	private static final int HALL_SOUTH = 3;
	private static final int HALL_HEIGHT = 5;

	/** Where a player goes back to: the guardian they used, and the spot in front of it. */
	public record Entry(UUID guardian, ResourceKey<Level> dimension, Vec3 position, float yaw) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("guardian").forGetter(Entry::guardian),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
			Vec3.CODEC.fieldOf("position").forGetter(Entry::position),
			Codec.FLOAT.fieldOf("yaw").forGetter(Entry::yaw)
		).apply(instance, Entry::new));
	}

	public static final AttachmentType<Entry> ENTRY = AttachmentRegistry.create(
		StellaNether.id("raid_entry"), builder -> builder.persistent(Entry.CODEC).copyOnDeath()
	);

	private RaidTravel() {
	}

	public static void register() {
		// A player who dies in the raid wakes in front of their guardian, not at a bed or the world's spawn.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && oldPlayer.level().dimension().equals(RAID) && newPlayer.level() instanceof ServerLevel level) {
				// The new player holds no entry when this runs, so it is read from the player who died.
				Entry entry = oldPlayer.getAttached(ENTRY);
				if (entry != null) {
					newPlayer.setAttached(ENTRY, entry);
				}
				// Vanilla points the connection at the new player only after the respawn returns, and a teleport moves the connection's.
				newPlayer.connection.player = newPlayer;
				newPlayer.teleport(wayBack(level, newPlayer));
				BehaviorLog.info("[NETHER] player={} RESPAWNED out of the raid back={}", newPlayer.getScoreboardName(), entry == null ? null : entry.position());
			}
		});
	}

	/**
	 * A player picks a mode at a guardian: they host that raid if none of its mode runs, or join the one that
	 * does, and cross into it. The spot in front of the guardian is kept for the way back. Returns whether they crossed.
	 */
	public static boolean enterRaid(final ServerPlayer player, final BlazeGuardian guardian, final RaidMode mode) {
		ServerLevel raid = player.level().getServer().getLevel(RAID);
		if (raid == null) {
			return false;
		}
		Entry entry = GuardianHub.entryFor(guardian);
		if (entry == null) {
			player.removeAttached(ENTRY);
		} else {
			player.setAttached(ENTRY, entry);
		}
		RaidInstance.enter(player, mode);
		buildHall(raid, mode);
		BehaviorLog.info("[NETHER] player={} ENTERS the {} raid from guardian={} back={}", player.getScoreboardName(), mode, guardian.blockPosition(),
			entry == null ? null : entry.position());
		player.teleport(new TeleportTransition(
			raid, Vec3.atBottomCenterOf(RaidInstance.arrival(player.level().getServer(), mode)), Vec3.ZERO, 180.0F, 0.0F,
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)
		));
		player.setPortalCooldown();
		return true;
	}

	/**
	 * What a nether portal inside the raid does: it leads a player back, and holds anything else (a null
	 * transition). A rider decides for its mount.
	 */
	public static @Nullable TeleportTransition leaveRaid(final ServerLevel raid, final Entity entity) {
		ServerPlayer player = entity instanceof ServerPlayer self ? self : rider(entity);
		return player == null ? null : wayBack(raid, player);
	}

	private static @Nullable ServerPlayer rider(final Entity entity) {
		for (Entity rider : entity.getIndirectPassengers()) {
			if (rider instanceof ServerPlayer player) {
				return player;
			}
		}
		return null;
	}

	/** Back to the spot in front of the guardian the player used; with none recorded, to their respawn point. */
	static TeleportTransition wayBack(final ServerLevel raid, final ServerPlayer player) {
		Entry entry = player.getAttached(ENTRY);
		ServerLevel home = entry == null ? null : raid.getServer().getLevel(entry.dimension());
		if (entry == null || home == null) {
			return TeleportTransition.createDefault(player, TeleportTransition.PLAY_PORTAL_SOUND);
		}
		return new TeleportTransition(home, entry.position(), Vec3.ZERO, entry.yaw(), 0.0F, TeleportTransition.PLAY_PORTAL_SOUND);
	}

	/** Carves one mode's arrival hall once: floor, air, a lit portal out, and any lava around it turned to blackstone. */
	static void buildHall(final ServerLevel raid, final RaidMode mode) {
		BlockPos arrival = RaidInstance.arrival(raid.getServer(), mode);
		BlockPos exit = RaidInstance.exitPortal(raid.getServer(), mode);
		if (raid.getBlockState(exit).is(Blocks.NETHER_PORTAL)) {
			return;
		}
		int floorY = arrival.getY() - 1;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -HALL_HALF_WIDTH - 1; x <= HALL_HALF_WIDTH + 1; x++) {
			for (int z = HALL_NORTH - 1; z <= HALL_SOUTH + 1; z++) {
				for (int y = floorY; y <= floorY + HALL_HEIGHT + 1; y++) {
					at.set(arrival.getX() + x, y, arrival.getZ() - 1 + z);
					boolean shell = Math.abs(x) > HALL_HALF_WIDTH || z < HALL_NORTH || z > HALL_SOUTH || y == floorY + HALL_HEIGHT + 1;
					if (shell) {
						if (!raid.getFluidState(at).isEmpty()) {
							raid.setBlock(at, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else {
						raid.setBlock(at, y == floorY ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		BlockState portalBlock = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		// The whole frame stands before any portal block, or the portal finds a gap and goes out.
		for (boolean inside : new boolean[] {false, true}) {
			for (int x = -1; x <= 2; x++) {
				for (int y = 0; y <= 4; y++) {
					boolean edge = x == -1 || x == 2 || y == 0 || y == 4;
					if (edge != inside) {
						at.set(exit.getX() + x, exit.getY() - 1 + y, exit.getZ());
						raid.setBlock(at, edge ? Blocks.OBSIDIAN.defaultBlockState() : portalBlock, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
		BehaviorLog.info("[NETHER] raid hall built mode={} at={}", mode, arrival);
	}
}
