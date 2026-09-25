package com.stella.equips.enchant;

import com.stella.equips.StellaEquips;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OreMender {
	private static final Identifier ID =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "tool/ore_mender");

	private OreMender() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(OreMender::afterBlockBreak);
	}

	private static void afterBlockBreak(
		final Level world,
		final Player player,
		final BlockPos pos,
		final BlockState state,
		final BlockEntity blockEntity
	) {
		if (!(world instanceof ServerLevel serverLevel)
			|| !(player instanceof ServerPlayer serverPlayer)
			|| !state.is(BlockTags.ORES)
		) {
			return;
		}

		ItemStack tool = serverPlayer.getMainHandItem();
		if (!tool.is(ItemTags.PICKAXES)) {
			return;
		}

		Optional<Holder.Reference<Enchantment>> found = serverLevel.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(ID);
		if (found.isEmpty()) {
			return;
		}

		if (EnchantmentHelper.getItemEnchantmentLevel(found.get(), tool) > 0) {
			serverPlayer.heal(1.0F);
		}
	}
}
