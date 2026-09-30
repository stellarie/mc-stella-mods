package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.slf4j.LoggerFactory;

final class SpellLogCapture extends AbstractAppender implements AutoCloseable {
	private static final org.slf4j.Logger TEST_OUTPUT = LoggerFactory.getLogger("mod-stella-spells-test-output");
	private final Logger logger;
	private final Level previousLevel;
	private final List<String> messages = new ArrayList<>();

	SpellLogCapture() {
		this("mod-stella-spells");
	}

	SpellLogCapture(final String loggerName) {
		super("spell-log-capture", null, null, true);
		this.logger = (Logger) LogManager.getLogger(loggerName);
		this.previousLevel = this.logger.getLevel();
		this.start();
		this.logger.addAppender(this);
		this.logger.setLevel(Level.DEBUG);
	}

	@Override
	public void append(final LogEvent event) {
		this.messages.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
	}

	boolean contains(final String... fragments) {
		return this.count(fragments) > 0;
	}

	int count(final String... fragments) {
		return (int) this.messages.stream().filter(message -> matches(message, fragments)).count();
	}

	void echoOne(final String... fragments) {
		String message = this.messages.stream()
			.filter(value -> matches(value, fragments))
			.findFirst()
			.orElseThrow();
		TEST_OUTPUT.info("[SPELLTEST] captured={}", message);
	}

	private static boolean matches(final String message, final String[] fragments) {
		for (String fragment : fragments) {
			if (!message.contains(fragment)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void close() {
		this.logger.removeAppender(this);
		this.stop();
		this.logger.setLevel(this.previousLevel);
	}
}
