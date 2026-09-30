package com.stella.learn.gym;

import com.mojang.authlib.GameProfile;
import com.stella.learn.LearnLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A server-side player that moves, fights, blocks, and edits blocks under player physics. A player on a real
 * connection gets its position from the client. The bot has no client, so each tick it applies its own input and
 * runs the player's own tick, which applies gravity, friction, collision, and item use.
 */
public final class Bot {
	private static final List<Bot> ALL = new ArrayList<>();

	static {
		ServerTickEvents.START_SERVER_TICK.register(Bot::tickAll);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> projectileLoaded(entity));
	}

	/** Chooses the input for the next tick from the bot state. */
	@FunctionalInterface
	public interface Brain {
		BotInput decide(Bot bot);
	}

	/** A player the server simulates: with no client to trust, movement runs on the server. */
	private static final class BotPlayer extends ServerPlayer {
		BotPlayer(final MinecraftServer server, final ServerLevel level, final GameProfile profile) {
			super(server, level, profile, ClientInformation.createDefault());
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}

		void revive() {
			dead = false;
			deathTime = 0;
			removeAllEffects();
			clearFire();
			setHealth(getMaxHealth());
			setPose(Pose.STANDING);
		}
	}

	private final BotPlayer player;
	private final EmbeddedChannel channel;
	private final int arena;
	private final int sampleTicks;
	private BotInput input = BotInput.IDLE;
	private Brain brain;
	private long ticks;
	private boolean wasUsing;
	private BlockPos digging;
	private float digProgress;
	private int digTicks;
	private int sequence;
	private long drainedMessages;
	private boolean digFromInput;
	private BotStats stats = new BotStats();
	private java.util.function.Supplier<Entity> opponent = () -> null;
	private int lastPotions = -1;

	private Bot(final BotPlayer player, final EmbeddedChannel channel, final int arena, final int sampleTicks) {
		this.player = player;
		this.channel = channel;
		this.arena = arena;
		this.sampleTicks = sampleTicks;
	}

	public static Bot spawn(final ServerLevel level, final String name, final Vec3 at, final float yaw, final int arena) {
		return spawn(level, name, at, yaw, arena, 20);
	}

	public static Bot spawn(final ServerLevel level, final String name, final Vec3 at, final float yaw, final int arena, final int sampleTicks) {
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		BotPlayer player = new BotPlayer(server, level, profile);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
		// A server may default to creative, which is invulnerable and breaks blocks at once.
		player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		markLoaded(player);
		player.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		Bot bot = new Bot(player, channel, arena, sampleTicks);
		ALL.add(bot);
		GymLog.info(GymLog.BOT, "SPAWN", bot.who() + " at=" + fmt(at) + " yaw=" + LearnLog.num(yaw));
		return bot;
	}

	/** A player takes no damage until its client reports it has loaded; a bot has no client to say so. */
	private static void markLoaded(final ServerPlayer player) {
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
	}

	private static void tickAll(final MinecraftServer server) {
		for (Bot bot : List.copyOf(ALL)) {
			if (bot.player.level().getServer() == server) {
				bot.tick();
			}
		}
	}

	public ServerPlayer player() {
		return player;
	}

	public int arena() {
		return arena;
	}

	/** What this bot did since the last reset, read from the world and the inputs, not from the brain's own claims. */
	public BotStats stats() {
		return stats;
	}

	/** The entity the stats measure distances against. */
	public void setOpponent(final java.util.function.Supplier<Entity> next) {
		opponent = next;
	}

	public long ticks() {
		return ticks;
	}

	public String who() {
		return "bot=" + player.getName().getString() + " pid=" + player.getUUID() + " arena=" + arena;
	}

	public void setInput(final BotInput next) {
		input = next;
	}

	public void setBrain(final Brain next) {
		brain = next;
	}

	public boolean isDigging() {
		return digging != null;
	}

	public int digTicks() {
		return digTicks;
	}

	public long drainedMessages() {
		return drainedMessages;
	}

	public void remove() {
		ALL.remove(this);
		GymLog.info(GymLog.BOT, "REMOVE", who() + " tick=" + ticks);
		player.level().getServer().getPlayerList().remove(player);
		channel.close();
	}

	/** Puts a dead or displaced bot back on its feet at `at` with full health. */
	public void reset(final Vec3 at, final float yaw) {
		player.revive();
		player.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.setInvulnerableTime(0);
		player.getFoodData().setFoodLevel(20);
		markLoaded(player);
		player.stopUsingItem();
		input = BotInput.IDLE;
		wasUsing = false;
		digging = null;
		digFromInput = false;
		stats = new BotStats();
		lastPotions = -1;
		GymLog.info(GymLog.BOT, "RESET", who() + " at=" + fmt(at) + " tick=" + ticks);
	}

	public void give(final int slot, final ItemStack stack) {
		player.getInventory().setItem(slot, stack);
	}

	private void tick() {
		ticks++;
		if (brain != null) {
			input = brain.decide(this);
		}

		applyInput();
		tickDig();
		player.doTick();
		drain();
		observe();
		if (ticks % sampleTicks == 0) {
			GymLog.debug(GymLog.BOT, "INPUT", who() + " tick=" + ticks + " pos=" + fmt(player.position()) + " " + input);
		}
	}

	private void applyInput() {
		BotInput in = input;
		player.setYRot(in.yaw());
		player.setXRot(in.pitch());
		player.setYHeadRot(in.yaw());
		player.getInventory().setSelectedSlot(Mth.clamp(in.slot(), 0, 8));
		float speedScale = player.isUsingItem() && !player.getUseItem().is(Items.SHIELD) ? 0.2F : 1.0F;
		if (in.sneak()) {
			speedScale *= 0.3F;
		}

		player.zza = Mth.clamp(in.forward(), -1.0F, 1.0F) * speedScale;
		player.xxa = Mth.clamp(in.strafe(), -1.0F, 1.0F) * speedScale;
		player.setJumping(in.jump());
		player.setShiftKeyDown(in.sneak());
		player.setSprinting(in.sprint() && in.forward() > 0.0F && !player.isUsingItem() && !in.sneak());
		if (in.use() && !wasUsing) {
			pressUse();
		} else if (!in.use() && wasUsing) {
			GymLog.info(GymLog.BOT, "RELEASE", who() + " tick=" + ticks + " item=" + player.getUseItem().getItem() + " usedTicks=" + player.getTicksUsingItem());
			player.releaseUsingItem();
		}

		if (in.dig() != null) {
			if (digging == null || !digging.equals(in.dig())) {
				startBreaking(in.dig());
			}

			digFromInput = true;
		} else if (digFromInput) {
			stopBreaking();
			digFromInput = false;
		}

		if (in.placeAgainst() != null && in.placeFace() != null) {
			placeAgainst(in.placeAgainst(), in.placeFace());
		}

		wasUsing = in.use();
		// Level triggered: a swing that misses leaves the cooldown full, and the brain decides when to swing again.
		if (in.attack()) {
			attackInSight();
		}
	}

	private void pressUse() {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()) {
				continue;
			}

			InteractionResult result = player.gameMode.useItem(player, player.level(), stack, hand);
			GymLog.info(GymLog.BOT, "USE", who() + " tick=" + ticks + " hand=" + hand + " item=" + stack.getItem() + " result=" + result.getClass().getSimpleName());
			if (result.consumesAction()) {
				return;
			}
		}
	}

	private void attackInSight() {
		Entity target = entityInSight(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
		if (target == null) {
			GymLog.debug(GymLog.BOT, "ATTACK", who() + " tick=" + ticks + " target=none");
			return;
		}

		float strength = player.getAttackStrengthScale(0.0F);
		stats.attack(strength, player.fallDistance > 0.0 && !player.onGround(), player.getMainHandItem().is(ItemTags.AXES));
		player.attack(target);
		GymLog.debug(GymLog.BOT, "ATTACK", who() + " tick=" + ticks + " target=" + target.getType().toShortString()
			+ " strength=" + LearnLog.num(strength));
	}

	/** The nearest pickable entity along the look ray within `reach`, as a client crosshair would pick it. */
	public Entity entityInSight(final double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(reach));
		AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
		Entity best = null;
		double bestDistance = reach * reach;
		for (Entity entity : player.level().getEntities(player, search, e -> !e.isSpectator() && e.isPickable())) {
			AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
			Optional<Vec3> hit = box.clip(eye, end);
			double distance = box.contains(eye) ? 0.0 : hit.map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
			if (distance < bestDistance) {
				best = entity;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** Starts breaking the block at `pos` the way a client does; the tick loop then mines it over its dig time. */
	public void startBreaking(final BlockPos pos) {
		digging = pos.immutable();
		digProgress = 0.0F;
		digTicks = 0;
		player.gameMode.handleBlockBreakAction(pos, Action.START_DESTROY_BLOCK, Direction.UP, player.level().getMaxY(), sequence++);
		GymLog.info(GymLog.BOT, "DIG_START", who() + " tick=" + ticks + " pos=" + pos.toShortString());
	}

	/** Stops mining the block in progress, as a client that lets go of the button would. */
	public void stopBreaking() {
		if (digging == null) {
			return;
		}

		player.gameMode.handleBlockBreakAction(digging, Action.ABORT_DESTROY_BLOCK, Direction.UP, player.level().getMaxY(), sequence++);
		GymLog.info(GymLog.BOT, "DIG_END", who() + " tick=" + ticks + " pos=" + digging.toShortString() + " digTicks=" + digTicks + " result=aborted");
		digging = null;
	}

	private void tickDig() {
		if (digging == null) {
			return;
		}

		BlockState state = player.level().getBlockState(digging);
		if (state.isAir()) {
			GymLog.info(GymLog.BOT, "DIG_END", who() + " tick=" + ticks + " pos=" + digging.toShortString() + " digTicks=" + digTicks + " result=broken");
			stats.broke(digging, player.blockPosition());
			digging = null;
			return;
		}

		digTicks++;
		digProgress += state.getDestroyProgress(player, player.level(), digging);
		if (digProgress >= 1.0F) {
			player.gameMode.handleBlockBreakAction(digging, Action.STOP_DESTROY_BLOCK, Direction.UP, player.level().getMaxY(), sequence++);
			// The block goes at once, and a brain may look away before the next tick sees it gone.
			if (player.level().getBlockState(digging).isAir()) {
				GymLog.info(GymLog.BOT, "DIG_END", who() + " tick=" + ticks + " pos=" + digging.toShortString() + " digTicks=" + digTicks + " result=broken");
				stats.broke(digging, player.blockPosition());
				digging = null;
			}
		}
	}

	/** Uses the held stack on the `face` of the block at `against`, as a right click on it would. */
	public InteractionResult placeAgainst(final BlockPos against, final Direction face) {
		InteractionHand hand = InteractionHand.MAIN_HAND;
		Vec3 hitAt = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
		BlockHitResult hit = new BlockHitResult(hitAt, face, against, false);
		InteractionResult result = player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand, hit);
		GymLog.info(GymLog.BOT, "PLACE", who() + " tick=" + ticks + " against=" + against.toShortString() + " face=" + face
			+ " result=" + result.getClass().getSimpleName());
		if (result.consumesAction()) {
			stats.placed(against.relative(face));
		}

		return result;
	}

	private void observe() {
		stats.tick(player.blockPosition(), player.isBlocking(), input.strafe(), player.getMainHandItem().is(ItemTags.AXES));
		ItemStack potions = player.getInventory().getItem(Hotbar.POTIONS);
		int held = potions.is(Items.POTION) ? potions.getCount() : 0;
		if (lastPotions >= 0 && held < lastPotions) {
			stats.drank();
		}

		lastPotions = held;
	}

	/** Height above the opponent and whether the bot moves away from it, read when an arrow leaves the bow. */
	private void noteShot() {
		Entity other = opponent.get();
		if (other == null) {
			return;
		}

		Vec3 away = player.position().subtract(other.position());
		Vec3 velocity = player.getDeltaMovement();
		double along = (away.x * velocity.x + away.z * velocity.z) / Math.max(Math.hypot(away.x, away.z), 1.0E-6);
		stats.bowShot(player.getY() - other.getY(), along > 0.02);
	}

	private static void projectileLoaded(final Entity entity) {
		if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer owner) {
			for (Bot bot : ALL) {
				if (bot.player == owner) {
					boolean crossbow = owner.getMainHandItem().is(Items.CROSSBOW);
					bot.stats.arrow(crossbow);
					if (!crossbow) {
						bot.noteShot();
					}
				}
			}
		} else if (entity instanceof ThrownEnderpearl pearl && pearl.getOwner() instanceof ServerPlayer owner) {
			for (Bot bot : ALL) {
				if (bot.player == owner) {
					bot.stats.pearl();
				}
			}
		}
	}

	/** Drops what the server queued for the missing client, so the queue does not grow with every tick. */
	private void drain() {
		int messages = channel.outboundMessages().size();
		if (messages == 0) {
			return;
		}

		long bytes = 0;
		for (Object message : channel.outboundMessages()) {
			if (message instanceof ByteBuf buffer) {
				bytes += buffer.readableBytes();
			}
		}

		channel.outboundMessages().clear();
		drainedMessages += messages;
		GymLog.debug(GymLog.BOT, "DRAIN", who() + " tick=" + ticks + " messages=" + messages + " bytes=" + bytes);
	}

	public static float yawTo(final Vec3 from, final Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * 180.0 / Math.PI) - 90.0F;
	}

	public static float pitchTo(final Vec3 from, final Vec3 to) {
		double horizontal = Math.hypot(to.x - from.x, to.z - from.z);
		return (float) -(Mth.atan2(to.y - from.y, horizontal) * 180.0 / Math.PI);
	}

	static String fmt(final Vec3 v) {
		return "(" + LearnLog.num(v.x) + "," + LearnLog.num(v.y) + "," + LearnLog.num(v.z) + ")";
	}
}
