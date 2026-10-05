package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.RuinClues;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * The Miner's Compass. One use aims it at the nearest large
 * ruin within the search radius, as a lodestone target does.
 */
public final class MinersCompassItem extends Item {
	/** The large ruins the compass may point at. */
	public static final List<String> LARGE_SITES = List.of("deep_forge", "ruined_keep", "sealed_vault", "proving_grounds");
	public static final int SEARCH_CHUNKS = RuinClues.SEARCH_CHUNKS;
	/** The reach of one search, in blocks. */
	public static final int SEARCH_BLOCKS = SEARCH_CHUNKS * 16;
	public static final String NOTHING_KEY = "message.mod-stella-ruins.camp.compass_nothing";
	public static final String FOUND_KEY = "message.mod-stella-ruins.camp.compass_found";
	/** The camp's own name for each large ruin, for the message. */
	public static final String RUIN_KEY_PREFIX = "message.mod-stella-ruins.camp.ruin.";

	/** One found ruin: its site and where it stands. */
	public record Target(String site, GlobalPos pos) {
	}

	public MinersCompassItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		long start = System.nanoTime();
		Optional<Target> target = nearest(server, player.blockPosition());
		long millis = (System.nanoTime() - start) / 1_000_000L;
		StellaRuins.LOGGER.debug("[RUINS] camp compass search took {} ms found={}", millis, target.map(found -> found.site() + " " + found.pos().pos().toShortString()).orElse("none"));
		if (target.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback(NOTHING_KEY, "The compass finds no great ruin within " + SEARCH_CHUNKS + " chunks."));
			return InteractionResult.CONSUME;
		}
		Target found = target.get();
		aim(stack, found.pos());
		Component name = Component.translatableWithFallback(RUIN_KEY_PREFIX + found.site(), found.site());
		serverPlayer.sendOverlayMessage(Component.translatableWithFallback(FOUND_KEY, "The needle swings toward %s.", name));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Writes the target onto the stack, the way a lodestone compass carries one. */
	public static void aim(final ItemStack stack, final GlobalPos target) {
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
	}

	/** The nearest large ruin within {@link #SEARCH_BLOCKS} blocks, or nothing. */
	public static Optional<Target> nearest(final ServerLevel level, final BlockPos from) {
		Target best = null;
		double bestDistance = (double) SEARCH_BLOCKS * SEARCH_BLOCKS;
		for (String id : LARGE_SITES) {
			Optional<BlockPos> found = RuinClues.locate(level, id, from);
			if (found.isEmpty()) {
				continue;
			}
			double distance = found.get().distSqr(from);
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = new Target(id, GlobalPos.of(level.dimension(), found.get()));
			}
		}
		return Optional.ofNullable(best);
	}
}
