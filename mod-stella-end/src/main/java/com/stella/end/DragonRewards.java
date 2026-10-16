package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** What a slain dragon leaves: an egg and its scales on the roof, and the experience of a level 120 kill for the players on it. */
final class DragonRewards {
	/** Beside the portal and not over it: an item that fell through the portal would leave the End. */
	static final Vec3 DROP_AT = new Vec3(6.5, Rooftop.DECK_Y + 2.0, 0.5);

	private DragonRewards() {
	}

	static List<UUID> drop(final ServerLevel end) {
		List<UUID> dropped = new ArrayList<>();
		dropped.add(spawn(end, new ItemStack(Items.DRAGON_EGG)));
		dropped.add(spawn(end, new ItemStack(DraconicGear.item(DraconicGear.DRAGON_SCALE), DragonRules.SCALES)));
		dropped.removeIf(id -> id == null);
		return dropped;
	}

	private static UUID spawn(final ServerLevel end, final ItemStack stack) {
		ItemEntity item = new ItemEntity(end, DROP_AT.x, DROP_AT.y, DROP_AT.z, stack);
		item.setUnlimitedLifetime();
		item.setDeltaMovement(0.0, 0.2, 0.0);
		return end.addFreshEntity(item) ? item.getUUID() : null;
	}

	/** Shares the experience of a kill at level 120 between the players on the roof. Empty when the rpg mod is absent. */
	static Map<UUID, Integer> pay(final ServerLevel end) {
		Map<UUID, Integer> paid = new HashMap<>();
		OptionalInt total = XpBridge.needFor(DragonRules.XP_LEVEL);
		List<ServerPlayer> players = Rooftop.players(end);
		if (total.isEmpty() || players.isEmpty()) {
			return paid;
		}
		int share = Math.max(1, total.getAsInt() / players.size());
		for (ServerPlayer player : players) {
			player.giveExperiencePoints(share);
			paid.put(player.getUUID(), share);
		}
		return paid;
	}
}
