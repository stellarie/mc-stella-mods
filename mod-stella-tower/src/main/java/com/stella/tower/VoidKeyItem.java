package com.stella.tower;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Given on entering the tower: used anywhere, it returns the player to where they came in by. Used in the
 * tower that is the spot beside the portal they entered; used outside it, that spot again. It is kept, not spent.
 */
public class VoidKeyItem extends Item {
	public static final int COOLDOWN_TICKS = 20;

	public VoidKeyItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!(player instanceof ServerPlayer traveller)) {
			return InteractionResult.SUCCESS;
		}
		return returnHome(traveller) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Sends the player back to where they entered; with no record, to the overworld's spawn. */
	public static boolean returnHome(final ServerPlayer player) {
		TowerTravel.Entry entry = player.getAttached(TowerTravel.ENTRY);
		ServerLevel target = entry == null ? null : player.level().getServer().getLevel(entry.dimension());
		Vec3 position;
		float yaw;
		if (target == null) {
			target = player.level().getServer().overworld();
			position = Vec3.atBottomCenterOf(target.getRespawnData().pos());
			yaw = player.getYRot();
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.void_key.no_entry"));
		} else {
			position = entry.position();
			yaw = entry.yaw();
		}
		player.getCooldowns().addCooldown(new ItemStack(TowerItems.VOID_KEY), COOLDOWN_TICKS);
		player.teleport(new TeleportTransition(target, position, Vec3.ZERO, yaw, 0.0F, TeleportTransition.PLAY_PORTAL_SOUND));
		StellaTower.LOGGER.debug("[TOWER] player={} RETURNED to={} at={}", player.getScoreboardName(), target.dimension().identifier(), position);
		return true;
	}

	@Override
	public void appendHoverText(
		final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> lines, final TooltipFlag flag
	) {
		DescriptionLines.add(lines, Component.translatable("item.mod-stella-tower.void_key.desc").withStyle(ChatFormatting.GRAY));
	}
}
