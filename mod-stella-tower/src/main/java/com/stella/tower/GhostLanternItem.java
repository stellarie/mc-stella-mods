package com.stella.tower;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** The Ghost Lantern in hand: held in either hand, it reveals the invisible mobs round its holder as it does when set down. */
public class GhostLanternItem extends BlockItem {
	public GhostLanternItem(final Block block, final Properties properties) {
		super(block, properties);
	}

	@Override
	public void inventoryTick(final ItemStack itemStack, final ServerLevel level, final Entity owner, final @Nullable EquipmentSlot slot) {
		super.inventoryTick(itemStack, level, owner, slot);
		boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
		if (held && owner.tickCount % GhostLanternBlock.SCAN_TICKS == 0) {
			GhostLanternBlock.reveal(level, owner.position());
		}
	}
}
