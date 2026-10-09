package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * An invisible player is hidden from every mob that is not undead. An attack reveals the player for
 * {@link #REVEAL_TICKS} ticks, and the player is then hidden again while the effect lasts. The reveal end sits on the
 * server tick count, which advances in game tests, while the level clock barely moves there. The synced flag tells
 * clients to draw a revealed player in full and a hidden one without equipment.
 */
public final class TrueInvisibility {
	public static final int REVEAL_TICKS = 200;
	public static final AttachmentType<Boolean> REVEALED = AttachmentRegistry.<Boolean>builder()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
		.buildAndRegister(Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "revealed"));

	// Reveal end per player, on the server tick count. Not saved: a restart ends every reveal.
	private static final Map<ServerPlayer, Integer> REVEAL_ENDS = new ConcurrentHashMap<>();

	private TrueInvisibility() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
				reveal(attacker);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TrueInvisibility::tick);
	}

	/** True for a player the server holds as revealed. A client reads {@link #REVEALED} instead. */
	public static boolean isRevealed(final Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		Integer end = REVEAL_ENDS.get(serverPlayer);
		return end != null && end > serverPlayer.level().getServer().getTickCount();
	}

	/** A player the looker cannot see: invisible, not revealed, and the looker is not undead. */
	public static boolean hides(final LivingEntity target, final Entity looker) {
		return target instanceof Player player
			&& player.hasEffect(MobEffects.INVISIBILITY)
			&& !isRevealed(player)
			&& !looker.is(EntityTypeTags.UNDEAD);
	}

	/** True when vanilla's invisibility sight penalty must not apply to this looker. */
	public static boolean seenDespiteInvisibility(final Player player, final Entity looker) {
		return isRevealed(player) || looker.is(EntityTypeTags.UNDEAD);
	}

	private static void reveal(final ServerPlayer player) {
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			return;
		}

		REVEAL_ENDS.put(player, player.level().getServer().getTickCount() + REVEAL_TICKS);
		setSynced(player, true);
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<ServerPlayer, Integer>> entries = REVEAL_ENDS.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<ServerPlayer, Integer> entry = entries.next();
			if (entry.getValue() > now && !entry.getKey().isRemoved()) {
				continue;
			}

			entries.remove();
			setSynced(entry.getKey(), false);
		}
	}

	private static void setSynced(final ServerPlayer player, final boolean revealed) {
		AttachmentTarget target = player;
		if (target.getAttachedOrElse(REVEALED, false) != revealed) {
			target.setAttached(REVEALED, revealed);
		}
	}
}
