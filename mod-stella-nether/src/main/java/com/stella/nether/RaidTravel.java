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
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetWindow(handler.player));
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forgetWindow(player));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forgetWindow(player);
			}
		});
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
		BehaviorLog.info("[NETHER] player={} CHOSE {} mode={}", player.getScoreboardName(), choice, mode);
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
			ask(player, portal);
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
		BehaviorLog.info("[NETHER] player={} ENTERS raid from={}", player.getScoreboardName(), beside.position());
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

	/** How far from the portal's plane a cancelled window sets the player down. */
	public static final double CANCEL_KNOCKBACK = 2.0;
	/** The players whose window is open, and the portal it was opened for; it is sent once, not again while open. */
	private static final Map<UUID, BlockPos> WINDOW_OPEN = new ConcurrentHashMap<>();
	private static java.util.function.Predicate<ServerPlayer> windowCapable =
		player -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, PortalWindow.Open.TYPE);
	private static java.util.function.BiConsumer<ServerPlayer, PortalWindow.Open> windowSender =
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking::send;

	/** Test seam: who can show the window, and how it is sent. Returns the pair it replaced. */
	static Object[] setWindowSeams(final java.util.function.Predicate<ServerPlayer> capable, final java.util.function.BiConsumer<ServerPlayer, PortalWindow.Open> sender) {
		Object[] previous = {windowCapable, windowSender};
		windowCapable = capable;
		windowSender = sender;
		return previous;
	}

	@SuppressWarnings("unchecked")
	static void restoreWindowSeams(final Object[] previous) {
		windowCapable = (java.util.function.Predicate<ServerPlayer>) previous[0];
		windowSender = (java.util.function.BiConsumer<ServerPlayer, PortalWindow.Open>) previous[1];
	}

	public static boolean windowOpen(final ServerPlayer player) {
		return WINDOW_OPEN.containsKey(player.getUUID());
	}

	static void forgetWindow(final ServerPlayer player) {
		WINDOW_OPEN.remove(player.getUUID());
	}

	/**
	 * A player stepping into an overworld nether portal is asked at once, not after the portal's wait. One just
	 * arrived through it, or who has chosen already, is left alone.
	 */
	public static void stepIn(final ServerLevel level, final Entity entity, final BlockPos portal) {
		if (entity instanceof ServerPlayer player && level.dimension().equals(Level.OVERWORLD) && !player.isSpectator()
			&& !player.isPassenger() && !player.isOnPortalCooldown() && pending(player) == null
			&& netherPortalInObsidian(level, portal)) {
			ask(player, portal);
		}
	}

	/**
	 * Whether a portal block is a nether portal standing on obsidian. Other portals built from the nether
	 * portal, such as the tower's in crying obsidian, never open the window.
	 */
	public static boolean netherPortalInObsidian(final ServerLevel level, final BlockPos portal) {
		if (!level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) {
			return false;
		}
		BlockPos.MutableBlockPos below = portal.mutable();
		do {
			below.move(Direction.DOWN);
		} while (level.getBlockState(below).is(Blocks.NETHER_PORTAL) && below.getY() > level.getMinY());
		return level.getBlockState(below).is(Blocks.OBSIDIAN);
	}

	private static void ask(final ServerPlayer player, final BlockPos portal) {
		RaidState state = RaidInstance.state(player.level().getServer());
		// A client with the mod gets the window, once, and it stays until a click or a cancel; any other, the chat prompt.
		if (windowCapable.test(player)) {
			if (WINDOW_OPEN.putIfAbsent(player.getUUID(), portal.immutable()) == null) {
				ASKED.put(player.getUUID(), player.level().getServer().getTickCount());
				windowSender.accept(player, PortalWindow.openFor(state));
				BehaviorLog.info("[NETHER] player={} ASKED by window", player.getScoreboardName());
			}
			return;
		}
		int now = player.level().getServer().getTickCount();
		Integer last = ASKED.get(player.getUUID());
		if (last != null && now - last < ASK_TICKS) {
			return;
		}
		ASKED.put(player.getUUID(), now);
		net.minecraft.network.chat.MutableComponent prompt = Component.translatable("message.mod-stella-nether.ask")
			.append(option("normal", "normal", ChatFormatting.RED)).append(" ");
		if (state.active()) {
			prompt.append(option("raid", "join." + state.mode().name().toLowerCase(java.util.Locale.ROOT), ChatFormatting.GOLD));
		} else {
			prompt.append(option("raid normal", "host.normal", ChatFormatting.GOLD)).append(" ")
				.append(option("raid hard", "host.hard", ChatFormatting.DARK_RED));
		}
		player.sendSystemMessage(prompt);
		BehaviorLog.info("[NETHER] player={} ASKED", player.getScoreboardName());
	}

	/**
	 * An option clicked in the window: the choice is made, and a player still standing in a nether portal is
	 * sent through it at once. An option closed by the running raid's mode is refused. Returns whether the
	 * player was sent.
	 */
	public static boolean chooseFromWindow(final ServerPlayer player, final PortalWindow.Option option) {
		BlockPos opened = WINDOW_OPEN.remove(player.getUUID());
		RaidState state = RaidInstance.state(player.level().getServer());
		if (!option.open(state.active() ? state.mode() : null)) {
			return false;
		}
		choose(player, option.choice(), option.mode());
		// The player may have slid off the block by now.
		BlockPos portal = opened != null && player.level().getBlockState(opened).is(Blocks.NETHER_PORTAL) ? opened : portalAt(player);
		if (portal == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		TeleportTransition transition = ((net.minecraft.world.level.block.Portal) net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
			.getPortalDestination(level, player, portal);
		if (transition == null) {
			return false;
		}
		player.teleport(transition);
		player.setPortalCooldown();
		BehaviorLog.info("[NETHER] player={} SENT by window option={}", player.getScoreboardName(), option);
		return true;
	}

	/**
	 * The window cancelled: the player is set down {@link #CANCEL_KNOCKBACK} blocks from the portal's plane, on
	 * the side they stand, or the other if that side is blocked, and thrown a little further. Returns whether
	 * they were moved.
	 */
	public static boolean cancelWindow(final ServerPlayer player) {
		BlockPos opened = WINDOW_OPEN.remove(player.getUUID());
		BlockPos portal = opened != null ? opened : portalAt(player);
		if (portal == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockState state = level.getBlockState(portal);
		net.minecraft.core.Direction.Axis axis = state.getOptionalValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS)
			.orElse(net.minecraft.core.Direction.Axis.X);
		// The portal spans its axis, so it is left across the other one.
		boolean alongZ = axis == net.minecraft.core.Direction.Axis.X;
		double plane = alongZ ? portal.getZ() + 0.5 : portal.getX() + 0.5;
		double offset = (alongZ ? player.getZ() : player.getX()) - plane;
		double side = Math.abs(offset) > 1.0E-3 ? Math.signum(offset) : -Math.signum(alongZ ? player.getLookAngle().z : player.getLookAngle().x);
		if (side == 0.0) {
			side = 1.0;
		}
		for (double tried : new double[] {side, -side}) {
			double x = alongZ ? player.getX() : plane + tried * CANCEL_KNOCKBACK;
			double z = alongZ ? plane + tried * CANCEL_KNOCKBACK : player.getZ();
			Vec3 to = new Vec3(x, player.getY(), z);
			if (level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position())))) {
				player.teleportTo(level, to.x, to.y, to.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
				player.setDeltaMovement(alongZ ? 0.0 : tried * 0.3, 0.25, alongZ ? tried * 0.3 : 0.0);
				player.needsSync = true;
				level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
				BehaviorLog.info("[NETHER] player={} CANCELLED the window, set down at {}", player.getScoreboardName(), to);
				return true;
			}
		}
		return false;
	}

	/** The nether portal block an entity stands in, if any. */
	static @Nullable BlockPos portalAt(final Entity entity) {
		return BlockPos.betweenClosedStream(entity.getBoundingBox().inflate(0.5))
			.filter(at -> entity.level().getBlockState(at).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL))
			.findFirst().map(BlockPos::immutable).orElse(null);
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
		BehaviorLog.info("[NETHER] raid hall built at={}", arrival);
	}
}
