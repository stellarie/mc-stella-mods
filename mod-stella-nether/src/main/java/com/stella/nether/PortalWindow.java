package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * The portal's choice as a window: the server opens it on a client that has the mod, with three options
 * stacked, and the client sends back the one clicked. A client without the mod gets the chat prompt instead.
 */
public final class PortalWindow {
	/** The window's options, top to bottom. */
	public enum Option {
		NORMAL_NETHER(RaidTravel.Choice.NORMAL, RaidMode.NORMAL),
		RAID_NORMAL(RaidTravel.Choice.RAID, RaidMode.NORMAL),
		RAID_HARD(RaidTravel.Choice.RAID, RaidMode.HARD);

		private final RaidTravel.Choice choice;
		private final RaidMode mode;

		Option(final RaidTravel.Choice choice, final RaidMode mode) {
			this.choice = choice;
			this.mode = mode;
		}

		public RaidTravel.Choice choice() {
			return this.choice;
		}

		public RaidMode mode() {
			return this.mode;
		}

		/** Whether this option can be taken while the raid runs in a mode, or none runs (null). */
		public boolean open(final @Nullable RaidMode running) {
			return this.choice == RaidTravel.Choice.NORMAL || running == null || running == this.mode;
		}

		public static @Nullable Option of(final int index) {
			return index >= 0 && index < values().length ? values()[index] : null;
		}
	}

	/** Server to client: open the window. The running raid's mode, or -1 when none runs. */
	public record Open(int running) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(StellaNether.id("portal_window"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::running, Open::new);

		public @Nullable RaidMode runningMode() {
			return this.running < 0 || this.running >= RaidMode.values().length ? null : RaidMode.values()[this.running];
		}

		public List<Option> options() {
			return List.of(Option.values());
		}

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client to server: the option clicked, by its place in the window. */
	public record Chosen(int option) implements CustomPacketPayload {
		public static final Type<Chosen> TYPE = new Type<>(StellaNether.id("portal_chosen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Chosen> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Chosen::option, Chosen::new);

		@Override
		public Type<Chosen> type() {
			return TYPE;
		}
	}

	private PortalWindow() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Chosen.TYPE, Chosen.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Chosen.TYPE, (payload, context) -> {
			Option option = Option.of(payload.option());
			if (option != null) {
				RaidTravel.chooseFromWindow(context.player(), option);
			}
		});
	}

	static Open openFor(final RaidState state) {
		return new Open(state.active() ? state.mode().ordinal() : -1);
	}
}
