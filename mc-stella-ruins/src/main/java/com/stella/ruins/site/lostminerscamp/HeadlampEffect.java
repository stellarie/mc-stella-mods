package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.reward.EffectHook;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Lucky Headlamp: Night Vision below Y 40, and one extra ore drop at a time. */
public final class HeadlampEffect implements EffectHook {
	public static final String ID = "headlamp";
	/** Below this height the lamp lights the way. */
	public static final int LAMP_Y = 40;
	public static final int NIGHT_VISION_TICKS = 260;
	public static final double EXTRA_DROP_CHANCE = 0.10;

	/** A random source for one wearer's rolls. Tests set it. */
	private static final Map<UUID, Supplier<RandomSource>> SOURCES = new ConcurrentHashMap<>();

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(HeadlampEffect::afterBreak);
	}

	/** Sets the random source of one wearer's rolls, or clears it with null. */
	public static void setRandom(final UUID user, final Supplier<RandomSource> source) {
		if (source == null) {
			SOURCES.remove(user);
		} else {
			SOURCES.put(user, source);
		}
	}

	/** One roll at the extra drop chance. */
	public static boolean roll(final RandomSource source) {
		return source.nextDouble() < EXTRA_DROP_CHANCE;
	}

	/** True when the lamp sits on the player's head. */
	public static boolean worn(final Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(CampItems.LUCKY_HEADLAMP);
	}

	@Override
	public void onTick(final LivingEntity user) {
		if (user.getY() < LAMP_Y) {
			user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false));
		}
	}

	private static void afterBreak(final Level level, final Player player, final BlockPos pos, final BlockState state, final BlockEntity entity) {
		ItemStack extra = extraDrop(level, player, pos, state);
		if (level instanceof ServerLevel server && !extra.isEmpty()) {
			server.addFreshEntity(new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extra));
		}
	}

	/** The one extra ore drop a wearer earns at the roll, or an empty stack. */
	public static ItemStack extraDrop(final Level level, final Player player, final BlockPos pos, final BlockState state) {
		if (!(level instanceof ServerLevel server) || !state.is(BlockTags.ORES) || !worn(player)) {
			return ItemStack.EMPTY;
		}
		Supplier<RandomSource> source = SOURCES.get(player.getUUID());
		if (!roll(source == null ? server.getRandom() : source.get())) {
			return ItemStack.EMPTY;
		}
		for (ItemStack drop : Block.getDrops(state, server, pos, null)) {
			if (!drop.isEmpty()) {
				ItemStack extra = drop.copy();
				extra.setCount(1);
				return extra;
			}
		}
		return ItemStack.EMPTY;
	}
}
