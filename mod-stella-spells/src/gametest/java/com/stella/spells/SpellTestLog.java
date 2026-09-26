package com.stella.spells;

import java.lang.reflect.Field;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SpellTestLog {
	private static final Field TEST_INFO_FIELD = testInfoField();

	private SpellTestLog() {
	}

	static Outcome track(final GameTestHelper helper, final String name) {
		try {
			GameTestInfo testInfo = (GameTestInfo) TEST_INFO_FIELD.get(helper);
			testInfo.addListener(new SummaryListener(name, LoggerFactory.getLogger("mod-stella-spells-test." + name)));
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException("Cannot read the GameTestInfo from GameTestHelper", exception);
		}
		return new Outcome();
	}

	private static Field testInfoField() {
		try {
			Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	static final class Outcome {
		void pass() {
		}

		void fail() {
		}
	}

	private static final class SummaryListener implements GameTestListener {
		private final String name;
		private final Logger logger;
		private boolean logged;

		private SummaryListener(final String name, final Logger logger) {
			this.name = name;
			this.logger = logger;
		}

		@Override
		public void testStructureLoaded(final GameTestInfo testInfo) {
		}

		@Override
		public void testPassed(final GameTestInfo testInfo, final GameTestRunner runner) {
			this.emit("PASS");
		}

		@Override
		public void testFailed(final GameTestInfo testInfo, final GameTestRunner runner) {
			this.emit("FAIL");
		}

		@Override
		public void testAddedForRerun(
			final GameTestInfo original, final GameTestInfo rerun, final GameTestRunner runner
		) {
		}

		private void emit(final String result) {
			if (this.logged) {
				return;
			}
			this.logged = true;
			this.logger.info("[SPELLTEST] test={} result={}", this.name, result);
		}
	}
}
