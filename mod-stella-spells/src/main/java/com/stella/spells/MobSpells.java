package com.stella.spells;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.jspecify.annotations.Nullable;

/**
 * Mobs that cast. A witch carries two spells chosen when it spawns, one to strike with and one to aid its
 * side. A stray casts Ice Lance, and an elite or boss stray Frozen Meteor too, with no combo needed. An
 * evoker or an illusioner casts Sanctuary over the illagers around it when one of them is hurt. The village
 * witch strikes the village's foes and heals and blesses its friends. Every spell keeps its own cooldown.
 */
public final class MobSpells {
	public static final List<String> OFFENSIVE = List.of(
		"spark_shot", "frostbind", "briar_snare", "static_lash", "tidal_push", "wither_seal", "ice_lance",
		"soul_siphon", "venom_dart", "blinding_flash", "ember_brand"
	);
	public static final List<String> SUPPORT = List.of("sanctuary", "bulwark_hymn", "tailwind", "battle_hymn", "mending_word", "stoneguard");
	public static final List<String> PACK_SUPPORT = List.of("sanctuary", "bulwark_hymn", "tailwind", "battle_hymn",
		"mending_word", "stoneguard", "phoenix_mantle", "life_bloom", "aegis_of_the_deep", "berserk",
		"shadow_veil", "purify", "quicksilver");
	public static final List<String> PACK_AREA_OFFENSIVE = List.of("void_well", "dragonfall", "wardens_roar",
		"sapping_hex", "frost_nova", "earthspike", "plague_cloud", "flame_ring", "gravity");
	public static final List<String> VILLAGE_WITCH_OFFENSIVE = List.of("wardens_roar", "spark_shot");
	public static final List<String> VILLAGE_WITCH_SUPPORT = List.of("life_bloom");
	public static final int VILLAGE_WITCH_ROAR_COOLDOWN_TICKS = 100;
	public static final int VILLAGE_WITCH_SPARK_COOLDOWN_TICKS = 4;
	public static final int VILLAGE_WITCH_BLOOM_COOLDOWN_TICKS = 100;
	public static final String FROZEN_METEOR = "frozen_meteor";
	public static final int FROZEN_METEOR_COOLDOWN_TICKS = 500;
	public static final String CAST_TAG_PREFIX = "mod-stella-spells.cast.";
	public static final String VILLAGE_WITCH_TAG = "mc-stella-villages.village_witch";
	/** Tells the mobs mod to leave this mob's goals alone. */
	public static final String NO_BEHAVIOR_TAG = "mod-stella-mobs.no_behavior";
	public static final double CAST_RANGE = 16.0;
	public static final int THINK_INTERVAL_TICKS = 10;
	/** A support spell waits for a hurt friend: one below this share of its health. */
	public static final float HURT_SHARE = 0.7F;
	public static final float SANCTUARY_RADIUS = 6.0F;
	public static final int ILLAGER_SANCTUARY_COOLDOWN_TICKS = 400;
	public static final double VILLAGE_WITCH_RANGE = 48.0;
	public static final int VILLAGE_WITCH_BLINK_COOLDOWN_TICKS = 40;
	public static final double VILLAGE_WITCH_RETREAT_DISTANCE = 6.0;
	public static final double VILLAGE_WITCH_SAFE_MIN_DISTANCE = 8.0;
	public static final double VILLAGE_WITCH_SAFE_MAX_DISTANCE = 12.0;
	public static final double VILLAGE_WITCH_CHASE_DISTANCE = 14.0;
	private static final Identifier ELITE_SCALE = Identifier.fromNamespaceAndPath("mod-stella-mobs", "elite_scale");
	private static final Identifier BOSS_SCALE = Identifier.fromNamespaceAndPath("mod-stella-mobs", "boss_scale");

	/** Test seam: each mob cast, with who it reached. */
	public interface Recorder {
		void cast(Mob caster, String spell, List<LivingEntity> reached);
	}

	private static volatile Recorder recorder = (caster, spell, reached) -> {
	};

