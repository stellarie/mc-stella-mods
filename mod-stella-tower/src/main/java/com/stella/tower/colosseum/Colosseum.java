package com.stella.tower.colosseum;

import com.stella.tower.StellaTower;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The ruined colosseum: one per world, 96 to 256 blocks from the spawn (never more than 1024), 32 blocks in radius. Its tiered seats, broken
 * columns and walls ring a sand arena. A raised platform at the middle carries a lit crying obsidian portal to the tower,
 * and an offering chest. Two Tower Guardians keep it.
 * <p>
 * The first start of a world builds it, in code, and saves the claim ({@link ColosseumData}); every later start only
 * looks the claim up.
 */
public final class Colosseum {
	/** The ring wall's outer radius, and the guardians' leash. */
	public static final int RADIUS = 32;
	public static final int MIN_FROM_SPAWN = 96;
	public static final int MAX_FROM_SPAWN = 256;
	/** The reach of the second pass, when every site in the first band is taken. The hard limit is 1024. */
	public static final int WIDE_FROM_SPAWN = 1000;
	public static final int PLATFORM_RADIUS = 6;
	public static final int PLATFORM_HEIGHT = 4;
	/** Two stacks of bones raise the guardians again. */
	public static final int OFFERING_BONES = 128;

	private Colosseum() {
	}

	/** Where everything stands, from the centre column at the floor layer. */
	public record Layout(BlockPos floor) {
		/** The inside's bottom-left block of the portal frame: two wide along x, three high. */
		public BlockPos portalInside() {
			return this.floor.above(PLATFORM_HEIGHT + 1);
		}

		public BlockPos chest() {
			return this.floor.offset(4, PLATFORM_HEIGHT + 1, 0);
		}

		/** Where a guardian stands: the melee one west of the platform, the ranged one east. */
		public BlockPos post(final GuardianKind kind) {
			return this.floor.offset(kind == GuardianKind.MELEE ? -11 : 11, 1, 0);
		}

		public Guardians.Home home(final GuardianKind kind) {
			return new Guardians.Home(this.floor, RADIUS, post(kind));
		}
	}

	/** What {@link #ensure} found or made. */
	public record Result(Layout layout, boolean built) {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Colosseum::onStarted);
		Guardians.register();
		OfferingChest.register();
	}

	private static void onStarted(final MinecraftServer server) {
		try {
			Result result = ensure(server.overworld());
			if (result != null) {
				StellaTower.LOGGER.info("[COLOSSEUM] floor={} built={}", result.layout().floor(), result.built());
			}
		} catch (RuntimeException failure) {
			StellaTower.LOGGER.warn("[COLOSSEUM] the colosseum could not be built: {}", failure.toString());
		}
	}

	/**
	 * The colosseum of this world, built first if the world holds none; null if no site could be built on. A site where
	 * someone has built is passed over, the next best is tried, and if every one is taken nothing is built or claimed.
	 */
	public static @Nullable Result ensure(final ServerLevel overworld) {
		ColosseumData data = ColosseumData.get(overworld);
		BlockPos claimed = data.floor();
		if (claimed != null) {
			return new Result(new Layout(claimed), false);
		}
		for (int reach : new int[] {MAX_FROM_SPAWN, WIDE_FROM_SPAWN}) {
			Result result = tryBand(overworld, data, reach);
			if (result != null) {
				return result;
			}
		}
		StellaTower.LOGGER.warn("[COLOSSEUM] no site fits near the spawn");
		return null;
	}

	private static @Nullable Result tryBand(final ServerLevel overworld, final ColosseumData data, final int reach) {
		long began = System.nanoTime();
		List<BlockPos> sites = ColosseumSite.candidates(overworld, reach);
		StellaTower.LOGGER.info("[COLOSSEUM] {} sites drawn in {} ms (reach {})", sites.size(), (System.nanoTime() - began) / 1_000_000L, reach);
		for (BlockPos floor : sites) {
			Layout layout = new Layout(floor);
			began = System.nanoTime();
			if (!ColosseumBuilder.build(overworld, layout)) {
				StellaTower.LOGGER.info("[COLOSSEUM] {} is built on already; passed over", floor);
				continue;
			}
			StellaTower.LOGGER.info("[COLOSSEUM] built in {} ms", (System.nanoTime() - began) / 1_000_000L);
			data.claim(floor, overworld.getRespawnData().pos());
			Guardians.raise(overworld, layout);
			return new Result(layout, true);
		}
		return null;
	}

	/**
	 * Builds a colosseum at a site, claiming nothing and raising nobody: false, with nothing touched, if someone has built there.
	 * The world's own colosseum goes through {@link #ensure}; this is the builder on its own.
	 */
	public static boolean buildAt(final ServerLevel overworld, final BlockPos floor) {
		return ColosseumBuilder.build(overworld, new Layout(floor));
	}

	/** The built colosseum, if the world holds one. */
	public static @Nullable Layout layoutOf(final ServerLevel overworld) {
		BlockPos claimed = ColosseumData.get(overworld).floor();
		return claimed == null ? null : new Layout(claimed);
	}
}
