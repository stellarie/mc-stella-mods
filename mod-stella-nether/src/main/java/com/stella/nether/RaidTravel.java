package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
 * Where nether portals lead. Outside, a player picks the normal nether or the raid nether by a clickable
 * prompt and is held in the portal until they do; a choice lasts one crossing or {@link #CHOICE_TICKS}.
 * Inside the raid nether, every nether portal leads back beside the portal the player came in by.
 */
public final class RaidTravel {
	public static final ResourceKey<Level> RAID = ResourceKey.create(Registries.DIMENSION, StellaNether.id("raid"));
	public static final int CHOICE_TICKS = 600;
	/** How often a player held in a portal is asked again. */
	public static final int ASK_TICKS = 100;
	/** How far out of the portal the entrance is remembered, so the way back never lands in it. */
	public static final double ENTRY_STEP = 1.5;
	public static final String COMMAND = "stellanether";
	/** The arrival hall around a run's origin: an obsidian floor with room above and a lit portal out at its north end. */
	private static final int HALL_HALF_WIDTH = 3;
	private static final int HALL_NORTH = -5;
	private static final int HALL_SOUTH = 3;
	private static final int HALL_HEIGHT = 5;

	public enum Choice { NORMAL, RAID }

	private record Pending(Choice choice, RaidMode mode, int until) {
	}

	/** A portal's verdict: undecided leaves the crossing to vanilla; decided replaces it, a null transition holding the entity. */
	public record Route(boolean decided, @Nullable TeleportTransition transition) {
		static final Route VANILLA = new Route(false, null);
		static final Route HOLD = new Route(true, null);
	}

	/** Where a player came into the raid nether from. */
	public record Entry(ResourceKey<Level> dimension, Vec3 position, float yaw) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
			Vec3.CODEC.fieldOf("position").forGetter(Entry::position),
			Codec.FLOAT.fieldOf("yaw").forGetter(Entry::yaw)
		).apply(instance, Entry::new));
	}

	public static final AttachmentType<Entry> ENTRY = AttachmentRegistry.create(
		StellaNether.id("raid_entry"), builder -> builder.persistent(Entry.CODEC).copyOnDeath()
	);

	private static final Map<UUID, Pending> CHOICES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> ASKED = new ConcurrentHashMap<>();

	private RaidTravel() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> dispatcher.register(
			Commands.literal(COMMAND).then(Commands.literal("portal")
				.then(Commands.literal("normal").executes(context -> chooseByCommand(context.getSource().getPlayerOrException(), Choice.NORMAL)))
				.then(Commands.literal("raid").executes(context -> chooseByCommand(context.getSource().getPlayerOrException(), Choice.RAID, RaidMode.NORMAL))
					.then(Commands.literal("normal").executes(context -> chooseByCommand(context.getSource().getPlayerOrException(), Choice.RAID, RaidMode.NORMAL)))
					.then(Commands.literal("hard").executes(context -> chooseByCommand(context.getSource().getPlayerOrException(), Choice.RAID, RaidMode.HARD)))))
		));
	}

	private static int chooseByCommand(final ServerPlayer player, final Choice choice) {
		return chooseByCommand(player, choice, RaidMode.NORMAL);
	}

	private static int chooseByCommand(final ServerPlayer player, final Choice choice, final RaidMode mode) {
		choose(player, choice, mode);
		player.sendSystemMessage(Component.translatable("message.mod-stella-nether.chosen." + key(choice)));
		return 1;
	}

	/** A raid choice carries the mode the player would host with; joining a running raid ignores it. */
	public static void choose(final ServerPlayer player, final Choice choice, final RaidMode mode) {
		CHOICES.put(player.getUUID(), new Pending(choice, mode, player.level().getServer().getTickCount() + CHOICE_TICKS));
		StellaNether.LOGGER.debug("[NETHER] player={} CHOSE {} mode={}", player.getScoreboardName(), choice, mode);
	}

	public static void choose(final ServerPlayer player, final Choice choice) {
		choose(player, choice, RaidMode.NORMAL);
	}

	public static @Nullable Choice pendingChoice(final ServerPlayer player) {
		Pending pending = pending(player);
		return pending == null ? null : pending.choice();
	}

	private static @Nullable Pending pending(final ServerPlayer player) {
		Pending pending = CHOICES.get(player.getUUID());
		if (pending == null) {
			return null;
		}
		if (player.level().getServer().getTickCount() > pending.until()) {
			CHOICES.remove(player.getUUID(), pending);
			return null;
		}
		return pending;
	}

	public static boolean wasAsked(final ServerPlayer player) {
		return ASKED.containsKey(player.getUUID());
	}

	public static Route route(final ServerLevel from, final Entity entity, final BlockPos portal) {
		ServerPlayer player = entity instanceof ServerPlayer self ? self : rider(entity);
		if (from.dimension().equals(RAID)) {
			return player == null ? Route.HOLD : new Route(true, wayBack(from, player));
		}
		if (player == null || !from.dimension().equals(Level.OVERWORLD)) {
			return Route.VANILLA;
		}
		Pending pending = pending(player);
		if (pending == null) {
			ask(player);
			return Route.HOLD;
		}
		CHOICES.remove(player.getUUID());
		if (pending.choice() == Choice.NORMAL) {
			return Route.VANILLA;
		}
		ServerLevel raid = from.getServer().getLevel(RAID);
		if (raid == null) {
			return Route.HOLD;
		}
		Entry beside = entryBeside(from, entity, portal);
		player.setAttached(ENTRY, beside);
		entity.getIndirectPassengers().forEach(rider -> {
			if (rider instanceof ServerPlayer other) {
				other.setAttached(ENTRY, beside);
			}
		});
		RaidInstance.enter(player, pending.mode());
		buildHall(raid);
		StellaNether.LOGGER.debug("[NETHER] player={} ENTERS raid from={}", player.getScoreboardName(), beside.position());
		return new Route(true, new TeleportTransition(
			raid, Vec3.atBottomCenterOf(RaidInstance.arrival(from.getServer())), Vec3.ZERO, 180.0F, 0.0F,
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)
		));
	}

	private static @Nullable ServerPlayer rider(final Entity entity) {
		for (Entity rider : entity.getIndirectPassengers()) {
			if (rider instanceof ServerPlayer player) {
				return player;
			}
		}
		return null;
	}

	private static void ask(final ServerPlayer player) {
		int now = player.level().getServer().getTickCount();
		Integer last = ASKED.get(player.getUUID());
		if (last != null && now - last < ASK_TICKS) {
			return;
		}
		ASKED.put(player.getUUID(), now);
		RaidState state = RaidInstance.state(player.level().getServer());
		net.minecraft.network.chat.MutableComponent prompt = Component.translatable("message.mod-stella-nether.ask")
			.append(option("normal", "normal", ChatFormatting.RED)).append(" ");
		if (state.active()) {
			prompt.append(option("raid", "join." + state.mode().name().toLowerCase(java.util.Locale.ROOT), ChatFormatting.GOLD));
		} else {
			prompt.append(option("raid normal", "host.normal", ChatFormatting.GOLD)).append(" ")
				.append(option("raid hard", "host.hard", ChatFormatting.DARK_RED));
		}
		player.sendSystemMessage(prompt);
		StellaNether.LOGGER.debug("[NETHER] player={} ASKED", player.getScoreboardName());
	}

	private static Component option(final String command, final String label, final ChatFormatting colour) {
		return Component.translatable("message.mod-stella-nether." + label).withStyle(style -> style
			.withColor(colour)
			.withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " portal " + command))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.mod-stella-nether." + label + ".hover"))));
	}

	private static String key(final Choice choice) {
		return choice == Choice.RAID ? "raid" : "normal";
	}

	static TeleportTransition wayBack(final ServerLevel raid, final ServerPlayer player) {
		Entry entry = player.getAttached(ENTRY);
		ServerLevel home = entry == null ? null : raid.getServer().getLevel(entry.dimension());
		if (entry == null || home == null) {
			return TeleportTransition.createDefault(player, TeleportTransition.PLAY_PORTAL_SOUND);
		}
		return new TeleportTransition(home, entry.position(), Vec3.ZERO, entry.yaw(), 0.0F, TeleportTransition.PLAY_PORTAL_SOUND);
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

	/** Carves the arrival hall once: floor, air, a lit portal out, and any lava around it turned to blackstone. */
	static void buildHall(final ServerLevel raid) {
		BlockPos arrival = RaidInstance.arrival(raid.getServer());
		BlockPos exit = RaidInstance.exitPortal(raid.getServer());
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
		StellaNether.LOGGER.debug("[NETHER] raid hall built at={}", arrival);
	}
}
