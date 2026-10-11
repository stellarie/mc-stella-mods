package com.stella.tower;

import com.stella.mobs.scaling.ThreatState;
import com.stella.tower.colosseum.Colosseum;
import com.stella.tower.colosseum.ColosseumData;
import com.stella.tower.colosseum.GuardianKind;
import com.stella.tower.colosseum.Guardians;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The ruined colosseum the world builds once, 96 to 256 blocks from its spawn: its look, its lit portal, its offering chest,
 * and what the chest does. The test world keeps the colosseum its first start built, so a later run reads the same one.
 */
public class ColosseumGameTest {
	private static final int SPEC_MIN_FROM_SPAWN = 96;
	private static final int SPEC_MAX_FROM_SPAWN = 256;
	private static final int SPEC_RADIUS = 32;
	private static final int SPEC_BONES = 128;

	private static ServerLevel overworld(final GameTestHelper helper) {
		return helper.getLevel().getServer().overworld();
	}

	private static Colosseum.Layout layout(final GameTestHelper helper) {
		Colosseum.Layout layout = Colosseum.layoutOf(overworld(helper));
		helper.assertTrue(layout != null, "the world holds no colosseum: it was never built, or the build failed at the start");
		return layout;
	}

	/** Loads the chunks of the whole colosseum, so what the case reads is what is saved. */
	private static void load(final ServerLevel level, final Colosseum.Layout layout) {
		BlockPos floor = layout.floor();
		for (int x = (floor.getX() - SPEC_RADIUS - 2) >> 4; x <= (floor.getX() + SPEC_RADIUS + 2) >> 4; x++) {
			for (int z = (floor.getZ() - SPEC_RADIUS - 2) >> 4; z <= (floor.getZ() + SPEC_RADIUS + 2) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
	}

	private static BlockPos at(final Colosseum.Layout layout, final double radius, final double degrees, final int up) {
		double angle = Math.toRadians(degrees);
		return layout.floor().offset((int) Math.round(Math.cos(angle) * radius), up, (int) Math.round(Math.sin(angle) * radius));
	}

	/**
	 * Catches a colosseum that is missing, or built too near or too far. The distance is read from the spawn the site was
	 * drawn from, which the claim keeps: the harness moves the world spawn to its test grid after the build.
	 */
	@GameTest(maxTicks = 100)
	public void theColosseumStandsBetween96And256BlocksFromTheSpawn(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		BlockPos spawn = ColosseumData.get(level).spawn();
		helper.assertTrue(spawn != null, "the claim does not record the spawn the site was drawn from");
		double away = Math.hypot(layout.floor().getX() - spawn.getX(), layout.floor().getZ() - spawn.getZ());
		TowerTestSupport.result("colosseumSite", "floor=" + layout.floor() + " spawn=" + spawn + " away=" + away);
		helper.assertTrue(away >= SPEC_MIN_FROM_SPAWN && away <= SPEC_MAX_FROM_SPAWN, "the colosseum stands " + away + " blocks from the spawn");
		helper.succeed();
	}

	/**
	 * Catches a ring with no wall, one that is whole all round or gone all round, seats that are not tiered or not stairs,
	 * columns of one height, and stone that is never cracked or mossy.
	 */
	@GameTest(maxTicks = 100)
	public void theRingHasTieredSeatsBrokenColumnsAndAWornWall(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		load(level, layout);
		int y0 = layout.floor().getY();
		int whole = 0;
		int broken = 0;
		for (int step = 0; step < 72; step++) {
			double degrees = step * 5.0 + 2.5;
			if (Math.abs(Math.sin(Math.toRadians(degrees * 2.0))) < 0.35) {
				continue;
			}
			int height = top(level, at(layout, 30.5, degrees, 0));
			whole += height >= 10 ? 1 : 0;
			broken += height <= 6 ? 1 : 0;
		}
		int cracked = 0;
		int mossy = 0;
		int stairs = 0;
		for (int dx = -SPEC_RADIUS; dx <= SPEC_RADIUS; dx++) {
			for (int dz = -SPEC_RADIUS; dz <= SPEC_RADIUS; dz++) {
				double r = Math.hypot(dx, dz);
				if (r < 20.0 || r > SPEC_RADIUS + 0.6) {
					continue;
				}
				for (int dy = 1; dy <= 11; dy++) {
					BlockState state = level.getBlockState(layout.floor().offset(dx, dy, dz));
					cracked += state.is(Blocks.CRACKED_STONE_BRICKS) ? 1 : 0;
					mossy += state.is(Blocks.MOSSY_STONE_BRICKS) ? 1 : 0;
					stairs += r < 29.0 && state.getBlock() instanceof StairBlock ? 1 : 0;
				}
			}
		}
		int rising = 0;
		for (int step = 0; step < 24; step++) {
			double degrees = step * 15.0 + 7.5;
			if (Math.abs(Math.sin(Math.toRadians(degrees * 2.0))) < 0.35) {
				continue;
			}
			rising += top(level, at(layout, 27.0, degrees, 0)) > top(level, at(layout, 22.0, degrees, 0)) ? 1 : 0;
		}
		Set<Integer> heights = new HashSet<>();
		int shafts = 0;
		for (int dx = -19; dx <= 19; dx++) {
			for (int dz = -19; dz <= 19; dz++) {
				double r = Math.hypot(dx, dz);
				if (r >= 17.5 && r <= 19.7 && !level.getBlockState(layout.floor().offset(dx, 2, dz)).isAir()) {
					shafts++;
					heights.add(top(level, layout.floor().offset(dx, 0, dz)));
				}
			}
		}
		TowerTestSupport.result("colosseumRing", "wallWhole=" + whole + " wallBroken=" + broken + " cracked=" + cracked + " mossy=" + mossy + " seatStairs=" + stairs
			+ " tiersRising=" + rising + " columnBlocks=" + shafts + " columnHeights=" + heights.size());
		helper.assertTrue(whole >= 3 && broken >= 1, "the wall is not a worn ring: " + whole + " whole stretches, " + broken + " broken");
		helper.assertTrue(cracked >= 30 && mossy >= 30, "the stone is not cracked and mossy: " + cracked + " cracked, " + mossy + " mossy");
		helper.assertTrue(stairs >= 150, "the seats are not stairs: " + stairs);
		helper.assertTrue(rising >= 8, "the seats do not rise in tiers: " + rising + " of 24 stretches");
		helper.assertTrue(shafts >= 20 && heights.size() >= 3, "the columns are not broken at different heights: " + shafts + " blocks, " + heights.size() + " heights");
		helper.succeed();
	}

	/** The highest block of a column that is not air, scanning down from sixteen above the floor. */
	private static int top(final ServerLevel level, final BlockPos column) {
		for (int dy = 16; dy >= 0; dy--) {
			if (!level.getBlockState(column.above(dy)).isAir()) {
				return dy;
			}
		}
		return -1;
	}

	/** Catches a platform that is not raised, with no stair up, a portal frame that is not crying obsidian, or a portal that is not lit. */
	@GameTest(maxTicks = 100)
	public void aRaisedPlatformCarriesALitTowerPortalAndTheOfferingChest(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		load(level, layout);
		BlockPos floor = layout.floor();
		int platform = top(level, floor.offset(0, 0, 4));
		int beyond = top(level, floor.offset(8, 0, 8));
		int flight = 0;
		for (int k = 1; k <= 4; k++) {
			flight += level.getBlockState(floor.offset(6 + k, 5 - k, 0)).getBlock() instanceof StairBlock ? 1 : 0;
		}
		BlockPos inside = layout.portalInside();
		int lit = 0;
		int frame = 0;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockState state = level.getBlockState(inside.offset(dx, dy, 0));
				lit += state.is(TowerBlocks.TOWER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == net.minecraft.core.Direction.Axis.X ? 1 : 0;
			}
		}
		for (int dx = -1; dx <= 2; dx++) {
			frame += level.getBlockState(inside.offset(dx, -1, 0)).is(Blocks.CRYING_OBSIDIAN) ? 1 : 0;
			frame += level.getBlockState(inside.offset(dx, 3, 0)).is(Blocks.CRYING_OBSIDIAN) ? 1 : 0;
		}
		for (int dy = 0; dy <= 2; dy++) {
			frame += level.getBlockState(inside.offset(-1, dy, 0)).is(Blocks.CRYING_OBSIDIAN) ? 1 : 0;
			frame += level.getBlockState(inside.offset(2, dy, 0)).is(Blocks.CRYING_OBSIDIAN) ? 1 : 0;
		}
		TowerTestSupport.result("colosseumPlatform", "platform=" + platform + " beyond=" + beyond + " flight=" + flight + " lit=" + lit + " frame=" + frame
			+ " chestOnPlatform=" + level.getBlockState(layout.chest().below()).isSolid());
		helper.assertTrue(platform == 4 && beyond < 4, "the middle is not a platform four blocks over the sand: " + platform + " against " + beyond);
		helper.assertTrue(flight == 4, "the platform has no flight of four stairs: " + flight);
		helper.assertTrue(frame == 14, "the portal frame is " + frame + " of 14 crying obsidian");
		helper.assertTrue(lit == 6, "the portal holds " + lit + " of 6 lit tower portal blocks");
		helper.assertTrue(level.getBlockState(layout.chest()).is(TowerBlocks.TOWER_CHEST) && level.getBlockState(layout.chest().below()).isSolid(), "the chest does not stand on the platform");
		helper.succeed();
	}

	/**
	 * Catches a start that builds the colosseum again, a claim the save does not keep, and a rebuild that erases a change
	 * made after the first build. The harness cannot restart the server, so the second start is the same code path called
	 * again over the saved data.
	 */
	@GameTest(maxTicks = 100)
	public void aSecondStartBuildsNoSecondColosseum(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		load(level, layout);
		BlockPos wall = layout.floor().offset(21, 2, 21);
		BlockState before = level.getBlockState(wall);
		helper.assertTrue(!before.isAir(), "the wall block the case digs out is already missing");
		try {
			level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			Colosseum.Result again = Colosseum.ensure(level);
			helper.assertTrue(again != null && !again.built() && again.layout().floor().equals(layout.floor()), "the second start built the colosseum again: " + again);
			helper.assertTrue(level.getBlockState(wall).isAir(), "the second start rebuilt the wall");
		} finally {
			level.setBlock(wall, before, Block.UPDATE_ALL);
		}
		var saved = ColosseumData.CODEC.encodeStart(NbtOps.INSTANCE, ColosseumData.get(level)).result().orElseThrow();
		ColosseumData read = ColosseumData.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
		TowerTestSupport.result("colosseumClaim", "saved=" + read.floor() + " built=" + layout.floor());
		helper.assertTrue(layout.floor().equals(read.floor()), "the claim does not survive a save: " + read.floor());
		helper.succeed();
	}

	/** Catches a colosseum portal that leads anywhere but where a portal a player lights does, or that forgets the way back. */
	@GameTest(maxTicks = 200)
	public void steppingIntoTheColosseumPortalEntersTheTowerAsAPlayerBuiltOneDoes(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		ServerLevel tower = TowerTestSupport.tower(helper);
		Colosseum.Layout layout = layout(helper);
		load(level, layout);
		BlockPos inside = layout.portalInside();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		TeleportTransition viaColosseum = TowerBlocks.TOWER_PORTAL.getPortalDestination(level, player, inside);
		TowerTravel.Entry way = player.getAttached(TowerTravel.ENTRY);
		BlockPos relative = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(relative, Blocks.FIRE);
		BlockPos built = helper.absolutePos(relative);
		player.teleportTo(helper.getLevel(), built.getX() + 0.5, built.getY(), built.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		TeleportTransition viaPlayerBuilt = TowerBlocks.TOWER_PORTAL.getPortalDestination(helper.getLevel(), player, built);
		TowerTestSupport.leave(helper, player);
		TowerTestSupport.result("colosseumPortal", "colosseum=" + (viaColosseum == null ? null : viaColosseum.position()) + " playerBuilt="
			+ (viaPlayerBuilt == null ? null : viaPlayerBuilt.position()) + " way=" + (way == null ? null : way.position()));
		helper.assertTrue(viaColosseum != null && viaPlayerBuilt != null, "a portal led nowhere");
		helper.assertTrue(viaColosseum.newLevel() == tower && viaColosseum.position().equals(viaPlayerBuilt.position()), "the colosseum portal does not lead where a player-built one does");
		helper.assertTrue(way != null && way.dimension().equals(Level.OVERWORLD) && way.position().distanceTo(Vec3.atBottomCenterOf(inside)) < 4.0,
			"the way back was not written beside the colosseum portal: " + way);
		helper.succeed();
	}

	/**
	 * Catches a build that levels what someone has built, and a guard that refuses every site. The site is fresh for each run, so
	 * the world holds no earlier colosseum on it: a plank is put in its footprint, the build must pass it over and touch
	 * nothing, and then the plank goes and the build must happen.
	 */
	@GameTest(maxTicks = 200)
	public void aSiteSomeoneHasBuiltOnIsPassedOverAndAClearSiteIsBuilt(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		BlockPos real = layout(helper).floor();
		BlockPos site = new BlockPos(3_000_000 + (int) ((System.currentTimeMillis() / 1000L) % 4000L) * 128, real.getY(), 3_000_000);
		BlockPos plank = site.offset(10, 1, 7);
		level.getChunk(plank.getX() >> 4, plank.getZ() >> 4);
		level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos middle = site.above(Colosseum.PLATFORM_HEIGHT);
		boolean builtOverHand = Colosseum.buildAt(level, site);
		boolean untouched = level.getBlockState(middle).isAir() && level.getBlockState(plank).is(Blocks.OAK_PLANKS);
		level.setBlock(plank, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		boolean builtClear = Colosseum.buildAt(level, site);
		int lit = 0;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				lit += level.getBlockState(new Colosseum.Layout(site).portalInside().offset(dx, dy, 0)).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
			}
		}
		TowerTestSupport.result("colosseumGuard", "site=" + site + " builtOverHand=" + builtOverHand + " untouched=" + untouched + " builtClear=" + builtClear + " lit=" + lit);
		helper.assertTrue(!builtOverHand && untouched, "the colosseum was built over a plank, or the refusal touched the site");
		helper.assertTrue(builtClear && lit == 6, "a clear site was not built: built " + builtClear + ", portal blocks " + lit);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the chest

	/** Catches a chest that can be mined or blown up, that holds no book, or whose book does not name the offering. */
	@GameTest(maxTicks = 100)
	public void theOfferingChestCannotBeMinedOrBlownUpAndHoldsItsBook(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		load(level, layout);
		BlockPos chestAt = layout.chest();
		helper.assertTrue(level.getBlockState(chestAt).is(TowerBlocks.TOWER_CHEST), "the offering chest is not a tower chest");
		ServerPlayer miner = TowerTestSupport.join(helper, 1.5, 1.5);
		miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
		float progress = level.getBlockState(chestAt).getDestroyProgress(miner, level, chestAt);
		TowerTestSupport.leave(helper, miner);
		helper.assertTrue(progress == 0.0F && level.getBlockState(chestAt).getDestroySpeed(level, chestAt) < 0.0F, "a pickaxe can mine the chest: " + progress);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestAt);
		ItemStack book = chest.getItem(0);
		WrittenBookContent text = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String pages = text == null ? "" : text.getPages(false).stream().map(page -> page.getString()).reduce("", (a, b) -> a + " " + b);
		TowerTestSupport.result("offeringChest", "book=" + book.getItem() + " pages=" + pages.length());
		helper.assertTrue(book.is(Items.WRITTEN_BOOK) && pages.contains(String.valueOf(SPEC_BONES)) && pages.contains("bones"), "the chest holds no book that names the 128 bones");
		helper.succeed();
	}

	/**
	 * Catches a chest block that a blast removes. The blast is lit on a copy over the open sky, so the colosseum is not
	 * cratered, and two blocks from the chest, because a blast lit inside the chest's own cell dies in it. The brick between
	 * the blast and the chest proves the blast was strong enough to break a block.
	 */
	@GameTest(maxTicks = 60)
	public void anExplosionLeavesTheChestStandingAndBreaksTheBrickBetween(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 20);
		BlockPos chestAt = rig.center.offset(0, 1, 0);
		BlockPos brick = rig.center.offset(1, 1, 0);
		rig.level.setBlock(chestAt, TowerBlocks.TOWER_CHEST.defaultBlockState(), Block.UPDATE_ALL);
		rig.level.setBlock(brick, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
		rig.level.explode(null, chestAt.getX() + 2.5, chestAt.getY() + 0.5, chestAt.getZ() + 0.5, 8.0F, Level.ExplosionInteraction.TNT);
		TowerTestSupport.result("chestBlast", "chestStands=" + rig.level.getBlockState(chestAt).is(TowerBlocks.TOWER_CHEST) + " brickGone=" + rig.level.getBlockState(brick).isAir());
		helper.assertTrue(rig.level.getBlockState(brick).isAir(), "the blast broke nothing, so the case proved nothing");
		helper.assertTrue(rig.level.getBlockState(chestAt).is(TowerBlocks.TOWER_CHEST), "the blast removed the chest");
		helper.succeed();
	}

	/**
	 * Catches an offering that works under 128 bones, one that works while a guardian lives, one that keeps the bones, and one
	 * that raises fewer or other than the pair. It runs the chest the world built, killing the guardians first.
	 */
	@GameTest(maxTicks = 500)
	public void anOfferingOf128BonesRaisesBothGuardiansOnlyWhenNoneLives(final GameTestHelper helper) {
		ServerLevel level = overworld(helper);
		Colosseum.Layout layout = layout(helper);
		ColosseumData data = ColosseumData.get(level);
		ChunkPos middle = ChunkPos.containing(layout.floor());
		int[] phase = {0};
		long[] since = {0L};
		Set<UUID> raised = new HashSet<>();
		Set<UUID> stale = new HashSet<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick % 50L == 0L) {
				level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, middle, 4);
			}
			ChestBlockEntity chest = level.getBlockEntity(layout.chest()) instanceof ChestBlockEntity found ? found : null;
			if (chest == null) {
				helper.assertTrue(tick < 80L, "the offering chest never loaded");
				return;
			}
			switch (phase[0]) {
				case 0 -> {
					if (tick >= 70L) {
						for (UUID id : data.guardians()) {
							if (level.getEntity(id) instanceof Mob mob) {
								mob.kill(level);
							}
						}
						clearBones(chest);
						put(chest, SPEC_BONES - 1);
						stale.addAll(data.guardians());
						phase[0] = 1;
						since[0] = tick;
					}
				}
				case 1 -> {
					if (tick - since[0] >= 50L) {
						helper.assertTrue(bones(chest) == SPEC_BONES - 1 && data.guardians().equals(stale), "127 bones raised the guardians: bones " + bones(chest)
							+ ", record " + data.guardians().size() + " against " + stale.size());
						put(chest, 1);
						phase[0] = 2;
						since[0] = tick;
					}
				}
				case 2 -> {
					if (data.guardians().size() == 2 && java.util.Collections.disjoint(data.guardians(), stale)) {
						raised.addAll(data.guardians());
						helper.assertTrue(bones(chest) == 0, "the bones were not taken: " + bones(chest));
						phase[0] = 3;
						since[0] = tick;
						put(chest, SPEC_BONES);
					} else {
						helper.assertTrue(tick - since[0] < 120L, "128 bones with no guardian alive raised " + data.guardians().size() + " guardians");
					}
				}
				case 3 -> {
					if (tick - since[0] >= 50L) {
						helper.assertTrue(bones(chest) == SPEC_BONES && data.guardians().equals(raised), "128 bones raised guardians while two lived: bones " + bones(chest)
							+ ", record " + data.guardians().size());
						clearBones(chest);
						int found = 0;
						int rightPlace = 0;
						for (UUID id : raised) {
							Entity entity = level.getEntity(id);
							if (entity instanceof Mob mob && mob.isAlive()) {
								GuardianKind kind = GuardianKind.of(mob);
								found += kind == null ? 0 : 1;
								rightPlace += kind != null && ThreatState.level(mob) == 50 && mob.getAttributeBaseValue(Attributes.SCALE) == 2.0
									&& Math.hypot(mob.getX() - (layout.floor().getX() + 0.5), mob.getZ() - (layout.floor().getZ() + 0.5)) < 16.0 ? 1 : 0;
							}
						}
						TowerTestSupport.result("offering", "raised=" + raised.size() + " alive=" + found + " levelFiftyAtTheMiddle=" + rightPlace);
						helper.assertTrue(found == 2 && rightPlace == 2, "the raised pair is not two level 50 guardians at the middle: " + found + " alive, " + rightPlace + " right");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
	}

	private static int bones(final ChestBlockEntity chest) {
		int total = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			total += chest.getItem(slot).is(Items.BONE) ? chest.getItem(slot).getCount() : 0;
		}
		return total;
	}

	private static void clearBones(final ChestBlockEntity chest) {
		for (int slot = 1; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(Items.BONE)) {
				chest.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	/** Puts bones in the free slots after the book. */
	private static void put(final ChestBlockEntity chest, final int amount) {
		int left = amount;
		for (int slot = 1; slot < chest.getContainerSize() && left > 0; slot++) {
			ItemStack held = chest.getItem(slot);
			if (held.isEmpty()) {
				int put = Math.min(left, 64);
				chest.setItem(slot, new ItemStack(Items.BONE, put));
				left -= put;
			} else if (held.is(Items.BONE) && held.getCount() < 64) {
				int put = Math.min(left, 64 - held.getCount());
				held.grow(put);
				left -= put;
			}
		}
		chest.setChanged();
	}
}
