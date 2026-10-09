package com.stella.tower;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A lost room's design is frozen. Rooms -1 to -10 hash to values taken before the biome floors changed any plan, so a
 * change to a room's volume, blocks, traps, chest spots, zones or ends fails here. The hash covers the design only:
 * the props a later slice adds are not in it.
 */
public class TowerLostRoomFrozenGameTest {
	/** A seed no world uses: the plans are pure, so the hash needs no world. */
	private static final long SEED = 0x5EED_10C7_0000L;
	private static final int ROOMS = 10;
	/** The room's volume as it was: 24 high from y -2. Written out so the floors' new height cannot move it. */
	private static final int LOW = -2;
	private static final int HIGH = 24;

	/** Taken from the code before any plan change, by this test's own hash. */
	private static final String[] GOLDEN = {
		"f2478dd053bb5d18",
		"6d017566190918c2",
		"7523eb45bb9b3760",
		"f336fa6b542b0aee",
		"0950b4815b9b03ab",
		"c3449f1268af3c1b",
		"d8906ca912985c2b",
		"ed656591bddca765",
		"dd8aa7841ed9166f",
		"a5864356ced78130"
	};

	/** Catches a plan change that moves a lost room's design, its traps, chests, zones or levels. */
	@GameTest
	public void theTenFirstLostRoomsKeepTheirDesign(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		for (int i = 0; i < ROOMS; i++) {
			int room = -(i + 1);
			String hash = hashOf(room);
			TowerTestSupport.result("lostRoomHash", "room=" + room + " hash=" + hash);
			if (!hash.equals(GOLDEN[i])) {
				bad.append(" room ").append(room).append(" is ").append(hash).append(" not ").append(GOLDEN[i]).append(';');
			}
		}
		helper.assertTrue(bad.length() == 0, "a lost room's design moved:" + bad);
		helper.succeed();
	}

	static String hashOf(final int room) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			TowerPlan plan = LostRooms.plan(SEED, room);
			TowerVolume volume = TowerVolume.of(plan);
			TowerTheme theme = plan.theme();
			StringBuilder text = new StringBuilder();
			text.append(plan.archetype()).append(plan.variation()).append(plan.rotation()).append(plan.attempt())
				.append(plan.arrivalX()).append(',').append(plan.arrivalZ()).append(',').append(plan.arrivalYaw())
				.append(plan.portalX()).append(',').append(plan.portalZ()).append(',').append(plan.portalFacing()).append(',').append(plan.portalStory())
				.append(plan.hall()).append(plan.look()).append(plan.stories().size()).append(plan.safeZones()).append(plan.chestSpots());
			text.append(TowerTraps.layout(SEED, LostRooms.looksFloor(room), plan));
			digest.update(text.toString().getBytes(StandardCharsets.UTF_8));
			for (int x = 0; x < TowerLayout.SIZE; x++) {
				for (int y = LOW; y <= HIGH; y++) {
					for (int z = 0; z < TowerLayout.SIZE; z++) {
						byte kind = volume.kind(x, y, z);
						digest.update(kind);
						if (kind != TowerVolume.AIR) {
							digest.update(TowerFloors.stateOf(volume, theme, plan, x, y, z).toString().getBytes(StandardCharsets.UTF_8));
						}
					}
				}
			}
			return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
