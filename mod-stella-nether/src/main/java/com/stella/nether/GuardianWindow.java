package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The Blaze Guardian's raid window: the server opens it for the player who used the guardian, with one row
 * for each raid, and the client sends back the row clicked or a cancel.
 */
public final class GuardianWindow {
	/** The window's options, top to bottom. Each one hosts the raid of its mode, or joins it. */
	public enum Option {
		RAID_NORMAL(RaidMode.NORMAL),
		RAID_HARD(RaidMode.HARD);

		private final RaidMode mode;

		Option(final RaidMode mode) {
			this.mode = mode;
		}

		public RaidMode mode() {
			return this.mode;
		}

		public static @Nullable Option of(final int index) {
			return index >= 0 && index < values().length ? values()[index] : null;
		}

		public static Option of(final RaidMode mode) {
			return values()[mode.ordinal()];
		}
	}

	/** One raid as the window shows it: its mode, the level its mobs take, and whether it already runs. */
	public record Row(int mode, int level, boolean running) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Row::mode, ByteBufCodecs.VAR_INT, Row::level, ByteBufCodecs.BOOL, Row::running, Row::new);

		public RaidMode raidMode() {
			return RaidMode.values()[this.mode];
		}
	}

	/** Server to client: open the window with these rows, in option order. */
	public record Open(List<Row> rows) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(StellaNether.id("guardian_window"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
			Row.CODEC.apply(ByteBufCodecs.list()), Open::rows, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client to server: the option clicked, by its place in the window. */
	public record Chosen(int option) implements CustomPacketPayload {
		public static final Type<Chosen> TYPE = new Type<>(StellaNether.id("guardian_chosen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Chosen> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Chosen::option, Chosen::new);

		@Override
		public Type<Chosen> type() {
			return TYPE;
		}
	}

	/** Client to server: the window was closed without a choice. */
	public record Cancelled() implements CustomPacketPayload {
		public static final Type<Cancelled> TYPE = new Type<>(StellaNether.id("guardian_cancelled"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cancelled> CODEC = StreamCodec.unit(new Cancelled());

		@Override
		public Type<Cancelled> type() {
			return TYPE;
		}
	}

	private GuardianWindow() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Chosen.TYPE, Chosen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Cancelled.TYPE, Cancelled.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cancelled.TYPE, (payload, context) -> GuardianHub.cancel(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Chosen.TYPE, (payload, context) -> {
			Option option = Option.of(payload.option());
			if (option != null) {
				GuardianHub.choose(context.player(), option);
			}
		});
	}

	/** The window as it stands now: a row for each raid, with its level and whether it runs. */
	static Open openFor(final MinecraftServer server) {
		Row[] rows = new Row[Option.values().length];
		for (Option option : Option.values()) {
			RaidMode mode = option.mode();
			rows[option.ordinal()] = new Row(mode.ordinal(), RaidInstance.displayLevel(server, mode), RaidInstance.state(server, mode).active());
		}
		return new Open(List.of(rows));
	}
}
