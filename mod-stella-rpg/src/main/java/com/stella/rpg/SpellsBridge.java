package com.stella.rpg;

import com.mojang.serialization.Codec;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The archer starter books, reached by name when {@code mod-stella-spells} is installed. Without it a new archer
 * is handed the job kit alone, and nothing is logged as an error. The books are handed out once per player, so a
 * repeated call gives nothing a second time.
 */
public final class SpellsBridge {
	/** The book every new archer is handed, from the sibling's own contract. */
	public static final Identifier ARCHER_STARTER_ID = Identifier.fromNamespaceAndPath("mod-stella-spells", "arrow_rain");

	/** One grant of starter books. It returns how many books the sibling handed over, or zero. */
	public interface Grant {
		int grant(ServerPlayer player, RandomSource random, Consumer<Identifier> onGranted);
	}

	private static final Grant NONE = (player, random, onGranted) -> 0;

	/** True once the archer starter was handed to this player. */
	public static final AttachmentType<Boolean> ARCHER_STARTER_GIVEN = AttachmentRegistry.create(
		StellaRpg.id("archer_starter_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	private static volatile Grant grant;

	private SpellsBridge() {
	}

	static void setGrant(final Grant stand) {
		grant = stand;
	}

	/** Hands the archer starter to a new archer once. Returns true on a gift. The job guard lives in Jobs. */
	public static boolean grantArcherStarter(final ServerPlayer player) {
		if (player.getAttachedOrElse(ARCHER_STARTER_GIVEN, false)) {
			return false;
		}
		int given = grant().grant(player, player.getRandom(), id -> {
		});
		if (given <= 0) {
			return false;
		}
		player.setAttached(ARCHER_STARTER_GIVEN, true);
		return true;
	}

	private static Grant grant() {
		Grant found = grant;
		if (found == null) {
			found = find();
			grant = found;
		}
		return found;
	}

	private static Grant find() {
		try {
			Class<?> starter = Class.forName("com.stella.spells.ArcherStarter", true, SpellsBridge.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle method = lookup.findStatic(starter, "grant", MethodType.methodType(int.class, ServerPlayer.class,
				RandomSource.class, Consumer.class));
			return (player, random, onGranted) -> {
				try {
					return (int) method.invokeExact(player, random, onGranted);
				} catch (Throwable e) {
					return 0;
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}
}
