package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/** The two themed mobs of the grotto, and the loot tables the grotto owns. */
public final class GrottoMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/crystal_grotto");
	public static final ResourceKey<LootTable> SENTINEL_DROPS = table("entities/shard_sentinel");
	public static final ResourceKey<LootTable> SLIME_DROPS = table("entities/prism_slime");

	/** The Slowness a sentinel's hit gives, in ticks. */
	public static final int SLOW_TICKS = 60;
	/** Ticks between two teleports of a Prism Slime. */
	public static final int TELEPORT_TICKS = 100;
	/** The farthest a Prism Slime jumps. */
	public static final int TELEPORT_RANGE = 6;
	public static final int SLIME_SIZE = 2;
	/** The amethyst tint of the sentinel's leather armor. */
	public static final int SENTINEL_COLOR = 0x9B6FD0;
	private static final String SIZED_TAG = "mod-stella-ruins.sized";

	public static RuinMobKind SHARD_SENTINEL;
	public static RuinMobKind PRISM_SLIME;

	private GrottoMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		SHARD_SENTINEL = RuinMobKind.builder("shard_sentinel")
			.base(EntityTypes.STRAY)
			.scale(1.1F)
			.gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW))
			.gear(EquipmentSlot.HEAD, () -> tinted(Items.LEATHER_HELMET))
			.gear(EquipmentSlot.CHEST, () -> tinted(Items.LEATHER_CHESTPLATE))
			.gear(EquipmentSlot.LEGS, () -> tinted(Items.LEATHER_LEGGINGS))
			.gear(EquipmentSlot.FEET, () -> tinted(Items.LEATHER_BOOTS))
			.ability(new MobAbility() {
				@Override
				public void onHurtTarget(final Mob mob, final LivingEntity target) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0));
				}
			})
			.drops(SENTINEL_DROPS)
			.register();
		PRISM_SLIME = RuinMobKind.builder("prism_slime")
			.base(EntityTypes.SLIME)
			.effect(MobEffects.GLOWING, 0)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					if (mob instanceof Slime slime && !slime.entityTags().contains(SIZED_TAG)) {
						slime.addTag(SIZED_TAG);
						slime.setSize(SLIME_SIZE, true);
					}
					if (mob.tickCount % TELEPORT_TICKS < RuinMobs.ABILITY_TICKS) {
						teleport(mob, level, level.getRandom());
					}
				}
			})
			.drops(SLIME_DROPS)
			.register();
	}

	private static ItemStack tinted(final Item armor) {
		ItemStack stack = new ItemStack(armor);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(SENTINEL_COLOR));
		return stack;
	}

	/**
	 * Moves a mob to a free floor spot within {@link #TELEPORT_RANGE} blocks, or leaves it where it stands. Returns
	 * true when it moved. The spot has solid ground below, room for the mob, and no fluid.
	 */
	public static boolean teleport(final Mob mob, final ServerLevel level, final RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = 2.0 + random.nextDouble() * (TELEPORT_RANGE - 2.0);
			double x = mob.getX() + Math.cos(angle) * reach;
			double z = mob.getZ() + Math.sin(angle) * reach;
			for (int dy = 2; dy >= -3; dy--) {
				double y = Math.floor(mob.getY()) + dy;
				BlockPos below = BlockPos.containing(x, y - 1.0, z);
				AABB box = mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ());
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
					&& level.getFluidState(BlockPos.containing(x, y, z)).isEmpty() && level.noCollision(mob, box)) {
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
					mob.snapTo(x, y, z, mob.getYRot(), mob.getXRot());
					mob.getNavigation().stop();
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 12, 0.3, 0.3, 0.3, 0.05);
					level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.6F);
					return true;
				}
			}
		}
		return false;
	}
}
