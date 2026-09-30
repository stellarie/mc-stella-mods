package com.stella.learn.gym;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What a brain may look at: its own body, the mob as it was a few ticks ago, and the arena. The mob's position is
 * delayed by the skill's reaction time and aims carry the skill's error, so a weak bot is late and misses.
 */
public final class BotView {
	private static final int PATH_REFRESH_TICKS = 8;
	private static final int NOISE_TICKS = 4;
	private static final double ARROW_WATCH_RADIUS = 20.0;

	private final Bot bot;
	private final Arena arena;
	private final Skill skill;
	private final RandomSource random;
	private final Deque<Vec3> history = new ArrayDeque<>();
	private Mob mob;
	private long tick;
	private float noiseYaw;
	private float noisePitch;
	private List<BlockPos> path;
	private long pathTick = Long.MIN_VALUE;
	private BlockPos pathGoal;
	private double arrowThreat = -1.0;
	private long arrowTick = Long.MIN_VALUE;
	private Vec3 lastPosition = Vec3.ZERO;

	public BotView(final Bot bot, final Arena arena, final Skill skill, final RandomSource random) {
		this.bot = bot;
		this.arena = arena;
		this.skill = skill;
		this.random = random;
	}

	/** Called once a tick by the episode before the brain runs. */
	void update(final Mob target) {
		mob = target;
		tick++;
		if (target != null && target.isAlive()) {
			history.addLast(target.getBoundingBox().getCenter());
			while (history.size() > skill.reactionTicks() + 1) {
				history.removeFirst();
			}
		}

		if (tick % NOISE_TICKS == 1) {
			float error = skill.aimErrorDegrees();
			noiseYaw = (random.nextFloat() * 2.0F - 1.0F) * error;
			noisePitch = (random.nextFloat() * 2.0F - 1.0F) * error * 0.5F;
		}
	}

	public Bot bot() {
		return bot;
	}

	public ServerPlayer player() {
		return bot.player();
	}

	public Arena arena() {
		return arena;
	}

	public Skill skill() {
		return skill;
	}

	public RandomSource random() {
		return random;
	}

	public long tick() {
		return tick;
	}

	public Vec3 pos() {
		return bot.player().position();
	}

	public Vec3 eye() {
		return bot.player().getEyePosition();
	}

	public boolean onGround() {
		return bot.player().onGround();
	}

	public float healthFraction() {
		return bot.player().getHealth() / bot.player().getMaxHealth();
	}

	public Vec3 velocity() {
		return bot.player().getDeltaMovement();
	}

	public boolean mobAlive() {
		return mob != null && mob.isAlive();
	}

	/** The real mob. Use it for hit checks; use {@link #seen()} to decide where to go. */
	public Mob mob() {
		return mob;
	}

	/** The mob's center as the bot saw it `reactionTicks` ago. */
	public Vec3 seen() {
		return history.isEmpty() ? pos() : history.peekFirst();
	}

	/** The mob's velocity per tick, from the positions the bot has seen. */
	public Vec3 seenVelocity() {
		if (history.size() < 2) {
			return Vec3.ZERO;
		}

		return history.peekLast().subtract(history.peekFirst()).scale(1.0 / (history.size() - 1));
	}

	/** Where to aim a shot that flies at `speed` blocks a tick, so it meets a mob that keeps its velocity. */
	public Vec3 leadPoint(final double speed) {
		Vec3 at = seen();
		double flight = Math.min(eye().distanceTo(at) / speed, 12.0);
		return at.add(seenVelocity().scale(flight)).add(0.0, eye().distanceTo(at) * 0.02, 0.0);
	}

	public double distance() {
		return eye().distanceTo(seen());
	}

	public double horizontalDistance() {
		Vec3 target = seen();
		return Math.hypot(target.x - pos().x, target.z - pos().z);
	}

	/** The mob's feet, as it is now. */
	public Vec3 mobFeet() {
		return mob == null ? pos() : mob.position();
	}

	public boolean inReach() {
		if (!mobAlive()) {
			return false;
		}

		double reach = bot.player().getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		return mob.getBoundingBox().distanceToSqr(eye()) <= reach * reach;
	}

	public float attackStrength() {
		return bot.player().getAttackStrengthScale(0.0F);
	}

	public boolean mobDrawing() {
		return mobAlive() && mob.isUsingItem();
	}

	public float yawTo(final Vec3 to) {
		return Bot.yawTo(eye(), to) + noiseYaw;
	}

	public float pitchTo(final Vec3 to) {
		return Mth.clamp(Bot.pitchTo(eye(), to) + noisePitch, -90.0F, 90.0F);
	}

	/** Faces the delayed mob, or the real one when `exact` is set (a melee swing must land where the mob is). */
	public void faceMob(final BotInput.Builder input, final boolean exact) {
		Vec3 aim = exact && mobAlive() ? mob.getBoundingBox().getCenter() : seen();
		input.look(yawTo(aim), pitchTo(aim));
	}

