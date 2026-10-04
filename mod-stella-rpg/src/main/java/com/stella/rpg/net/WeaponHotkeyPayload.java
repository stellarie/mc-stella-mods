package com.stella.rpg.net;

import com.stella.rpg.StellaRpg;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A hotkey press: 0..8, or the ready key. */
public record WeaponHotkeyPayload(int index) implements CustomPacketPayload {
	public static final Type<WeaponHotkeyPayload> TYPE = new Type<>(StellaRpg.id("weapon_hotkey"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponHotkeyPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WeaponHotkeyPayload::index,
		WeaponHotkeyPayload::new
	);

	@Override
	public Type<WeaponHotkeyPayload> type() {
		return TYPE;
	}
}
