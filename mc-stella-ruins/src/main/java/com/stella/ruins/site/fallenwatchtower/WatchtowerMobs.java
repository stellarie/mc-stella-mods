package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The three themed mobs of the tower, the tables the tower owns, and the clamp that keeps the garrison inside. */
public final class WatchtowerMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/fallen_watchtower");
	public static final ResourceKey<LootTable> STORE_LOOT = table("chests/fallen_watchtower_store");
	public static final ResourceKey<LootTable> ARCHER_DROPS = table("entities/watch_archer");
	public static final ResourceKey<LootTable> CROSSBOWMAN_DROPS = table("entities/tower_crossbowman");
	public static final ResourceKey<LootTable> CAPTAIN_DROPS = table("entities/watch_captain");

	/** Layout markers that name the inside corners of the shaft, for the clamp. */
	public static final String BOUND_MIN = "bound_min";
	public static final String BOUND_MAX = "bound_max";
	/** The tag a garrison mob carries: its box and its post, as whole world coordinates. */
	public static final String GARRISON_TAG = "mod-stella-ruins.garrison.";

	public static final int VOLLEY_TICKS = 200;
	public static final int VOLLEY_ARROWS = 3;
	public static final double VOLLEY_SPREAD_DEGREES = 9.0;
	public static final float VOLLEY_POWER = 1.6F;
	public static final int CAP_COLOR = 0x4F8A3A;
	public static final double CAPTAIN_HEALTH = 5.0;
	public static final float CAPTAIN_SCALE = 1.3F;
	public static final double CAPTAIN_DAMAGE = 1.5;

	public static RuinMobKind WATCH_ARCHER;
	public static RuinMobKind TOWER_CROSSBOWMAN;
	public static RuinMobKind WATCH_CAPTAIN;

	/** A test sets this to see the arrows of a volley. It is null in play. */
	public static volatile @Nullable Consumer<List<UUID>> volleyListener;

	private WatchtowerMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		MobAbility clamp = new MobAbility() {
			@Override
			public void tick(final Mob mob, final ServerLevel level) {
				keepInside(mob);
			}
		};
		WATCH_ARCHER = RuinMobKind.builder("watch_archer")
			.base(EntityTypes.SKELETON)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW))
			.gear(EquipmentSlot.HEAD, () -> capOf())
			.ability(clamp)
			.drops(ARCHER_DROPS)
			.register();
		TOWER_CROSSBOWMAN = RuinMobKind.builder("tower_crossbowman")
			.base(EntityTypes.PILLAGER)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.CROSSBOW))
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.IRON_HELMET))
			.ability(clamp)
			.drops(CROSSBOWMAN_DROPS)
			.register();
		WATCH_CAPTAIN = RuinMobKind.builder("watch_captain")
			.base(EntityTypes.STRAY)
			.scale(CAPTAIN_SCALE)
			.health(CAPTAIN_HEALTH)
			.damage(CAPTAIN_DAMAGE)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW))
			.gear(EquipmentSlot.HEAD, () -> capOf())
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					keepInside(mob);
					if (mob.getTarget() != null && mob.tickCount % VOLLEY_TICKS < RuinMobs.ABILITY_TICKS) {
						volley(mob, level, mob.getTarget());
					}
				}
			})
			.drops(CAPTAIN_DROPS)
			.boss(true)
			.register();
	}

	private static ItemStack capOf() {
		ItemStack cap = new ItemStack(Items.LEATHER_HELMET);
		cap.set(DataComponents.DYED_COLOR, new DyedItemColor(CAP_COLOR));
		return cap;
	}

	/** Looses {@link #VOLLEY_ARROWS} arrows at the target, spread across a fan. Returns the arrows it added. */
	public static List<UUID> volley(final Mob mob, final ServerLevel level, final LivingEntity target) {
		List<UUID> arrows = new ArrayList<>();
		Vec3 from = mob.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 aim = to.subtract(from);
		for (int i = 0; i < VOLLEY_ARROWS; i++) {
			double turn = Math.toRadians((i - (VOLLEY_ARROWS - 1) / 2.0) * VOLLEY_SPREAD_DEGREES);
			double dx = aim.x * Math.cos(turn) - aim.z * Math.sin(turn);
			double dz = aim.x * Math.sin(turn) + aim.z * Math.cos(turn);
			ItemStack ammo = new ItemStack(Items.ARROW);
			Arrow arrow = new Arrow(level, mob, ammo, mob.getMainHandItem());
			Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, aim.y + Math.sqrt(aim.x * aim.x + aim.z * aim.z) * 0.1, dz, VOLLEY_POWER, 0.0F);
			if (level.getEntity(arrow.getUUID()) == arrow) {
				arrows.add(arrow.getUUID());
			}
		}
		mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.8F);
		Consumer<List<UUID>> listener = volleyListener;
		if (listener != null) {
			listener.accept(List.copyOf(arrows));
		}
		return arrows;
	}

	/** The tag that gives a mob its box and its post. */
	public static String garrisonTag(final int minX, final int minZ, final int maxX, final int maxZ, final int homeX, final int homeY, final int homeZ) {
		return GARRISON_TAG + minX + "." + minZ + "." + maxX + "." + maxZ + "." + homeX + "." + homeY + "." + homeZ;
	}

	/**
	 * Puts a garrison mob back on its post when it stands outside the shaft's inside, so a retreat of the mobs mod's
	 * archer cannot carry it out through the breach. Returns true when it moved the mob.
	 */
	public static boolean keepInside(final Mob mob) {
		for (String tag : mob.entityTags()) {
			if (!tag.startsWith(GARRISON_TAG)) {
				continue;
			}
			String[] parts = tag.substring(GARRISON_TAG.length()).split("[.]");
			if (parts.length != 7) {
				return false;
			}
			int[] v = new int[7];
			for (int i = 0; i < 7; i++) {
				v[i] = Integer.parseInt(parts[i]);
			}
			double x = mob.getX();
			double z = mob.getZ();
			if (x < v[0] || x >= v[2] + 1 || z < v[1] || z >= v[3] + 1) {
				mob.snapTo(v[4] + 0.5, v[5], v[6] + 0.5, mob.getYRot(), mob.getXRot());
				mob.getNavigation().stop();
				return true;
			}
			return false;
		}
		return false;
	}

}
