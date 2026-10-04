package com.stella.ruins.site.wayshrine;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The three themed mobs of the wayshrine. Each is a vanilla type with a name, a look, and one ability. The abilities
 * count the mob's own age, so each fires once in its period whatever the world clock does.
 */
public final class WayshrineMobs {
	public static final String PILGRIM = "hollow_pilgrim";
	public static final String WISP = "lantern_wisp";
	public static final String ACOLYTE = "fallen_acolyte";
	public static final Set<String> KIND_IDS = Set.of(PILGRIM, WISP, ACOLYTE);

	public static final int HEAL_PERIOD = 100;
	public static final double HEAL_RANGE = 6.0;
	public static final float HEAL_AMOUNT = 4.0F;
	public static final int BLIND_TICKS = 40;
	public static final int SLOW_PERIOD = 160;
	public static final double SLOW_RANGE = 8.0;
	public static final int SLOW_TICKS = 100;
	private static final int WHITE = 0xFFFFFF;

	public static final ResourceKey<LootTable> PILGRIM_DROPS = table(PILGRIM);
	public static final ResourceKey<LootTable> WISP_DROPS = table(WISP);
	public static final ResourceKey<LootTable> ACOLYTE_DROPS = table(ACOLYTE);

	private static RuinMobKind pilgrim;
	private static RuinMobKind wisp;
	private static RuinMobKind acolyte;

	private WayshrineMobs() {
	}

	private static ResourceKey<LootTable> table(final String kind) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("entities/" + kind));
	}

	public static RuinMobKind pilgrim() {
		return pilgrim;
	}

	public static RuinMobKind wisp() {
		return wisp;
	}

	public static RuinMobKind acolyte() {
		return acolyte;
	}

	/** True for a mob of one of the wayshrine's kinds. */
	public static boolean isShrineMob(final Entity entity) {
		RuinMobKind kind = RuinMobs.kindOf(entity);
		return kind != null && KIND_IDS.contains(kind.id());
	}

	/** A leather piece dyed white that never wears out, so the sun never burns the mob that wears it. */
	static ItemStack robe(final Item leather) {
		ItemStack stack = new ItemStack(leather);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(WHITE));
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return stack;
	}

	public static void register() {
		pilgrim = RuinMobKind.builder(PILGRIM)
			.base(EntityTypes.ZOMBIE)
			.gear(EquipmentSlot.HEAD, () -> robe(Items.LEATHER_HELMET))
			.gear(EquipmentSlot.CHEST, () -> robe(Items.LEATHER_CHESTPLATE))
			.gear(EquipmentSlot.LEGS, () -> robe(Items.LEATHER_LEGGINGS))
			.ability(new PilgrimAbility())
			.drops(PILGRIM_DROPS)
			.register();
		wisp = RuinMobKind.builder(WISP)
			.base(EntityTypes.VEX)
			.health(2.0)
			.effect(MobEffects.GLOWING, 0)
			.ability(new WispAbility())
			.drops(WISP_DROPS)
			.register();
		acolyte = RuinMobKind.builder(ACOLYTE)
			.base(EntityTypes.WITCH)
			.scale(1.1F)
			.health(2.5)
			.ability(new AcolyteAbility())
			.drops(ACOLYTE_DROPS)
			.boss(true)
			.register();
	}

	/** True once in each period of the mob's age, given that the ability ticks every few ticks. */
	private static boolean due(final Mob mob, final int period) {
		return mob.tickCount % period < RuinMobs.ABILITY_TICKS;
	}

	/** Heals the most hurt ruin ally within range. It never heals a player, itself, or a mob of the world. */
	static final class PilgrimAbility implements MobAbility {
		@Override
		public void tick(final Mob mob, final ServerLevel level) {
			if (!due(mob, HEAL_PERIOD)) {
				return;
			}
			List<Mob> hurt = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(HEAL_RANGE), ally -> ally != mob
				&& RuinMobs.isRuinMob(ally) && ally.isAlive() && ally.getHealth() < ally.getMaxHealth() && ally.distanceTo(mob) <= HEAL_RANGE);
			hurt.stream().min(Comparator.comparingDouble(ally -> ally.getHealth() / ally.getMaxHealth())).ifPresent(ally -> ally.heal(HEAL_AMOUNT));
		}
	}

	/**
	 * A hit from the wisp blinds the player it hit. Two signals carry it: the damage event, and the wisp's own record of its
	 * last melee hit. With the mobs mod a scaled hit on a player raises no damage event, so the record keeps the sting.
	 */
	static final class WispAbility implements MobAbility {
		@Override
		public void tick(final Mob mob, final ServerLevel level) {
			if (mob.getLastHurtMob() instanceof Player player && mob.tickCount - mob.getLastHurtMobTimestamp() <= RuinMobs.ABILITY_TICKS) {
				blind(player);
			}
		}

		@Override
		public void onHurtTarget(final Mob mob, final LivingEntity target) {
			if (target instanceof Player player) {
				blind(player);
			}
		}

		private static void blind(final Player player) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0));
		}
	}

	/** Slows every player within range, once in each period. */
	static final class AcolyteAbility implements MobAbility {
		@Override
		public void tick(final Mob mob, final ServerLevel level) {
			if (!due(mob, SLOW_PERIOD)) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.isAlive() && player.distanceTo(mob) <= SLOW_RANGE) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0));
				}
			}
		}
	}
}
