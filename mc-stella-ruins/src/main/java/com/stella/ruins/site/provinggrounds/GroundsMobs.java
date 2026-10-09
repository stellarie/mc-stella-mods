package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The four themed mobs of the proving grounds. Each is a vanilla type with a name and a look. The retiarius has a net
 * and the hound is always angry.
 */
public final class GroundsMobs {
	public static final String GLADIATOR = "gladiator";
	public static final String RETIARIUS = "retiarius";
	public static final String HOUND = "arena_hound";
	public static final String PIT_BEAST = "pit_beast";
	public static final Set<String> KIND_IDS = Set.of(GLADIATOR, RETIARIUS, HOUND, PIT_BEAST);

	public static final float GLADIATOR_SCALE = 1.05F;
	public static final double HOUND_HEALTH = 2.0;
	public static final double BEAST_HEALTH = 3.0;
	public static final int NET_TICKS = 40;
	public static final int NET_AMPLIFIER = 1;
	public static final double HOUND_SIGHT = 40.0;

	public static final ResourceKey<LootTable> GLADIATOR_DROPS = table(GLADIATOR);
	public static final ResourceKey<LootTable> RETIARIUS_DROPS = table(RETIARIUS);
	public static final ResourceKey<LootTable> HOUND_DROPS = table(HOUND);
	public static final ResourceKey<LootTable> BEAST_DROPS = table(PIT_BEAST);

	private static RuinMobKind gladiator;
	private static RuinMobKind retiarius;
	private static RuinMobKind hound;
	private static RuinMobKind beast;

	private GroundsMobs() {
	}

	private static ResourceKey<LootTable> table(final String kind) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("entities/" + kind));
	}

	public static RuinMobKind gladiator() {
		return gladiator;
	}

	public static RuinMobKind retiarius() {
		return retiarius;
	}

	public static RuinMobKind hound() {
		return hound;
	}

	public static RuinMobKind beast() {
		return beast;
	}

	/** The kind of a given id, for the wave plan and the spawn markers. */
	public static RuinMobKind kind(final String id) {
		return switch (id) {
			case GLADIATOR -> gladiator;
			case RETIARIUS -> retiarius;
			case HOUND -> hound;
			case PIT_BEAST -> beast;
			default -> throw new IllegalArgumentException("not a proving grounds kind: " + id);
		};
	}

	/** True for a mob of one of the proving grounds' kinds. */
	public static boolean isGroundsMob(final Entity entity) {
		RuinMobKind kind = RuinMobs.kindOf(entity);
		return kind != null && KIND_IDS.contains(kind.id());
	}

	public static void register() {
		gladiator = RuinMobKind.builder(GLADIATOR)
			.base(EntityTypes.VINDICATOR)
			.scale(GLADIATOR_SCALE)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.IRON_HELMET))
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.IRON_AXE))
			.drops(GLADIATOR_DROPS)
			.register();
		retiarius = RuinMobKind.builder(RETIARIUS)
			.base(EntityTypes.DROWNED)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.GOLDEN_HELMET))
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.TRIDENT))
			.ability(new NetAbility())
			.drops(RETIARIUS_DROPS)
			.register();
		hound = RuinMobKind.builder(HOUND)
			.base(EntityTypes.WOLF)
			.health(HOUND_HEALTH)
			.ability(new HoundAbility())
			.hostile()
			.drops(HOUND_DROPS)
			.register();
		beast = RuinMobKind.builder(PIT_BEAST)
			.base(EntityTypes.RAVAGER)
			.health(BEAST_HEALTH)
			.drops(BEAST_DROPS)
			.boss(true)
			.register();
	}

	/** A hit from the retiarius slows the player it hit, as a net would. */
	static final class NetAbility implements MobAbility {
		@Override
		public void onHurtTarget(final Mob mob, final LivingEntity target) {
			if (target instanceof Player player) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, NET_TICKS, NET_AMPLIFIER));
			}
		}
	}

	/** An arena hound hunts the nearest player in sight range and never calms down. */
	static final class HoundAbility implements MobAbility {
		@Override
		public void tick(final Mob mob, final ServerLevel level) {
			if (!(mob instanceof Wolf wolf) || (wolf.getTarget() != null && wolf.getTarget().isAlive())) {
				return;
			}
			ServerPlayer nearest = null;
			double best = HOUND_SIGHT * HOUND_SIGHT;
			for (ServerPlayer player : level.players()) {
				double d = player.distanceToSqr(wolf);
				if (!player.isSpectator() && !player.isCreative() && player.isAlive() && d <= best) {
					best = d;
					nearest = player;
				}
			}
			if (nearest != null) {
				wolf.setTarget(nearest);
			}
		}
	}
}
