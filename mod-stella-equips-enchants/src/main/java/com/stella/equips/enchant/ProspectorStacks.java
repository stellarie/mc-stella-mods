package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prospector builds mining speed. Each block broken with a Prospector tool adds one stack, up to
 * {@link #STACKS_PER_LEVEL} per level. Each stack adds 1% block break speed while the player holds a
 * Prospector tool. After {@link #IDLE_TICKS} without a break, one stack falls off every {@link #DECAY_TICKS}.
 */
public final class ProspectorStacks {
	public static final int STACKS_PER_LEVEL = 6;
	public static final double SPEED_PER_STACK = 0.01;
	public static final int IDLE_TICKS = 60;
	public static final int DECAY_TICKS = 20;
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "prospector_speed");
	private static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "tool/prospector");
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private static final class State {
		int stacks;
		long nextDecay;
	}

	private ProspectorStacks() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(ProspectorStacks::afterBlockBreak);
		ServerTickEvents.END_SERVER_TICK.register(ProspectorStacks::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reset(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
	}

	public static int stacks(final Player player) {
		State state = STATES.get(player.getUUID());
		return state == null ? 0 : state.stacks;
	}

	public static int level(final Player player) {
		ItemStack tool = player.getMainHandItem();
		return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ID)
			.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool)).orElse(0);
	}

	public static void reset(final ServerPlayer player) {
		STATES.remove(player.getUUID());
		apply(player, 0);
	}

	private static void afterBlockBreak(
		final Level world,
		final Player player,
		final BlockPos pos,
		final BlockState state,
		final BlockEntity blockEntity
	) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		int level = level(serverPlayer);
		if (level <= 0) {
			return;
		}
		State stacks = STATES.computeIfAbsent(serverPlayer.getUUID(), id -> new State());
		stacks.stacks = Math.min(stacks.stacks + 1, STACKS_PER_LEVEL * level);
		stacks.nextDecay = serverPlayer.level().getServer().getTickCount() + IDLE_TICKS;
		apply(serverPlayer, stacks.stacks);
	}

	private static void tick(final MinecraftServer server) {
		long now = server.getTickCount();
		STATES.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				return true;
			}
			State state = entry.getValue();
			if (state.stacks > 0 && now >= state.nextDecay) {
				state.stacks--;
				state.nextDecay = now + DECAY_TICKS;
			}
			apply(player, level(player) > 0 ? state.stacks : 0);
			return state.stacks <= 0;
		});
	}

	private static void apply(final ServerPlayer player, final int stacks) {
		AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (speed == null) {
			return;
		}
		if (stacks <= 0) {
			speed.removeModifier(SPEED_ID);
			return;
		}
		AttributeModifier current = speed.getModifier(SPEED_ID);
		double amount = stacks * SPEED_PER_STACK;
		if (current == null || current.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
