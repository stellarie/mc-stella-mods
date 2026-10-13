package com.stella.rpg.tomb;

import com.stella.rpg.DescriptionLines;
import com.stella.rpg.StellaRpg;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * A player whose death placed a tombstone respawns with a Grave Compass that points to it. A later death retargets the
 * compass the player already carries instead of adding another. The compass is a vanilla compass with a lodestone
 * target, so it needs no lodestone and works in any client.
 */
public final class GraveCompass {
	public static final String MARKER = "stella_grave_compass";
	/** Where the player's last tombstone stands; read when the player respawns. */
	public static final AttachmentType<GlobalPos> LAST_TOMB = AttachmentRegistry.createPersistent(
		StellaRpg.id("last_tomb"), GlobalPos.CODEC
	);

	private GraveCompass() {
	}

	public static void register() {
		StellaRpg.LOGGER.debug("Registered the grave compass attachment {}", LAST_TOMB.identifier());
	}

	static void mark(final ServerPlayer player, final ServerLevel level, final BlockPos tomb) {
		player.setAttached(LAST_TOMB, GlobalPos.of(level.dimension(), tomb));
	}

	public static boolean isGraveCompass(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.COMPASS) && data != null && data.copyTag().getBooleanOr(MARKER, false);
	}

	/** Gives the respawned player a compass to the tombstone the old body left, or retargets the one it carries. */
	public static void give(final ServerPlayer oldPlayer, final ServerPlayer newPlayer) {
		GlobalPos tomb = oldPlayer.getAttached(LAST_TOMB);
		if (tomb == null) {
			return;
		}
		oldPlayer.removeAttached(LAST_TOMB);
		Inventory inventory = newPlayer.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isGraveCompass(inventory.getItem(slot))) {
				aim(inventory.getItem(slot), tomb);
				return;
			}
		}
		ItemStack compass = new ItemStack(Items.COMPASS);
		CustomData.update(DataComponents.CUSTOM_DATA, compass, tag -> tag.putBoolean(MARKER, true));
		compass.set(DataComponents.CUSTOM_NAME, Component.literal("Grave Compass").withStyle(style -> style.withItalic(false)));
		aim(compass, tomb);
		if (!inventory.add(compass) && !compass.isEmpty()) {
			newPlayer.level().addFreshEntity(new ItemEntity(newPlayer.level(), newPlayer.getX(), newPlayer.getY(), newPlayer.getZ(), compass));
		}
	}

	private static void aim(final ItemStack compass, final GlobalPos tomb) {
		compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(tomb), false));
		BlockPos pos = tomb.pos();
		List<Component> lore = new ArrayList<>();
		DescriptionLines.addBlock(lore, Component.literal("none").withStyle(ChatFormatting.WHITE),
			Component.literal("Points to your tombstone.").withStyle(ChatFormatting.GRAY));
		DescriptionLines.add(lore, Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " in " + tomb.dimension().identifier().getPath())
			.withStyle(ChatFormatting.GRAY));
		compass.set(DataComponents.LORE, new ItemLore(lore));
	}
}
