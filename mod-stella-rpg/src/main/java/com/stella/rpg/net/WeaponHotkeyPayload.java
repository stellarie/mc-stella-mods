package com.stella.rpg.net;

import com.stella.rpg.StellaRpg;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A hotkey edge: 0..8 or the ready key; down or up. */
public record WeaponHotkeyPayload(int index, boolean down) implements CustomPacketPayload {
	public static final Type<WeaponHotkeyPayload> TYPE = new Type<>(StellaRpg.id("weapon_hotkey"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponHotkeyPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WeaponHotkeyPayload::index,
		ByteBufCodecs.BOOL, WeaponHotkeyPayload::down,
		WeaponHotkeyPayload::new
	);

	@Override
	public Type<WeaponHotkeyPayload> type() {
		return TYPE;
	}
}