	public float facingYaw() {
		return bot.player().getYRot();
	}

	/** The time in ticks until the nearest arrow of the mob reaches the bot, or a negative number when none flies at it. */
	public double arrowThreat() {
		if (arrowTick == tick) {
			return arrowThreat;
		}

		arrowTick = tick;
		arrowThreat = -1.0;
		if (mob == null) {
			return arrowThreat;
		}

		Vec3 me = player().getBoundingBox().getCenter();
		var box = player().getBoundingBox().inflate(ARROW_WATCH_RADIUS);
		for (AbstractArrow arrow : arena.level().getEntitiesOfClass(AbstractArrow.class, box, a -> a.getOwner() == mob)) {
			Vec3 velocity = arrow.getDeltaMovement();
			Vec3 toMe = me.subtract(arrow.position());
			double speed = velocity.length();
			if (speed < 0.1) {
				continue;
			}

			double along = toMe.dot(velocity) / speed;
			if (along <= 0.0) {
				continue;
			}

			double miss = toMe.subtract(velocity.scale(along / speed)).length();
			if (miss < 1.8 && (arrowThreat < 0.0 || along / speed < arrowThreat)) {
				arrowThreat = along / speed;
			}
		}

		return arrowThreat;
	}

	// -- equipment --------------------------------------------------------------------------------------------------

	public void swapMelee(final boolean axe) {
		Gear gear = skill.gear();
		bot.give(Hotbar.MELEE, new ItemStack(switch (gear) {
			case NONE -> axe ? Items.STONE_AXE : Items.STONE_SWORD;
			case IRON -> axe ? Items.IRON_AXE : Items.IRON_SWORD;
			case DIAMOND -> axe ? Items.DIAMOND_AXE : Items.DIAMOND_SWORD;
		}));
	}

	public boolean hasShield() {
		return bot.player().getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD);
	}

	// -- movement ---------------------------------------------------------------------------------------------------

	private BlockPos relative(final Vec3 at) {
		BlockPos origin = arena.origin();
		return new BlockPos(Mth.floor(at.x) - origin.getX(), Mth.floor(at.y + 1.0E-3) - origin.getY(), Mth.floor(at.z) - origin.getZ());
	}

	/** The next point to walk to on the way to `goal`, from a path over the live blocks, or the goal itself. */
	public Vec3 waypoint(final Vec3 goal) {
		BlockPos from = relative(pos());
		BlockPos to = relative(goal);
		boolean stale = path == null || tick - pathTick >= PATH_REFRESH_TICKS || pathGoal == null || pathGoal.distManhattan(to) > 2;
		if (stale) {
			pathTick = tick;
			pathGoal = to;
			path = insideArena(from) && insideArena(to) ? Walker.path(arena::blockAt, from, to) : null;
		}

		if (path == null || path.size() < 2) {
			return goal;
		}

		int index = 0;
		for (int i = 0; i < path.size(); i++) {
			if (path.get(i).equals(from)) {
				index = i;
				break;
			}
		}

		BlockPos next = path.get(Math.min(index + 2, path.size() - 1));
		BlockPos origin = arena.origin();
		return new Vec3(origin.getX() + next.getX() + 0.5, origin.getY() + next.getY(), origin.getZ() + next.getZ() + 0.5);
	}

	private static boolean insideArena(final BlockPos cell) {
		return cell.getX() >= 0 && cell.getX() < Arena.OPEN && cell.getZ() >= 0 && cell.getZ() < Arena.OPEN
			&& cell.getY() >= -Arena.FLOOR && cell.getY() < Arena.OPEN;
	}

	/** Walks toward `goal` along the path, jumping a one-block step. Leaves the look to the caller when `look` is false. */
	public void walkTo(final BotInput.Builder input, final Vec3 goal, final boolean sprint, final boolean look) {
		Vec3 way = waypoint(goal);
		if (look) {
			input.look(Bot.yawTo(pos(), way), 0.0F);
		}

		double toWay = Bot.yawTo(pos(), way);
		float relative = Mth.wrapDegrees((float) toWay - input.yaw);
		double radians = Math.toRadians(relative);
		input.forward = (float) Math.cos(radians);
		input.strafe = (float) -Math.sin(radians);
		input.sprint = sprint && input.forward > 0.5F;
		boolean climb = way.y > pos().y + 0.4;
		boolean stuck = lastPosition.distanceToSqr(pos()) < 1.0E-4 && onGround();
		input.jump |= climb || stuck && tick % 6 == 0;
		lastPosition = pos();
	}

	public boolean isMobShootingAtMe() {
		return mobAlive() && mob.isUsingItem() && mob.getMainHandItem().is(Items.BOW);
	}

	public LivingEntity target() {
		return mob;
	}
}
