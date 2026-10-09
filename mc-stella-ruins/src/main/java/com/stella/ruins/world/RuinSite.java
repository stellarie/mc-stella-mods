package com.stella.ruins.world;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * One kind of structure: its place, its plan, its objective, its mobs and its payout. A site slice builds one with
 * {@link #builder} and registers it; the structure and its data files name it by id.
 */
public final class RuinSite {
	/** The largest box a site may fill, in blocks. */
	public static final int MAX_X = 64;
	public static final int MAX_Y = 48;
	public static final int MAX_Z = 64;
	public static final int DEFAULT_ACTIVATION_RADIUS = 24;

	private final String id;
	private final SiteSize size;
	private final Placement placement;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final SiteLayout layout;
	private final Claim claim;
	private final Supplier<Objective> objective;
	private final Roster roster;
	private final int activationRadius;
	private final ResourceKey<LootTable> vaultLoot;
	private final @Nullable Supplier<ItemStack> unique;
	private final int clueWeight;
	private final List<String> leads;
	private final @Nullable RearmHook rearmHook;
	private final boolean omens;

	private RuinSite(final Builder b) {
		this.id = b.id;
		this.size = b.size;
		this.placement = b.placement;
		this.sizeX = b.sizeX;
		this.sizeY = b.sizeY;
		this.sizeZ = b.sizeZ;
		this.layout = b.layout;
		this.claim = b.claim;
		this.objective = b.objective;
		this.roster = b.roster;
		this.activationRadius = b.activationRadius;
		this.vaultLoot = b.vaultLoot;
		this.unique = b.unique;
		this.clueWeight = b.clueWeight;
		this.leads = List.copyOf(b.leads);
		this.rearmHook = b.rearmHook;
		this.omens = b.omens;
	}

	public static Builder builder(final String id) {
		return new Builder(id);
	}

	public String id() {
		return this.id;
	}

	public SiteSize size() {
		return this.size;
	}

	public Placement placement() {
		return this.placement;
	}

	public int sizeX() {
		return this.sizeX;
	}

	public int sizeY() {
		return this.sizeY;
	}

	public int sizeZ() {
		return this.sizeZ;
	}

	public SiteLayout layout() {
		return this.layout;
	}

	public Claim claim() {
		return this.claim;
	}

	/** A fresh objective, to be loaded from saved state or started. */
	public Objective newObjective() {
		return this.objective.get();
	}

	public Roster roster() {
		return this.roster;
	}

	public int activationRadius() {
		return this.activationRadius;
	}

	public ResourceKey<LootTable> vaultLoot() {
		return this.vaultLoot;
	}

	/** The site's unique item, or an empty stack when the site has none. */
	public ItemStack newUnique() {
		return this.unique == null ? ItemStack.EMPTY : this.unique.get();
	}

	public boolean hasUnique() {
		return this.unique != null;
	}

	public int clueWeight() {
		return this.clueWeight;
	}

	/** What a site does when its core rearms, such as growing back what a clear spent. */
	@FunctionalInterface
	public interface RearmHook {
		void onRearm(ServerLevel level, BlockPos core, Map<String, List<BlockPos>> markers);
	}

	public @Nullable RearmHook rearmHook() {
		return this.rearmHook;
	}

	/** True when a run of this site can carry omens. */
	public boolean omens() {
		return this.omens;
	}

	/** The sites a clear of this site can point to, as clues in the vault. */
	public List<String> leads() {
		return this.leads;
	}

	public static final class Builder {
		private final String id;
		private SiteSize size = SiteSize.SMALL;
		private @Nullable Placement placement;
		private int sizeX;
		private int sizeY;
		private int sizeZ;
		private @Nullable SiteLayout layout;
		private Claim claim = Claim.once();
		private @Nullable Supplier<Objective> objective;
		private Roster roster = Roster.empty();
		private int activationRadius = DEFAULT_ACTIVATION_RADIUS;
		private @Nullable ResourceKey<LootTable> vaultLoot;
		private @Nullable Supplier<ItemStack> unique;
		private int clueWeight;
		private List<String> leads = List.of();
		private @Nullable RearmHook rearmHook;
		private boolean omens = true;

		private Builder(final String id) {
			if (id == null || id.isEmpty() || !id.matches("[a-z0-9_/.-]+")) {
				throw new IllegalArgumentException("a site id is lower case letters, digits and _ / . -: " + id);
			}
			this.id = id;
		}

		public Builder size(final SiteSize value) {
			this.size = value;
			return this;
		}

		public Builder placement(final Placement value) {
			this.placement = value;
			return this;
		}

		/** The box the site fills, at most 64 x 48 x 64. */
		public Builder bounds(final int x, final int y, final int z) {
			if (x < 1 || y < 1 || z < 1 || x > MAX_X || y > MAX_Y || z > MAX_Z) {
				throw new IllegalArgumentException("site " + this.id + " bounds " + x + "x" + y + "x" + z + " are outside 1.." + MAX_X + " x 1.." + MAX_Y + " x 1.." + MAX_Z);
			}
			this.sizeX = x;
			this.sizeY = y;
			this.sizeZ = z;
			return this;
		}

		public Builder layout(final SiteLayout value) {
			this.layout = value;
			return this;
		}

		public Builder claim(final Claim value) {
			this.claim = value;
			return this;
		}

		public Builder objective(final Supplier<Objective> value) {
			this.objective = value;
			return this;
		}

		public Builder roster(final Roster value) {
			this.roster = value;
			return this;
		}

		public Builder activationRadius(final int blocks) {
			if (blocks < 1) {
				throw new IllegalArgumentException("the activation radius is below 1");
			}
			this.activationRadius = blocks;
			return this;
		}

		public Builder vaultLoot(final ResourceKey<LootTable> table) {
			this.vaultLoot = table;
			return this;
		}

		public Builder unique(final Supplier<ItemStack> value) {
			this.unique = value;
			return this;
		}

		/** The share of clues that point here. Zero means no clue. */
		public Builder clueWeight(final int weight) {
			if (weight < 0) {
				throw new IllegalArgumentException("the clue weight is negative");
			}
			this.clueWeight = weight;
			return this;
		}

		public Builder onRearm(final RearmHook hook) {
			this.rearmHook = hook;
			return this;
		}

		/** A site whose core never completes, such as an arena, carries no omens. */
		public Builder noOmens() {
			this.omens = false;
			return this;
		}

		/** The sites a clear may pay a clue to. A clue to an unknown site is a data error, so register() checks nothing about them. */
		public Builder leads(final String... sites) {
			this.leads = List.of(sites);
			return this;
		}

		/** Checks that nothing is missing, adds the site to the registry and returns it. */
		public RuinSite register() {
			if (this.placement == null || this.layout == null || this.objective == null || this.vaultLoot == null || this.sizeX == 0) {
				throw new IllegalStateException("site " + this.id + " needs a placement, bounds, a layout, an objective and a vault table");
			}
			RuinSite site = new RuinSite(this);
			RuinSites.add(site);
			return site;
		}
	}
}
