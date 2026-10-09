package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/** The four themed mobs of the keep, and the abilities of the warden and the Castellan. */
public final class KeepMobs {
	public static final ResourceKey<LootTable> SENTINEL_DROPS = drops("keep_sentinel");
	public static final ResourceKey<LootTable> KNIGHT_DROPS = drops("spectral_knight");
	public static final ResourceKey<LootTable> WARDEN_DROPS = drops("floor_warden");
	public static final ResourceKey<LootTable> CASTELLAN_DROPS = drops("castellan");

	/** Ticks between two warden charges. */
	public static final int CHARGE_TICKS = 120;
	/** Ticks between two summons of the Castellan. */
	public static final int SUMMON_TICKS = 300;
	public static final int SUMMON_COUNT = 2;
	/** The most summoned sentinels alive at once. */
	public static final int SUMMON_CAP = 8;
	public static final int CLEAVE_TICKS = 40;
	public static final double CLEAVE_RANGE = 3.0;
	public static final int WEAKNESS_TICKS = 60;
	public static final String SUMMON_TAG = "mod-stella-ruins.keep_summon";

	public static RuinMobKind sentinel;
	public static RuinMobKind knight;
	public static RuinMobKind warden;
	public static RuinMobKind castellan;

	private KeepMobs() {
	}

	private static ResourceKey<LootTable> drops(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("entities/" + path));
	}

	public static void register() {
		sentinel = RuinMobKind.builder("keep_sentinel")
			.base(EntityTypes.ZOMBIE)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.IRON_HELMET))
			.gear(EquipmentSlot.CHEST, () -> new ItemStack(Items.IRON_CHESTPLATE))
			.gear(EquipmentSlot.LEGS, () -> new ItemStack(Items.IRON_LEGGINGS))
			.gear(EquipmentSlot.FEET, () -> new ItemStack(Items.IRON_BOOTS))
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.IRON_SWORD))
			.gear(EquipmentSlot.OFFHAND, () -> new ItemStack(Items.SHIELD))
			.drops(SENTINEL_DROPS)
			.register();
		knight = RuinMobKind.builder("spectral_knight")
			.base(EntityTypes.SKELETON)
			.scale(1.1F)
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.CHAINMAIL_HELMET))
			.gear(EquipmentSlot.CHEST, () -> new ItemStack(Items.CHAINMAIL_CHESTPLATE))
			.gear(EquipmentSlot.LEGS, () -> new ItemStack(Items.CHAINMAIL_LEGGINGS))
			.gear(EquipmentSlot.FEET, () -> new ItemStack(Items.CHAINMAIL_BOOTS))
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW))
			.effect(MobEffects.GLOWING, 0)
			.ability(new MobAbility() {
				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0));
				}
			})
			.drops(KNIGHT_DROPS)
			.register();
		warden = RuinMobKind.builder("floor_warden")
			.base(EntityTypes.VINDICATOR)
			.scale(1.3F)
			.health(4.0)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.IRON_AXE))
			.gear(EquipmentSlot.HEAD, () -> new ItemStack(Items.IRON_HELMET))
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					charge(mob);
				}
			})
			.drops(WARDEN_DROPS)
			.boss(true)
			.register();
		castellan = RuinMobKind.builder("castellan")
			.base(EntityTypes.WITHER_SKELETON)
			.scale(2.0F)
			.health(8.0)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.NETHERITE_SWORD))
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					cleave(mob, level);
					summon(mob, level);
				}
			})
			.drops(CASTELLAN_DROPS)
			.boss(true)
			.register();
	}

	/** True in the five-tick window that opens each {@code period} ticks of the mob's life. */
	static boolean due(final Mob mob, final int period) {
		return mob.tickCount > 0 && mob.tickCount % period < 5;
	}

	/** Every {@value #CHARGE_TICKS} ticks, a lunge toward the warden's target. */
	static void charge(final Mob mob) {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !due(mob, CHARGE_TICKS)) {
			return;
		}
		Vec3 flat = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (flat.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 dir = flat.normalize();
		mob.push(dir.x * 1.4, 0.3, dir.z * 1.4);
		mob.needsSync = true;
	}

	/** Players within {@value #CLEAVE_RANGE} blocks take one hit of the Castellan's attack damage. */
	static void cleave(final Mob mob, final ServerLevel level) {
		if (!due(mob, CLEAVE_TICKS)) {
			return;
		}
		float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.distanceTo(mob) <= CLEAVE_RANGE + mob.getBbWidth() * 0.5) {
				player.hurtServer(level, level.damageSources().mobAttack(mob), damage);
			}
		}
	}

	/** Above half health, every {@value #SUMMON_TICKS} ticks, two sentinels join the fight and the roster. */
	static void summon(final Mob mob, final ServerLevel level) {
		if (!due(mob, SUMMON_TICKS) || mob.getHealth() * 2.0F <= mob.getMaxHealth()) {
			return;
		}
		int alive = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(40.0), m -> m.isAlive() && m.entityTags().contains(SUMMON_TAG)).size();
		RuinData data = RuinData.get(level);
		BlockPos core = data.coreOf(mob.getUUID());
		RuinData.Entry entry = core == null ? null : data.entry(core);
		for (int i = 0; i < SUMMON_COUNT && alive + i < SUMMON_CAP; i++) {
			double angle = (Math.PI * 2.0) * (i + level.getRandom().nextDouble()) / SUMMON_COUNT;
			Vec3 at = mob.position().add(Math.cos(angle) * 3.0, 0.0, Math.sin(angle) * 3.0);
			Mob summoned = sentinel.spawn(level, at);
			if (summoned != null) {
				summoned.addTag(SUMMON_TAG);
				RuinMobs.markSummoned(summoned);
				if (entry != null) {
					entry.addMob(summoned.getUUID());
				}
			}
		}
	}
}
