package com.stella.nether;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared fixtures: a player that can change dimension, the vanilla portal's destination, the raid level, the
 * standing guardian a raid test enters by, the summary line.
 */
final class NetherTestSupport {
	/** Where the standing test guardian stands in the nether, far from any portal a test makes. */
	private static final int HUB_X = 3000;
	private static final int HUB_Z = 3000;

	private NetherTestSupport() {
	}

	/** A player joined through the player list, so a teleport to another dimension carries them. */
	static ServerPlayer join(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "raider"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	/**
	 * Lets a test player be hurt and hunted. A player with no client is still counted as changing dimension
	 * and as not yet loaded, and either makes them invulnerable; a real client clears both within moments.
	 */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	/**
	 * Starts a raid test from nothing: any player an earlier failing test left inside the raid or the nether is
	 * removed, both raids reset, and every guardian goes. The raids are shared, so a leftover raider would keep
	 * one open for every later test.
	 */
	static void fresh(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (ServerLevel level : new ServerLevel[] {server.getLevel(RaidTravel.RAID), server.getLevel(Level.NETHER)}) {
			if (level != null) {
				for (ServerPlayer leftover : List.copyOf(level.players())) {
					server.getPlayerList().remove(leftover);
				}
			}
		}
		RaidInstance.resetAll(server, "test");
		GuardianHub.clear(nether(helper));
	}

	static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Where a vanilla nether portal at a relative spot would send an entity standing in it now. */
	static @Nullable TeleportTransition portal(final ServerLevel level, final Entity entity, final BlockPos absolute) {
		return ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(level, entity, absolute);
	}

	static ServerLevel raid(final GameTestHelper helper) {
		ServerLevel raid = helper.getLevel().getServer().getLevel(RaidTravel.RAID);
		helper.assertTrue(raid != null, "the raid dimension is not loaded");
		return raid;
	}

	static ServerLevel nether(final GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the nether is not loaded");
		return nether;
	}

	static void result(final String test, final String outcome) {
		StellaNether.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}

	// -- Portals in the overworld ------------------------------------------------------------------------------

	/** A player standing in a nether portal block on obsidian, both placed with no neighbour update so they stand without a frame. */
	static ServerPlayer inPortal(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = join(helper, x, z);
		helper.getLevel().setBlock(player.blockPosition().below(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.getLevel().setBlock(player.blockPosition(), Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return player;
	}

	/** Steps a player into the portal it stands in, as the block does each tick an entity is inside it. */
	static void step(final GameTestHelper helper, final ServerPlayer player) {
		BlockPos at = player.blockPosition();
		BlockState state = helper.getLevel().getBlockState(at);
		state.entityInside(helper.getLevel(), at, player, net.minecraft.world.entity.InsideBlockEffectApplier.NOOP, true);
	}

	static void clearPortal(final GameTestHelper helper, final BlockPos portal) {
		helper.getLevel().setBlock(portal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** A player crosses the world's own nether portal from where they stand and arrives in the nether. */
	static void crossToNether(final GameTestHelper helper, final ServerPlayer player) {
		TeleportTransition transition = portal(helper.getLevel(), player, player.blockPosition());
		helper.assertTrue(transition != null && transition.newLevel().dimension().equals(Level.NETHER), "the overworld portal led to " + transition);
		player.teleport(transition);
	}

	// -- Guardians ---------------------------------------------------------------------------------------------

	/** Runs a body and returns every window the server sent while it ran. */
	static List<GuardianWindow.Open> capture(final Runnable body) {
		List<GuardianWindow.Open> sent = new ArrayList<>();
		var previous = GuardianHub.setSender((who, open) -> sent.add(open));
		try {
			body.run();
		} finally {
			GuardianHub.setSender(previous);
		}
		return sent;
	}

	/** The standing test guardian in the nether: the one already there, or a new one on the ground. */
	static BlazeGuardian hub(final GameTestHelper helper) {
		ServerLevel nether = nether(helper);
		BlockPos around = new BlockPos(HUB_X, 0, HUB_Z);
		BlazeSovereignGameTest.hold(nether, around);
		GuardianHub.Post post = GuardianHub.nearest(nether, Vec3.atBottomCenterOf(around), 2.0);
		if (post != null && nether.getEntity(post.id()) instanceof BlazeGuardian standing) {
			return standing;
		}
		return guardianAt(helper, HUB_X, HUB_Z, 0.0F);
	}

	/** A guardian on the nether's ground at a column, looking along a yaw. */
	static BlazeGuardian guardianAt(final GameTestHelper helper, final int x, final int z, final float yaw) {
		ServerLevel nether = nether(helper);
		BlazeSovereignGameTest.hold(nether, new BlockPos(x, 0, z));
		int floor = 0;
		for (int y = 120; y > nether.getMinY(); y--) {
			BlockPos at = new BlockPos(x, y, z);
			if (nether.getBlockState(at).isFaceSturdy(nether, at, net.minecraft.core.Direction.UP)) {
				floor = y + 1;
				break;
			}
		}
		BlazeGuardian guardian = GuardianHub.station(nether, new Vec3(x + 0.5, floor, z + 0.5), yaw);
		helper.assertTrue(guardian != null, "no guardian could stand at " + x + ", " + z);
		return guardian;
	}

	/** A new player in the nether in front of a guardian, as if they had walked up to it. */
	static ServerPlayer atGuardian(final GameTestHelper helper, final BlazeGuardian guardian) {
		ServerPlayer player = join(helper, 1.5, 1.5);
		Vec3 front = GuardianHub.frontOf(guardian);
		helper.assertTrue(front != null, "no free spot in front of the guardian at " + guardian.blockPosition());
		player.teleport(new TeleportTransition(nether(helper), front, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		return player;
	}

	/** The player uses the guardian as in play. Returns the windows the server sent. */
	static List<GuardianWindow.Open> use(final ServerPlayer player, final BlazeGuardian guardian) {
		return capture(() -> player.interactOn(guardian, InteractionHand.MAIN_HAND, Vec3.ZERO));
	}

	/** A player crosses into one mode's raid by the guardian: uses it, then picks the mode's option. */
	static ServerPlayer enterAt(final GameTestHelper helper, final ServerPlayer player, final BlazeGuardian guardian, final RaidMode mode) {
		List<GuardianWindow.Open> windows = use(player, guardian);
		helper.assertTrue(windows.size() == 1, "using the guardian sent " + windows.size() + " windows");
		boolean sent = GuardianHub.choose(player, GuardianWindow.Option.of(mode));
		helper.assertTrue(sent && player.level().dimension().equals(RaidTravel.RAID), "picking the " + mode + " raid sent=" + sent + " to " + player.level().dimension().identifier());
		return player;
	}

	/** A new player crosses into one mode's raid by the standing test guardian. */
	static ServerPlayer enter(final GameTestHelper helper, final RaidMode mode) {
		BlazeGuardian guardian = hub(helper);
		return enterAt(helper, atGuardian(helper, guardian), guardian, mode);
	}

	/** A player leaves the raid they stand in by its portal out, as a walk into it would. Returns the transition used. */
	static TeleportTransition leaveByPortal(final GameTestHelper helper, final ServerPlayer player, final RaidMode mode) {
		TeleportTransition back = portal((ServerLevel) player.level(), player, RaidInstance.exitPortal(helper.getLevel().getServer(), mode));
		helper.assertTrue(back != null, "the raid portal led nowhere");
		player.teleport(back);
		return back;
	}
}
