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
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

public final class OreRegeneration {
	private static final Identifier ID =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "tool/ore_regeneration");
	private static final int CHANCE_PER_LEVEL_PERCENT = 5;
	private static final int COOLDOWN_TICKS = 20;
	private static final int MAX_LEVEL = 5;
	private static final String CLOCK_OBJECTIVE = "ench_clock";
	private static final String COOLDOWN_OBJECTIVE = "ench_cd_ore";
	private static final ScoreHolder CLOCK_HOLDER = ScoreHolder.forNameOnly("#clock");

	private OreRegeneration() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(OreRegeneration::afterBlockBreak);
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

		int level = Math.min(
			MAX_LEVEL,
			EnchantmentHelper.getItemEnchantmentLevel(found.get(), tool)
		);
		if (level <= 0) {
			return;
		}

		Scoreboard scoreboard = serverLevel.getServer().getScoreboard();
		Objective clockObjective = scoreboard.getObjective(CLOCK_OBJECTIVE);
		Objective cooldownObjective = scoreboard.getObjective(COOLDOWN_OBJECTIVE);
		if (clockObjective == null || cooldownObjective == null) {
			return;
		}

		int now = scoreboard.getOrCreatePlayerScore(CLOCK_HOLDER, clockObjective).get();
		ScoreAccess cooldownScore = scoreboard.getOrCreatePlayerScore(serverPlayer, cooldownObjective);
		if (now - cooldownScore.get() < COOLDOWN_TICKS) {
			return;
		}

		int chance = Math.min(100, CHANCE_PER_LEVEL_PERCENT * level);
		int roll = serverPlayer.getRandom().nextInt(100) + 1;
		if (roll > chance) {
			return;
		}

		if (serverLevel.setBlockAndUpdate(pos, state)) {
			cooldownScore.set(now);
		}
	}
}
