package com.stella.rpg;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The per-player auto-pickup toggle. With it off, walking over an item entity leaves it on the ground, and sneaking
 * picks it up. Experience orbs are not affected. The value is saved, kept on death, and synced to its own player.
 */
public final class AutoPickup {
	public static final AttachmentType<Boolean> TYPE = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(StellaRpg.id("auto_pickup"));

	/** A request to set the toggle. */
	public record TogglePayload(boolean on) implements CustomPacketPayload {
		public static final Type<TogglePayload> PAYLOAD_TYPE = new Type<>(StellaRpg.id("auto_pickup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, TogglePayload::on,
			TogglePayload::new
		);

		@Override
		public Type<TogglePayload> type() {
			return PAYLOAD_TYPE;
		}
	}

	private AutoPickup() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TogglePayload.PAYLOAD_TYPE, TogglePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TogglePayload.PAYLOAD_TYPE, (payload, context) -> set(context.player(), payload.on()));
	}

	public static boolean isOn(final Player player) {
		return player.getAttachedOrElse(TYPE, true);
	}

	public static void set(final ServerPlayer player, final boolean on) {
		player.setAttached(TYPE, on);
	}

	public static boolean allows(final Player player) {
		return isOn(player) || player.isShiftKeyDown();
	}
}
