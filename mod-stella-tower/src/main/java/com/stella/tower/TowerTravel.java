package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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

	private TowerTravel() {
	}

	public static void register() {
		StellaTower.LOGGER.debug("Registered {}", ENTRY.identifier());
	}

	/** The floor a portal outside the tower leads to. Floor keys will raise it. */
	public static int startFloor(final Entity entity) {
		return 1;
	}

	public static @Nullable TeleportTransition destination(final ServerLevel from, final Entity entity, final BlockPos portal) {
		ServerLevel tower = from.getServer().getLevel(TOWER);
		if (tower == null) {
			return null;
		}
		int floor;
		if (from.dimension().equals(TOWER)) {
			int here = TowerLayout.floorAt(portal);
			if (here < 1) {
				return null;
			}
			floor = here + 1;
		} else {
			floor = startFloor(entity);
			if (entity instanceof ServerPlayer player) {
				player.setAttached(ENTRY, entryBeside(from, entity, portal));
			}
		}
		TowerFloors.ensure(tower, floor);
		int arrived = floor;
		return new TeleportTransition(
			tower, TowerLayout.arrival(floor), Vec3.ZERO, TowerLayout.arrivalYaw(), 0.0F,
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET).then(moved -> arrive(moved, arrived))
		);
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
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		if (!player.getInventory().contains(stack -> stack.is(TowerItems.VOID_KEY))) {
			ItemStack key = new ItemStack(TowerItems.VOID_KEY);
			if (!player.getInventory().add(key)) {
				player.spawnAtLocation(player.level(), key);
			}
		}
		player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.floor", floor));
		StellaTower.LOGGER.debug("[TOWER] player={} ARRIVED floor={}", player.getScoreboardName(), floor);
	}
}
