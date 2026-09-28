package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's own mobs: who they are, how strong their floor makes them, and how many a floor holds.
 * <p>
 * A floor with a player on it is kept stocked, a few at a time, up to {@link #FLOOR_CAP}. A tower mob's
 * health is raised on its attribute as far as the game allows; the rest of its size is kept as a divisor
 * on every hit it takes. What it deals, by any means, grows with its floor on the way in.
 */
public final class TowerMobs {
	public static final String TAG = "mod-stella-tower.mob";
	/** Mobs from the mobs mod roll no elites of their own in the tower; the tower decides that. */
	public static final String NO_SPAWN_ROLL_TAG = "mod-stella-mobs.no_spawn_roll";
	public static final int FLOOR_CAP = 36;
	public static final int SPAWN_INTERVAL_TICKS = 20;
	public static final int SPAWN_BATCH = 4;
	public static final int SPAWN_TRIES = 24;
	public static final double MIN_PLAYER_DISTANCE = 12.0;
	public static final int OVERWORLD_ONLY_UNTIL = 20;
	public static final double MAX_HEALTH_ATTRIBUTE = 1024.0;
	public static final Identifier HEALTH_ID = StellaTower.id("floor_health");
	public static final Identifier ARMOR_ID = StellaTower.id("floor_armor");
	public static final Identifier SPEED_ID = StellaTower.id("floor_speed");

	/** The overworld's hostiles, witches aside: all the first floors hold. */
	public static final List<EntityType<? extends Mob>> OVERWORLD = List.of(
		EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.CREEPER,
		EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.CAVE_SPIDER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR
	);
	/**
	 * Joins from the twenty-first floor. Ghasts are too big for an eight-block room, and shulkers teleport
	 * by a road no room can close.
	 */
	public static final List<EntityType<? extends Mob>> LATER = List.of(
		EntityTypes.WITCH, EntityTypes.BLAZE, EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE,
		EntityTypes.MAGMA_CUBE, EntityTypes.HOGLIN, EntityTypes.ENDERMAN, EntityTypes.ENDERMITE
	);

	/** What a tower mob carries: its floor, its element, and the health its attribute could not hold. */
	public record State(int floor, TowerElement element, double healthDivisor, TowerRank rank) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("floor").forGetter(State::floor),
			TowerElement.CODEC.fieldOf("element").forGetter(State::element),
			Codec.DOUBLE.fieldOf("health_divisor").forGetter(State::healthDivisor),
			TowerRank.CODEC.optionalFieldOf("rank", TowerRank.MOB).forGetter(State::rank)
		).apply(instance, State::new));
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(
		StellaTower.id("tower_mob"), builder -> builder.persistent(State.CODEC).copyOnDeath()
	);

	private TowerMobs() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerMobs::tick);
		// A tower mob that becomes another, a split magma cube or a converted mob, stays a tower mob of its floor.
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			State state = stateOf(previous);
			if (state != null) {
				makeTowerMob(converted, state.floor(), state.element(), state.rank());
			}
		});
	}

	public static @Nullable State stateOf(final LivingEntity entity) {
		return entity.getAttached(STATE);
	}

	/** The kinds a floor may hold. */
	public static List<EntityType<? extends Mob>> pool(final int floor) {
		if (floor <= OVERWORLD_ONLY_UNTIL) {
			return OVERWORLD;
		}
		List<EntityType<? extends Mob>> all = new ArrayList<>(OVERWORLD);
		all.addAll(LATER);
		return all;
	}

	/** Makes a mob a tower mob of this floor, in this element, at full health. */
	public static void makeTowerMob(final Mob mob, final int floor, final TowerElement element) {
		makeTowerMob(mob, floor, element, TowerRank.MOB);
	}

	/** Makes a mob a tower mob of this floor and rank, in this element, at full health. */
	public static void makeTowerMob(final Mob mob, final int floor, final TowerElement element, final TowerRank rank) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		double want = TowerCurve.health(health.getBaseValue(), floor) * rank.healthFactor();
		double held = Math.min(want, MAX_HEALTH_ATTRIBUTE);
		// The floor alone sets a tower mob's health: a zombie leader's bonus or any other is set aside.
		for (AttributeModifier other : List.copyOf(health.getModifiers())) {
			health.removeModifier(other.id());
		}
		health.addPermanentModifier(new AttributeModifier(HEALTH_ID, held - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
		AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ARMOR_ID);
			double room = Math.max(0.0, TowerCurve.ARMOR_CAP - armor.getValue());
			armor.addPermanentModifier(new AttributeModifier(ARMOR_ID, Math.min(room, TowerCurve.armorBonus(floor)), AttributeModifier.Operation.ADD_VALUE));
		}
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
			speed.addPermanentModifier(new AttributeModifier(SPEED_ID, TowerCurve.speedBonus(floor), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setAttached(STATE, new State(floor, element, want / mob.getMaxHealth(), rank));
		mob.addTag(TAG);
		mob.addTag(NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		net.minecraft.network.chat.MutableComponent name = Component.empty();
		if (rank != TowerRank.MOB) {
			name.append(Component.literal(rank.label() + " ").withStyle(rank.color()));
		}
		name.append(Component.literal(element.label() + " ").withStyle(element.color())).append(mob.getType().getDescription());
		mob.setCustomName(name);
	}

	/** Sends away anything riding the mob or carrying it, so a jockey never enters the tower half made. */
	public static void settle(final Mob mob) {
		for (Entity rider : List.copyOf(mob.getPassengers())) {
			rider.stopRiding();
			rider.discard();
		}
		Entity vehicle = mob.getVehicle();
		if (vehicle != null) {
			mob.stopRiding();
			vehicle.discard();
		}
	}

	/** Raises one tower mob on the floor, somewhere clear and away from every player; null if none fit. */
	public static @Nullable Mob spawnOne(final ServerLevel tower, final int floor) {
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = pool(floor);
		EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
		Mob mob = type.create(tower, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		BlockPos origin = TowerLayout.origin(floor);
		List<ServerPlayer> players = playersOn(tower, floor);
		Vec3 arrival = TowerLayout.arrival(floor);
		// Finalized first: a magma cube's size, and so the room it needs, is only drawn here.
		BlockPos middle = origin.offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
		mob.snapTo(Vec3.atBottomCenterOf(middle));
		mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(middle), EntitySpawnReason.EVENT, null);
		settle(mob);
		for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
			int x = origin.getX() + 1 + random.nextInt(TowerLayout.SIZE - 2);
			int z = origin.getZ() + 1 + random.nextInt(TowerLayout.SIZE - 2);
			BlockPos feet = new BlockPos(x, TowerLayout.BASE_Y, z);
			Vec3 spot = Vec3.atBottomCenterOf(feet);
			if (!tower.isLoaded(feet) || spot.distanceTo(arrival) < MIN_PLAYER_DISTANCE || tooClose(players, spot)) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			if (!standsFree(tower, mob, feet)) {
				continue;
			}
			makeTowerMob(mob, floor, TowerElement.values()[random.nextInt(TowerElement.values().length)]);
			if (tower.addFreshEntity(mob)) {
				return mob;
			}
			return null;
		}
		mob.discard();
		return null;
	}

	/** On solid ground, with the mob's whole body clear of every block. */
	static boolean standsFree(final ServerLevel tower, final Mob mob, final BlockPos feet) {
		return tower.getBlockState(feet.below()).isFaceSturdy(tower, feet.below(), net.minecraft.core.Direction.UP)
			&& tower.noCollision(mob, mob.getBoundingBox());
	}

	private static boolean tooClose(final List<ServerPlayer> players, final Vec3 spot) {
		for (ServerPlayer player : players) {
			if (player.position().distanceTo(spot) < MIN_PLAYER_DISTANCE) {
				return true;
			}
		}
		return false;
	}

	static List<ServerPlayer> playersOn(final ServerLevel tower, final int floor) {
		List<ServerPlayer> on = new ArrayList<>();
		for (ServerPlayer player : tower.players()) {
			if (!player.isSpectator() && TowerLayout.floorAt(player.blockPosition()) == floor) {
				on.add(player);
			}
		}
		return on;
	}

	static AABB room(final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY() - 1, o.getZ(), o.getX() + TowerLayout.SIZE, o.getY() + TowerLayout.HEIGHT, o.getZ() + TowerLayout.SIZE);
	}

	/** Tower mobs standing on the floor now; its own record, not a kind, says which floor a mob is of. */
	public static int count(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(Mob.class, room(floor), mob -> {
			State state = stateOf(mob);
			return mob.isAlive() && state != null && state.floor() == floor && state.rank() == TowerRank.MOB;
		}).size();
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		Map<Integer, Boolean> occupied = new HashMap<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
				occupied.put(floor, true);
			}
		}
		for (int floor : occupied.keySet()) {
			// The whole room is held loaded while it is occupied, so no mob in it goes uncounted.
			BlockPos middle = TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
			tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.CHUNK_RADIUS);
			int room = FLOOR_CAP - count(tower, floor);
			for (int i = 0; i < Math.min(room, SPAWN_BATCH); i++) {
				spawnOne(tower, floor);
			}
		}
	}

	/** A hit's damage once the tower has had its say: a tower mob hits harder, and a big one takes less. */
	public static float scaleDamage(final LivingEntity victim, final DamageSource source, final float amount) {
		float scaled = amount;
		if (source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
			State from = stateOf(attacker);
			if (from != null) {
				scaled = (float) (TowerCurve.damage(scaled, from.floor()) * from.rank().damageFactor());
			}
		}
		State to = stateOf(victim);
		if (to != null && to.healthDivisor() > 1.0) {
			scaled = (float) (scaled / to.healthDivisor());
		}
		return scaled;
	}
}
