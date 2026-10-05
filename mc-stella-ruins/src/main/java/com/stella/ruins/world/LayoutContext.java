package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * What a {@link SiteLayout} draws with. Every call takes a spot in the site's local frame, drawn as if the site
 * faced north; the context turns the spot and the block state into the world. A write outside the current chunk
 * box, or outside the site's own box, is dropped, so a layout may draw its whole plan on every call.
 * <p>
 * Three kinds of context share this class. A world context writes to a level. A capture context writes into a
 * map, for tests. A dry run writes nothing and only collects markers; the core uses it to recover its spawn points
 * after a reload.
 * <p>
 * {@link #get} reads only inside the current chunk box and gives air elsewhere, so a layout that looks at its own
 * blocks still draws the same plan whatever order the chunks come in.
 */
public final class LayoutContext {
	private static final long SALT_MIX = 0x9E3779B97F4A7C15L;

	/** Where the blocks go. */
	private interface Canvas {
		void set(BlockPos pos, BlockState state);

		BlockState get(BlockPos pos);

		default void afterCore(final BlockPos pos, final RuinFrame frame) {
		}

		default void afterChest(final BlockPos pos, final RandomSource random, final ResourceKey<LootTable> table) {
		}
	}

	private final RuinFrame frame;
	private final Canvas canvas;
	private final BoundingBox clip;
	private final BoundingBox box;
	private final Map<String, List<BlockPos>> markers = new LinkedHashMap<>();

	private LayoutContext(final RuinFrame frame, final Canvas canvas, final BoundingBox clip) {
		this.frame = frame;
		this.canvas = canvas;
		this.clip = clip;
		this.box = frame.box();
	}

	/** A context that writes to a level, inside one chunk's box. */
	public static LayoutContext forWorld(final RuinFrame frame, final WorldGenLevel level, final BoundingBox clip) {
		return new LayoutContext(frame, new Canvas() {
			@Override
			public void set(final BlockPos pos, final BlockState state) {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}

			@Override
			public BlockState get(final BlockPos pos) {
				return level.getBlockState(pos);
			}

			@Override
			public void afterCore(final BlockPos pos, final RuinFrame placed) {
				if (level.getBlockEntity(pos) instanceof RuinCoreBlockEntity core) {
					core.init(placed);
				}
			}

			@Override
			public void afterChest(final BlockPos pos, final RandomSource random, final ResourceKey<LootTable> table) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos, table);
			}
		}, clip);
	}

	/** A context that writes into a map, for tests; the map holds world positions. */
	public static LayoutContext forCapture(final RuinFrame frame, final Map<BlockPos, BlockState> sink, final BoundingBox clip) {
		return new LayoutContext(frame, new Canvas() {
			@Override
			public void set(final BlockPos pos, final BlockState state) {
				sink.put(pos.immutable(), state);
			}

			@Override
			public BlockState get(final BlockPos pos) {
				return sink.getOrDefault(pos, Blocks.AIR.defaultBlockState());
			}
		}, clip);
	}

	/** A context that writes nothing and collects markers. */
	public static LayoutContext forDryRun(final RuinFrame frame) {
		return new LayoutContext(frame, null, new BoundingBox(BlockPos.ZERO));
	}

	public RuinFrame frame() {
		return this.frame;
	}

	/** The site's own size along local x. */
	public int sizeX() {
		return this.frame.sizeX();
	}

	public int sizeY() {
		return this.frame.sizeY();
	}

	public int sizeZ() {
		return this.frame.sizeZ();
	}

	/** How far below local y 0 the layout may draw. */
	public int foundation() {
		return this.frame.foundation();
	}

	/** A random source that starts from the piece seed on every call, so every chunk draws the same plan. */
	public RandomSource random() {
		return RandomSource.create(this.frame.seed());
	}

	/** Like {@link #random()}, with a salt, for a part of the plan that needs its own stream. */
	public RandomSource random(final long salt) {
		return RandomSource.create(this.frame.seed() ^ (salt + 1L) * SALT_MIX);
	}

	public void set(final int x, final int y, final int z, final BlockState state) {
		if (this.canvas == null) {
			return;
		}
		BlockPos pos = this.frame.toWorld(x, y, z);
		if (inside(pos)) {
			this.canvas.set(pos, state.rotate(this.frame.rotation()));
		}
	}

	/** Fills the cuboid between two local corners, both inclusive. */
	public void fill(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState state) {
		if (this.canvas == null) {
			return;
		}
		BlockState turned = state.rotate(this.frame.rotation());
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					BlockPos pos = this.frame.toWorld(x, y, z);
					if (inside(pos)) {
						this.canvas.set(pos, turned);
					}
				}
			}
		}
	}

	/** The block at a local spot, or air outside the current chunk box. */
	public BlockState get(final int x, final int y, final int z) {
		BlockPos pos = this.frame.toWorld(x, y, z);
		if (this.canvas == null || !inside(pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.canvas.get(pos);
	}

	/** Places the objective core. The core reads its site back from the frame. */
	public void placeCore(final int x, final int y, final int z) {
		marker(RuinMarkers.CORE, x, y, z);
		BlockPos pos = this.frame.toWorld(x, y, z);
		if (this.canvas != null && inside(pos)) {
			this.canvas.set(pos, RuinBlocks.CORE.defaultBlockState());
			this.canvas.afterCore(pos, this.frame);
		}
	}

	/** Places the locked vault. Its core finds it again through the {@code vault} marker. */
	public void placeVault(final int x, final int y, final int z) {
		marker(RuinMarkers.VAULT, x, y, z);
		set(x, y, z, RuinBlocks.VAULT.defaultBlockState());
	}

	/** Places a chest that rolls {@code table} when it is first opened. It faces local north. */
	public void placeChest(final int x, final int y, final int z, final ResourceKey<LootTable> table) {
		placeChest(x, y, z, Direction.NORTH, table);
	}

	public void placeChest(final int x, final int y, final int z, final Direction facing, final ResourceKey<LootTable> table) {
		BlockPos pos = this.frame.toWorld(x, y, z);
		if (this.canvas == null || !inside(pos)) {
			return;
		}
		this.canvas.set(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).rotate(this.frame.rotation()));
		this.canvas.afterChest(pos, random(pos.asLong()), table);
	}

	/** Names a spot, such as {@code spawn}. A marker records in every kind of context, wherever the chunk box lies. */
	public void marker(final String name, final int x, final int y, final int z) {
		this.markers.computeIfAbsent(name, key -> new ArrayList<>()).add(this.frame.toWorld(x, y, z));
	}

	/** Every marker this draw collected, in the order named. */
	public Map<String, List<BlockPos>> markers() {
		Map<String, List<BlockPos>> copy = new LinkedHashMap<>();
		this.markers.forEach((name, spots) -> copy.put(name, Collections.unmodifiableList(spots)));
		return Collections.unmodifiableMap(copy);
	}

	private boolean inside(final BlockPos pos) {
		return this.clip.isInside(pos) && this.box.isInside(pos);
	}
}
