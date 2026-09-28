package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/** Gives support and hostile splash potions separate two-second throw timers. */
public final class PatrolWitchPotionGoal extends Goal {
	private record Choice(LivingEntity target, ItemStack potion, boolean hostile) { }
	public interface Recorder {
		void thrown(Witch witch, UUID target, String kind, boolean hostile, int tick);
	}
	private static volatile Recorder recorder = (witch, target, kind, hostile, tick) -> { };

	private final Witch witch;
	private final UUID villageId;
	private Choice chosen;
	private int nextSupportTick;
	private int nextOffenseTick;
	private boolean preferHostile;

	public PatrolWitchPotionGoal(final Witch witch, final UUID villageId) {
		this.witch = witch;
		this.villageId = villageId;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static void setRecorder(final Recorder next) {
		recorder = next == null ? (witch, target, kind, hostile, tick) -> { } : next;
	}

	@Override
	public boolean canUse() {
		Choice support = this.witch.tickCount >= this.nextSupportTick ? support() : null;
		Choice offense = this.witch.tickCount >= this.nextOffenseTick ? offense() : null;
		this.chosen = this.preferHostile ? offense != null ? offense : support
			: support != null ? support : offense;
		return this.chosen != null;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		Choice choice = this.chosen;
		this.chosen = null;
		if (choice == null || !(this.witch.level() instanceof ServerLevel level) || !choice.target().isAlive()) {
			return;
		}
		if (choice.hostile()) {
			this.nextOffenseTick = this.witch.tickCount + PatrolService.WITCH_POTION_INTERVAL_TICKS;
		} else {
			this.nextSupportTick = this.witch.tickCount + PatrolService.WITCH_POTION_INTERVAL_TICKS;
		}
		this.preferHostile = !choice.hostile();
		this.witch.getLookControl().setLookAt(choice.target());
		Vec3 movement = choice.target().getDeltaMovement();
		double dx = choice.target().getX() + movement.x - this.witch.getX();
		double dz = choice.target().getZ() + movement.z - this.witch.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double midpoint = choice.target().getY() + choice.target().getBbHeight() * 0.5;
		double dy = midpoint - (this.witch.getEyeY() - 0.1) + 0.015 * distance * distance;
		Projectile.spawnProjectileUsingShoot((world, owner, stack) -> {
			ThrownSplashPotion splash = new ThrownSplashPotion(world, owner, stack);
			if (choice.hostile()) {
				splash.addTag(VillageWitchSplash.OFFENSIVE_TAG);
			}
			return splash;
		}, level, choice.potion(), this.witch, dx, dy, dz, 0.75F, 0.0F);
		recorder.thrown(this.witch, choice.target().getUUID(), offensiveKind(choice.potion()),
			choice.hostile(), this.witch.tickCount);
		if (!this.witch.isSilent()) {
			level.playSound(null, this.witch.getX(), this.witch.getY(), this.witch.getZ(),
				SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
		}
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=witch_potion village={} witch={} target={} kind={} hostile={} tick={}",
			McStellaVillages.MOD_ID, this.villageId, this.witch.getUUID(), choice.target().getUUID(),
			offensiveKind(choice.potion()), choice.hostile(), this.witch.tickCount
		);
	}

	private Choice support() {
		List<LivingEntity> friends = new ArrayList<>(PatrolService.members(this.witch.level(), this.villageId).stream()
			.filter(mob -> mob != this.witch && mob.isAlive())
			.filter(mob -> mob.distanceToSqr(this.witch)
				<= PatrolService.WITCH_SUPPORT_RANGE * PatrolService.WITCH_SUPPORT_RANGE)
			.toList());
		friends.addAll(this.witch.level().getEntitiesOfClass(LivingEntity.class,
			this.witch.getBoundingBox().inflate(PatrolService.WITCH_SUPPORT_RANGE),
			entity -> entity.isAlive() && entity.distanceToSqr(this.witch)
					<= PatrolService.WITCH_SUPPORT_RANGE * PatrolService.WITCH_SUPPORT_RANGE
				&& (entity instanceof AbstractVillager || entity instanceof ServerPlayer player && !player.isSpectator()
					&& this.witch.level() instanceof ServerLevel level
					&& !PatrolService.hasThreat(level, this.villageId, player.getUUID()))));
		friends.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(this.witch)));
		for (LivingEntity friend : friends) {
			if (friend.getHealth() < friend.getMaxHealth()) {
				return new Choice(friend, potion(Potions.HEALING), false);
			}
		}
		for (var buff : List.of(
			java.util.Map.entry(MobEffects.REGENERATION, Potions.REGENERATION),
			java.util.Map.entry(MobEffects.SPEED, Potions.SWIFTNESS),
			java.util.Map.entry(MobEffects.STRENGTH, Potions.STRENGTH))) {
			for (LivingEntity friend : friends) {
				if (!friend.hasEffect(buff.getKey())) {
					return new Choice(friend, potion(buff.getValue()), false);
				}
			}
		}
		return null;
	}

	private Choice offense() {
		List<Mob> foes = this.witch.level().getEntitiesOfClass(Mob.class,
			this.witch.getBoundingBox().inflate(PatrolService.WITCH_POTION_RANGE),
			mob -> VillageWitchSplash.hostile(mob) && mob.distanceToSqr(this.witch)
				<= PatrolService.WITCH_POTION_RANGE * PatrolService.WITCH_POTION_RANGE
				&& this.witch.hasLineOfSight(mob));
		return foes.stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(this.witch)))
			.map(target -> new Choice(target, randomOffensivePotion(this.witch.getRandom()), true))
			.orElse(null);
	}

	public static ItemStack randomOffensivePotion(final RandomSource random) {
		return switch (random.nextInt(3)) {
			case 0 -> potion(Potions.HARMING);
			case 1 -> potion(Potions.POISON);
			default -> {
				ItemStack wither = new ItemStack(Items.SPLASH_POTION);
				wither.set(DataComponents.POTION_CONTENTS,
					PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 100, 0)));
				yield wither;
			}
		};
	}

	public static String offensiveKind(final ItemStack stack) {
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		if (contents.potion().filter(Potions.HARMING::equals).isPresent()) {
			return "harming";
		}
		if (contents.potion().filter(Potions.POISON::equals).isPresent()) {
			return "poison";
		}
		if (contents.customEffects().stream().anyMatch(effect -> effect.getEffect().equals(MobEffects.WITHER))) {
			return "wither";
		}
		return contents.potion().map(holder -> holder.unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("custom")).orElse("custom");
	}

	private static ItemStack potion(final net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
		return PotionContents.createItemStack(Items.SPLASH_POTION, potion);
	}
}
