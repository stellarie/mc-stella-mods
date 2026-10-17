package com.stella.end;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;

/**
 * A volley of {@link #VOLLEY_SIZE} burning arrows, one every {@link #SPACING} ticks at a target in sight, then a rest of
 * {@link #COOLDOWN} ticks on the End clock. The archer stands still while it shoots. Each arrow the volley adds is
 * recorded, with the tick of the volley it left on, so a test reads the arrows that flew and not a box of the level.
 */
public final class ArcherVolleyGoal extends Goal {
	public static final int VOLLEY_SIZE = 12;
	public static final int SPACING = 3;
	public static final double RANGE = 24.0;
	public static final int COOLDOWN = 100;
	public static final int FIRE_TICKS = 100;
	/** A volley logs a line every this many ticks. */
	private static final int HEARTBEAT = 5;
	/** Arrows of the archer in flight are looked for this close to it. */
	private static final double SEARCH = 6.0;

	/** A finished volley: the arrows, the tick of the volley each left on, and how many burned when they left. */
	public record Volley(List<UUID> arrows, List<Integer> ticks, int burning) {
	}

	private final AbstractSkeleton archer;
	private final List<Volley> volleys = new ArrayList<>();
	private List<UUID> arrows = new ArrayList<>();
	private List<Integer> ticks = new ArrayList<>();
	private int burning;
	private int tick;
	private int begun;
	private long nextAt;

	public ArcherVolleyGoal(final AbstractSkeleton archer) {
		this.archer = archer;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	/** The volleys that have ended. */
	public List<Volley> volleys() {
		return volleys;
	}

	/** How many volleys have begun, the one in flight included. */
	public int begun() {
		return begun;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = archer.getTarget();
		if (!(archer.level() instanceof ServerLevel level) || target == null || !target.isAlive() || !archer.getMainHandItem().is(Items.BOW)) {
			return false;
		}
		return EndClock.now(level) >= nextAt && archer.distanceTo(target) <= RANGE && archer.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = archer.getTarget();
		return target != null && target.isAlive() && arrows.size() < VOLLEY_SIZE;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** Another goal that wants to move or look must not cut a volley short. */
	@Override
	public boolean isInterruptable() {
		return false;
	}

	@Override
	public void start() {
		arrows = new ArrayList<>();
		ticks = new ArrayList<>();
		burning = 0;
		tick = 0;
		begun++;
	}

	@Override
	public void tick() {
		LivingEntity target = archer.getTarget();
		if (target == null) {
			return;
		}
		archer.getNavigation().stop();
		archer.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (tick % SPACING == 0 && arrows.size() < VOLLEY_SIZE) {
			fire(target);
		}
		if (tick % HEARTBEAT == 0) {
			BehaviorLog.info("[END] archerVolley tick={} arrows={} target={}", tick, arrows.size(), target.getType().getDescriptionId());
		}
		tick++;
	}

	private void fire(final LivingEntity target) {
		ServerLevel level = (ServerLevel) archer.level();
		Set<UUID> before = new HashSet<>();
		for (AbstractArrow arrow : own(level)) {
			before.add(arrow.getUUID());
		}
		archer.performRangedAttack(target, 1.0F);
		for (AbstractArrow arrow : own(level)) {
			if (before.contains(arrow.getUUID())) {
				continue;
			}
			arrow.igniteForTicks(FIRE_TICKS);
			burning += arrow.isOnFire() ? 1 : 0;
			arrows.add(arrow.getUUID());
			ticks.add(tick);
		}
	}

	private List<AbstractArrow> own(final ServerLevel level) {
		return level.getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(SEARCH), arrow -> arrow.getOwner() == archer);
	}

	@Override
	public void stop() {
		if (!arrows.isEmpty()) {
			volleys.add(new Volley(List.copyOf(arrows), List.copyOf(ticks), burning));
			BehaviorLog.info("[END] archerVolley arrows={} burning={} ticks={}", arrows.size(), burning, ticks);
		}
		nextAt = EndClock.now((ServerLevel) archer.level()) + COOLDOWN;
	}
}
