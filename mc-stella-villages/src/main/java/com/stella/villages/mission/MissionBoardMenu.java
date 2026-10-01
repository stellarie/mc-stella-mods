package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import com.stella.villages.patrol.Conversation;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;

/**
 * V2: the village mission board, a two-row chest menu that any client can show without a mod. Each offer is
 * the target's spawn egg. The top row holds the three Nemesis contracts, the bottom row the three elite
 * contracts on its left and the three kill contracts on its right. Slots 9, 13 and 17 hold the
 * {@link SlimeTrade} potions, slot 8 the Scroll of Return trade, and slot 0 the {@link SlayerContract}; the rest is glass. Clicking an offer asks {@link MissionService#acceptOffer},
 * which stays the only authority, and no item ever leaves the menu.
 */
public final class MissionBoardMenu extends ChestMenu {
	/** The slot of each offer, in board order: Nemesis, then elite, then kill. */
	public static final int[] OFFER_SLOTS = {2, 4, 6, 10, 11, 12, 14, 15, 16};
	public static final String TITLE = "Village Missions";
	private static final int ROWS = 2;
	private static final int SIZE = ROWS * 9;

	/** Test seam: each offer a player chose from the board, and what the server answered. */
	public interface Recorder {
		default void chose(final ServerPlayer player, final UUID offerId, final boolean accepted, final String reason) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	private final Mob head;
	private final List<MissionService.MissionOffer> offers;

	private MissionBoardMenu(final int id, final Inventory inventory, final Mob head, final List<MissionService.MissionOffer> offers) {
		super(MenuType.GENERIC_9x2, id, inventory, board(offers), ROWS);
		this.head = head;
		this.offers = List.copyOf(offers);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	static void open(final ServerPlayer player, final Mob head, final List<MissionService.MissionOffer> offers) {
		if (player.openMenu(new SimpleMenuProvider(
			(id, inventory, opener) -> new MissionBoardMenu(id, inventory, head, offers), Component.literal(TITLE)
		)).isPresent()) {
			Conversation.begin(head, player);
		}
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer serverPlayer) {
			Conversation.linger(this.head, serverPlayer);
		}
	}

	public UUID headId() {
		return this.head.getUUID();
	}

	public List<MissionService.MissionOffer> offers() {
		return this.offers;
	}

	/** The offer an icon slot shows, or -1 for glass and for any slot outside the board. */
	public static int offerIndex(final int slot) {
		for (int i = 0; i < OFFER_SLOTS.length; i++) {
			if (OFFER_SLOTS[i] == slot) {
				return i;
			}
		}
		return -1;
	}

	public static ItemStack icon(final MissionService.MissionOffer offer) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(offer.targetType());
		ItemStack icon = SpawnEggItem.byId(type).map(holder -> new ItemStack(holder)).orElseGet(() -> new ItemStack(Items.PAPER));
		Component name = type.getDescription();
		String distance = "Somewhere " + MissionService.MISSION_MIN_DISTANCE + " to " + MissionService.MISSION_MAX_DISTANCE + " blocks from the village";
		switch (offer.kind()) {
			case ELITE -> {
				icon.set(DataComponents.ITEM_NAME, Component.literal("Slay the Elite ").append(name));
				icon.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Elite contract: one elite and two " + plural(name) + " guard it"),
					Component.literal(distance),
					Component.literal("Reward: 64 emeralds, 64 iron ingots, 2 potions"),
					Component.literal("Click to accept")
				)));
			}
			case KILL -> {
				icon.set(DataComponents.ITEM_NAME, Component.literal("Kill " + offer.count() + " ").append(name));
				icon.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Kill contract: " + offer.count() + " of them"),
					Component.literal(distance),
					Component.literal("Reward at the last kill: 64 iron ingots, 2 potions"),
					Component.literal("Click to accept")
				)));
			}
			default -> {
				icon.set(DataComponents.ITEM_NAME, Component.literal("Hunt the Nemesis ").append(name));
				icon.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Nemesis contract"),
					Component.literal(distance),
					Component.literal("Accepting gives you a map to it"),
					Component.literal("Click to accept")
				)));
			}
		}
		return icon;
	}

	private static String plural(final Component name) {
		return name.getString() + "s";
	}

	private static SimpleContainer board(final List<MissionService.MissionOffer> offers) {
		SimpleContainer board = new SimpleContainer(SIZE);
		ItemStack glass = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		glass.set(DataComponents.ITEM_NAME, Component.literal(" "));
		for (int slot = 0; slot < SIZE; slot++) {
			int index = offerIndex(slot);
			int trade = SlimeTrade.tradeIndex(slot);
			board.setItem(slot, index >= 0 && index < offers.size() ? icon(offers.get(index))
				: trade >= 0 ? SlimeTrade.icon(trade).orElse(glass.copy()) : slot == SlayerContract.SLOT ? SlayerContract.icon() : glass.copy());
		}
		return board;
	}

	/** No click moves anything. A click on an offer asks the server to accept it; a click on a trade buys it. */
	@Override
	public void clicked(final int slot, final int button, final ContainerInput input, final Player player) {
		int index = offerIndex(slot);
		if (player instanceof ServerPlayer serverPlayer && index >= 0 && index < this.offers.size()
			&& (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			this.choose(serverPlayer, this.offers.get(index));
			return;
		}
		if (player instanceof ServerPlayer serverPlayer && slot == SlayerContract.SLOT
			&& (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			this.slayer(serverPlayer);
			return;
		}
		int trade = SlimeTrade.tradeIndex(slot);
		if (player instanceof ServerPlayer serverPlayer && trade >= 0
			&& (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			SlimeTrade.trade(serverPlayer, trade);
		}

		// The client predicted a pickup, so it is told the board is unchanged.
		this.sendAllDataToRemote();
	}

	private void choose(final ServerPlayer player, final MissionService.MissionOffer offer) {
		boolean accepted = MissionService.acceptOffer(player, this.headId(), offer.id());
		String reason = accepted ? "accepted" : MissionService.lastRejection(player.getUUID());
		recorder.chose(player, offer.id(), accepted, reason);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_board_choice owner={} offer={} accepted={} reason={}",
			McStellaVillages.MOD_ID, player.getUUID(), offer.id(), accepted, reason
		);
		player.closeContainer();
		player.sendSystemMessage(Component.literal(accepted
			? "Mission accepted. Your map marks the target."
			: "The village cannot give you that mission: " + reasonText(reason)));
	}

	private void slayer(final ServerPlayer player) {
		String reason = MissionService.acceptSlayerContract(player, this.headId());
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=slayer_board_choice owner={} accepted={} reason={}",
			McStellaVillages.MOD_ID, player.getUUID(), reason == null, reason == null ? "accepted" : reason);
		if (reason == null) {
			player.closeContainer();
			player.sendSystemMessage(Component.literal("Contract accepted. Your map marks the enraged red slime."));
			return;
		}
		player.sendSystemMessage(Component.literal("The duke cannot give you that contract: " + reasonText(reason)));
		this.sendAllDataToRemote();
	}

	static String reasonText(final String reason) {
		return switch (reason) {
			case "no_safe_target" -> "no safe place to find that target right now";
			case "slayer_active" -> "you already hunt the enraged red slime";
			case "head_missing_or_out_of_range" -> "you walked too far from the patrol head";
			case "stale_offer", "stale_or_foreign_head" -> "the board changed, so open it again";
			default -> "something went wrong (" + reason + ")";
		};
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.head.isAlive() && player.distanceToSqr(this.head) <= (double) MissionService.MISSION_MENU_RANGE * MissionService.MISSION_MENU_RANGE;
	}
}
