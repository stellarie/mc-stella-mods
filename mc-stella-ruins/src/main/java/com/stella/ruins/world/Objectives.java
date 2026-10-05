package com.stella.ruins.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The objectives every site may use. A site slice may write its own {@link Objective} in its own package. */
public final class Objectives {
	private Objectives() {
	}

	/** Kill the whole roster. */
	public static Objective clear() {
		return new Clear();
	}

	/** Kill each wave in turn. The next wave spawns when the last one is dead. */
	public static Objective waves(final WavePlan plan) {
		return new Waves(plan);
	}

	/** Use the core with an item the predicate accepts. One item is spent. */
	public static Objective offer(final Predicate<ItemStack> accepts) {
		return new Offer(accepts);
	}

	/** Use the core with {@code count} of a key item, one use for each. Each is spent. */
	public static Objective keys(final Item key, final int count) {
		return new Keys(key, count);
	}

	/** Run the objectives in order. The next one starts when the last one completes. */
	public static Objective sequence(final Objective... steps) {
		return new Sequence(List.of(steps));
	}

	private static final class Clear implements Objective {
		@Override
		public void start(final SiteRun run) {
		}

		@Override
		public Progress tick(final SiteRun run) {
			return run.alive() == 0 ? Progress.COMPLETE : Progress.RUNNING;
		}

		@Override
		public CompoundTag save() {
			return new CompoundTag();
		}

		@Override
		public void load(final CompoundTag tag) {
		}
	}

	private static final class Waves implements Objective {
		private final WavePlan plan;
		private int wave;

		private Waves(final WavePlan plan) {
			this.plan = plan;
		}

		@Override
		public void start(final SiteRun run) {
			this.wave = 0;
			if (!this.plan.waves().isEmpty()) {
				run.spawn(this.plan.waves().get(0));
			}
		}

		@Override
		public Progress tick(final SiteRun run) {
			if (this.plan.waves().isEmpty()) {
				return Progress.COMPLETE;
			}
			if (run.alive() > 0) {
				return Progress.RUNNING;
			}
			if (this.wave + 1 >= this.plan.waves().size()) {
				return Progress.COMPLETE;
			}
			this.wave++;
			run.spawn(this.plan.waves().get(this.wave));
			return Progress.RUNNING;
		}

		@Override
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("wave", this.wave);
			return tag;
		}

		@Override
		public void load(final CompoundTag tag) {
			this.wave = tag.getIntOr("wave", 0);
		}
	}

	private static final class Offer implements Objective {
		private final Predicate<ItemStack> accepts;
		private boolean offered;

		private Offer(final Predicate<ItemStack> accepts) {
			this.accepts = accepts;
		}

		@Override
		public void start(final SiteRun run) {
			this.offered = false;
		}

		@Override
		public Progress tick(final SiteRun run) {
			return this.offered ? Progress.COMPLETE : Progress.RUNNING;
		}

		@Override
		public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
			if (this.offered || held.isEmpty() || !this.accepts.test(held)) {
				return false;
			}
			held.shrink(1);
			this.offered = true;
			return true;
		}

		@Override
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("offered", this.offered);
			return tag;
		}

		@Override
		public void load(final CompoundTag tag) {
			this.offered = tag.getBooleanOr("offered", false);
		}
	}

	private static final class Keys implements Objective {
		private final Item key;
		private final int count;
		private int used;

		private Keys(final Item key, final int count) {
			if (count < 1) {
				throw new IllegalArgumentException("a keys objective needs at least one key");
			}
			this.key = key;
			this.count = count;
		}

		@Override
		public void start(final SiteRun run) {
			this.used = 0;
		}

		@Override
		public Progress tick(final SiteRun run) {
			return this.used >= this.count ? Progress.COMPLETE : Progress.RUNNING;
		}

		@Override
		public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
			if (this.used >= this.count || !held.is(this.key)) {
				return false;
			}
			held.shrink(1);
			this.used++;
			return true;
		}

		@Override
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("used", this.used);
			return tag;
		}

		@Override
		public void load(final CompoundTag tag) {
			this.used = tag.getIntOr("used", 0);
		}
	}

	private static final class Sequence implements Objective {
		private final List<Objective> steps;
		private int index;

		private Sequence(final List<Objective> steps) {
			this.steps = steps;
		}

		@Override
		public void start(final SiteRun run) {
			this.index = 0;
			if (!this.steps.isEmpty()) {
				this.steps.get(0).start(run);
			}
		}

		@Override
		public Progress tick(final SiteRun run) {
			if (this.index >= this.steps.size()) {
				return Progress.COMPLETE;
			}
			if (this.steps.get(this.index).tick(run) == Progress.COMPLETE) {
				this.index++;
				if (this.index >= this.steps.size()) {
					return Progress.COMPLETE;
				}
				this.steps.get(this.index).start(run);
			}
			return Progress.RUNNING;
		}

		@Override
		public void onKill(final SiteRun run, final LivingEntity mob) {
			if (this.index < this.steps.size()) {
				this.steps.get(this.index).onKill(run, mob);
			}
		}

		@Override
		public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
			return this.index < this.steps.size() && this.steps.get(this.index).onUse(run, player, held);
		}

		@Override
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("index", this.index);
			for (int i = 0; i < this.steps.size(); i++) {
				tag.put("step" + i, this.steps.get(i).save());
			}
			return tag;
		}

		@Override
		public void load(final CompoundTag tag) {
			this.index = tag.getIntOr("index", 0);
			for (int i = 0; i < this.steps.size(); i++) {
				this.steps.get(i).load(tag.getCompoundOrEmpty("step" + i));
			}
		}
	}
}
