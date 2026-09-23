package com.stella.equips.readout;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ReadoutCommand {
	private static final String ROOT = "stellaequips";
	private static final String READOUT = "readout";

	private ReadoutCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
			dispatcher.register(
				Commands.literal(ROOT)
					.then(
						Commands.literal(READOUT)
							.then(Commands.literal("on").executes(context -> switchReadout(context.getSource(), true)))
							.then(Commands.literal("off").executes(context -> switchReadout(context.getSource(), false)))
					)
			)
		);
	}

	private static int switchReadout(final net.minecraft.commands.CommandSourceStack source, final boolean enabled) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("The readout setting belongs to a player."));
			return 0;
		}

		ReadoutService.setEnabled(player, enabled);
		source.sendSuccess(
			() -> Component.literal("Mob level readout " + (enabled ? "on" : "off") + "."),
			false
		);
		return 1;
	}
}
