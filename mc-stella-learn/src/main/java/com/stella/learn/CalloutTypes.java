package com.stella.learn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The registered callout types. Another mod adds its own by id, so "anything goes" needs no change here. */
public final class CalloutTypes {
	private static final Map<Identifier, CalloutHandler> HANDLERS = new ConcurrentHashMap<>();

	private CalloutTypes() {
	}

	public static void register(final Identifier type, final CalloutHandler handler) {
		HANDLERS.put(type, handler);
	}

	public static @Nullable CalloutHandler get(final Identifier type) {
		return HANDLERS.get(type);
	}
}
