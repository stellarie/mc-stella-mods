package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Void shamans, when the spells mod is installed: now and then a floor holds a skeleton that carries one
 * spell to strike with and one to aid itself, and blinks away to a safe spot when struck. They are never
 * elites or bosses. A slain shaman may leave a rune, and a void ingot, which is the only way to void gear.
 */
public final class TowerShamans {
	/** The share of floors that hold a shaman. */
	public static final double FLOOR_CHANCE = 0.02;
	public static final double RUNE_CHANCE = TowerRunes.DROP_CHANCE;
	/** A slain shaman leaves a void ingot with this chance, and this much more for each floor it stood on. */
	public static final double INGOT_BASE = 0.005;
	public static final double INGOT_PER_FLOOR = 0.0001;
	/** A struck shaman blinks away, and cannot again for this long. */
	public static final int TELEPORT_TICKS = 200;
	/** A slain shaman's floor gives up another after this long. */
	public static final int REST_TICKS = 12_000;
	public static final double CAST_RANGE = 24.0;
	/** How often a shaman thinks about casting. */
	public static final int THINK_TICKS = 10;
	private static final long SALT = 0x5348414DL;

	/** The two spells a shaman carries: the one it strikes with, and the one it aids itself with. */
	public record Kit(String offensive, String support) {
		public static final Codec<Kit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("offensive").forGetter(Kit::offensive),
			Codec.STRING.fieldOf("support").forGetter(Kit::support)
		).apply(instance, Kit::new));
	}

	public static final AttachmentType<Kit> KIT = AttachmentRegistry.create(StellaTower.id("shaman_kit"), builder -> builder.persistent(Kit.CODEC));
	private static final AttachmentType<Integer> BLINK_AT = AttachmentRegistry.create(StellaTower.id("shaman_blink_at"));
	private static final AttachmentType<Integer> STRIKE_AT = AttachmentRegistry.create(StellaTower.id("shaman_strike_at"));
	private static final AttachmentType<Integer> AID_AT = AttachmentRegistry.create(StellaTower.id("shaman_aid_at"));

	private static final Map<Integer, Boolean> PINNED_HOLD = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Double> PINNED_INGOT = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PINNED_REST = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<Integer, Integer> PINNED_BLINK = new java.util.concurrent.ConcurrentHashMap<>();
	/** The server tick each floor's shaman is rested until, after one was slain; only ever touched on the server thread. */
	private static final Map<Integer, Integer> RESTED_UNTIL = new java.util.HashMap<>();

	private TowerShamans() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerShamans::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0.0F && entity.isAlive() && isShaman(entity) && entity.level() instanceof ServerLevel level) {
				blink(level, (Skeleton) entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (!isShaman(entity) || state == null || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			RESTED_UNTIL.put(state.floor(), level.getServer().getTickCount() + PINNED_REST.getOrDefault(state.floor(), REST_TICKS));
			if (entity.getLastHurtByPlayer() != null && level.getGameRules().get(GameRules.MOB_DROPS)
				&& entity.getRandom().nextDouble() < PINNED_INGOT.getOrDefault(state.floor(), ingotChance(state.floor()))) {
				entity.spawnAtLocation(level, new ItemStack(TowerItems.VOID_INGOT));
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> RESTED_UNTIL.clear());
	}

	/** The chance a slain shaman of this floor leaves a void ingot. */
	public static double ingotChance(final int floor) {
		return Math.min(1.0, INGOT_BASE + INGOT_PER_FLOOR * Math.max(0, floor));
	}

	/** Whether this floor of the world holds a shaman: a fixed share of floors do, drawn from the seed. */
	public static boolean holds(final long seed, final int floor) {
		Boolean pin = PINNED_HOLD.get(floor);
		if (pin != null) {
			return pin;
		}
		return new Random(seed ^ SALT ^ (floor * 0x9E3779B97F4A7C15L)).nextDouble() < FLOOR_CHANCE;
	}

	public static void pinHolds(final int floor, final @Nullable Boolean holds) {
		pin(PINNED_HOLD, floor, holds);
	}

	public static void pinIngotChance(final int floor, final @Nullable Double chance) {
		pin(PINNED_INGOT, floor, chance);
	}

	public static void pinRestTicks(final int floor, final @Nullable Integer ticks) {
		pin(PINNED_REST, floor, ticks);
	}

	public static void pinBlinkTicks(final int floor, final @Nullable Integer ticks) {
		pin(PINNED_BLINK, floor, ticks);
	}

	private static <T> void pin(final Map<Integer, T> map, final int floor, final @Nullable T value) {
		if (value == null) {
			map.remove(floor);
		} else {
			map.put(floor, value);
		}
	}

	public static boolean isShaman(final LivingEntity entity) {
		return entity instanceof Skeleton && entity.hasAttached(KIT);
	}

	/** The shamans standing on a floor. */
	public static List<Skeleton> on(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(Skeleton.class, TowerMobs.room(floor), skeleton -> skeleton.isAlive() && isShaman(skeleton));
	}

	/** Keeps a floor that holds a shaman stocked with one, once it has rested, if the spells mod is here. */
	public static void haunt(final ServerLevel tower, final int floor) {
		TowerSpells.Caster spells = TowerSpells.get(floor);
		if (!spells.available() || !holds(tower.getSeed(), floor) || !TowerElites.roomLoaded(tower, floor) || !on(tower, floor).isEmpty()) {
			return;
		}
		Integer rested = RESTED_UNTIL.get(floor);
		if (rested != null && tower.getServer().getTickCount() < rested) {
			return;
		}
		Skeleton shaman = make(tower, floor, spells, tower.getRandom());
		if (shaman == null) {
			return;
		}
		if (!TowerMobs.placeInRoom(tower, floor, shaman, tower.getRandom()) || !tower.addFreshEntity(shaman)) {
			shaman.discard();
			return;
		}
	}

	/** Raises a shaman of the floor at a spot; null if it could not be added. */
	public static @Nullable Skeleton summon(final ServerLevel tower, final int floor, final Vec3 at) {
		Skeleton shaman = make(tower, floor, TowerSpells.get(floor), tower.getRandom());
		if (shaman == null) {
			return null;
		}
		shaman.snapTo(at);
		return tower.addFreshEntity(shaman) ? shaman : null;
	}

	/** A shaman of the floor, carrying its two spells; not yet in the world. Null when no spells are to be had. */
	private static @Nullable Skeleton make(final ServerLevel tower, final int floor, final TowerSpells.Caster spells, final RandomSource random) {
		if (spells.offensive().isEmpty() || spells.support().isEmpty()) {
			return null;
		}
		Skeleton shaman = EntityTypes.SKELETON.create(tower, EntitySpawnReason.EVENT);
		if (shaman == null) {
			return null;
		}
		TowerPlan room = TowerPlan.at(tower, floor);
		net.minecraft.core.BlockPos middle = TowerLayout.origin(floor).offset(room.width() / 2, 0, room.depth() / 2);
		shaman.snapTo(Vec3.atBottomCenterOf(middle));
		shaman.finalizeSpawn(tower, tower.getCurrentDifficultyAt(middle), EntitySpawnReason.EVENT, null);
		TowerMobs.settle(shaman);
		TowerElement element = TowerElement.values()[random.nextInt(TowerElement.values().length)];
		// Never an elite or a boss: the plain rank, whatever the floor.
		TowerMobs.makeTowerMob(shaman, floor, element, TowerRank.MOB);
		shaman.setAttached(KIT, new Kit(
			spells.offensive().get(random.nextInt(spells.offensive().size())),
			spells.support().get(random.nextInt(spells.support().size()))
		));
		shaman.setCustomName(Component.literal(element.label() + " ").withStyle(element.color())
			.append(Component.literal("Void Shaman").withStyle(ChatFormatting.DARK_PURPLE)));
		return shaman;
	}

	private static int now(final LivingEntity entity) {
		return entity.level().getServer().getTickCount();
	}

	/** A struck shaman blinks to a safe spot of its floor, and rests before it can again. */
	private static void blink(final ServerLevel tower, final Skeleton shaman) {
		TowerMobs.State state = TowerMobs.stateOf(shaman);
		int now = now(shaman);
		if (state == null || now < shaman.getAttachedOrElse(BLINK_AT, Integer.MIN_VALUE)) {
			return;
		}
		Vec3 from = shaman.position();
		if (TowerMobs.placeInRoom(tower, state.floor(), shaman, tower.getRandom())) {
			shaman.setAttached(BLINK_AT, now + PINNED_BLINK.getOrDefault(state.floor(), TELEPORT_TICKS));
			shaman.getNavigation().stop();
			tower.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 24, 0.3, 0.6, 0.3, 0.2);
		} else {
			// No safe spot was found: it stays where it stood, not stranded where the search left off.
			shaman.snapTo(from.x, from.y, from.z);
		}
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % THINK_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		java.util.Set<Integer> floors = new java.util.HashSet<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
				floors.add(floor);
			}
		}
		for (int floor : floors) {
			TowerSpells.Caster spells = TowerSpells.get(floor);
			for (Skeleton shaman : on(tower, floor)) {
				think(tower, shaman, spells);
			}
		}
	}

	/** A shaman strikes its quarry with its offensive spell and aids itself with the other, each on its own cooldown. */
	private static void think(final ServerLevel tower, final Skeleton shaman, final TowerSpells.Caster spells) {
		Kit kit = shaman.getAttached(KIT);
		if (kit == null) {
			return;
		}
		int now = now(shaman);
		LivingEntity quarry = shaman.getTarget();
		if (quarry == null || !quarry.isAlive() || shaman.distanceTo(quarry) > CAST_RANGE || TowerSafety.sheltered(quarry)) {
			// A player in a safe zone is never the quarry: the nearest one who is not is picked instead.
			quarry = tower.getNearestPlayer(shaman.getX(), shaman.getY(), shaman.getZ(), CAST_RANGE,
				player -> !player.isSpectator() && !((ServerPlayer) player).isCreative() && !TowerSafety.sheltered((LivingEntity) player));
		}
		boolean inFight = quarry != null && shaman.hasLineOfSight(quarry);
		if (inFight && now >= shaman.getAttachedOrElse(STRIKE_AT, Integer.MIN_VALUE) && spells.castAt(shaman, kit.offensive(), quarry)) {
			shaman.setAttached(STRIKE_AT, now + Math.max(THINK_TICKS, spells.cooldownOf(kit.offensive())));
		}
		if ((inFight || shaman.getHealth() < shaman.getMaxHealth()) && now >= shaman.getAttachedOrElse(AID_AT, Integer.MIN_VALUE)
			&& spells.support(shaman, kit.support())) {
			shaman.setAttached(AID_AT, now + Math.max(THINK_TICKS, spells.cooldownOf(kit.support())));
		}
	}
}
