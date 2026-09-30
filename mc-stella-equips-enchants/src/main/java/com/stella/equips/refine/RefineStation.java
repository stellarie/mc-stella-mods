package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Where refining happens: at a Refining Anvil, or with a village smith. The first use quotes the next step,
 * its odds, its price in material and, when a failure would break the piece, a warning; a second use at the
 * same station with the same piece within five seconds pays and rolls. At a smith, the material goes in the
 * off hand, so an ordinary use still opens the trades.
 */
public final class RefineStation {
	public static final int CONFIRM_WINDOW_TICKS = 100;
	private static final List<ResourceKey<VillagerProfession>> SMITHS = List.of(
		VillagerProfession.WEAPONSMITH, VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER
	);

	/** Whether one player's step succeeds, given its chance in percent; the test seam can force either side. */
	public interface Roller {
		boolean succeeds(ServerPlayer player, RandomSource random, int chancePercent);
	}

	/** Test seam: each quote and each attempt, keyed by the player. */
	public interface Recorder {
		default void quoted(final ServerPlayer player, final int nextLevel, final int chance, final int cost, final boolean warning) {
		}

		default void attempted(final ServerPlayer player, final int nextLevel, final boolean success, final boolean broke) {
		}

		default void refused(final ServerPlayer player, final String reason) {
		}
	}

	private record Pending(String station, ItemStack snapshot, int until) {
	}

	private static final Roller FAIR = (player, random, chance) -> fairRoll(random, chance);
	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Roller roller = FAIR;
	private static volatile Recorder recorder = SILENT;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private RefineStation() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(RefineStation::onUseEntity);
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

	private static InteractionResult onUseEntity(final Player player, final Level level, final InteractionHand hand, final Entity entity, final EntityHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer customer) || player.isShiftKeyDown()
			|| player.isSpectator() || !(entity instanceof Villager smith) || smith.isBaby()
			|| SMITHS.stream().noneMatch(smith.getVillagerData().profession()::is)) {
			return InteractionResult.PASS;
		}
		ItemStack piece = customer.getMainHandItem();
		if (!Refine.canRefine(piece) || !Refine.isMaterial(piece, customer.getOffhandItem())) {
			return InteractionResult.PASS;
		}
		use(customer, "smith@" + smith.getUUID());
		return InteractionResult.SUCCESS;
	}

	/** One use at a station: a quote, or the paid attempt that confirms the last quote. */
	public static void use(final ServerPlayer player, final String station) {
		ItemStack piece = player.getMainHandItem();
		if (!Refine.canRefine(piece)) {
			refuse(player, "no_piece", Component.literal("Hold a weapon or a piece of armour to refine it"));
			return;
		}
		int level = Refine.level(piece);
		if (level >= Refine.MAX_LEVEL) {
			refuse(player, "capped", Component.literal("This piece is already +" + Refine.MAX_LEVEL));
			return;
		}
		int next = level + 1;
		int chance = Refine.grade(piece).chanceTo(next);
		int cost = Refine.cost(level);
		Item material = Refine.material(piece);
		int now = player.level().getServer().getTickCount();
		Pending pending = PENDING.remove(player.getUUID());
		if (pending == null || !pending.station().equals(station) || now > pending.until() || !ItemStack.isSameItemSameComponents(pending.snapshot(), piece)) {
			quote(player, piece, station, next, chance, cost, material, now);
			return;
		}
		if (count(player.getInventory(), piece) < cost) {
			refuse(player, "short_of_material", Component.literal("Refining to +" + next + " takes " + cost + " ").append(material.getName(material.getDefaultInstance())));
			return;
		}
		take(player.getInventory(), piece, cost);
		boolean success = roller.succeeds(player, player.getRandom(), chance);
		boolean broke = false;
		if (success) {
			Refine.setLevel(piece, next);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
			player.sendSystemMessage(Component.literal("Success! ").withStyle(ChatFormatting.GREEN).append(piece.getHoverName()).append(" is now +" + next), true);
		} else if (Refine.wouldBreak(piece)) {
			broke = true;
			Component name = piece.getHoverName();
			piece.shrink(1);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			player.sendSystemMessage(Component.literal("The refine failed and ").withStyle(ChatFormatting.RED).append(name).append(" shattered"), true);
		} else {
			piece.setDamageValue(piece.getDamageValue() + Refine.chip(piece));
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.8F);
			player.sendSystemMessage(Component.literal("The refine failed: ").withStyle(ChatFormatting.RED).append(piece.getHoverName())
				.append(" lost " + Refine.chip(piece) + " durability"), true);
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refine ATTEMPT player={} item={} next={} chance={} success={} broke={} station={}",
			player.getUUID(), BuiltInRegistries.ITEM.getKey(piece.getItem()), next, chance, success, broke, station);
		recorder.attempted(player, next, success, broke);
	}

	private static void quote(final ServerPlayer player, final ItemStack piece, final String station, final int next, final int chance,
		final int cost, final Item material, final int now) {
		boolean warning = Refine.wouldBreak(piece);
		MutableComponent text = Component.literal("Refine to +" + next + ": " + chance + "% for " + cost + " ").withStyle(ChatFormatting.AQUA)
			.append(material.getName(material.getDefaultInstance())).append(Component.literal(". Use again to refine.").withStyle(ChatFormatting.AQUA));
		if (warning) {
			text = Component.literal("Warning: a failure will break it! ").withStyle(ChatFormatting.RED).append(text);
		}
		player.sendSystemMessage(text, true);
		PENDING.put(player.getUUID(), new Pending(station, piece.copy(), now + CONFIRM_WINDOW_TICKS));
		recorder.quoted(player, next, chance, cost, warning);
	}

	private static void refuse(final ServerPlayer player, final String reason, final Component message) {
		player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
		recorder.refused(player, reason);
	}

	private static int count(final Inventory inventory, final ItemStack piece) {
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
