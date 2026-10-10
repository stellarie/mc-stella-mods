package com.stella.rpg;

import com.stella.rpg.net.StatsSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Draw speed of bows and crossbows. AGI and DEX shorten a full draw by {@code PER_POINT} each, at most {@code CAP}.
 * The server reads the stat sheet. The client has no sheet, so it reads the payload the server syncs to its own player.
 */
public final class DrawSpeed {
	public static final double PER_POINT = 0.0025;
	public static final double CAP = 0.50;
	public static final int VANILLA_DRAW_TICKS = 20;

	/** The client side stat source. It returns the reduction for the entity, or 0 when the entity is not the local player. */
	@FunctionalInterface
	public interface ClientSource {
		double reduction(LivingEntity entity);
	}

	private static volatile ClientSource clientSource = entity -> 0.0;

	private DrawSpeed() {
	}

	/** The client calls this once, so a server side class never loads a client class. */
	public static void setClientSource(final ClientSource source) {
		clientSource = source == null ? entity -> 0.0 : source;
	}

	public static double reduction(final int agi, final int dex) {
		return Math.min(CAP, PER_POINT * (Math.max(0, agi) + Math.max(0, dex)));
	}

	/** The reduction of a player on either side. A mob and a remote player draw at the vanilla speed. */
	public static double reductionOf(final LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return reduction(StatBonuses.effective(player, Stat.AGI), StatBonuses.effective(player, Stat.DEX));
		}
		if (entity instanceof Player player && player.level().isClientSide()) {
			return clientSource.reduction(player);
		}
		return 0.0;
	}

	/** The reduction from the synced payload, built exactly as {@link StatBonuses#effective} builds a player's stat. */
	public static double reduction(final StatsSyncPayload payload) {
		return reduction(effective(payload, Stat.AGI), effective(payload, Stat.DEX));
	}

	private static int effective(final StatsSyncPayload payload, final Stat stat) {
		int total = payload.base()[stat.ordinal()] + payload.bonus()[stat.ordinal()];
		return Math.max(0, Math.min(StatBonuses.EFFECTIVE_CEILING, total));
	}

	public static int fullDrawTicks(final int agi, final int dex) {
		long ticks = Math.round(VANILLA_DRAW_TICKS * (1.0 - reduction(agi, dex)));
		return (int) Math.max(1, Math.min(VANILLA_DRAW_TICKS, ticks));
	}
}
