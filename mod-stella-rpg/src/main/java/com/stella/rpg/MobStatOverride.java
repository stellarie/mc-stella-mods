package com.stella.rpg;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * Fixed stats that another mod gives one mob, for example a boss with a set vitality. The object share
 * {@link #SHARE_KEY} holds a {@code BiConsumer<Mob, Map<String, Integer>>} and needs no compile dependency on this mod.
 * A key is a stat key ({@code str agi vit dex int luk}). A listed stat reads the value instead of the role formula,
 * and growth still adds. A call with a valid entry replaces the earlier map, and a call with none changes nothing. A value clamps to 0..{@link StatSheet#CEILING}, and an
 * unknown key, a negative value or a non-number is skipped. The call never throws.
 */
public final class MobStatOverride {
	public static final String SHARE_KEY = "mod-stella-rpg:mob-stat-set";
	/** Keyed by `Stat.key()`, so a renamed constant cannot break a save. */
	public static final AttachmentType<Map<String, Integer>> ATTACHMENT = AttachmentRegistry.create(
		StellaRpg.id("mob_stat_override"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
	);

	private MobStatOverride() {
	}

	public static void register() {
		BiConsumer<Mob, Map<?, ?>> share = MobStatOverride::set;
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, share);
	}

	static void set(final Mob mob, final Map<?, ?> values) {
		if (mob == null || values == null) {
			return;
		}
		Map<String, Integer> clean = new HashMap<>();
		for (Map.Entry<?, ?> entry : values.entrySet()) {
			if (entry.getKey() instanceof String key && Stat.byKey(key).isPresent()
				&& entry.getValue() instanceof Number number && number.doubleValue() >= 0) {
				clean.put(key, (int) Math.min(StatSheet.CEILING, number.longValue()));
			}
		}
		if (clean.isEmpty()) {
			return;
		}
		mob.setAttached(ATTACHMENT, Map.copyOf(clean));
	}

	public static OptionalInt value(final Mob mob, final Stat stat) {
		Integer value = mob.getAttachedOrElse(ATTACHMENT, Map.of()).get(stat.key());
		return value == null ? OptionalInt.empty() : OptionalInt.of(value);
	}
}
