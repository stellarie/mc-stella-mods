package com.stella.tower;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the tower's portals lead. From outside, a portal leads to the first floor and remembers where the
 * player stood; inside, a floor's portal leads to the floor above.
 * <p>
 * However a player comes in, by the portal, riding through it, or carried by an ender pearl, the way back
 * is recorded and a Void Key given: the portal writes the exact spot beside itself, and any other arrival
 * is recorded where the player stood before the jump.
 */
public final class TowerTravel {
	public static final ResourceKey<Level> TOWER = ResourceKey.create(Registries.DIMENSION, StellaTower.id("endless_tower"));
	/** How far out of the portal the entrance is remembered, so the Void Key never drops a player back in. */
	public static final double ENTRY_STEP = 1.5;

	/** Where a player came into the tower from. */
	public record Entry(ResourceKey<Level> dimension, Vec3 position, float yaw) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
			Vec3.CODEC.fieldOf("position").forGetter(Entry::position),
			Codec.FLOAT.fieldOf("yaw").forGetter(Entry::yaw)
		).apply(instance, Entry::new));
	}

	public static final AttachmentType<Entry> ENTRY = AttachmentRegistry.create(
		StellaTower.id("tower_entry"), builder -> builder.persistent(Entry.CODEC).copyOnDeath()
	);

	/** The floor a player last stood on in the tower; absent until their first visit. */
	public static final AttachmentType<Integer> LAST_FLOOR = AttachmentRegistry.create(
		StellaTower.id("last_floor"), builder -> builder.persistent(Codec.INT).copyOnDeath()
	);

	/** Players whose way back a portal has just written; their teleport keeps it. */
	private static final Set<UUID> RECORDED = ConcurrentHashMap.newKeySet();

	private TowerTravel() {
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (destination.dimension().equals(TOWER) && !origin.dimension().equals(TOWER)) {
				grantKey(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> RECORDED.clear());
		ServerTickEvents.END_SERVER_TICK.register(TowerTravel::rememberFloors);
	}

	private static void rememberFloors(final net.minecraft.server.MinecraftServer server) {
		if (server.getTickCount() % TowerProgress.CHECK_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TOWER);
		if (tower == null) {
			return;
		}
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
				remember(player, floor);
			}
		}
	}

	private static void remember(final ServerPlayer player, final int floor) {
		if (lastFloor(player) != floor) {
			player.setAttached(LAST_FLOOR, floor);
		}
	}

	/**
	 * Called as a player is about to jump into the tower from outside: unless a portal has just written the
	 * exact spot, the way back is where the player stands now.
	 */
	public static void beforeEntering(final ServerPlayer player) {
		if (!RECORDED.remove(player.getUUID())) {
			player.setAttached(ENTRY, new Entry(player.level().dimension(), player.position(), player.getYRot()));
		}
	}

	public static int lastFloor(final ServerPlayer player) {
		return player.getAttachedOrElse(LAST_FLOOR, 0);
	}

	/** Gives a Void Key unless the player holds one, or keeps one in the ender chest. */
	public static void grantKey(final ServerPlayer player) {
		boolean held = player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY))
			|| player.getEnderChestInventory().hasAnyMatching(stack -> stack.is(TowerItems.VOID_KEY));
		if (held) {
			return;
		}
		ItemStack key = new ItemStack(TowerItems.VOID_KEY);
		if (!player.getInventory().add(key)) {
			player.spawnAtLocation(player.level(), key);
		}
	}

	/**
	 * The floor a portal outside the tower leads to: the highest of the floor keys a player holds and the
	 * floors each traveller last stood on, else the first.
	 */
	public static int startFloor(final Entity entity) {
		int floor = 1;
		List<Entity> travellers = new java.util.ArrayList<>();
		travellers.add(entity);
		entity.getIndirectPassengers().forEach(travellers::add);
		// A rider's keys count as much as a walker's.
		for (Entity traveller : travellers) {
			if (traveller instanceof ServerPlayer player) {
				floor = Math.max(floor, lastFloor(player));
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					floor = Math.max(floor, FloorKeyItem.floorOf(player.getInventory().getItem(i)));
				}
			}
		}
		return floor;
	}

	public static @Nullable TeleportTransition destination(final ServerLevel from, final Entity entity, final BlockPos portal) {
		ServerLevel tower = from.getServer().getLevel(TOWER);
		if (tower == null) {
			return null;
		}
		if (from.dimension().equals(TOWER) ? TowerLayout.roomAt(portal) < 0 : LostRooms.isHutPortal(from, portal)) {
			return LostRooms.destination(from, entity, portal);
		}
		int floor;
		if (from.dimension().equals(TOWER)) {
			int here = TowerLayout.floorAt(portal);
			// Only players climb: a mob that follows one in stays on its own floor.
			boolean carriesPlayer = entity instanceof ServerPlayer
				|| entity.hasPassenger(rider -> rider instanceof ServerPlayer);
			if (here < 1 || !carriesPlayer) {
				return null;
			}
			floor = here + 1;
		} else {
			floor = startFloor(entity);
			Entry beside = entryBeside(from, entity, portal);
			if (entity instanceof ServerPlayer player) {
				record(player, beside);
			}
			for (Entity rider : entity.getIndirectPassengers()) {
				if (rider instanceof ServerPlayer player) {
					record(player, beside);
				}
			}
		}
		TowerFloors.ensure(tower, floor);
		int arrived = floor;
		TowerPlan plan = TowerPlan.at(tower, floor);
		return new TeleportTransition(
			tower, plan.arrivalPos(), Vec3.ZERO, plan.arrivalYaw(), 0.0F,
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET).then(moved -> arrive(moved, arrived))
		);
	}

	static void record(final ServerPlayer player, final Entry entry) {
		player.setAttached(ENTRY, entry);
		RECORDED.add(player.getUUID());
	}

	/** A spot one and a half blocks out of the portal, on the side the player came in from. */
	static Entry entryBeside(final ServerLevel level, final Entity entity, final BlockPos portal) {
		BlockState state = level.getBlockState(portal);
		Direction.Axis axis = state.hasProperty(NetherPortalBlock.AXIS) ? state.getValue(NetherPortalBlock.AXIS) : Direction.Axis.X;
		Vec3 center = Vec3.atBottomCenterOf(portal);
		Vec3 out;
		if (axis == Direction.Axis.X) {
			double side = Math.signum(entity.getZ() - center.z);
			out = center.add(0.0, 0.0, (side == 0.0 ? 1.0 : side) * ENTRY_STEP);
		} else {
			double side = Math.signum(entity.getX() - center.x);
			out = center.add((side == 0.0 ? 1.0 : side) * ENTRY_STEP, 0.0, 0.0);
		}
		return new Entry(level.dimension(), new Vec3(out.x, entity.getY(), out.z), entity.getYRot() + 180.0F);
	}

	private static void arrive(final Entity entity, final int floor) {
		if (entity.level() instanceof ServerLevel tower) {
			// The climb is built ahead of the climber.
			TowerFloors.prepare(tower, floor + 1);
		}
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		// A pulse may miss a player who leaves within a second.
		remember(player, floor);
		player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.floor", floor));
		StellaTower.LOGGER.debug("[TOWER] player={} ARRIVED floor={}", player.getScoreboardName(), floor);
	}
}
