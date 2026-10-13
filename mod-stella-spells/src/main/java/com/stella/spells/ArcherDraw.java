package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;

/**
 * The draw of an archer cast. A short while after a cast the caster starts to use the bow or crossbow in hand,
 * which every client draws as the pull of that weapon, and stops again without loosing anything. The client
 * releases a use the player is not holding, so while the draw lasts the server marks the player and the client
 * keeps its release back; the server drops a release that still arrives.
 */
public final class ArcherDraw {
	/** How many ticks the held bow or crossbow stays drawn after a cast. */
	static final int DRAW_TICKS = 6;
	/** True on the player while the draw lasts. Synced to every viewer, so the client can hold its release. */
	public static final AttachmentType<Boolean> DRAWING = AttachmentRegistry.create(
		SpellStatus.id("archer_draw"), builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
	);

	private static final Map<UUID, Draw> DRAWS = new ConcurrentHashMap<>();
	private static int clock;

	private ArcherDraw() {
	}

	private static final class Draw {
		final InteractionHand hand;
		final int startsAt;
		final int endsAt;
		boolean drawn;

		Draw(final InteractionHand hand, final int startsAt) {
			this.hand = hand;
			this.startsAt = startsAt;
			this.endsAt = startsAt + DRAW_TICKS;
		}
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ArcherDraw::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DRAWS.clear();
			clock = 0;
		});
	}

	/** Starts the draw on the next tick, once whatever cast the skill has put the weapon back in hand. */
	static void play(final ServerPlayer caster) {
		InteractionHand hand = weaponHand(caster);
		if (hand != null && !caster.isUsingItem()) {
			DRAWS.put(caster.getUUID(), new Draw(hand, clock + 1));
		}
	}

	/** True while this player's draw is held, which is the only time a release of the use is refused. */
	public static boolean holding(final ServerPlayer player) {
		Draw draw = DRAWS.get(player.getUUID());
		return draw != null && draw.drawn;
	}

	private static @Nullable InteractionHand weaponHand(final ServerPlayer caster) {
		if (caster.getMainHandItem().getItem() instanceof ProjectileWeaponItem) {
			return InteractionHand.MAIN_HAND;
		}
		return caster.getOffhandItem().getItem() instanceof ProjectileWeaponItem ? InteractionHand.OFF_HAND : null;
	}

	private static void tick(final MinecraftServer server) {
		int now = ++clock;
		for (Map.Entry<UUID, Draw> entry : DRAWS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Draw draw = entry.getValue();
			if (player == null || !player.isAlive() || (!draw.drawn && now > draw.startsAt)) {
				DRAWS.remove(entry.getKey());
			} else if (now == draw.startsAt) {
				if (!begin(player, draw)) {
					DRAWS.remove(entry.getKey());
				}
			} else if (draw.drawn && (now >= draw.endsAt || !player.isUsingItem())) {
				end(player, draw);
				DRAWS.remove(entry.getKey());
			}
		}
	}

	// The weapon must still be in the hand it was drawn from, and the player must not already be using something.
	private static boolean begin(final ServerPlayer player, final Draw draw) {
		ItemStack weapon = player.getItemInHand(draw.hand);
		if (player.isUsingItem() || !(weapon.getItem() instanceof ProjectileWeaponItem)) {
			return false;
		}
		draw.drawn = true;
		((AttachmentTarget) player).setAttached(DRAWING, Boolean.TRUE);
		player.startUsingItem(draw.hand);
		return true;
	}

	private static void end(final ServerPlayer player, final Draw draw) {
		((AttachmentTarget) player).removeAttached(DRAWING);
		if (player.isUsingItem() && player.getUsedItemHand() == draw.hand) {
			player.stopUsingItem();
		}
	}
}
