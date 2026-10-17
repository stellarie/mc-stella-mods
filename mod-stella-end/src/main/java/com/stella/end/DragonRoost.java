package com.stella.end;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The roost: at half health the dragon lands on the portal for a while, takes a quarter of the damage, and calls skeletons. */
final class DragonRoost {
	private static final double SCATTER_NEAR = 3.0;
	private static final double SCATTER_FAR = 7.0;

	private DragonRoost() {
	}

	static boolean due(final EnderDragon dragon, final DragonState state, final long now) {
		return !state.roosted && !state.inGrace(now) && dragon.getHealth() <= dragon.getMaxHealth() * DragonRules.ROOST_HEALTH;
	}

	static void begin(final EnderDragon dragon, final DragonState state, final long now) {
		state.mode = DragonState.Mode.ROOST;
		state.modeSince = now;
		state.roosted = true;
		state.roostStartedAt = now;
		state.nextSummonAt = now + DragonRules.ROOST_SUMMON_PERIOD;
		state.current = null;
		state.launchAt = EndState.NEVER;
		state.path.clear();
		dragon.addTag(EndDragons.ROOSTED_TAG);
		BehaviorLog.info("[END] dragon roost begins health={} clock={}", dragon.getHealth() / dragon.getMaxHealth(), now);
	}

	static boolean over(final DragonState state, final long now) {
		return now - state.roostStartedAt >= DragonRules.ROOST_TICKS;
	}

	/** Flies to the portal and sits there, and on its schedule calls two wither skeletons and two archers, up to twelve alive. */
	static void tick(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		DragonFlight.arrive(dragon, state, Rooftop.roostPoint(end), DragonFlight.CRUISE * DragonRules.speedMultiplier(state.buffs));
		if (now < state.nextSummonAt) {
			return;
		}
		state.nextSummonAt = now + DragonRules.ROOST_SUMMON_PERIOD;
		if (Rooftop.spawnsBlocked(end)) {
			return;
		}
		int room = DragonRules.ROOST_CAP - alive(end, state);
		int skeletons = Math.min(DragonRules.ROOST_SKELETONS, room);
		int archers = Math.min(DragonRules.ROOST_ARCHERS, room - skeletons);
		int added = 0;
		for (int i = 0; i < skeletons; i++) {
			added += call(end, state, true) ? 1 : 0;
		}
		for (int i = 0; i < archers; i++) {
			added += call(end, state, false) ? 1 : 0;
		}
		if (added > 0) {
			BehaviorLog.info("[END] dragon roost summons added={} alive={}", added, alive(end, state));
		}
	}

	private static int alive(final ServerLevel end, final DragonState state) {
		int count = 0;
		for (UUID id : state.summons) {
			count += end.getEntity(id) != null && end.getEntity(id).isAlive() ? 1 : 0;
		}
		return count;
	}

	private static boolean call(final ServerLevel end, final DragonState state, final boolean wither) {
		Vec3 portal = Rooftop.roostPoint(end);
		double angle = end.getRandom().nextDouble() * Math.PI * 2.0;
		double reach = SCATTER_NEAR + end.getRandom().nextDouble() * (SCATTER_FAR - SCATTER_NEAR);
		int x = Mth.floor(portal.x + Math.cos(angle) * reach);
		int z = Mth.floor(portal.z + Math.sin(angle) * reach);
		BlockPos ground = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		Mob mob = wither ? EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.EVENT) : EntityTypes.SKELETON.create(end, EntitySpawnReason.EVENT);
		if (mob == null) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wither ? Items.NETHERITE_SWORD : Items.BOW));
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		Wyverns.own(mob, new Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5), EndLevels.ROOFTOP);
		if (!end.addFreshEntity(mob)) {
			return false;
		}
		state.summons.add(mob.getUUID());
		return true;
	}
}
