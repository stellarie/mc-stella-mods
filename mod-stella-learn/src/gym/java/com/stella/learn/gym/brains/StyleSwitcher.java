package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import java.util.Set;
import net.minecraft.util.RandomSource;

/** Fights in one style, then changes to a second one partway through the episode. */
public final class StyleSwitcher implements BotBrain {
	private static final int EARLIEST = 60;
	private static final int SPREAD = 120;
	private final BotBrain first;
	private final BotBrain second;
	private final int switchTick;
	private BotBrain current;

	public StyleSwitcher(final BotBrain first, final BotBrain second, final int switchTick) {
		this.first = first;
		this.second = second;
		this.switchTick = switchTick;
		this.current = first;
	}

	public static int drawSwitchTick(final RandomSource random) {
		return EARLIEST + random.nextInt(SPREAD);
	}

	@Override
	public String name() {
		return "StyleSwitcher";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		if (current == first && view.tick() >= switchTick) {
			current = second;
		}

		current.tick(view, input);
	}

	@Override
	public void active(final Set<String> names) {
		current.active(names);
	}

	public BotBrain first() {
		return first;
	}

	public BotBrain second() {
		return second;
	}
}
