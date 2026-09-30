package com.stella.rpg;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stella.rpg.net.RpgNet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RpgCommand {
	private RpgCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("rpgstats")
			.executes(RpgCommand::status)
			.then(Commands.literal("allocate")
				.then(Commands.argument("stat", StringArgumentType.word())
					.suggests((command, builder) -> SharedSuggestionProvider.suggest(
						Arrays.stream(Stat.values()).map(Stat::key), builder))
					.then(Commands.argument("amount", IntegerArgumentType.integer())
						.executes(RpgCommand::allocate))))
			.then(Commands.literal("respec").executes(RpgCommand::respec)));
	}

	public static String statusLine(final ServerPlayer player, final Stat stat) {
		int base = StatPoints.sheet(player).get(stat);
		int bonus = StatBonuses.bonus(player, stat);
		String head = stat.name() + " " + base;
		return bonus == 0 ? head : head + " + " + bonus;
	}

	public static List<String> statusLines(final ServerPlayer player) {
		List<String> lines = new ArrayList<>();
		for (Stat stat : Stat.values()) {
			lines.add(statusLine(player, stat));
		}
		lines.add("Unspent: " + StatPoints.unspent(player.experienceLevel, StatPoints.sheet(player)));
		return lines;
	}

	public static List<String> diceLines(final ServerPlayer player) {
		return List.of(
			"Atk dice " + RpgDice.of(StatBonuses.effective(player, Stat.STR)),
			"Ranged dice " + RpgDice.of(StatBonuses.effective(player, Stat.DEX))
		);
	}

	public static String message(final StatPoints.Result result) {
		return switch (result) {
			case OK -> "Done.";
			case NOT_ENOUGH_POINTS -> "Not enough unspent points.";
			case ABOVE_CEILING -> "A stat cannot go above " + StatSheet.CEILING + ".";
			case NOT_ENOUGH_EMERALDS -> "A respec needs " + StatPoints.RESPEC_EMERALDS + " emeralds.";
			case NOTHING_TO_RESET -> "No points are allocated.";
			case INVALID_AMOUNT -> "The amount must be at least 1.";
			case INVALID_STAT -> "Unknown stat.";
		};
	}

	private static int status(final CommandContext<CommandSourceStack> command) {
		ServerPlayer player = player(command);
		if (player == null) {
			return 0;
		}
		List<String> lines = new ArrayList<>(statusLines(player));
		lines.addAll(diceLines(player));
		for (String line : lines) {
			command.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int allocate(final CommandContext<CommandSourceStack> command) {
		ServerPlayer player = player(command);
		if (player == null) {
			return 0;
		}
		String key = StringArgumentType.getString(command, "stat");
		Stat stat = Stat.byKey(key).orElse(null);
		if (stat == null) {
			command.getSource().sendFailure(Component.literal("Unknown stat: " + key));
			return 0;
		}
		int amount = IntegerArgumentType.getInteger(command, "amount");
		StatPoints.Result result = StatPoints.allocate(player, stat, amount);
		if (result != StatPoints.Result.OK) {
			command.getSource().sendFailure(Component.literal(message(result)));
			return 0;
		}
		RpgNet.sync(player);
		command.getSource().sendSuccess(() -> Component.literal(statusLine(player, stat)), false);
		return 1;
	}

	private static int respec(final CommandContext<CommandSourceStack> command) {
		ServerPlayer player = player(command);
		if (player == null) {
			return 0;
		}
		StatPoints.Result result = StatPoints.respec(player);
		if (result != StatPoints.Result.OK) {
			command.getSource().sendFailure(Component.literal(message(result)));
			return 0;
		}
		RpgNet.sync(player);
		command.getSource().sendSuccess(
			() -> Component.literal("Respec done. Cost: " + StatPoints.RESPEC_EMERALDS + " emeralds."), false);
		return 1;
	}

	private static ServerPlayer player(final CommandContext<CommandSourceStack> command) {
		ServerPlayer player = command.getSource().getPlayer();
		if (player == null) {
			command.getSource().sendFailure(Component.literal("Only a player can use this command."));
		}
		return player;
	}
}
