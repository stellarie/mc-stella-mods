package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/** The castle's mobs: what each floor spawns, and how each one is kitted, levelled and recorded. */
public final class EndMobs {
	public static final String FLOOR_TAG = "mod-stella-end.floor_";
	public static final String ARCHER_TAG = Factions.PREFIX + "archer";
	public static final String MOUNT_TAG = Factions.PREFIX + "mount";
	/** The roll space of {@link #pick}: shares are parts of this. */
	public static final int ROLLS = 1000;
	public static final int FIRST_SHULKER_FLOOR = 3;
	/** Parts of {@link #ROLLS} of the castle wither skeletons that ride a skeleton horse: one in ten. */
	public static final int MOUNT_SHARE = 100;
	/** A castle wither skeleton moves at this part of its base speed and hits at this multiple of its base damage. */
	public static final double SPEED_FACTOR = 0.7;
	public static final double DAMAGE_FACTOR = 1.5;
	public static final Identifier SPEED_ID = StellaEnd.id("castle_skeleton_speed");
	public static final Identifier DAMAGE_ID = StellaEnd.id("castle_skeleton_damage");
	/** The name, never the class, of the mobs mod's own shield goal: the mod is optional. */
	private static final String SHIELD_GOAL_OF_MOBS = "ShieldUseGoal";

	public enum Kind {
		WITHER_SKELETON,
		ARCHER,
		SHULKER
	}

	private EndMobs() {
	}

	/** How many parts of {@link #ROLLS} a floor gives a kind. Floors 1 and 2 give the shulker share to wither skeletons. */
	public static int share(final int floor, final Kind kind) {
		boolean shulkers = floor >= FIRST_SHULKER_FLOOR;
		return switch (kind) {
			case WITHER_SKELETON -> shulkers ? 600 : 750;
			case ARCHER -> 250;
			case SHULKER -> shulkers ? 150 : 0;
		};
	}

	public static Kind pick(final int floor, final int roll) {
		int skeletons = share(floor, Kind.WITHER_SKELETON);
		if (roll < skeletons) {
			return Kind.WITHER_SKELETON;
		}
		return roll < skeletons + share(floor, Kind.ARCHER) ? Kind.ARCHER : Kind.SHULKER;
	}

	/** Whether a new castle wither skeleton rides a skeleton horse. */
	public static boolean rollMount(final RandomSource random) {
		return random.nextInt(ROLLS) < MOUNT_SHARE;
	}

	/** Spawns a castle mob with its kit and level, standing at {@code feet}. A wither skeleton rides by chance. Null when the level refuses it. */
	public static @Nullable Mob spawn(final ServerLevel end, final int floor, final Kind kind, final BlockPos feet) {
		return spawn(end, floor, kind, feet, kind == Kind.WITHER_SKELETON && rollMount(end.getRandom()));
	}

	/** As {@link #spawn(ServerLevel, int, Kind, BlockPos)}, with the ride decided by the caller. Only a wither skeleton can ride. */
	public static @Nullable Mob spawn(final ServerLevel end, final int floor, final Kind kind, final BlockPos feet, final boolean mounted) {
		Mob mob = switch (kind) {
			case WITHER_SKELETON -> create(end, EntityTypes.WITHER_SKELETON);
			case ARCHER -> create(end, EntityTypes.SKELETON);
			case SHULKER -> create(end, EntityTypes.SHULKER);
		};
		if (mob == null) {
			return null;
		}
		prepare(mob, floor, feet, kind == Kind.ARCHER ? EndLevels.ARCHER : EndLevels.floorLevel(floor));
		if (mob instanceof WitherSkeleton skeleton) {
			kit(end, skeleton);
		}
		if (mob instanceof Skeleton archer) {
			bow(archer);
		}
		SkeletonHorse horse = null;
		if (mounted && mob instanceof WitherSkeleton) {
			horse = mount(end, feet);
			if (horse == null) {
				return null;
			}
		}
		if (!end.addFreshEntity(mob)) {
			if (horse != null) {
				horse.discard();
			}
			return null;
		}
		if (horse != null) {
			mob.startRiding(horse, true, false);
		}
		if (mob instanceof WitherSkeleton skeleton) {
			guard(skeleton);
			skeleton.getGoalSelector().addGoal(1, new TeleportChaseGoal(skeleton));
		}
		if (mob instanceof Skeleton archer) {
			archer.getGoalSelector().addGoal(1, new HurtTeleportGoal(archer));
			archer.getGoalSelector().addGoal(2, new ArcherVolleyGoal(archer));
		}
		return mob;
	}

	/** The level 95 skeleton horse of a mounted wither skeleton, added to the level; null when the level refuses it. */
	private static @Nullable SkeletonHorse mount(final ServerLevel end, final BlockPos feet) {
		SkeletonHorse horse = create(end, EntityTypes.SKELETON_HORSE);
		if (horse == null) {
			return null;
		}
		LevelBridge.claim(horse);
		horse.addTag(MOUNT_TAG);
		horse.setTamed(true);
		horse.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, horse.getRandom().nextFloat() * 360.0F, 0.0F);
		horse.setPersistenceRequired();
		LevelBridge.apply(horse, EndLevels.MOUNT);
		horse.getGoalSelector().addGoal(1, new MountGoals.Aura(horse));
		return end.addFreshEntity(horse) ? horse : null;
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

	public static boolean isArcher(final Entity entity) {
		return entity.entityTags().contains(ARCHER_TAG);
	}

	/**
	 * Slower and harder-hitting than its base, in full netherite with three random enchantments on every piece and on the
	 * sword, a thorns shield in the off hand, and nothing that drops: treasure comes from chests.
	 */
	private static void kit(final ServerLevel end, final WitherSkeleton skeleton) {
		Registry<Enchantment> enchantments = end.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RandomSource random = skeleton.getRandom();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, MobGear.skeletonSword(random, enchantments));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, MobGear.thornsShield(enchantments));
		MobGear.netheriteArmor(skeleton, MobGear.SKELETON_ENCHANTS, random, enchantments);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			skeleton.setDropChance(slot, 0.0F);
		}
		scale(skeleton, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_FACTOR);
		scale(skeleton, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_FACTOR);
		skeleton.addTag(Factions.PREFIX + "wither_skeleton");
	}

	/** Multiplies an attribute by a factor of what it is after every other modifier, so a level's scaling stays under it. */
	private static void scale(final Mob mob, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, final Identifier id, final double factor) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
			instance.addPermanentModifier(new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** A skeleton archer wears full netherite and holds a bow; the volley and the escape goals follow once it stands in the level. */
	private static void bow(final Skeleton archer) {
		archer.addTag(ARCHER_TAG);
		MobGear.netheriteArmor(archer, 0, archer.getRandom(), archer.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
		archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			archer.setDropChance(slot, 0.0F);
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
