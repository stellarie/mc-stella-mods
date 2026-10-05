package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * The three themed mobs of the camp, the loot tables the camp owns, and the Chief's hiding and reveal.
 * <p>
 * The Chief spawns without gear and invisible, with no AI, so he stands where he hid. A player within
 * {@link #REVEAL_RADIUS} blocks, or a hit on him, reveals him. The reveal gives him his hood and axe, calls every
 * mob of his own run near him onto the player, sends up a flare from each lookout, and starts the reinforcement timer.
 */
public final class CampMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/ambush_camp");
	public static final ResourceKey<LootTable> BANDIT_DROPS = table("entities/bandit");
	public static final ResourceKey<LootTable> LOOKOUT_DROPS = table("entities/lookout");
	public static final ResourceKey<LootTable> CHIEF_DROPS = table("entities/bandit_chief");

	public static final String TAG_HIDDEN = "mod-stella-ruins.hidden";
	public static final String TAG_REVEALED = "mod-stella-ruins.revealed";
	/** A player this close, in blocks, finds the Chief. */
	public static final double REVEAL_RADIUS = 4.0;
	/** The mobs within this many blocks of the Chief hear his call. */
	public static final double CALL_RADIUS = 48.0;
	public static final float CHIEF_SCALE = 1.2F;
	public static final double CHIEF_HEALTH = 5.0;
	/** The brown of the bandit's leather hood. */
	public static final int HOOD_COLOR = 0x6B4423;
	public static final int CHIEF_HOOD_COLOR = 0x4A1F14;

	public static RuinMobKind BANDIT;
	public static RuinMobKind LOOKOUT;
	public static RuinMobKind CHIEF;
	private static boolean hooked;
	private static int flares;

	private CampMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		BANDIT = RuinMobKind.builder("bandit")
			.base(EntityTypes.VINDICATOR)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.IRON_AXE))
			.gear(EquipmentSlot.HEAD, () -> hood(HOOD_COLOR))
			.drops(BANDIT_DROPS)
			.register();
		LOOKOUT = RuinMobKind.builder("lookout")
			.base(EntityTypes.SKELETON)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW))
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.LEATHER_HELMET))
			.drops(LOOKOUT_DROPS)
			.register();
		CHIEF = RuinMobKind.builder("bandit_chief")
			.base(EntityTypes.VINDICATOR)
			.scale(CHIEF_SCALE)
			.health(CHIEF_HEALTH)
			.boss(true)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					if (isHidden(mob)) {
						for (ServerPlayer player : level.players()) {
							if (!player.isSpectator() && player.isAlive() && player.distanceTo(mob) <= REVEAL_RADIUS) {
								reveal(mob, player);
								return;
							}
						}
					}
				}
			})
			.drops(CHIEF_DROPS)
			.register();
		if (!hooked) {
			hooked = true;
			ServerLivingEntityEvents.AFTER_DAMAGE.register(CampMobs::onDamage);
		}
	}

	private static ItemStack hood(final int color) {
		ItemStack stack = new ItemStack(Items.LEATHER_HELMET);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
		return stack;
	}

	/** How many flares lookouts fired since the game began. Tests read it, because a particle and a sound cannot be observed. */
	public static int flaresFired() {
		return flares;
	}

	public static boolean isHidden(final Entity entity) {
		return entity.entityTags().contains(TAG_HIDDEN);
	}

	public static boolean isChief(final Entity entity) {
		return entity.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "bandit_chief");
	}

	/** Hides a freshly spawned Chief: invisible, gearless, and standing still. */
	public static void hide(final Mob chief) {
		chief.addTag(TAG_HIDDEN);
		chief.setNoAi(true);
		chief.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD}) {
			chief.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	private static void onDamage(final LivingEntity target, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked) {
		if (target instanceof Mob chief && isHidden(chief) && chief.level() instanceof ServerLevel level) {
			LivingEntity by = source.getEntity() instanceof LivingEntity attacker ? attacker : nearestPlayer(level, chief);
			reveal(chief, by);
		}
	}

	private static LivingEntity nearestPlayer(final ServerLevel level, final Mob chief) {
		return level.getNearestPlayer(chief, CALL_RADIUS);
	}

	/**
	 * Turns the hidden Chief visible, once. Returns false when he was not hidden. Everything he calls is told to hunt
	 * {@code by}.
	 */
	public static boolean reveal(final Mob chief, final LivingEntity by) {
		if (!isHidden(chief) || !(chief.level() instanceof ServerLevel level)) {
			return false;
		}
		chief.removeTag(TAG_HIDDEN);
		chief.addTag(TAG_REVEALED);
		chief.removeEffect(MobEffects.INVISIBILITY);
		chief.setNoAi(false);
		chief.setItemSlot(EquipmentSlot.HEAD, hood(CHIEF_HOOD_COLOR));
		chief.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		chief.setDropChance(EquipmentSlot.HEAD, 0.0F);
		chief.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		chief.setCustomNameVisible(true);
		level.playSound(null, chief.blockPosition(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.7F);
		AABB area = chief.getBoundingBox().inflate(CALL_RADIUS);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, RuinMobs::isRuinMob)) {
			if (mob == chief || !RuinMobs.sameCore(mob, chief)) {
				continue;
			}
			if (by != null) {
				mob.setTarget(by);
			}
			if (RuinMobs.kindOf(mob) == LOOKOUT) {
				flare(level, mob);
			}
		}
		if (by != null) {
			chief.setTarget(by);
		}
		ChiefObjective.noteReveal(level, chief);
		return true;
	}

	/** A lookout's flare: a burst of firework sparks over his head, with the sound of a rocket. */
	private static void flare(final ServerLevel level, final Mob lookout) {
		flares++;
		level.sendParticles(ParticleTypes.FIREWORK, lookout.getX(), lookout.getY() + 3.0, lookout.getZ(), 40, 0.4, 0.8, 0.4, 0.15);
		level.playSound(null, lookout.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3.0F, 1.0F);
		level.playSound(null, lookout.blockPosition().above(3), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 3.0F, 1.0F);
	}
}
