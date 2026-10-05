package com.stella.ruins.site.sealedvault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The vaults that have woken, with the box each one fills. A vault wall asks here whether the vault around it is
 * complete. A vault that never woke is not listed, and its walls stay unbreakable.
 */
public final class VaultRegistry extends SavedData {
	private record Saved(BlockPos core, BlockPos min, BlockPos max) {
		private static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("core").forGetter(Saved::core),
			BlockPos.CODEC.fieldOf("min").forGetter(Saved::min),
			BlockPos.CODEC.fieldOf("max").forGetter(Saved::max)
		).apply(instance, Saved::new));

		boolean contains(final BlockPos pos) {
			return pos.getX() >= this.min.getX() && pos.getX() <= this.max.getX()
				&& pos.getY() >= this.min.getY() && pos.getY() <= this.max.getY()
				&& pos.getZ() >= this.min.getZ() && pos.getZ() <= this.max.getZ();
		}
	}

	public static final Codec<VaultRegistry> CODEC = Saved.CODEC.listOf().fieldOf("vaults").xmap(VaultRegistry::new, VaultRegistry::snapshot).codec();
	public static final SavedDataType<VaultRegistry> TYPE = new SavedDataType<>(StellaRuins.id("sealed_vaults"), VaultRegistry::new, CODEC, DataFixTypes.LEVEL);

	private final List<Saved> vaults = new ArrayList<>();

	public VaultRegistry() {
	}

	private VaultRegistry(final List<Saved> saved) {
		this.vaults.addAll(saved);
	}

	private List<Saved> snapshot() {
		return List.copyOf(this.vaults);
	}

	public static VaultRegistry get(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Lists a vault that woke. A core listed twice keeps its first box. */
	public void add(final BlockPos core, final BoundingBox box) {
		for (Saved saved : this.vaults) {
			if (saved.core.equals(core)) {
				return;
			}
		}
		this.vaults.add(new Saved(core.immutable(), new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
		setDirty();
	}

	public int size() {
		return this.vaults.size();
	}

	/** True when a listed vault holds this block and its core is complete. */
	public boolean opened(final ServerLevel level, final BlockPos pos) {
		RuinData data = RuinData.get(level);
		for (Saved saved : this.vaults) {
			if (saved.contains(pos)) {
				RuinData.Entry entry = data.entry(saved.core);
				if (entry != null && entry.phase() == RuinPhase.COMPLETE) {
					return true;
				}
			}
		}
		return false;
	}
}
