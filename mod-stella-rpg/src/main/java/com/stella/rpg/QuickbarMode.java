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
 * The per-player hotbar mode. Quick bar mode (the default) keeps the weapon slot in hand and turns hotkeys into
 * actions. Vanilla mode is the vanilla hotbar, and index 35 is an ordinary slot. Saved, kept on death, and synced.
 */
public final class QuickbarMode {
	public static final AttachmentType<Boolean> TYPE = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(StellaRpg.id("quickbar_mode"));

	/** A request to set the mode: true for quick bar. */
	public record ModePayload(boolean quickbar) implements CustomPacketPayload {
		public static final Type<ModePayload> PAYLOAD_TYPE = new Type<>(StellaRpg.id("quickbar_mode"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ModePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ModePayload::quickbar,
			ModePayload::new
		);

		@Override
		public Type<ModePayload> type() {
			return PAYLOAD_TYPE;
		}
	}

	private QuickbarMode() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ModePayload.PAYLOAD_TYPE, ModePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ModePayload.PAYLOAD_TYPE, (payload, context) -> set(context.player(), payload.quickbar()));
	}

	public static boolean isOn(final Player player) {
		return player.getAttachedOrElse(TYPE, true);
	}

	/** Vanilla mode takes slot 0 in hand; quick bar mode takes the weapon. No item moves. */
	public static void set(final ServerPlayer player, final boolean quickbar) {
		WeaponSlot.endEating(player);
		player.setAttached(TYPE, quickbar);
		WeaponSlot.select(player, quickbar ? WeaponSlot.SLOT : 0);
	}
}
