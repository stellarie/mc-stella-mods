package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Summon Skeleton: one servant that never despawns, guards its master, gathers loot into its pack, opens
 * its gear to its master, and answers a second cast by coming to its master's side at no cooldown.
 */
public class SummonSkeletonGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summon");

	@GameTest(maxTicks = 40)
	public void aSummonRaisesOneLastingServantAndRestsFiveMinutes(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.CastResult first = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		boolean resting = master.getCooldowns().isOnCooldown(master.getMainHandItem());
		LOG.info("[SPELLTEST] test=aSummonRaisesOneLastingServantAndRestsFiveMinutes RESULT result={} servant={} persistent={} resting={}",
			first, servant != null, servant != null && servant.isPersistenceRequired(), resting);
		helper.assertTrue(first == SpellCasting.CastResult.CAST && servant != null && servant.isPersistenceRequired(), "no lasting servant rose: " + first);
		helper.assertTrue(resting, "the book does not rest after a summon");
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aSecondCastCallsTheServantBackAtNoCooldown(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "no servant rose, so the case proves nothing");
		Vec3 far = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		master.teleportTo(far.x, far.y, far.z);
		SpellCasting.CastResult second = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton after = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		double distance = after == null ? -1.0 : after.distanceTo(master);
		LOG.info("[SPELLTEST] test=aSecondCastCallsTheServantBackAtNoCooldown RESULT result={} same={} distance={}", second, after == servant, distance);
		helper.assertTrue(second == SpellCasting.CastResult.CAST && after == servant && distance <= 3.0, "the recall went " + second + " and left it " + distance + " away");
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theServantFightsAHostileNearItsMaster(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, 5).withNoFreeWill().spawn();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.succeedWhen(() -> {
			helper.assertTrue(servant.getTarget() == zombie, "the servant targets " + servant.getTarget());
			LOG.info("[SPELLTEST] test=theServantFightsAHostileNearItsMaster RESULT target=zombie");
			servant.discard();
			leave(helper, master);
		});
	}

	/**
	 * A servant left in an unloaded chunk cannot be called back, so a new cast raises another. When the old one loads
	 * again it is retired, its pack and gear dropped where it stands, so a master never keeps two.
	 */
	@GameTest(maxTicks = 60)
	public void aServantThatReturnsAfterANewSummonIsRetired(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton first = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(first != null, "no servant rose, so the case proves nothing");
		SummonSkeleton.stow(first, new ItemStack(Items.DIAMOND, 3));
		TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		first.saveWithoutId(saved);
		first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		master.getCooldowns().removeCooldown(master.getCooldowns().getCooldownGroup(master.getMainHandItem()));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton second = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(second != null && second != first, "the second cast raised no new servant");
		Skeleton returned = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.LOAD);
		returned.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
		helper.getLevel().addFreshEntity(returned);
		helper.succeedWhen(() -> {
			long diamonds = helper.getLevel().getEntitiesOfClass(ItemEntity.class, returned.getBoundingBox().inflate(3.0),
				item -> item.getItem().is(Items.DIAMOND)).stream().mapToLong(item -> item.getItem().getCount()).sum();
			helper.assertTrue(returned.isRemoved(), "the returned servant still serves beside the new one");
			helper.assertTrue(second.isAlive(), "the new servant was retired in the old one's place: removed=" + second.getRemovalReason()
				+ " health=" + second.getHealth() + " returned=" + returned.getRemovalReason() + " sameId=" + second.getUUID().equals(returned.getUUID()));
			helper.assertTrue(diamonds == 3, "the retired servant's pack dropped " + diamonds + " of 3 diamonds");
			LOG.info("[SPELLTEST] test=aServantThatReturnsAfterANewSummonIsRetired RESULT retired=true newAlive=true diamonds={}", diamonds);
			second.discard();
			leave(helper, master);
		});
	}

	/** A servant struck by its own master, by a stray blow or a sweep, never turns on that master. */
	@GameTest(maxTicks = 60)
	public void theServantNeverTurnsOnItsMaster(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 4, 4);
		final boolean[] turned = {false};
		helper.onEachTick(() -> turned[0] |= servant.getTarget() == master);
		// A few ticks in: vanilla's retaliation ignores a blow stamped on the servant's first tick.
		helper.runAfterDelay(5L, () -> {
			servant.hurtServer(helper.getLevel(), master.damageSources().playerAttack(master), 1.0F);
			helper.assertTrue(servant.getLastHurtByMob() == master, "the master's blow did not register, so the case proves nothing");
		});
		helper.runAfterDelay(45L, () -> {
			LOG.info("[SPELLTEST] test=theServantNeverTurnsOnItsMaster RESULT turned={} target={}", turned[0], servant.getTarget());
			helper.assertFalse(turned[0], "the servant turned on its master");
			servant.discard();
			leave(helper, master);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void theServantGathersLootIntoItsPack(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		servant.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		ItemEntity loot = new ItemEntity(helper.getLevel(), servant.getX(), servant.getY(), servant.getZ(), new ItemStack(Items.BONE, 5));
		// Dropped where it stands, not flung, so the loot lies within its reach.
		loot.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(loot);
		helper.succeedWhen(() -> {
			int bones = SummonSkeleton.pack(servant).stream().filter(stack -> stack.is(Items.BONE)).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(bones == 5, "the pack holds " + bones + " bones");
			LOG.info("[SPELLTEST] test=theServantGathersLootIntoItsPack RESULT bones={} lootGone={}", bones, loot.isRemoved());
			servant.discard();
			leave(helper, master);
		});
	}

	@GameTest
	public void theServantsScreenChangesItsGearAndItsName(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		SkeletonMenu menu = new SkeletonMenu(1, master.getInventory(), servant);
		menu.container().setItem(0, new ItemStack(Items.IRON_HELMET));
		ItemStack paper = new ItemStack(Items.PAPER);
		paper.set(DataComponents.CUSTOM_NAME, Component.literal("Bones"));
		menu.container().setItem(SkeletonMenu.NAME_SLOT, paper);
		menu.container().setItem(SkeletonMenu.PACK_START, new ItemStack(Items.ARROW, 16));
		boolean paneLocked = !menu.slots.get(6).mayPlace(new ItemStack(Items.DIRT)) && !menu.slots.get(6).mayPickup(master);
		menu.removed(master);
		String name = servant.getCustomName() == null ? "none" : servant.getCustomName().getString();
		boolean paperBack = master.getInventory().countItem(Items.PAPER) == 1;
		LOG.info("[SPELLTEST] test=theServantsScreenChangesItsGearAndItsName RESULT head={} name={} paperBack={} pack={} paneLocked={}",
			servant.getItemBySlot(EquipmentSlot.HEAD), name, paperBack, SummonSkeleton.pack(servant).get(0), paneLocked);
		helper.assertTrue(servant.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet did not reach the servant");
		helper.assertTrue(name.equals("Bones") && paperBack, "the name slot left the servant named " + name);
		helper.assertTrue(SummonSkeleton.pack(servant).get(0).is(Items.ARROW) && paneLocked, "the pack or the panes are wrong");
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest
	public void aGolemSparesTheServant(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		IronGolem golem = helper.spawnMob(EntityTypes.IRON_GOLEM, 5, 1, 5).withNoFreeWill().spawn();
		Skeleton wild = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.EVENT);
		boolean spares = !golem.canAttack(servant);
		boolean huntsWild = golem.canAttack(wild);
		LOG.info("[SPELLTEST] test=aGolemSparesTheServant RESULT spares={} huntsWild={}", spares, huntsWild);
		helper.assertTrue(huntsWild, "a golem spares a wild skeleton too, so the case proves nothing");
		helper.assertTrue(spares, "a golem would attack the servant");
		wild.discard();
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest
	public void theSummonBookHasNoScrollAndTakesNoImbue(final GameTestHelper helper) {
		boolean scroll = net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Spellbooks.scrollId(SummonSkeleton.ID));
		boolean imbuable = !SpellImbue.NOT_IMBUABLE.contains(SummonSkeleton.ID);
		LOG.info("[SPELLTEST] test=theSummonBookHasNoScrollAndTakesNoImbue RESULT scroll={} imbuable={}", scroll, imbuable);
		helper.assertTrue(!scroll && !imbuable, "the summon has a scroll or takes an imbue");
		helper.succeed();
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master, final int x, final int z) {
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		servant.teleportTo(at.x, at.y, at.z);
		servant.setNoAi(false);
		return servant;
	}

	private static SpellbookItem book() {
		return (SpellbookItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
	}

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
