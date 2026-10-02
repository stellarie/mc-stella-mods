package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * A spider that webs a player calls the monsters around it onto that player. Only a webbed survival or adventure
 * player draws a call. The nearest monsters inside {@link #RADIUS} blocks of the spider take the player as their
 * target, up to {@link #CAP} of them. A spider calls at most once every {@link #COOLDOWN_TICKS} of its own ticks.
 * Creepers and neutral mobs never answer, and neither does a mob that another mod drives.
 */
public final class SpiderSignal {
	public static final double RADIUS = 24.0;
	public static final int CAP = 6;
	public static final int COOLDOWN_TICKS = 100;

	/** Test seam: reports each signal. */
	public interface Recorder {
		void signalled(Spider spider, LivingEntity target, List<Mob> accepted, List<Mob> declined);
	}

	private static final Map<Spider, Integer> LAST = new WeakHashMap<>();
	private static volatile Recorder recorder = (spider, target, accepted, declined) -> {
	};

	private SpiderSignal() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (spider, target, accepted, declined) -> {
		} : newRecorder;
	}

	public static boolean isWebbed(final LivingEntity target) {
		AABB box = target.getBoundingBox().deflate(1.0E-5);
		BlockPos low = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos high = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
			if (target.level().getBlockState(pos).is(Blocks.COBWEB)) {
				return true;
			}
		}

		return false;
	}

	/** Returns the monsters that took the target, nearest first. */
	public static List<Mob> alert(final Spider spider, final LivingEntity target) {
		if (!(target instanceof Player player)
			|| player.isCreative()
			|| player.isSpectator()
			|| !(spider.level() instanceof ServerLevel level)
			|| !isWebbed(player)
			|| !ready(spider)) {
			return List.of();
		}

		List<Monster> candidates = new ArrayList<>(level.getEntitiesOfClass(
			Monster.class, spider.getBoundingBox().inflate(RADIUS), monster -> eligible(spider, monster)
		));
		candidates.removeIf(monster -> spider.distanceToSqr(monster) > RADIUS * RADIUS);
		candidates.sort(Comparator.comparingDouble(spider::distanceToSqr));

		List<Mob> accepted = new ArrayList<>();
		List<Mob> declined = new ArrayList<>();
		for (Monster candidate : candidates) {
			if (accepted.size() >= CAP) {
				break;
			}

			LivingEntity current = candidate.getTarget();
			// A monster that hunts this player
			// needs no call and takes no slot.
			if (current == player) {
				continue;
			}

			if (!takes(candidate, current, player)) {
				declined.add(candidate);
				continue;
			}

			candidate.setTarget(player);
			// A Brain mob reads its target from the Brain.
			// For it, setTarget does not stick.
			if (candidate.getTarget() == player) {
				accepted.add(candidate);
			} else {
				declined.add(candidate);
			}
		}

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=spiderSignal SIGNAL spider={} accepted={} declined={}",
			spider.getUUID(), accepted.size(), declined.size()
		);
		recorder.signalled(spider, player, List.copyOf(accepted), List.copyOf(declined));
		return List.copyOf(accepted);
	}

	public static boolean eligible(final Spider spider, final Monster monster) {
		return monster != spider
			&& monster.isAlive()
			&& monster.getHealth() > monster.getMaxHealth() * RetreatGoal.HEALTH_FRACTION
			&& !monster.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)
			&& !FriendlyPatrol.isVillageFriend(monster)
			&& !(monster instanceof Creeper)
			&& !(monster instanceof NeutralMob);
	}

	private static boolean takes(final Monster candidate, final LivingEntity current, final Player webbed) {
		return current == null
			|| !(current instanceof Player)
			|| candidate.distanceToSqr(current) > candidate.distanceToSqr(webbed);
	}

	private static boolean ready(final Spider spider) {
		synchronized (LAST) {
			Integer last = LAST.get(spider);
			if (last != null && spider.tickCount - last < COOLDOWN_TICKS) {
				return false;
			}

			LAST.put(spider, spider.tickCount);
			return true;
		}
	}
}
