package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Refining happens in the Refine tab of an anvil. The client sends the open anvil's container id; the server
 * refines the piece in the first input slot. A success raises the level by one. A failure lowers it by one and
 * never costs durability or breaks the piece. A failure from +7 up spends a {@link RefineCatalyst} when the player
 * holds one, and the level then stays. The refine never runs the anvil's own take step, so the anvil
 * never wears.
 */
public final class RefineStation {
	/** Whether one player's step succeeds, given its chance in percent; the test seam can force either side. */
	public interface Roller {
		boolean succeeds(ServerPlayer player, RandomSource random, int chancePercent);
	}

	/** Test seam: each attempt and each refusal, keyed by the player. */
	public interface Recorder {
		default void attempted(final ServerPlayer player, final int nextLevel, final boolean success) {
		}

		default void refused(final ServerPlayer player, final String reason) {
		}
	}

	public enum Result {
		SUCCESS, FAILURE, WRONG_MENU, NO_PIECE, CAPPED, SHORT_OF_MATERIAL
	}

	private static final Roller FAIR = (player, random, chance) -> fairRoll(random, chance);
	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Roller roller = FAIR;
	private static volatile Recorder recorder = SILENT;

	private RefineStation() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RefineRequestPayload.TYPE, RefineRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenRefineTabPayload.TYPE, OpenRefineTabPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RefineRequestPayload.TYPE,
			(payload, context) -> refine(context.player(), payload.containerId()));
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** The odds as they ship, for a chance in percent. */
	public static boolean fairRoll(final RandomSource random, final int chancePercent) {
		return random.nextInt(100) < chancePercent;
	}

	/** The same odds for a chance that has a fraction. */
	public static boolean fairRoll(final RandomSource random, final double chancePercent) {
		return random.nextDouble() * 100.0 < chancePercent;
	}

	/** One refine of the piece in the open anvil's first slot. */
	public static Result refine(final ServerPlayer player, final int containerId) {
		if (!(player.containerMenu instanceof AnvilMenu menu) || menu.containerId != containerId) {
			return refuse(player, Result.WRONG_MENU, null);
		}
		Slot slot = menu.getSlot(0);
		ItemStack piece = slot.getItem();
		if (Refine.isSpellbook(piece)) {
			return refuse(player, Result.NO_PIECE, Component.literal("A spell or skill book refines with an identical book in the second slot"));
		}
		if (!Refine.canRefine(piece)) {
			return refuse(player, Result.NO_PIECE, Component.literal("Put a weapon, a piece of armour or a shield in the first slot"));
		}
		int level = Refine.level(piece);
		if (level >= Refine.MAX_LEVEL) {
			return refuse(player, Result.CAPPED, Component.literal("This piece is already +" + Refine.MAX_LEVEL));
		}
		int next = level + 1;
		int cost = Refine.cost(level);
		if (count(player.getInventory(), piece) < cost) {
			return refuse(player, Result.SHORT_OF_MATERIAL, Component.literal("Refining to +" + next + " takes " + cost + " ")
				.append(Refine.material(piece).getName(Refine.material(piece).getDefaultInstance())));
		}
		take(player.getInventory(), piece, cost);
		int chance = Refine.grade(piece).chanceTo(next);
		boolean success = roller.succeeds(player, player.getRandom(), chance);
		if (success) {
			Refine.setLevel(piece, next);
			piece.setDamageValue(0);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
			player.sendSystemMessage(Component.literal("Success! ").withStyle(ChatFormatting.GREEN).append(piece.getHoverName())
				.append(" is now +" + next), true);
		} else {
			// The catalyst is spent only on a failed roll, so a whetstone success never reaches it.
			boolean spared = level >= RefineCatalyst.MIN_LEVEL && RefineCatalyst.take(player.getInventory());
			int dropped = spared ? level : Refine.levelAfterFailure(level);
			Refine.setLevel(piece, dropped);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.8F);
			player.sendSystemMessage(Component.literal("The refine failed: ").withStyle(ChatFormatting.RED).append(piece.getHoverName())
				.append(dropped == level ? " kept +" + dropped : " dropped to +" + dropped)
				.append(spared ? " (catalyst spent)" : ""), true);
		}
		slot.setChanged();
		menu.broadcastChanges();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refine ATTEMPT player={} item={} next={} chance={} success={}",
			player.getUUID(), BuiltInRegistries.ITEM.getKey(piece.getItem()), next, chance, success);
		recorder.attempted(player, next, success);
		return success ? Result.SUCCESS : Result.FAILURE;
	}

	private static Result refuse(final ServerPlayer player, final Result reason, final Component message) {
		if (message != null) {
			player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
		}
		recorder.refused(player, reason.name());
		return reason;
	}

	public static int count(final Inventory inventory, final ItemStack piece) {
		int found = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != piece && Refine.isMaterial(piece, stack)) {
				found += stack.getCount();
			}
		}
		return found;
	}

	private static void take(final Inventory inventory, final ItemStack piece, final int cost) {
		int left = cost;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != piece && Refine.isMaterial(piece, stack)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
	}
}
