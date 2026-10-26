import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

// Both formats contain the same bytes, including a 24-byte segment header.
public class MhcFile {
    static final int MAGIC = 0x4D484331; // ASCII "MHC1"
    static final int HEADER_SIZE = 24;

    static byte[] pack(Assembler.Result result) {
        ByteBuffer buffer = ByteBuffer.allocate(
            HEADER_SIZE + result.text.length + result.data.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putInt(Assembler.TEXT_BASE);
        buffer.putInt(Assembler.DATA_BASE);
        buffer.putInt(result.entry);
        buffer.putInt(result.text.length);
        buffer.putInt(result.data.length);
        buffer.put(result.text);
        buffer.put(result.data);
        return buffer.array();
    }

    static void write(String prefix, Assembler.Result result) throws IOException {
        byte[] bytes = pack(result);
        StringBuilder bits = new StringBuilder();
        int wordEnd = HEADER_SIZE + result.text.length;
        for (int i = 0; i < bytes.length; i++) {
            for (int shift = 7; shift >= 0; shift--) {
                bits.append((bytes[i] >>> shift) & 1);
            }
            // Header/text: one 32-bit word per line. Data: one byte per line.
            if (i >= wordEnd || (i + 1) % 4 == 0) bits.append('\n');
        }
        Files.write(Paths.get(prefix + ".mhc-ascii"),
            bits.toString().getBytes(StandardCharsets.US_ASCII));
        Files.write(Paths.get(prefix + ".mhc-bin"), bytes);
    }

    static Assembler.Result read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (path.toString().endsWith(".mhc-ascii")) {
            String source = new String(bytes, StandardCharsets.US_ASCII);
            StringBuilder bits = new StringBuilder();
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '0' || c == '1') bits.append(c);
                else if (!Character.isWhitespace(c)) {
                    throw new IllegalArgumentException(
                        "ASCII machine code allows only 0, 1 and whitespace.");
                }
            }
            if (bits.length() % 8 != 0) {
                throw new IllegalArgumentException("Incomplete 8-bit byte in ASCII input.");
            }
            bytes = new byte[bits.length() / 8];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2);
            }
        } else if (!path.toString().endsWith(".mhc-bin")) {
            throw new IllegalArgumentException("Input must end in .mhc-ascii or .mhc-bin.");
        }
        if (bytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Truncated MHC1 header (needs 24 bytes).");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != MAGIC) {
            throw new IllegalArgumentException("Invalid MHC1 magic/version.");
        }
        int textBase = buffer.getInt();
        int dataBase = buffer.getInt();
        int entry = buffer.getInt();
        int textSize = buffer.getInt();
        int dataSize = buffer.getInt();
        if (textBase != Assembler.TEXT_BASE || dataBase != Assembler.DATA_BASE) {
            throw new IllegalArgumentException("Unsupported segment base address.");
        }
        if (textSize <= 0 || textSize % 4 != 0 || dataSize < 0
                || (long) HEADER_SIZE + textSize + dataSize != bytes.length
                || (long) textBase + textSize > dataBase) {
            throw new IllegalArgumentException("Invalid segment lengths or file size.");
        }
        if (entry < textBase || (long) entry >= (long) textBase + textSize
                || (entry - textBase) % 4 != 0) {
            throw new IllegalArgumentException("Invalid entry address.");
        }
        byte[] text = Arrays.copyOfRange(bytes, HEADER_SIZE, HEADER_SIZE + textSize);
        byte[] data = Arrays.copyOfRange(bytes, HEADER_SIZE + textSize, bytes.length);
        return new Assembler.Result(text, data, entry);
    }
}
