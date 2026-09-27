package com.stella.spells;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.minecraft.world.phys.Vec3;
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
	public static final List<String> VILLAGE_WITCH_OFFENSIVE = List.of("ice_lance", "frostbind", "venom_dart");
	public static final List<String> VILLAGE_WITCH_SUPPORT = List.of("sanctuary", "bulwark_hymn", "battle_hymn", "tailwind");
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
	public static final double VILLAGE_WITCH_RANGE = 16.0;
	public static final double VILLAGE_WITCH_BLINK_DISTANCE = 12.0;
	public static final int VILLAGE_WITCH_BLINK_COOLDOWN_TICKS = 100;
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

	/** The village witch: no potions, its own sense of friend and foe, and a blink to reach a far foe. */
	private static void installVillageWitch(final Witch witch) {
		witch.addTag(NO_BEHAVIOR_TAG);
		witch.setCanJoinRaid(false);
		witch.setPersistenceRequired();
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
			if (++this.ticks % THINK_INTERVAL_TICKS != 0) {
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

	/** The village witch steps to a far foe's side, at most once every five seconds. */
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
			return target != null && target.isAlive() && this.mob.distanceToSqr(target) > VILLAGE_WITCH_BLINK_DISTANCE * VILLAGE_WITCH_BLINK_DISTANCE
				&& this.mob.distanceToSqr(target) <= VILLAGE_WITCH_RANGE * VILLAGE_WITCH_RANGE * 4;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			this.cooldown = VILLAGE_WITCH_BLINK_COOLDOWN_TICKS;
			LivingEntity target = this.mob.getTarget();
			if (target != null && blinkNear(this.mob, target, 4.0, 7.0)) {
				recorder.cast(this.mob, "blink", List.of(target));
			}
		}
	}

	/** Teleports a mob to a free, floored spot between two distances from an anchor. */
	static boolean blinkNear(final Mob mob, final Entity anchor, final double min, final double max) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = min + mob.getRandom().nextDouble() * (max - min);
			@Nullable Vec3 spot = standing(level, mob, anchor.getX() + Math.cos(angle) * distance, anchor.getBlockY(), anchor.getZ() + Math.sin(angle) * distance);
			if (spot != null) {
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
