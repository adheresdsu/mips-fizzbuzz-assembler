import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

// Reconstructs real instructions and exact .byte data from either MHC format.
public class Disassembler {
    private static final String[] REG = {
        "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
        "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
        "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
        "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"
    };

    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                throw new IllegalArgumentException(
                    "Usage: java Disassembler input.mhc-ascii|input.mhc-bin output.asm");
            }
            Assembler.Result original = MhcFile.read(Paths.get(args[0]));
            String source = disassemble(original);
            // Reject encodings with reserved fields or other bits we cannot preserve.
            Assembler.Result rebuilt = Assembler.assemble(source);
            if (!Arrays.equals(MhcFile.pack(original), MhcFile.pack(rebuilt))) {
                throw new IllegalArgumentException(
                    "Noncanonical encoding: reconstruction would change machine bytes.");
            }
            Files.write(Paths.get(args[1]), source.getBytes(StandardCharsets.UTF_8));
            System.out.println("Created " + args[1] + "; round-trip bytes verified.");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Disassembly failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static String disassemble(Assembler.Result result) {
        StringBuilder out = new StringBuilder();
        out.append("# Reconstructed from MHC1. Delayed branching must be OFF.\n");
        out.append(".data 0x10010000\n");
        for (int i = 0; i < result.data.length; i += 16) {
            out.append("    .byte ");
            for (int j = i; j < Math.min(i + 16, result.data.length); j++) {
                if (j > i) out.append(", ");
                out.append(result.data[j] & 255);
            }
            out.append('\n');
        }
        out.append("\n.text 0x00400000\n.globl main\n");
        ByteBuffer words = ByteBuffer.wrap(result.text).order(ByteOrder.BIG_ENDIAN);
        for (int offset = 0; offset < result.text.length; offset += 4) {
            int pc = Assembler.TEXT_BASE + offset;
            int word = words.getInt();
            out.append(label(pc, result)).append(":\n    ");
            try {
                out.append(decode(word, pc, result)).append('\n');
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format(
                    "At 0x%08X (word 0x%08X): %s", pc, word, e.getMessage()));
            }
        }
        // A label immediately after the last instruction is a legal branch target.
        out.append(label(Assembler.TEXT_BASE + result.text.length, result)).append(":\n");
        return out.toString();
    }

    private static String label(int address, Assembler.Result result) {
        if (address == result.entry) return "main";
        return String.format("L%08X", address);
    }

    private static String target(int address, Assembler.Result result) {
        long end = (long) Assembler.TEXT_BASE + result.text.length;
        if (address < Assembler.TEXT_BASE || address > end || address % 4 != 0) {
            throw new IllegalArgumentException("Control-flow target is outside .text.");
        }
        return label(address, result);
    }

    private static String decode(int word, int pc, Assembler.Result result) {
        int op = word >>> 26;
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int shift = (word >>> 6) & 31;
        int function = word & 63;
        int signed = (short) (word & 65535);
        int unsigned = word & 65535;
        if (op == 0) {
            switch (function) {
                case 0: return "sll " + REG[rd] + ", " + REG[rt] + ", " + shift;
                case 8: return "jr " + REG[rs];
                case 12: return "syscall";
                case 16: return "mfhi " + REG[rd];
                case 26: return "div " + REG[rs] + ", " + REG[rt];
                case 32: return "add " + REG[rd] + ", " + REG[rs] + ", " + REG[rt];
                case 33: return "addu " + REG[rd] + ", " + REG[rs] + ", " + REG[rt];
                case 34: return "sub " + REG[rd] + ", " + REG[rs] + ", " + REG[rt];
                default: throw new IllegalArgumentException("Unsupported R function: " + function);
            }
        }
        switch (op) {
            case 2:
            case 3:
                int jump = ((pc + 4) & 0xF0000000) | ((word & 0x03FFFFFF) << 2);
                return (op == 2 ? "j " : "jal ") + target(jump, result);
            case 4:
            case 5:
                int branch = pc + 4 + signed * 4;
                return (op == 4 ? "beq " : "bne ") + REG[rs] + ", " + REG[rt]
                    + ", " + target(branch, result);
            case 8:
            case 9:
                return (op == 8 ? "addi " : "addiu ") + REG[rt] + ", " + REG[rs]
                    + ", " + signed;
            case 13: return "ori " + REG[rt] + ", " + REG[rs] + ", " + unsigned;
            case 15: return "lui " + REG[rt] + ", " + unsigned;
            case 35:
            case 43:
                return (op == 35 ? "lw " : "sw ") + REG[rt] + ", " + signed
                    + "(" + REG[rs] + ")";
            default: throw new IllegalArgumentException("Unsupported opcode: " + op);
        }
    }
}
