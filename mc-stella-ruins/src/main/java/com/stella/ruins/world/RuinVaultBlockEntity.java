package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** The stacks of one vault. Nothing goes in by hand or by hopper, and nothing comes out by hopper while it is sealed. */
public final class RuinVaultBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 27;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** Rewards that found no free slot. They drop at the vault when a player opens it. */
	private final List<ItemStack> overflow = new ArrayList<>();

	public RuinVaultBlockEntity(final BlockPos pos, final BlockState state) {
		super(RuinBlocks.VAULT_ENTITY, pos, state);
	}

	/** True while the vault is shut. */
	public boolean sealed() {
		return getBlockState().getValue(RuinVaultBlock.LOCKED);
	}

	/** Opens or shuts the vault. The stacks stay either way. */
	public void setSealed(final ServerLevel level, final boolean sealed) {
		BlockState state = getBlockState();
		if (state.getValue(RuinVaultBlock.LOCKED) != sealed) {
			level.setBlock(this.worldPosition, state.setValue(RuinVaultBlock.LOCKED, sealed), Block.UPDATE_ALL);
		}
	}

	/** Rolls a table once for each roll. Stacks go to free slots at random, and the rest wait as overflow. */
	public void fill(final ServerLevel level, final ResourceKey<LootTable> key, final int rolls) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
			.create(LootContextParamSets.CHEST);
		for (int roll = 0; roll < rolls; roll++) {
			for (ItemStack stack : table.getRandomItems(params, level.getRandom().nextLong())) {
				place(level.getRandom(), stack);
			}
		}
		setChanged();
	}

	private void place(final RandomSource random, final ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		List<Integer> free = new ArrayList<>();
		for (int slot = 0; slot < this.items.size(); slot++) {
			if (this.items.get(slot).isEmpty()) {
				free.add(slot);
			}
		}
		if (free.isEmpty()) {
			this.overflow.add(stack);
		} else {
			this.items.set(free.get(random.nextInt(free.size())), stack);
		}
	}

	/** The rewards that did not fit, still waiting. */
	public List<ItemStack> overflow() {
		return List.copyOf(this.overflow);
	}

	/** Puts one stack in the first free slot, or keeps it as overflow. */
	public boolean addReward(final ItemStack stack) {
		for (int slot = 0; slot < this.items.size(); slot++) {
			if (this.items.get(slot).isEmpty()) {
				this.items.set(slot, stack);
				setChanged();
				return true;
			}
		}
		this.overflow.add(stack);
		setChanged();
		return true;
	}

	private void dropOverflow(final Level level) {
		if (this.overflow.isEmpty() || level.isClientSide()) {
			return;
		}
		for (ItemStack stack : this.overflow) {
			Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, stack);
		}
		this.overflow.clear();
		setChanged();
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.mod-stella-ruins.ruin_vault");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> list) {
		this.items = list;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		dropOverflow(inventory.player.level());
		return ChestMenu.threeRows(containerId, inventory, this);
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack stack) {
		return false;
	}

	@Override
	public boolean canTakeItem(final Container into, final int slot, final ItemStack stack) {
		return !sealed();
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		ValueOutput.TypedOutputList<ItemStack> pending = output.list("overflow", ItemStack.CODEC);
		this.overflow.forEach(pending::add);
	}
}
