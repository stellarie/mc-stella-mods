package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** `/stellalearn profile`: tells a player what mobs have learned about them, without reading the log. */
public final class LearnCommand {
	private static final String ROOT = "stellalearn";
	private static final String PROFILE = "profile";

	private LearnCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
			dispatcher.register(Commands.literal(ROOT).then(Commands.literal(PROFILE).executes(context -> profile(context.getSource()))))
		);
	}

	private static int profile(final CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("A profile belongs to a player."));
			return 0;
		}

		List<String> lines = ProfileReport.describe(player);
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return lines.size();
	}
}
