package com.stella.villages.patrol;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Applies a village witch's harmful splash only to hostile mobs. */
public final class VillageWitchSplash {
	public static final String OFFENSIVE_TAG = "mc-stella-villages.witch_offensive_splash";
	/** The spells mod's friend mark for a summon, named here so this mod needs no compile-time link. */
	public static final String SUMMONED_TAG = "mod-stella-spells.summoned";
	private static final double RADIUS = 4.0;
	public interface Recorder {
		void applied(UUID witch, List<UUID> victims);
	}
	private static volatile Recorder recorder = (witch, victims) -> { };

	private VillageWitchSplash() {
	}

	public static void setRecorder(final Recorder next) {
		recorder = next == null ? (witch, victims) -> { } : next;
	}

	public static void apply(final ThrownSplashPotion splash, final ServerLevel level, final ItemStack stack) {
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		Entity source = splash.getOwner() == null ? splash : splash.getOwner();
		List<UUID> reached = new ArrayList<>();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(splash.position(), splash.position()).inflate(RADIUS),
			candidate -> candidate.isAlive() && candidate.distanceToSqr(splash) <= RADIUS * RADIUS
				&& hostile(candidate))) {
			reached.add(victim.getUUID());
			for (MobEffectInstance effect : contents.getAllEffects()) {
				if (effect.getEffect().equals(MobEffects.INSTANT_DAMAGE) && victim.isInvertedHealAndHarm()) {
					victim.damageCooldownTime = 0;
					victim.hurtServer(level, level.damageSources().indirectMagic(splash, source),
						6.0F * (1 << effect.getAmplifier()));
				} else if (effect.getEffect().value().isInstantaneous()) {
					effect.getEffect().value().applyInstantaneousEffect(level, splash, source, victim,
						effect.getAmplifier(), 1.0);
				} else {
					victim.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier()), source);
				}
			}
		}
		if (splash.getOwner() != null) {
			recorder.applied(splash.getOwner().getUUID(), List.copyOf(reached));
		}
	}

	public static boolean hostile(final LivingEntity candidate) {
		return candidate instanceof Mob mob && mob instanceof Enemy && !PatrolState.isMarked(mob)
			&& !mob.entityTags().contains("mod-stella-mobs.friendly_patrol")
			&& !mob.entityTags().contains(SUMMONED_TAG)
			&& !mob.entityTags().contains(com.stella.mobs.entity.BlueSlime.HARMLESS_TAG);
	}
}
