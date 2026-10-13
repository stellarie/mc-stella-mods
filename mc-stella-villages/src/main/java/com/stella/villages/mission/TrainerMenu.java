package com.stella.villages.mission;

import com.stella.villages.DescriptionLines;
import com.stella.villages.patrol.Conversation;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The trainer's menu, a three-row chest menu that any client can show without a mod. Slot 4 states what the player
 * can do. Slots 10, 12, 14 and 16 hold the four jobs, and slot 22 the next boss trial. A click asks
 * {@link JobTrainer} or {@link JobTrials}, which stay the only authority, and no item ever leaves the menu.
 * Each job describes itself, its stat bonuses and its innate skill, and every line of text fits in
 * {@link DescriptionLines#MAX_CHARS} characters.
 */
public final class TrainerMenu extends ChestMenu {
	public static final String TITLE = "Job Trainer";
	public static final int INFO_SLOT = 4;
	public static final int TRIAL_SLOT = 22;
	/** The slot of each job, in {@link RpgJobs#JOBS} order. */
	public static final int[] JOB_SLOTS = {10, 12, 14, 16};
	private static final int ROWS = 3;
	private static final int SIZE = ROWS * 9;
	private static final List<Item> JOB_ICONS = List.of(Items.BOW, Items.IRON_SWORD, Items.ENCHANTED_BOOK, Items.GOLDEN_APPLE);
	private final Mob trainer;

	private TrainerMenu(final int id, final Inventory inventory, final Mob trainer, final ServerPlayer player) {
		super(MenuType.GENERIC_9x3, id, inventory, board(player), ROWS);
		this.trainer = trainer;
	}

	static void open(final ServerPlayer player, final Mob trainer) {
		if (player.openMenu(new SimpleMenuProvider(
			(id, inventory, opener) -> new TrainerMenu(id, inventory, trainer, player), Component.literal(TITLE)
		)).isPresent()) {
			Conversation.begin(trainer, player);
		}
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer serverPlayer) {
			Conversation.linger(this.trainer, serverPlayer);
		}
	}

	/** The job a slot offers, or -1. */
	public static int jobIndex(final int slot) {
		for (int i = 0; i < JOB_SLOTS.length; i++) {
			if (JOB_SLOTS[i] == slot) {
				return i;
			}
		}
		return -1;
	}

	private static SimpleContainer board(final ServerPlayer player) {
		SimpleContainer board = new SimpleContainer(SIZE);
		ItemStack glass = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		glass.set(DataComponents.ITEM_NAME, Component.literal(" "));
		for (int slot = 0; slot < SIZE; slot++) {
			board.setItem(slot, glass.copy());
		}
		String refusal = JobTrainer.chooseRefusal(player);
		board.setItem(INFO_SLOT, info(player, refusal));
		if (refusal == null) {
			for (int i = 0; i < JOB_SLOTS.length; i++) {
				board.setItem(JOB_SLOTS[i], jobIcon(i));
			}
		}
		OptionalInt trial = JobTrials.offer(player);
		if (trial.isPresent()) {
			board.setItem(TRIAL_SLOT, trialIcon(trial.getAsInt()));
		}
		return board;
	}

	private static ItemStack info(final ServerPlayer player, final String refusal) {
		ItemStack icon = new ItemStack(refusal == null ? Items.PAPER : Items.BARRIER);
		if (refusal == null) {
			icon.set(DataComponents.ITEM_NAME, Component.literal("Choose your job"));
			icon.set(DataComponents.LORE, new ItemLore(DescriptionLines.lines("A job is permanent")));
		} else if (refusal.equals("has_job")) {
			icon.set(DataComponents.ITEM_NAME, Component.literal("Your job: " + RpgJobs.job(player)));
		} else {
			icon.set(DataComponents.ITEM_NAME, Component.literal("No job for you"));
			String reason = JobTrainer.reasonText(refusal);
			icon.set(DataComponents.LORE, new ItemLore(DescriptionLines.lines(Character.toUpperCase(reason.charAt(0)) + reason.substring(1))));
		}
		return icon;
	}

	private static ItemStack jobIcon(final int index) {
		String job = RpgJobs.JOBS.get(index);
		ItemStack icon = new ItemStack(JOB_ICONS.get(index));
		icon.set(DataComponents.ITEM_NAME, Component.literal(JobInfo.choiceTitle(job)));
		icon.set(DataComponents.LORE, new ItemLore(JobInfo.lore(job)));
		return icon;
	}

	/** The trial button for a level. Public for the tooltip test. */
	public static ItemStack trialIcon(final int level) {
		ItemStack icon = new ItemStack(Items.SKELETON_SKULL);
		icon.set(DataComponents.ITEM_NAME, Component.literal("Boss Trial: level " + level));
		List<Component> lore = new java.util.ArrayList<>();
		lore.addAll(DescriptionLines.block("job, level " + level, "Hunt a level " + level + " boss "
			+ JobTrials.MIN_DISTANCE + " to " + JobTrials.MAX_DISTANCE + " blocks from the village. Reward: +5 to your main stat, +1 to the others."));
		lore.addAll(DescriptionLines.lines("Click to accept"));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return icon;
	}

	/** No click moves anything. A click on a job or the trial asks the trainer. */
	@Override
	public void clicked(final int slot, final int button, final ContainerInput input, final Player player) {
		if (player instanceof ServerPlayer serverPlayer && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			int job = jobIndex(slot);
			if (job >= 0 && this.getSlot(slot).getItem().is(JOB_ICONS.get(job))) {
				if (JobTrainer.choose(serverPlayer, RpgJobs.JOBS.get(job))) {
					serverPlayer.closeContainer();
					return;
				}
			} else if (slot == TRIAL_SLOT && this.getSlot(slot).getItem().is(Items.SKELETON_SKULL)) {
				if (JobTrials.accept(serverPlayer, this.trainer)) {
					serverPlayer.closeContainer();
					return;
				}
			}
		}
		// The client predicted a pickup, so it is told the board is unchanged.
		this.sendAllDataToRemote();
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.trainer.isAlive() && player.distanceToSqr(this.trainer) <= (double) MissionService.MISSION_MENU_RANGE * MissionService.MISSION_MENU_RANGE;
	}
}
