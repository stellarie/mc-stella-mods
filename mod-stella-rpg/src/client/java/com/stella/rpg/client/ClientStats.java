package com.stella.rpg.client;

import com.stella.rpg.StatPoints;
import com.stella.rpg.net.StatsSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class ClientStats {
	private static final long MESSAGE_MILLIS = 4000L;

	private static volatile StatsSyncPayload latest;
	private static StatPoints.Result lastFailure = StatPoints.Result.OK;
	private static long failureUntil;

	private ClientStats() {
	}

	public static StatsSyncPayload get() {
		return latest;
	}

	public static boolean has() {
		return latest != null;
	}

	public static void accept(final StatsSyncPayload payload) {
		latest = payload;
		StatPoints.Result result = payload.resultCode();
		if (result != StatPoints.Result.OK) {
			lastFailure = result;
			failureUntil = Util.getMillis() + MESSAGE_MILLIS;
		}
	}

	public static StatPoints.Result failure() {
		return Util.getMillis() < failureUntil ? lastFailure : StatPoints.Result.OK;
	}

	public static void clear() {
		latest = null;
		lastFailure = StatPoints.Result.OK;
	}
}
