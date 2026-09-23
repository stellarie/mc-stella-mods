package com.stella.equips;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Records the packets the server sends to one player. It replaces the transport, not the send call,
 * so the mod still reaches the connection it always uses.
 */
public final class PacketRecorder extends ServerGamePacketListenerImpl {
	private final List<Packet<?>> sent = Collections.synchronizedList(new ArrayList<>());

	private PacketRecorder(
		final MinecraftServer server,
		final Connection connection,
		final ServerPlayer player,
		final CommonListenerCookie cookie
	) {
		super(server, connection, player, cookie);
	}

	public static PacketRecorder attach(final ServerPlayer player) {
		CommonListenerCookie cookie = new CommonListenerCookie(
			new GameProfile(player.getUUID(), player.getScoreboardName()),
			0,
			player.clientInformation(),
			false
		);
		PacketRecorder recorder = new PacketRecorder(
			player.level().getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie
		);
		player.connection = recorder;
		return recorder;
	}

	public static PacketRecorder of(final ServerPlayer player) {
		if (player.connection instanceof PacketRecorder existing) {
			return existing;
		}

		return attach(player);
	}

	@Override
	public void send(final Packet<?> packet, final ChannelFutureListener listener) {
		this.sent.add(packet);
	}

	/**
	 * `ServerPlayer.isInvulnerableTo` reads this, so a mock player without a real
	 * client counts as loaded here. Otherwise every hit on it is negated.
	 */
	@Override
	public boolean hasClientLoaded() {
		return true;
	}

	public int count() {
		return this.sent.size();
	}

	public List<Packet<?>> snapshot() {
		synchronized (this.sent) {
			return List.copyOf(this.sent);
		}
	}

	public int countMatching(final Predicate<Packet<?>> filter) {
		int found = 0;

		for (Packet<?> packet : this.snapshot()) {
			if (filter.test(packet)) {
				found++;
			}
		}

		return found;
	}

	public int addPackets(final int entityId) {
		return countMatching(packet -> packet instanceof ClientboundAddEntityPacket add && add.getId() == entityId);
	}

	public int dataPackets(final int entityId) {
		return countMatching(packet -> packet instanceof ClientboundSetEntityDataPacket data && data.id() == entityId);
	}

	public int removePackets(final int entityId) {
		return countMatching(
			packet -> packet instanceof ClientboundRemoveEntitiesPacket remove
				&& remove.entityIds().contains(entityId)
		);
	}

	public int teleportPackets(final int entityId) {
		return countMatching(
			packet -> packet instanceof ClientboundTeleportEntityPacket teleport && teleport.id() == entityId
		);
	}

	public int entityPackets(final Set<Integer> entityIds) {
		return countMatching(packet -> {
			if (packet instanceof ClientboundAddEntityPacket add) {
				return entityIds.contains(add.getId());
			}

			if (packet instanceof ClientboundSetEntityDataPacket data) {
				return entityIds.contains(data.id());
			}

			if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
				for (int id : remove.entityIds()) {
					if (entityIds.contains(id)) {
						return true;
					}
				}
			}

			return false;
		});
	}

	public Set<Integer> entityIdsSeen() {
		Set<Integer> seen = new LinkedHashSet<>();

		for (Packet<?> packet : this.snapshot()) {
			if (packet instanceof ClientboundAddEntityPacket add) {
				seen.add(add.getId());
			} else if (packet instanceof ClientboundSetEntityDataPacket data) {
				seen.add(data.id());
			} else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
				for (int id : remove.entityIds()) {
					seen.add(id);
				}
			}
		}

		return seen;
	}
}
