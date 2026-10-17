package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The airdrop: two archers and two wither skeletons, in full netherite, fall from the dragon onto the deck. */
final class DragonAirdrop {
	/** The mobs let go this high over the deck: well under the dragon, whose wings throw back what comes near them. */
	private static final double HEIGHT = 10.0;
	/** The portal podium is sunk in the deck at the middle, and the cover on the deck starts ten blocks out. */
	private static final double NEAR = 6.0;
	private static final double FAR = 9.0;

	private DragonAirdrop() {
	}

	/** Drops what the cap allows, records it, and keeps the mobs as the dragon's own side. */
	static void drop(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		int room = DragonRules.AIRDROP_CAP - alive(end, state);
		int archers = Math.min(DragonRules.AIRDROP_ARCHERS, Math.max(0, room));
		int withers = Math.min(DragonRules.AIRDROP_WITHERS, Math.max(0, room - archers));
		List<UUID> archerIds = new ArrayList<>();
		List<UUID> witherIds = new ArrayList<>();
		int count = archers + withers;
		double turn = end.getRandom().nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < count; i++) {
			boolean wither = i >= archers;
			double angle = turn + Math.PI * 2.0 * i / count;
			double reach = NEAR + end.getRandom().nextDouble() * (FAR - NEAR);
			Vec3 at = new Vec3(Math.cos(angle) * reach, Rooftop.DECK_Y + HEIGHT, Math.sin(angle) * reach);
			Mob mob = make(end, wither, at);
			if (mob != null) {
				(wither ? witherIds : archerIds).add(mob.getUUID());
			}
		}
		state.airdrops.add(new DragonState.Airdrop(now, List.copyOf(archerIds), List.copyOf(witherIds)));
		BehaviorLog.info("[END] dragon airdrop archers={} withers={}", archerIds.size(), witherIds.size());
	}

	private static int alive(final ServerLevel end, final DragonState state) {
		int count = 0;
		for (DragonState.Airdrop drop : state.airdrops) {
			for (List<UUID> ids : List.of(drop.archers(), drop.withers())) {
				for (UUID id : ids) {
					count += end.getEntity(id) != null && end.getEntity(id).isAlive() ? 1 : 0;
				}
			}
		}
		return count;
	}

	private static Mob make(final ServerLevel end, final boolean wither, final Vec3 at) {
		Mob mob = wither ? EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.EVENT) : EntityTypes.SKELETON.create(end, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wither ? Items.NETHERITE_SWORD : Items.BOW));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		Wyverns.own(mob, at, EndLevels.ROOFTOP);
		return end.addFreshEntity(mob) ? mob : null;
	}
}