	private MobSpells() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? (caster, spell, reached) -> {
		} : newRecorder;
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				install(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> MobImbue.forget(entity.getUUID()));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				MobImbue.forget(entity.getUUID());
			}
		});
	}

	/** Gives a mob its casting, once; a witch rolls its two spells the first time and keeps them in its tags. */
	public static void install(final Mob mob) {
		if (has(mob, Caster.class) || has(mob, IllagerMend.class)) {
			return;
		}
		if (mob instanceof Witch witch && witch.entityTags().contains(VILLAGE_WITCH_TAG)) {
			installVillageWitch(witch);
			return;
		}
		if (mob instanceof Witch witch) {
			if (spellsOf(witch).isEmpty()) {
				witch.addTag(CAST_TAG_PREFIX + OFFENSIVE.get(witch.getRandom().nextInt(OFFENSIVE.size())));
				witch.addTag(CAST_TAG_PREFIX + SUPPORT.get(witch.getRandom().nextInt(SUPPORT.size())));
			}
			witch.getGoalSelector().addGoal(1, new Caster(witch, false));
		} else if (mob instanceof Stray stray) {
			stray.getGoalSelector().addGoal(1, new Caster(stray, false));
		} else if (mob instanceof Evoker || mob instanceof Illusioner) {
			mob.getGoalSelector().addGoal(1, new IllagerMend((AbstractIllager) mob));
		}
	}

	private static boolean has(final Mob mob, final Class<? extends Goal> type) {
		return mob.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> type.isInstance(wrapped.getGoal()));
	}

	/** The spells a mob carries in its tags, in tag order. */
	public static List<String> spellsOf(final Mob mob) {
		List<String> spells = new ArrayList<>();
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(CAST_TAG_PREFIX)) {
				spells.add(tag.substring(CAST_TAG_PREFIX.length()));
			}
		}
		return spells;
	}

	/** What this mob strikes with: its tagged spells, the stray's lance and meteor, or the village witch's set. */
	public static List<String> offensiveOf(final Mob mob) {
		if (mob.entityTags().contains(VILLAGE_WITCH_TAG)) {
			return VILLAGE_WITCH_OFFENSIVE;
		}
		if (mob instanceof Stray) {
			return isElite(mob) ? List.of("ice_lance", FROZEN_METEOR) : List.of("ice_lance");
		}
		return spellsOf(mob).stream().filter(OFFENSIVE::contains).toList();
	}

	public static List<String> supportOf(final Mob mob) {
		if (mob.entityTags().contains(VILLAGE_WITCH_TAG)) {
			return VILLAGE_WITCH_SUPPORT;
		}
		return spellsOf(mob).stream().filter(SUPPORT::contains).toList();
	}

	/** The mobs mod marks an elite or a boss with a size modifier; read without depending on that mod. */
	public static boolean isElite(final Mob mob) {
		AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
		return scale != null && (scale.getModifier(ELITE_SCALE) != null || scale.getModifier(BOSS_SCALE) != null);
	}

	public static int cooldownOf(final String spell) {
		if (FROZEN_METEOR.equals(spell)) {
			return FROZEN_METEOR_COOLDOWN_TICKS;
		}
		SpellDefinition definition = SpellbookCatalog.find(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		return definition == null ? 200 : definition.baseCooldownTicks();
	}

	/** One spell at a foe, at full strength. */
	public static boolean castAt(final Mob caster, final String spell, final LivingEntity target) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		List<LivingEntity> reached;
		if (FROZEN_METEOR.equals(spell)) {
			reached = frozenMeteor(level, caster, target.position());
		} else if ("chainstorm".equals(spell) && SpellStorms.startForMob(caster, target.position())) {
			reached = List.of(target);
		} else if ("time_lock".equals(spell)) {
			reached = level.getEntitiesOfClass(LivingEntity.class, new AABB(target.position(), target.position())
				.inflate(SpellCasting.TIME_LOCK_RADIUS), other -> other.isAlive() && isFoeOf(caster, other)
					&& other.distanceToSqr(target) <= SpellCasting.TIME_LOCK_RADIUS * SpellCasting.TIME_LOCK_RADIUS);
			for (LivingEntity other : reached) {
				other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), caster);
			}
		} else if (PACK_AREA_OFFENSIVE.contains(spell)) {
			reached = castArea(caster, spell, target);
			if (reached.isEmpty()) {
				return false;
			}
		} else if (MobImbue.apply(level, caster, spell, target, 1.0, 1.0)) {
			reached = List.of(target);
		} else {
			return false;
		}
		level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.4, 0.6, 0.4, 0.4);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_CAST_SPELL, caster.getSoundSource(), 0.7F, 1.2F);
		recorder.cast(caster, spell, reached);
		return true;
	}

	private static List<LivingEntity> castArea(final Mob caster, final String spell, final LivingEntity target) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return List.of();
		}
		Vec3 point = switch (spell) {
			case "wardens_roar", "sapping_hex", "frost_nova", "flame_ring", "gravity" -> caster.position();
			default -> target.position();
		};
		double radius = switch (spell) {
			case "void_well" -> SpellCasting.VOID_WELL_RADIUS;
			case "dragonfall" -> SpellCasting.DRAGONFALL_RADIUS;
			case "wardens_roar" -> SpellCasting.WARDENS_ROAR_RADIUS;
			case "sapping_hex" -> MoreSpells.HEX_RADIUS;
			case "frost_nova" -> MoreSpells.FROST_NOVA_RADIUS;
			case "earthspike" -> MoreSpells.EARTHSPIKE_RADIUS;
			case "plague_cloud" -> MoreSpells.PLAGUE_RADIUS;
			case "flame_ring" -> MoreSpells.FLAME_RING_RADIUS;
			case "gravity" -> MoreSpells.GRAVITY_RADIUS;
			default -> 0.0;
		};
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
			other -> other != caster && other.isAlive() && isFoeOf(caster, other)
				&& other.distanceToSqr(point) <= radius * radius);
		for (LivingEntity foe : foes) {
			switch (spell) {
				case "void_well" -> {
					Vec3 toward = point.subtract(foe.position());
					Vec3 flat = new Vec3(toward.x, 0.0, toward.z);
					if (flat.lengthSqr() > 1.0e-6) {
						flat = flat.normalize();
						foe.setDeltaMovement(flat.x * 0.8, 0.1, flat.z * 0.8);
					}
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), caster);
				}
				case "dragonfall" -> hurt(level, caster, foe, 10.0F);
				case "wardens_roar" -> {
					hurt(level, caster, foe, 6.0F);
					Vec3 away = foe.position().subtract(caster.position()).normalize();
					foe.push(away.x, 0.2, away.z);
				}
				case "sapping_hex" -> foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
					Math.min(MoreSpells.HEX_MAX_TICKS, MoreSpells.HEX_TICKS_PER_FOE * foes.size()), MoreSpells.HEX_AMPLIFIER), caster);
				case "frost_nova" -> {
					hurt(level, caster, foe, MoreSpells.FROST_NOVA_DAMAGE);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
						MoreSpells.FROST_NOVA_SLOW_TICKS, MoreSpells.FROST_NOVA_SLOW_AMPLIFIER), caster);
				}
				case "earthspike" -> {
					hurt(level, caster, foe, MoreSpells.EARTHSPIKE_DAMAGE);
					Vec3 motion = foe.getDeltaMovement();
					foe.setDeltaMovement(motion.x, MoreSpells.EARTHSPIKE_LIFT, motion.z);
				}
				case "plague_cloud" -> foe.addEffect(new MobEffectInstance(MobEffects.POISON,
					MoreSpells.PLAGUE_POISON_TICKS, 0), caster);
				case "flame_ring" -> {
					hurt(level, caster, foe, MoreSpells.FLAME_RING_DAMAGE);
					foe.igniteForTicks(MoreSpells.FLAME_RING_BURN_TICKS);
				}
				case "gravity" -> {
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
						MoreSpells.GRAVITY_TICKS, MoreSpells.GRAVITY_SLOW_AMPLIFIER), caster);
					if (foe instanceof Mob mob) {
						SpellTimers.ground(mob, MoreSpells.GRAVITY_TICKS);
					}
				}
				default -> { }
			}
		}
		if ("sapping_hex".equals(spell) && !foes.isEmpty()) {
			caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
				Math.min(MoreSpells.HEX_MAX_TICKS, MoreSpells.HEX_TICKS_PER_FOE * foes.size()),
				MoreSpells.HEX_STRENGTH_AMPLIFIER), caster);
		}
		return foes;
	}

	private static void hurt(final ServerLevel level, final Mob caster, final LivingEntity foe, final float damage) {
		foe.damageCooldownTime = 0;
		foe.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
	}

	/** Frozen Meteor from a mob: a crash of ice that hurts and freezes the caster's foes around the point. */
	static List<LivingEntity> frozenMeteor(final ServerLevel level, final Mob caster, final Vec3 point) {
		double radius = SpellCombos.FROZEN_METEOR_RADIUS;
		List<LivingEntity> reached = level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
			other -> other != caster && other.isAlive() && other.position().distanceToSqr(point) <= radius * radius && isFoeOf(caster, other));
		for (LivingEntity victim : reached) {
			victim.damageCooldownTime = 0;
			victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SpellCombos.FROZEN_METEOR_DAMAGE);
			MoreSpells.freeze(victim, 140);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, point.x, point.y + 0.5, point.z, 80, radius * 0.5, 0.5, radius * 0.5, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y + 0.5, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		return reached;
	}

	/** A caster's foe: for the village witch, the village's foes; for any other caster, players and its target. */
	public static boolean isFoeOf(final Mob caster, final LivingEntity other) {
		if (caster.entityTags().contains(VILLAGE_WITCH_TAG)) {
			return Friends.isFoe(other) || other == caster.getTarget();
		}
		if (caster.entityTags().contains(SummonSkeleton.SUMMON_TAG)) {
			UUID owner = SummonSkeleton.ownerOf(caster).orElse(null);
			return !(other instanceof Player player && owner != null && owner.equals(player.getUUID()))
				&& (other == caster.getTarget() || Friends.isFoe(other));
		}
		return other == caster.getTarget() || other instanceof Player player && !player.isCreative() && !player.isSpectator();
	}

	/** A caster's friend: for the village witch, the village; for any other caster, the hostiles around it. */
	public static boolean isFriendOf(final Mob caster, final LivingEntity other) {
		if (other == caster.getTarget()) {
			return false;
		}
		if (caster.entityTags().contains(VILLAGE_WITCH_TAG)) {
			return MoreSpells.isAlly(other);
		}
		if (caster.entityTags().contains(SummonSkeleton.SUMMON_TAG)) {
			UUID owner = SummonSkeleton.ownerOf(caster).orElse(null);
			return other == caster || owner != null && other instanceof ServerPlayer player
				&& owner.equals(player.getUUID());
		}
		return other == caster || Friends.isFoe(other);
	}

	/** One support spell over a caster's side; true when it reached anyone. */
	public static boolean support(final Mob caster, final String spell) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		double radius = "sanctuary".equals(spell) ? SANCTUARY_RADIUS : MoreSpells.SUPPORT_RADIUS;
		List<LivingEntity> side = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
			other -> other.isAlive() && other.distanceToSqr(caster) <= radius * radius && isFriendOf(caster, other));
		List<LivingEntity> reached = new ArrayList<>();
		for (LivingEntity friend : side) {
			boolean self = friend == caster;
			boolean done = switch (spell) {
				case "sanctuary" -> heal(friend, self ? MoreSpells.SANCTUARY_SELF_HEAL : MoreSpells.SANCTUARY_ALLY_HEAL);
				case "mending_word" -> heal(friend, 6.0F);
				case "bulwark_hymn" -> friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, MoreSpells.BULWARK_TICKS, MoreSpells.BULWARK_AMPLIFIER), caster);
				case "tailwind" -> friend.addEffect(new MobEffectInstance(MobEffects.SPEED, MoreSpells.TAILWIND_TICKS, MoreSpells.TAILWIND_AMPLIFIER), caster);
				case "battle_hymn" -> friend.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MoreSpells.BATTLE_HYMN_TICKS, MoreSpells.BATTLE_HYMN_AMPLIFIER), caster);
				case "stoneguard" -> self && friend.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0), caster);
				case "phoenix_mantle" -> friend.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0), caster)
					| heal(friend, 4.0F);
				case "life_bloom" -> heal(friend, 8.0F)
					| friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0), caster);
				case "aegis_of_the_deep" -> friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 1), caster)
					| friend.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0), caster);
				case "berserk" -> friend.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MoreSpells.BERSERK_TICKS,
					MoreSpells.BERSERK_AMPLIFIER), caster);
				case "shadow_veil" -> friend.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MoreSpells.VEIL_TICKS, 0), caster);
				case "quicksilver" -> friend.addEffect(new MobEffectInstance(MobEffects.SPEED,
					MoreSpells.QUICKSILVER_TICKS, MoreSpells.QUICKSILVER_AMPLIFIER), caster);
				case "purify" -> {
					var harmful = friend.getActiveEffects().stream().map(MobEffectInstance::getEffect)
						.filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL).toList();
					harmful.forEach(friend::removeEffect);
					yield !harmful.isEmpty();
				}
				default -> false;
			};
			if (done) {
				reached.add(friend);
			}
		}
		if (reached.isEmpty()) {
			return false;
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, caster.getX(), caster.getY(0.5), caster.getZ(), 20, radius * 0.4, 0.6, radius * 0.4, 0.05);
		recorder.cast(caster, spell, reached);
		return true;
	}

	private static boolean heal(final LivingEntity body, final float amount) {
		if (body.getHealth() >= body.getMaxHealth()) {
			return false;
		}
		body.heal(amount);
		return true;
	}

	/** True when someone on the caster's side, the caster included, is hurt below the threshold. */
	static boolean sideIsHurt(final Mob caster, final double radius) {
		return !caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
			other -> other.isAlive() && other.distanceToSqr(caster) <= radius * radius && isFriendOf(caster, other)
				&& other.getHealth() < other.getMaxHealth() * HURT_SHARE).isEmpty();
	}

	/** The village witch keeps its own target and spells; the villages mod handles its splash potions. */
	private static void installVillageWitch(final Witch witch) {
		witch.addTag(NO_BEHAVIOR_TAG);
		witch.setCanJoinRaid(false);
		witch.setPersistenceRequired();
		witch.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(VILLAGE_WITCH_RANGE);
		witch.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedAttackGoal);
		var targets = ((com.stella.spells.mixin.MobGoalsAccessor) witch).stellaSpells$targetSelector();
		targets.removeAllGoals(goal -> true);
		targets.addGoal(1, new HurtByTargetGoal(witch));
		targets.addGoal(2, new NearestAttackableTargetGoal<>(witch, Mob.class, 10, true, false,
			(target, level) -> Friends.isFoe(target) && threatens(target)));
		targets.addGoal(3, new NearestAttackableTargetGoal<>(witch, Mob.class, 10, true, false, (target, level) -> Friends.isFoe(target)));
		witch.getGoalSelector().addGoal(1, new Caster(witch, true));
		witch.getGoalSelector().addGoal(2, new Blink(witch));
	}

	// A hostile set on a player or a villager, whom the village witch goes for first.
	private static boolean threatens(final LivingEntity mob) {
		return mob instanceof Mob hunter && (hunter.getTarget() instanceof Player || hunter.getTarget() instanceof AbstractVillager);
	}

	/** A flagless goal that casts the mob's spells on their own cooldowns, counted in the goal's own ticks. */
	public static final class Caster extends Goal {
		private final Mob mob;
		private final boolean village;
		private final Map<String, Integer> readyAt = new HashMap<>();
		private int ticks;

		public Caster(final Mob mob, final boolean village) {
			this.mob = mob;
			this.village = village;
		}

		@Override
		public boolean canUse() {
			return this.mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			if (this.village) {
				this.tickVillageWitch();
				return;
			}
			if (this.ticks % THINK_INTERVAL_TICKS != 0) {
				return;
			}
			LivingEntity target = this.mob.getTarget();
			boolean fighting = target != null && target.isAlive();
			if (fighting && this.mob.distanceToSqr(target) <= CAST_RANGE * CAST_RANGE && this.mob.getSensing().hasLineOfSight(target)) {
				for (String spell : offensiveOf(this.mob)) {
					if (this.ready(spell) && castAt(this.mob, spell, target)) {
						this.readyAt.put(spell, this.ticks + cooldownOf(spell));
						break;
					}
				}
			}
			for (String spell : supportOf(this.mob)) {
				boolean healing = "sanctuary".equals(spell) || "mending_word".equals(spell);
				boolean wanted = healing ? sideIsHurt(this.mob, MoreSpells.SUPPORT_RADIUS) : fighting || this.village && sideIsHurt(this.mob, MoreSpells.SUPPORT_RADIUS);
				if (wanted && this.ready(spell) && support(this.mob, spell)) {
					this.readyAt.put(spell, this.ticks + cooldownOf(spell));
					break;
				}
			}
		}

		private void tickVillageWitch() {
			if (this.ticks % VILLAGE_WITCH_SPARK_COOLDOWN_TICKS != 0) {
				return;
			}
			if (this.mob.getHealth() <= this.mob.getMaxHealth() * 0.5F && this.ready("life_bloom")
				&& support(this.mob, "life_bloom")) {
				this.readyAt.put("life_bloom", this.ticks + VILLAGE_WITCH_BLOOM_COOLDOWN_TICKS);
				return;
			}
			LivingEntity target = this.mob.getTarget();
			if (target == null || !target.isAlive() || !isFoeOf(this.mob, target)
				|| this.mob.distanceToSqr(target) > CAST_RANGE * CAST_RANGE
				|| !this.mob.getSensing().hasLineOfSight(target)) {
				return;
			}
			if (this.mob.distanceToSqr(target) <= SpellCasting.WARDENS_ROAR_RADIUS * SpellCasting.WARDENS_ROAR_RADIUS
				&& this.ready("wardens_roar") && castAt(this.mob, "wardens_roar", target)) {
				this.readyAt.put("wardens_roar", this.ticks + VILLAGE_WITCH_ROAR_COOLDOWN_TICKS);
				return;
			}
			if (this.ready("spark_shot") && castAt(this.mob, "spark_shot", target)) {
				this.readyAt.put("spark_shot", this.ticks + VILLAGE_WITCH_SPARK_COOLDOWN_TICKS);
			}
		}

		private boolean ready(final String spell) {
			return this.ticks >= this.readyAt.getOrDefault(spell, 0);
		}
	}

	/** An evoker or an illusioner casts Sanctuary over its hurt illagers. Flagless. */
	public static final class IllagerMend extends Goal {
		private final AbstractIllager caster;
		private int ticks;
		private int readyAt;

		public IllagerMend(final AbstractIllager caster) {
			this.caster = caster;
		}

		@Override
		public boolean canUse() {
			return this.caster.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (++this.ticks % THINK_INTERVAL_TICKS != 0 || this.ticks < this.readyAt || !(this.caster.level() instanceof ServerLevel level)) {
				return;
			}
			List<AbstractIllager> hurt = level.getEntitiesOfClass(AbstractIllager.class, this.caster.getBoundingBox().inflate(SANCTUARY_RADIUS),
				other -> other.isAlive() && !Friends.isFriend(other) && other.distanceToSqr(this.caster) <= SANCTUARY_RADIUS * SANCTUARY_RADIUS
					&& other.getHealth() < other.getMaxHealth() * HURT_SHARE);
			if (hurt.isEmpty()) {
				return;
			}
			List<LivingEntity> reached = new ArrayList<>();
			for (AbstractIllager other : level.getEntitiesOfClass(AbstractIllager.class, this.caster.getBoundingBox().inflate(SANCTUARY_RADIUS),
				other -> other.isAlive() && !Friends.isFriend(other) && other.distanceToSqr(this.caster) <= SANCTUARY_RADIUS * SANCTUARY_RADIUS)) {
				if (heal(other, other == this.caster ? MoreSpells.SANCTUARY_SELF_HEAL : MoreSpells.SANCTUARY_ALLY_HEAL)) {
					reached.add(other);
				}
			}
			this.readyAt = this.ticks + ILLAGER_SANCTUARY_COOLDOWN_TICKS;
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.caster.getX(), this.caster.getY(0.5), this.caster.getZ(), 30, 2.0, 0.6, 2.0, 0.1);
			recorder.cast(this.caster, "sanctuary", reached);
		}
	}

	/** The village witch blinks into casting range or out of melee reach. */
	public static final class Blink extends Goal {
		private final Mob mob;
		private int cooldown;

		public Blink(final Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			if (this.cooldown > 0) {
				this.cooldown--;
				return false;
			}
			LivingEntity target = this.mob.getTarget();
			if (target == null || !target.isAlive() || !isFoeOf(this.mob, target)) {
				return false;
			}
			double distance = this.mob.distanceToSqr(target);
			return distance < VILLAGE_WITCH_RETREAT_DISTANCE * VILLAGE_WITCH_RETREAT_DISTANCE
				|| distance > VILLAGE_WITCH_CHASE_DISTANCE * VILLAGE_WITCH_CHASE_DISTANCE
					&& distance <= VILLAGE_WITCH_RANGE * VILLAGE_WITCH_RANGE;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			this.cooldown = VILLAGE_WITCH_BLINK_COOLDOWN_TICKS;
			LivingEntity target = this.mob.getTarget();
			if (target != null && blinkNear(this.mob, target,
				VILLAGE_WITCH_SAFE_MIN_DISTANCE, VILLAGE_WITCH_SAFE_MAX_DISTANCE)) {
				recorder.cast(this.mob, "blink", List.of(target));
			}
		}
	}

	/** Teleports a mob to a free, floored spot between two distances from an anchor. */
	static boolean blinkNear(final Mob mob, final Entity anchor, final double min, final double max) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = min + mob.getRandom().nextDouble() * (max - min);
			@Nullable Vec3 spot = standing(level, mob, anchor.getX() + Math.cos(angle) * distance, anchor.getBlockY(), anchor.getZ() + Math.sin(angle) * distance);
			if (spot != null && clearSight(level, mob, anchor, spot)) {
				level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY(0.5), mob.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
				mob.teleportTo(spot.x, spot.y, spot.z);
				mob.getNavigation().stop();
				level.sendParticles(ParticleTypes.WITCH, spot.x, spot.y + 1.0, spot.z, 20, 0.3, 0.6, 0.3, 0.05);
				level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 0.6F, 1.4F);
				return true;
			}
		}
		return false;
	}

	private static boolean clearSight(final ServerLevel level, final Mob mob, final Entity target, final Vec3 feet) {
		Vec3 eye = feet.add(0.0, mob.getEyeY() - mob.getY(), 0.0);
		return level.clip(new ClipContext(eye, target.getEyePosition(),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	private static @Nullable Vec3 standing(final ServerLevel level, final Mob mob, final double x, final int y, final double z) {
		for (int dy = 3; dy >= -5; dy--) {
			BlockPos feet = BlockPos.containing(x, y + dy, z);
			if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(feet);
			AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(at);
			if (level.noCollision(mob, box) && !level.containsAnyLiquid(box)) {
				return at;
			}
		}
		return null;
	}
}
