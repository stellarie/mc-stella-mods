package com.stella.rpg.net;

import com.stella.rpg.DefenseBonuses;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StatPoints;
import com.stella.rpg.StatSheet;
import com.stella.rpg.StellaRpg;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The stat state the server sends to its owner. Both arrays follow the order of {@code Stat.values()}. */
public record StatsSyncPayload(
	int[] base,
	int[] bonus,
	int unspent,
	int level,
	int flatDefense,
	byte result
) implements CustomPacketPayload {
	public static final Type<StatsSyncPayload> TYPE = new Type<>(StellaRpg.id("stats_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatsSyncPayload> CODEC = StreamCodec.ofMember(
		StatsSyncPayload::write, StatsSyncPayload::read
	);

	private static final int COUNT = Stat.values().length;

	public static StatsSyncPayload of(final ServerPlayer player) {
		return of(player, StatPoints.Result.OK);
	}

	public static StatsSyncPayload of(final ServerPlayer player, final StatPoints.Result result) {
		StatSheet sheet = StatPoints.sheet(player);
		int[] base = new int[COUNT];
		int[] bonus = new int[COUNT];
		for (Stat stat : Stat.values()) {
			base[stat.ordinal()] = sheet.get(stat);
			bonus[stat.ordinal()] = StatBonuses.bonus(player, stat);
		}
		return new StatsSyncPayload(
			base,
			bonus,
			StatPoints.unspent(player.experienceLevel, sheet),
			player.experienceLevel,
			Math.round(DefenseBonuses.flat(player)),
			(byte) result.ordinal()
		);
	}

	public StatPoints.Result resultCode() {
		StatPoints.Result[] all = StatPoints.Result.values();
		return this.result >= 0 && this.result < all.length ? all[this.result] : StatPoints.Result.OK;
	}

	public StatsSyncPayload withResult(final StatPoints.Result code) {
		return new StatsSyncPayload(this.base, this.bonus, this.unspent, this.level, this.flatDefense, (byte) code.ordinal());
	}

	private static void write(final StatsSyncPayload payload, final RegistryFriendlyByteBuf buf) {
		for (int i = 0; i < COUNT; i++) {
			buf.writeVarInt(payload.base[i]);
		}
		for (int i = 0; i < COUNT; i++) {
			buf.writeVarInt(payload.bonus[i]);
		}
		buf.writeVarInt(payload.unspent);
		buf.writeVarInt(payload.level);
		buf.writeVarInt(payload.flatDefense);
		buf.writeByte(payload.result);
	}

	private static StatsSyncPayload read(final RegistryFriendlyByteBuf buf) {
		int[] base = new int[COUNT];
		int[] bonus = new int[COUNT];
		for (int i = 0; i < COUNT; i++) {
			base[i] = buf.readVarInt();
		}
		for (int i = 0; i < COUNT; i++) {
			bonus[i] = buf.readVarInt();
		}
		int unspent = buf.readVarInt();
		int level = buf.readVarInt();
		int flatDefense = buf.readVarInt();
		return new StatsSyncPayload(base, bonus, unspent, level, flatDefense, buf.readByte());
	}

	@Override
	public Type<StatsSyncPayload> type() {
		return TYPE;
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof StatsSyncPayload that
			&& Arrays.equals(this.base, that.base)
			&& Arrays.equals(this.bonus, that.bonus)
			&& this.unspent == that.unspent
			&& this.level == that.level
			&& this.flatDefense == that.flatDefense
			&& this.result == that.result;
	}

	@Override
	public int hashCode() {
		int hash = Arrays.hashCode(this.base) * 31 + Arrays.hashCode(this.bonus);
		return ((hash * 31 + this.unspent) * 31 + this.level) * 31 + this.flatDefense;
	}

	@Override
	public String toString() {
		return "StatsSyncPayload[base=" + Arrays.toString(this.base) + ", bonus=" + Arrays.toString(this.bonus)
			+ ", unspent=" + this.unspent + ", level=" + this.level + ", flatDefense=" + this.flatDefense
			+ ", result=" + this.result + "]";
	}
}
