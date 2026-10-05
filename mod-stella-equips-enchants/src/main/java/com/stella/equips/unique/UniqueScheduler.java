package com.stella.equips.unique;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Runs a task a number of server ticks later, in the order the tasks fell due. */
public final class UniqueScheduler {
	private record Task(long due, long order, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long counter;

	private UniqueScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(UniqueScheduler::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
	}

	public static void after(final MinecraftServer server, final int ticks, final Runnable task) {
		TASKS.add(new Task(server.getTickCount() + Math.max(0, ticks), counter++, task));
	}

	private static void tick(final MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		List<Task> due = TASKS.stream().filter(task -> task.due() <= now)
			.sorted((a, b) -> a.due() != b.due() ? Long.compare(a.due(), b.due()) : Long.compare(a.order(), b.order())).toList();
		TASKS.removeAll(due);
		for (Task task : due) {
			task.run().run();
		}
	}
}
