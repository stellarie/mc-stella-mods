package com.stella.rpg.tomb;

import com.stella.rpg.StellaRpg;
import com.stella.rpg.bag.Bag;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

public final class Tombstones {
	public static final String TAG = "mod-stella-rpg.tombstone";
	public static final TagKey<Item> NEVER_TOMBSTONE = TagKey.create(Registries.ITEM, StellaRpg.id("never_tombstone"));
	public static final int MIN_PICK = 5;
	public static final int SEARCH_RADIUS = 8;
	private static final int LAST_PICKABLE_SLOT = Inventory.SLOT_OFFHAND;
	/** A bag stack is a candidate at this offset plus its bag index. */
	public static final int BAG_CANDIDATE_BASE = 1000;
	private static final double LABEL_HEIGHT = 1.6;
	private static final double HEAD_HEIGHT = 1.2;
	private static Picker picker = Tombstones::randomPick;

	private Tombstones() {
	}

	/** Chooses which candidate inventory slots go into the tombstone. */
	@FunctionalInterface
	public interface Picker {
		List<Integer> pick(List<Integer> candidates, RandomSource random);
	}

	public static void setPicker(final Picker newPicker) {
		picker = newPicker;
	}

	public static void resetPicker() {
		picker = Tombstones::randomPick;
	}

	public static List<Integer> randomPick(final List<Integer> candidates, final RandomSource random) {
		List<Integer> pool = new ArrayList<>(candidates);
		int count = Math.min(pool.size(), MIN_PICK + random.nextInt(TombstoneBlockEntity.CAPACITY - MIN_PICK + 1));
		List<Integer> picked = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			picked.add(pool.remove(random.nextInt(pool.size())));
		}
		return picked;
	}

	/** The grave compass stays with the player, so the respawn can re-aim it instead of adding a second one. */
	static boolean buriable(final ItemStack stack) {
		return !stack.isEmpty() && !stack.is(NEVER_TOMBSTONE) && !GraveCompass.isGraveCompass(stack);
	}

	/** Returns true when the tombstone took over the death drop, so vanilla must not drop the inventory. */
	public static boolean bury(final ServerPlayer player) {
		ServerLevel level = player.level();
		if (!player.isDeadOrDying() || level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return false;
		}
		Inventory inventory = player.getInventory();
		List<Integer> candidates = new ArrayList<>();
		for (int slot = 0; slot <= LAST_PICKABLE_SLOT; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (buriable(stack)) {
				candidates.add(slot);
			}
		}
		Bag bag = Bag.of(player);
		for (int index = 0; index < Bag.SIZE; index++) {
			ItemStack stack = bag.get(index);
			if (buriable(stack)) {
				candidates.add(BAG_CANDIDATE_BASE + index);
			}
		}
		Set<Integer> chosen = new LinkedHashSet<>(picker.pick(List.copyOf(candidates), player.getRandom()));
		chosen.retainAll(candidates);
		List<ItemStack> moved = new ArrayList<>();
		for (int slot : chosen) {
			if (moved.size() == TombstoneBlockEntity.CAPACITY) {
				break;
			}
			if (slot >= BAG_CANDIDATE_BASE) {
				moved.add(bag.get(slot - BAG_CANDIDATE_BASE));
				bag.set(slot - BAG_CANDIDATE_BASE, ItemStack.EMPTY);
			} else {
				moved.add(inventory.getItem(slot));
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (!moved.isEmpty() && !place(level, player, moved)) {
			for (ItemStack stack : moved) {
				ItemEntity entity = player.createItemStackToDrop(stack, true, false);
				if (entity != null) {
					level.addFreshEntity(entity);
				}
			}
		}
		return true;
	}

	public static void remove(final ServerLevel level, final BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity tomb) {
			removeDisplays(level, tomb);
		}
		level.removeBlock(pos, false);
	}

	static void removeDisplays(final ServerLevel level, final TombstoneBlockEntity tomb) {
		discard(level, tomb.nameDisplayId());
		discard(level, tomb.headDisplayId());
	}

	private static void discard(final ServerLevel level, final UUID id) {
		if (id != null) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	private static boolean place(final ServerLevel level, final ServerPlayer player, final List<ItemStack> stacks) {
		BlockPos spot = findSpot(level, player.blockPosition());
		if (spot == null || !level.setBlock(spot, TombstoneBlock.BLOCK.defaultBlockState(), 3)) {
			return false;
		}
		if (!(level.getBlockEntity(spot) instanceof TombstoneBlockEntity tomb)) {
			level.removeBlock(spot, false);
			return false;
		}
		String name = player.getPlainTextName();
		tomb.fill(player.getUUID(), name, level.getGameTime(), stacks);
		GraveCompass.mark(player, level, spot);
		try {
			tomb.setDisplays(spawnLabel(level, spot, name), spawnHead(level, spot, player));
		} catch (RuntimeException e) {
			StellaRpg.LOGGER.error("Tombstone displays failed at {}", spot, e);
		}
		return true;
	}

	public static BlockPos findSpot(final ServerLevel level, final BlockPos origin) {
		BlockPos start = new BlockPos(origin.getX(), Mth.clamp(origin.getY(), level.getMinY(), level.getMaxY()), origin.getZ());
		BlockPos best = null;
		int bestDistance = Integer.MAX_VALUE;
		int limit = SEARCH_RADIUS * SEARCH_RADIUS;
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					int distance = dx * dx + dy * dy + dz * dz;
					if (distance > limit || distance >= bestDistance) {
						continue;
					}
					BlockPos pos = start.offset(dx, dy, dz);
					if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.canBeReplaced()) {
						best = pos;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private static UUID spawnLabel(final ServerLevel level, final BlockPos pos, final String name) {
		Display.TextDisplay label = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (label == null) {
			return null;
		}
		label.setPos(pos.getX() + 0.5, pos.getY() + LABEL_HEIGHT, pos.getZ() + 0.5);
		label.setText(Component.literal(name));
		label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		label.addTag(TAG);
		level.addFreshEntity(label);
		return label.getUUID();
	}

	private static UUID spawnHead(final ServerLevel level, final BlockPos pos, final ServerPlayer player) {
		Display.ItemDisplay head = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (head == null) {
			return null;
		}
		ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
		skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
		head.setPos(pos.getX() + 0.5, pos.getY() + HEAD_HEIGHT, pos.getZ() + 0.5);
		head.setItemStack(skull);
		head.addTag(TAG);
		level.addFreshEntity(head);
		return head.getUUID();
	}
}
