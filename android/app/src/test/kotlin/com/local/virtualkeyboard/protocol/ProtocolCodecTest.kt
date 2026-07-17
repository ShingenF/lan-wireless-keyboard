package com.local.virtualkeyboard.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun `text commit encodes a single JSON line with escaped unicode-safe text`() {
        val encoded = ProtocolCodec.encodeCommand(
            command = OutgoingCommand.TextCommit("你好\n\"PC\""),
            seq = 7,
            timestamp = 1234,
        )

        assertEquals(
            "{\"version\":1,\"type\":\"textCommit\",\"seq\":7,\"timestamp\":1234,\"text\":\"你好\\n\\\"PC\\\"\"}",
            encoded,
        )
    }

    @Test
    fun `synchronous direct text declares physical key preference`() {
        val encoded = ProtocolCodec.encodeCommand(
            command = OutgoingCommand.TextCommit("wasd", preferPhysicalKeys = true),
            seq = 8,
            timestamp = 1235,
        )

        assertEquals(
            "{\"version\":1,\"type\":\"textCommit\",\"seq\":8,\"timestamp\":1235,\"text\":\"wasd\",\"preferPhysicalKeys\":true}",
            encoded,
        )
    }

    @Test
    fun `synchronous tail replacement declares physical key preference`() {
        val encoded = ProtocolCodec.encodeCommand(
            command = OutgoingCommand.ReplaceTail(
                deleteCodePoints = 1,
                text = "d",
                preferPhysicalKeys = true,
            ),
            seq = 9,
            timestamp = 1236,
        )

        assertEquals(
            "{\"version\":1,\"type\":\"replaceTail\",\"seq\":9,\"timestamp\":1236,\"deleteCodePoints\":1,\"text\":\"d\",\"preferPhysicalKeys\":true}",
            encoded,
        )
    }

    @Test
    fun `pointer button preserves down and up actions for dragging`() {
        assertEquals(
            "{\"version\":1,\"type\":\"pointerButton\",\"seq\":9,\"timestamp\":88,\"button\":\"left\",\"action\":\"down\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.PointerButton(button = MouseButton.LEFT, action = ButtonAction.DOWN),
                seq = 9,
                timestamp = 88,
            ),
        )
    }

    @Test
    fun `game movement key state preserves physical down and up actions`() {
        assertEquals(
            "{\"version\":1,\"type\":\"keyState\",\"seq\":10,\"timestamp\":89,\"key\":\"w\",\"action\":\"down\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.KeyState(key = GameKey.W, action = ButtonAction.DOWN),
                seq = 10,
                timestamp = 89,
            ),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"keyState\",\"seq\":11,\"timestamp\":90,\"key\":\"a\",\"action\":\"up\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.KeyState(key = GameKey.A, action = ButtonAction.UP),
                seq = 11,
                timestamp = 90,
            ),
        )
    }

    @Test
    fun `keyboard pointer and wheel commands use the shared v1 field names`() {
        assertEquals(
            "{\"version\":1,\"type\":\"keyPress\",\"seq\":1,\"timestamp\":2,\"key\":\"left\"}",
            ProtocolCodec.encodeCommand(OutgoingCommand.KeyPress(RemoteKey.LEFT), 1, 2),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"keyPress\",\"seq\":2,\"timestamp\":3,\"key\":\"escape\"}",
            ProtocolCodec.encodeCommand(OutgoingCommand.KeyPress(RemoteKey.ESCAPE), 2, 3),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"pointerMove\",\"seq\":3,\"timestamp\":4,\"dx\":12,\"dy\":-7}",
            ProtocolCodec.encodeCommand(OutgoingCommand.PointerMove(12, -7), 3, 4),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"wheel\",\"seq\":5,\"timestamp\":6,\"delta\":-120}",
            ProtocolCodec.encodeCommand(OutgoingCommand.Wheel(-120), 5, 6),
        )
    }

    @Test
    fun `system shortcut encodes only a whitelisted shortcut name`() {
        assertEquals(
            "{\"version\":1,\"type\":\"systemShortcut\",\"seq\":12,\"timestamp\":91,\"shortcut\":\"windowsSpace\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.SystemShortcutPress(SystemShortcut.WINDOWS_SPACE),
                seq = 12,
                timestamp = 91,
            ),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"systemShortcut\",\"seq\":13,\"timestamp\":92,\"shortcut\":\"shift\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.SystemShortcutPress(SystemShortcut.SHIFT),
                seq = 13,
                timestamp = 92,
            ),
        )
        assertEquals(
            "{\"version\":1,\"type\":\"systemShortcut\",\"seq\":14,\"timestamp\":93,\"shortcut\":\"altShift\"}",
            ProtocolCodec.encodeCommand(
                OutgoingCommand.SystemShortcutPress(SystemShortcut.ALT_SHIFT),
                seq = 14,
                timestamp = 93,
            ),
        )
    }

    @Test
    fun `authentication proof matches the protocol fixture`() {
        assertEquals(
            "49E066BBAF57ECF4DCE6033C4E694A195E0DA8923050536919DCD10F3EF6AA00",
            AuthenticationProof.calculate(
                pairingCode = "abcd-2345-efgh-6789",
                nonce = "bm9uY2U=",
                fingerprint = "A1B2C3",
            ),
        )
    }

    @Test
    fun `authentication frame escapes the device name`() {
        assertEquals(
            "{\"version\":1,\"type\":\"auth\",\"proof\":\"ABC123\",\"device\":\"Example \\\"Phone\\\"\"}",
            ProtocolCodec.encodeAuth("ABC123", "Example \"Phone\""),
        )
    }

    @Test
    fun `heartbeat uses the same sequenced command envelope`() {
        assertEquals(
            "{\"version\":1,\"type\":\"ping\",\"seq\":11,\"timestamp\":99}",
            ProtocolCodec.encodeCommand(OutgoingCommand.Ping, 11, 99),
        )
    }
}
