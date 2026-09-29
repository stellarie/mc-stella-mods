package com.stella.learn;

import com.stella.learn.gym.Bridge;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Java frame codec against the fixed bytes that `training/tests/test_wire.py` checks on the Python side. */
public class GymWireGameTest {
	static final String HELLO = "2b0000000101001000040020000000000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
	static final String STEP = "2b00000002070000000200000000000000010000003f0000803f000000400500000006000080bf000040c00000803e";
	static final String ACT = "31000000030700000002000000000000000000803f000000000000003f000080bf05000000000000000000803f0000000000000000";
	static final String RESET = "0900000004ffffffff2a000000";
	static final String BYE = "0100000005";

	private static String hex(final byte[] bytes) {
		return HexFormat.of().formatHex(bytes);
	}

	private static byte[] hash() {
		byte[] hash = new byte[Bridge.HASH_SIZE];
		for (int i = 0; i < hash.length; i++) {
			hash[i] = (byte) i;
		}

		return hash;
	}

	@GameTest
	public void helloEncodesToTheFixedBytes(final GameTestHelper helper) {
		String encoded = hex(Bridge.helloFrame(16, 4, 32, hash()));
		helper.assertTrue(encoded.equals(HELLO), "hello encodes to " + encoded);
		helper.succeed();
	}

	@GameTest
	public void stepEncodesToTheFixedBytes(final GameTestHelper helper) {
		String encoded = hex(Bridge.stepFrame(7, new int[] {0, 5}, new int[] {1, 6}, new float[] {0.5F, -1.0F}, new float[][] {{1.0F, 2.0F}, {-3.0F, 0.25F}}));
		helper.assertTrue(encoded.equals(STEP), "step encodes to " + encoded);
		helper.succeed();
	}

	@GameTest
	public void actDecodesFromTheFixedBytes(final GameTestHelper helper) {
		ByteBuffer buffer = ByteBuffer.wrap(HexFormat.of().parseHex(ACT)).order(ByteOrder.LITTLE_ENDIAN);
		int length = buffer.getInt();
		helper.assertTrue(length == buffer.remaining(), "the length prefix reads " + length);
		helper.assertTrue((buffer.get() & 0xFF) == Bridge.ACT, "the type byte is not Act");
		Bridge.Act act = Bridge.decodeAct(buffer, 4);
		helper.assertTrue(act.stepId() == 7 && act.agents().length == 2 && act.agents()[1] == 5, "the act header is wrong");
		helper.assertTrue(act.actions()[0][2] == 0.5F && act.actions()[0][3] == -1.0F && act.actions()[1][1] == 1.0F, "the act values are wrong");
		helper.assertTrue(hex(Bridge.actFrame(7, act.agents(), act.actions())).equals(ACT), "the act does not encode back to the same bytes");
		helper.succeed();
	}

	@GameTest
	public void resetAndByeMatchTheFixedBytes(final GameTestHelper helper) {
		String reset = hex(Bridge.resetFrame(Bridge.ALL_ARENAS, 42));
		helper.assertTrue(reset.equals(RESET), "reset encodes to " + reset);
		String bye = hex(Bridge.byeFrame());
		helper.assertTrue(bye.equals(BYE), "bye encodes to " + bye);
		ByteBuffer body = ByteBuffer.wrap(HexFormat.of().parseHex(RESET)).order(ByteOrder.LITTLE_ENDIAN);
		body.position(5);
		Bridge.Reset decoded = Bridge.decodeReset(body);
		helper.assertTrue(decoded.arena() == Bridge.ALL_ARENAS && decoded.seed() == 42, "the reset decodes to " + decoded);
		helper.succeed();
	}
}
