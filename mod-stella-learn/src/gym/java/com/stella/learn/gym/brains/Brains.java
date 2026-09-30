package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;

/**
 * The roster. A new brain is a class plus one line in LEAVES. The order fixes the index the trainer sends in a Reset
 * frame, and `training/stella_train/roster.py` lists the same names in the same order.
 */
public final class Brains {
	private record Entry(String name, Supplier<BotBrain> make) {
	}

	private static final List<Entry> LEAVES = List.of(
		new Entry("CritRusher", CritRusher::new),
		new Entry("SwordSweeper", SwordSweeper::new),
		new Entry("AxeBreaker", AxeBreaker::new),
		new Entry("ShieldTurtle", ShieldTurtle::new),
		new Entry("StrafeDuelist", StrafeDuelist::new),
		new Entry("BowKiter", BowKiter::new),
		new Entry("CrossbowSniper", CrossbowSniper::new),
		new Entry("HighGroundShooter", HighGroundShooter::new),
		new Entry("PillarUp", PillarUp::new),
		new Entry("BlockTrapper", BlockTrapper::new),
		new Entry("DoorwayHolder", DoorwayHolder::new),
		new Entry("Burrower", Burrower::new),
		new Entry("Bunker", Bunker::new),
		new Entry("Tunneler", Tunneler::new),
		new Entry("PotionHealer", PotionHealer::new),
		new Entry("ArmorTank", ArmorTank::new),
		new Entry("PearlEscaper", PearlEscaper::new)
	);
	public static final int STYLE_SWITCHER = LEAVES.size();
	public static final int RANDOM_MIX = LEAVES.size() + 1;
	public static final int COUNT = LEAVES.size() + 2;
	/** Not part of the roster: the band 0 target, which the trainer names in a Reset frame. */
	public static final int PASSIVE = 100;
	/** The band before it: a target that stands still. */
	public static final int STATIC = 101;

	/** True for the two practice targets, which sit outside the roster. */
	public static boolean isTarget(final int index) {
		return index == PASSIVE || index == STATIC;
	}

	private Brains() {
	}

	public static String name(final int index) {
		if (index == PASSIVE) {
			return "Passive";
		}

		if (index == STATIC) {
			return "Static";
		}

		if (index == STYLE_SWITCHER) {
			return "StyleSwitcher";
		}

		return index == RANDOM_MIX ? "RandomMix" : LEAVES.get(index).name();
	}

	public static int indexOf(final String name) {
		if ("Passive".equals(name)) {
			return PASSIVE;
		}

		if ("Static".equals(name)) {
			return STATIC;
		}

		for (int i = 0; i < COUNT; i++) {
			if (name(i).equals(name)) {
				return i;
			}
		}

		throw new IllegalArgumentException("unknown brain " + name);
	}

	/** A fresh brain. The random source draws the delegates of the two meta brains, so a seed fixes them. */
	public static BotBrain create(final int index, final RandomSource random) {
		if (index == PASSIVE) {
			return new Passive();
		}

		if (index == STATIC) {
			return new Static();
		}

		if (index == STYLE_SWITCHER) {
			int a = random.nextInt(LEAVES.size());
			int b = (a + 1 + random.nextInt(LEAVES.size() - 1)) % LEAVES.size();
			return new StyleSwitcher(LEAVES.get(a).make().get(), LEAVES.get(b).make().get(), StyleSwitcher.drawSwitchTick(random));
		}

		if (index == RANDOM_MIX) {
			return new RandomMix(LEAVES.get(random.nextInt(LEAVES.size())).make().get());
		}

		if (index < 0 || index >= LEAVES.size()) {
			throw new IllegalArgumentException("no brain at index " + index);
		}

		return LEAVES.get(index).make().get();
	}
}
