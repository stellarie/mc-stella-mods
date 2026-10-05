package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;

/** The one line a camp check logs, so a passing run leaves a record of what it measured. */
final class MinerTestSupport {
	private MinerTestSupport() {
	}

	static void result(final String test, final String line) {
		StellaRuins.LOGGER.info("[RUINS] test={} RESULT {}", test, line);
	}
}
