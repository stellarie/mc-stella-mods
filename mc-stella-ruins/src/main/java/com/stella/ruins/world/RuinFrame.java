package com.stella.ruins.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Where one placed site stands: its local frame in the world. Local x and z run over the site's own size and local
 * y over the size above the floor, with the foundation below it. A quarter turn rotates the frame clockwise about
 * the vertical axis. The frame holds the sizes it was placed with, so a site that later changes size still reads
 * back as it was built.
 *
 * @param min the world spot of local (0, 0, 0) after the turn: the lowest corner, at floor level
 */
public record RuinFrame(String site, BlockPos min, int turns, long seed, int sizeX, int sizeY, int sizeZ, int foundation) {
	public static final Codec<RuinFrame> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("site").forGetter(RuinFrame::site),
		BlockPos.CODEC.fieldOf("min").forGetter(RuinFrame::min),
		Codec.INT.fieldOf("turns").forGetter(RuinFrame::turns),
		Codec.LONG.fieldOf("seed").forGetter(RuinFrame::seed),
		Codec.INT.fieldOf("size_x").forGetter(RuinFrame::sizeX),
		Codec.INT.fieldOf("size_y").forGetter(RuinFrame::sizeY),
		Codec.INT.fieldOf("size_z").forGetter(RuinFrame::sizeZ),
		Codec.INT.fieldOf("foundation").forGetter(RuinFrame::foundation)
	).apply(instance, RuinFrame::new));

	public RuinFrame {
		turns = Math.floorMod(turns, 4);
	}

	public static RuinFrame of(final RuinSite site, final BlockPos min, final Rotation rotation, final long seed) {
		return new RuinFrame(site.id(), min, rotation.ordinal(), seed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
	}

	public Rotation rotation() {
		return Rotation.values()[this.turns];
	}

	/** The world extent along x, after the turn. */
	public int worldSizeX() {
		return this.turns % 2 == 0 ? this.sizeX : this.sizeZ;
	}

	/** The world extent along z, after the turn. */
	public int worldSizeZ() {
		return this.turns % 2 == 0 ? this.sizeZ : this.sizeX;
	}

	/** The box the site may write in, foundation included. */
	public BoundingBox box() {
		return new BoundingBox(
			this.min.getX(), this.min.getY() - this.foundation, this.min.getZ(),
			this.min.getX() + worldSizeX() - 1, this.min.getY() + this.sizeY - 1, this.min.getZ() + worldSizeZ() - 1
		);
	}

	/** Maps a local spot to the world. A clockwise quarter turn sends local north to world east. */
	public BlockPos toWorld(final int x, final int y, final int z) {
		int worldX;
		int worldZ;
		switch (this.turns) {
			case 1 -> {
				worldX = this.sizeZ - 1 - z;
				worldZ = x;
			}
			case 2 -> {
				worldX = this.sizeX - 1 - x;
				worldZ = this.sizeZ - 1 - z;
			}
			case 3 -> {
				worldX = z;
				worldZ = this.sizeX - 1 - x;
			}
			default -> {
				worldX = x;
				worldZ = z;
			}
		}
		return new BlockPos(this.min.getX() + worldX, this.min.getY() + y, this.min.getZ() + worldZ);
	}

	/** The site this frame names, or null when its mod no longer registers it. */
	public @Nullable RuinSite siteOrNull() {
		return RuinSites.get(this.site);
	}
}
