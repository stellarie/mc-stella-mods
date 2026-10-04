package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Every registered site, by id, in the order registered. */
public final class RuinSites {
	private static final Map<String, RuinSite> SITES = new LinkedHashMap<>();

	private RuinSites() {
	}

	static synchronized void add(final RuinSite site) {
		if (SITES.putIfAbsent(site.id(), site) != null) {
			throw new IllegalStateException("site " + site.id() + " is registered twice");
		}
	}

	public static synchronized @Nullable RuinSite get(final String id) {
		return SITES.get(id);
	}

	public static synchronized Collection<RuinSite> all() {
		return List.copyOf(new ArrayList<>(SITES.values()));
	}
}
