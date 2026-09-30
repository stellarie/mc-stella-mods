package com.stella.rpg.tomb;

import com.stella.rpg.StellaRpg;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the stacks of one death. It is not a Container, so hoppers cannot reach it. */
public final class TombstoneBlockEntity extends BlockEntity {
	public static final int CAPACITY = 8;
	public static final int OPEN_DELAY_TICKS = 12000;
	public static final BlockEntityType<TombstoneBlockEntity> TYPE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		StellaRpg.id("tombstone"),
		new BlockEntityType<>(TombstoneBlockEntity::new, Set.of(TombstoneBlock.BLOCK))
	);

	private final NonNullList<ItemStack> stacks = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
	private final Slots slots = new Slots();
	private UUID ownerId = new UUID(0L, 0L);
	private String ownerName = "";
	private long deathTime;
	private UUID nameDisplayId;
	private UUID headDisplayId;

	public TombstoneBlockEntity(final BlockPos pos, final BlockState state) {
		super(TYPE, pos, state);
	}

	public static void register() {
	}

	public void fill(final UUID owner, final String name, final long time, final List<ItemStack> contents) {
		this.ownerId = owner;
		this.ownerName = name;
		this.deathTime = time;
		for (int slot = 0; slot < CAPACITY; slot++) {
			this.stacks.set(slot, slot < contents.size() ? contents.get(slot) : ItemStack.EMPTY);
		}
		this.setChanged();
	}

	public void setDisplays(final UUID nameDisplay, final UUID headDisplay) {
		this.nameDisplayId = nameDisplay;
		this.headDisplayId = headDisplay;
		this.setChanged();
	}

	public UUID ownerId() {
		return this.ownerId;
	}

	public String ownerName() {
		return this.ownerName;
	}

	public long deathTime() {
		return this.deathTime;
	}

	public UUID nameDisplayId() {
		return this.nameDisplayId;
	}

	public UUID headDisplayId() {
		return this.headDisplayId;
	}

	public List<ItemStack> contents() {
		return this.stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
	}

	public Container slots() {
		return this.slots;
	}

	public long ticksUntilOpen(final Player player, final long now) {
		if (player.getUUID().equals(this.ownerId)) {
			return 0L;
		}
		return Math.max(0L, this.deathTime + OPEN_DELAY_TICKS - now);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.store("Owner", UUIDUtil.CODEC, this.ownerId);
		output.putString("OwnerName", this.ownerName);
		output.putLong("DeathTime", this.deathTime);
		output.storeNullable("NameDisplay", UUIDUtil.CODEC, this.nameDisplayId);
		output.storeNullable("HeadDisplay", UUIDUtil.CODEC, this.headDisplayId);
		ContainerHelper.saveAllItems(output, this.stacks);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(new UUID(0L, 0L));
		this.ownerName = input.getStringOr("OwnerName", "");
		this.deathTime = input.getLongOr("DeathTime", 0L);
		this.nameDisplayId = input.read("NameDisplay", UUIDUtil.CODEC).orElse(null);
		this.headDisplayId = input.read("HeadDisplay", UUIDUtil.CODEC).orElse(null);
		this.stacks.clear();
		ContainerHelper.loadAllItems(input, this.stacks);
	}

	@Override
	public void preRemoveSideEffects(final BlockPos pos, final BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (this.level instanceof ServerLevel serverLevel) {
			Tombstones.removeDisplays(serverLevel, this);
			Containers.dropContents(serverLevel, pos, this.stacks);
		}
	}

	private boolean isDrained() {
		return this.stacks.stream().allMatch(ItemStack::isEmpty);
	}

	private final class Slots implements Container {
		@Override
		public int getContainerSize() {
			return CAPACITY + 1;
		}

		@Override
		public boolean isEmpty() {
			return TombstoneBlockEntity.this.isDrained();
		}

		@Override
		public ItemStack getItem(final int slot) {
			return slot < CAPACITY ? TombstoneBlockEntity.this.stacks.get(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(final int slot, final int count) {
			if (slot >= CAPACITY) {
				return ItemStack.EMPTY;
			}
			ItemStack removed = ContainerHelper.removeItem(TombstoneBlockEntity.this.stacks, slot, count);
			if (!removed.isEmpty()) {
				this.setChanged();
			}
			return removed;
		}

		@Override
		public ItemStack removeItemNoUpdate(final int slot) {
			return slot < CAPACITY ? ContainerHelper.takeItem(TombstoneBlockEntity.this.stacks, slot) : ItemStack.EMPTY;
		}

		@Override
		public void setItem(final int slot, final ItemStack stack) {
			if (slot < CAPACITY) {
				TombstoneBlockEntity.this.stacks.set(slot, stack);
			}
			this.setChanged();
		}

		@Override
		public void setChanged() {
			TombstoneBlockEntity.this.setChanged();
			if (TombstoneBlockEntity.this.isDrained() && TombstoneBlockEntity.this.level instanceof ServerLevel serverLevel) {
				Tombstones.remove(serverLevel, TombstoneBlockEntity.this.getBlockPos());
			}
		}

		@Override
		public boolean stillValid(final Player player) {
			return true;
		}

		@Override
		public boolean canPlaceItem(final int slot, final ItemStack stack) {
			return false;
		}

		@Override
		public void clearContent() {
			TombstoneBlockEntity.this.stacks.clear();
		}
	}
}
