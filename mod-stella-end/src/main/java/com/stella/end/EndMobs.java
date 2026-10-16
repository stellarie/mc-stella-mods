package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The castle's mobs: what each floor spawns, and how each one is kitted, levelled and recorded. */
public final class EndMobs {
	public static final String FLOOR_TAG = "mod-stella-end.floor_";
	public static final String WITCH_TAG = "mod-stella-end.witch";
	public static final String ELITE_TAG = "mod-stella-end.elite";
	/** The roll space of {@link #pick}: shares are parts of this. */
	public static final int ROLLS = 1000;
	public static final int FIRST_SHULKER_FLOOR = 3;
	public static final int ELITE_WITCH_FLOOR = 5;
	/** The elite witch multiplies its total health by two. */
	private static final Identifier ELITE_HEALTH = StellaEnd.id("elite_witch_health");
	/** The name, never the class, of the mobs mod's own shield goal: the mod is optional. */
	private static final String SHIELD_GOAL_OF_MOBS = "ShieldUseGoal";

	public enum Kind {
		WITHER_SKELETON,
		ENDERMAN,
		SHULKER
	}

	private EndMobs() {
	}

	/** How many parts of {@link #ROLLS} a floor gives a kind. Floors 1 and 2 give the shulker share to wither skeletons. */
	public static int share(final int floor, final Kind kind) {
		boolean shulkers = floor >= FIRST_SHULKER_FLOOR;
		return switch (kind) {
			case WITHER_SKELETON -> shulkers ? 700 : 850;
			case ENDERMAN -> 150;
			case SHULKER -> shulkers ? 150 : 0;
		};
	}

	public static Kind pick(final int floor, final int roll) {
		int skeletons = share(floor, Kind.WITHER_SKELETON);
		if (roll < skeletons) {
			return Kind.WITHER_SKELETON;
		}
		return roll < skeletons + share(floor, Kind.ENDERMAN) ? Kind.ENDERMAN : Kind.SHULKER;
	}

	/** Spawns a castle mob with its kit and level, standing at {@code feet}. Null when the level refuses it. */
	public static @Nullable Mob spawn(final ServerLevel end, final int floor, final Kind kind, final BlockPos feet) {
		Mob mob = switch (kind) {
			case WITHER_SKELETON -> create(end, EntityTypes.WITHER_SKELETON);
			case ENDERMAN -> create(end, EntityTypes.ENDERMAN);
			case SHULKER -> create(end, EntityTypes.SHULKER);
		};
		if (mob == null) {
			return null;
		}
		prepare(mob, floor, feet, EndLevels.floorLevel(floor));
		if (mob instanceof WitherSkeleton skeleton) {
			kit(skeleton);
		}
		if (!end.addFreshEntity(mob)) {
			return null;
		}
		if (mob instanceof WitherSkeleton skeleton) {
			guard(skeleton);
		}
		if (mob instanceof Enderman enderman) {
			hunt(end, enderman);
		}
		return mob;
	}

	/** Spawns the floor's witch: the elite one on floor 5. Null for a floor with no witch or when the level refuses it. */
	public static @Nullable Witch spawnWitch(final ServerLevel end, final int floor, final BlockPos feet) {
		if (floor < 1 || floor > ELITE_WITCH_FLOOR) {
			return null;
		}
		Witch witch = create(end, EntityTypes.WITCH);
		if (witch == null) {
			return null;
		}
		boolean elite = floor == ELITE_WITCH_FLOOR;
		prepare(witch, floor, feet, elite ? EndLevels.eliteWitchLevel(floor) : EndLevels.floorLevel(floor));
		witch.addTag(WITCH_TAG);
		if (elite) {
			witch.addTag(ELITE_TAG);
			witch.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(ELITE_HEALTH, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			witch.setHealth(witch.getMaxHealth());
			witch.getGoalSelector().addGoal(1, new EliteWitchAuraGoal(witch));
		}
		return end.addFreshEntity(witch) ? witch : null;
	}

	/** An enderman takes the nearest player within hunting range as its target, with no eye contact asked. */
	static void hunt(final ServerLevel end, final Enderman enderman) {
		ServerPlayer nearest = null;
		double best = FloorSpawner.HUNT_RANGE;
		for (ServerPlayer player : end.players()) {
			double distance = player.distanceTo(enderman);
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && distance <= best) {
				nearest = player;
				best = distance;
			}
		}
		if (nearest != null && enderman.getTarget() != nearest) {
			enderman.setTarget(nearest);
			BehaviorLog.info("[END] hunt floor={} distance={}", floorOf(enderman), best);
		}
	}

	private static <T extends Mob> @Nullable T create(final ServerLevel end, final EntityType<T> type) {
		return type.create(end, EntitySpawnReason.SPAWNER);
	}

	/** What every castle mob shares: the claim on its level, its floor tag, its place, and a life that outlasts despawn. */
	private static void prepare(final Mob mob, final int floor, final BlockPos feet, final int level) {
		LevelBridge.claim(mob);
		mob.addTag(FLOOR_TAG + floor);
		mob.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, mob.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		LevelBridge.apply(mob, level);
	}

	public static int floorOf(final Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(FLOOR_TAG)) {
				try {
					int floor = Integer.parseInt(tag.substring(FLOOR_TAG.length()));
					return floor >= 1 && floor <= EndCastle.FLOORS ? floor : 0;
				} catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		return 0;
	}

	public static boolean isCastleMob(final Entity entity) {
		return floorOf(entity) > 0;
	}

	public static boolean isElite(final Entity entity) {
		return entity.entityTags().contains(ELITE_TAG);
	}

	public static boolean isWitch(final Entity entity) {
		return entity.entityTags().contains(WITCH_TAG);
	}

	/** A netherite sword, a shield and iron armour; none of it drops, since treasure comes from chests. */
	private static void kit(final WitherSkeleton skeleton) {
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			skeleton.setDropChance(slot, 0.0F);
		}
	}

	/** Adds the local shield goal unless the mobs mod already put its own on the skeleton when it loaded. */
	private static void guard(final WitherSkeleton skeleton) {
		boolean owned = skeleton.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals(SHIELD_GOAL_OF_MOBS));
		if (!owned) {
			skeleton.getGoalSelector().addGoal(1, new CastleShieldGoal(skeleton));
		}
	}
}
