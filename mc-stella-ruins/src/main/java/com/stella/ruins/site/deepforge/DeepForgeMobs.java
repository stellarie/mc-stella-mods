package com.stella.ruins.site.deepforge;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/** The four themed mobs of the Deep Forge. */
public final class DeepForgeMobs {
	public static final int IMP_FIRE_TICKS = 80;
	public static final int SLAM_PERIOD_TICKS = 160;
	public static final double SLAM_RADIUS = 5.0;
	public static final float SLAM_DAMAGE = 6.0F;
	/** How far a golem looks for a player to hunt. */
	public static final double HUNT_RANGE = 24.0;

	public static final ResourceKey<LootTable> KEEPER_DROPS = table("entities/forge_keeper");
	public static final ResourceKey<LootTable> IMP_DROPS = table("entities/cinder_imp");
	public static final ResourceKey<LootTable> GOLEM_DROPS = table("entities/deep_golem");
	public static final ResourceKey<LootTable> GUARDIAN_DROPS = table("entities/forge_guardian");

	public static RuinMobKind keeper;
	public static RuinMobKind imp;
	public static RuinMobKind golem;
	public static RuinMobKind guardian;

	private DeepForgeMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id(path));
	}

	/** An iron golem looks for no player on its own. These two hunt. */
	private static void hunt(final Mob mob, final ServerLevel level) {
		if (mob.getTarget() != null && mob.getTarget().isAlive()) {
			return;
		}
		Player near = level.getNearestPlayer(mob, HUNT_RANGE);
		if (near != null && !near.isSpectator() && !near.isCreative() && near.isAlive()) {
			mob.setTarget(near);
		}
	}

	/** The Forge Guardian's slam: every player within the radius takes damage and is thrown clear. */
	public static void slam(final Mob mob, final ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			Vec3 away = player.position().subtract(mob.position());
			if (away.horizontalDistance() > SLAM_RADIUS) {
				continue;
			}
			double length = Math.max(away.horizontalDistance(), 1.0E-4);
			player.hurtServer(level, level.damageSources().mobAttack(mob), SLAM_DAMAGE);
			player.setDeltaMovement(player.getDeltaMovement().add(away.x / length * 1.4, 0.6, away.z / length * 1.4));
			player.needsSync = true;
		}
		mob.playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.2, mob.getZ(), 24, SLAM_RADIUS / 2.0, 0.1, SLAM_RADIUS / 2.0, 0.05);
	}

	public static void register() {
		keeper = RuinMobKind.builder("forge_keeper")
			.base(EntityTypes.WITHER_SKELETON)
			.scale(1.2F)
			.health(2.5)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.STONE_AXE))
			.drops(KEEPER_DROPS)
			.register();
		imp = RuinMobKind.builder("cinder_imp")
			.base(EntityTypes.BLAZE)
			.scale(0.6F)
			.health(0.8)
			.ability(new MobAbility() {
				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					target.igniteForTicks(IMP_FIRE_TICKS);
				}
			})
			.drops(IMP_DROPS)
			.register();
		golem = RuinMobKind.builder("deep_golem")
			.base(EntityTypes.IRON_GOLEM)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					hunt(mob, level);
				}
			})
			.drops(GOLEM_DROPS)
			.register();
		guardian = RuinMobKind.builder("forge_guardian")
			.base(EntityTypes.IRON_GOLEM)
			.scale(1.8F)
			.health(6.0)
			.boss(true)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					hunt(mob, level);
					if (mob.tickCount >= SLAM_PERIOD_TICKS && mob.tickCount % SLAM_PERIOD_TICKS < com.stella.ruins.world.RuinMobs.ABILITY_TICKS) {
						slam(mob, level);
					}
				}
			})
			.drops(GUARDIAN_DROPS)
			.register();
	}
}
