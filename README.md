# MIPS FizzBuzz assembler and disassembler

This project uses the original FizzBuzz program to test conversion between MIPS
assembly and machine code. The Java assembler reads a source file and creates
both required machine code formats. The disassembler reads either format and
reconstructs assembly that prints the same output.

## Files

- Assembler.java: source parser, pseudoinstruction expansion and instruction encoding.
- MhcFile.java: shared file format reader and writer. Compile it with both programs.
- Disassembler.java: instruction decoding and assembly reconstruction.
- fizzbuzz.asm: original uploaded FizzBuzz source, preserved without changes.
- fizzbuzz.mhc-ascii: complete file represented by readable binary digits.
- fizzbuzz.mhc-bin: the same contents stored as actual bytes.
- reconstructed.asm: assembly recovered from the binary file.
- reconstructed_ascii.asm: assembly recovered from the ASCII file; identical contents.
- test_project.py: repeatable tests, including execution in MARS.
- evidence/: test results, exact program output and a hex dump of the binary file.

## Compile and run

Install a Java Development Kit (JDK 8 or newer). Open a terminal in this folder.

```sh
javac Assembler.java MhcFile.java Disassembler.java
java Assembler fizzbuzz.asm
java Disassembler fizzbuzz.mhc-bin reconstructed.asm
java Disassembler fizzbuzz.mhc-ascii reconstructed_ascii.asm
java Assembler reconstructed.asm roundtrip_bin
java Assembler reconstructed_ascii.asm roundtrip_ascii
```

The assembler accepts an optional output prefix. Without one, it uses the input
path with its final extension removed. It writes prefix.mhc-ascii and prefix.mhc-bin.
The disassembler requires an input file and an output assembly filename.
Do not give it the original source filename as its output unless you intend to
overwrite that file.

Each disassembly automatically reassembles its reconstructed source in memory and
checks that the entire file would remain identical. To compare the saved files on
macOS or Linux, run:

```sh
cmp fizzbuzz.mhc-bin roundtrip_bin.mhc-bin
cmp fizzbuzz.mhc-bin roundtrip_ascii.mhc-bin
cmp fizzbuzz.mhc-ascii roundtrip_bin.mhc-ascii
```

No output from cmp means the files match. The Python test below also performs
these comparisons and works on Windows.

## Check execution in MARS

Use MARS 4.5 with its default memory configuration and delayed branching OFF.
Open fizzbuzz.asm, assemble it and run it. Then do the same for reconstructed.asm.
Each program should print the correct FizzBuzz results for 1 through 100.
MARS assembles the .asm files; it does not directly load the custom MHC1 files.

For command line execution, replace the jar path with its location on your machine:

```sh
java -jar /path/to/Mars4_5.jar nc me ae1 se2 sm 100000 fizzbuzz.asm
java -jar /path/to/Mars4_5.jar nc me ae1 se2 sm 100000 reconstructed.asm
```

The step limit stops an accidental infinite loop. These commands leave delayed
branching disabled. The automated test compares all output with an independently
generated FizzBuzz sequence, so reaching the limit cannot count as a passing run.

With Python 3 installed, run all checks:

```sh
python3 test_project.py --mars /path/to/Mars4_5.jar
```

The tests compile the Java files, check known instruction words, exercise both
formats, reject invalid input and run the original and both reconstructed programs
in MARS. They also compare MARS's encoding of the reconstructed text with the
assembler's text bytes. Results are saved in evidence/test_results.txt.
The development environment used OpenJDK 17.0.20. Its compiler was invoked through
`java -m jdk.compiler/com.sun.tools.javac.Main` because no javac launcher was on PATH.
The test script selects that equivalent fallback only when javac is unavailable.

## Supported features

Real instructions: addi, addiu, add, addu, sub, ori, lui, div, mfhi, sll,
beq, bne, j, jal, jr, lw, sw and syscall.

Pseudoinstructions: li, la, move, rem and nop.

Directives: .text, .data, .asciiz, .byte, .globl and .global.
Symbolic labels, register aliases, numeric registers and # comments are supported.
Labels are case sensitive. Instruction names and register names are not.
Decimal, hexadecimal, binary and ASCII character immediates are accepted.

Pass 1 expands pseudoinstructions before assigning following label addresses.
Pass 2 resolves the labels and encodes the real instructions.
A small li uses addiu or ori; a large li uses lui followed by ori.
la always uses lui and ori. move uses addu. nop uses sll $zero, $zero, 0.
rem with an immediate divisor loads $at, uses div and reads the remainder with mfhi.
Branches use (target - (PC + 4)) / 4. Jumps store the target address shifted right
by two bits in the 26 bit target field.

The disassembler generates address labels and uses .byte for data so every data
byte survives. It outputs real instructions instead of trying to recover the
original pseudoinstructions. Original comments and label names are not recovered.

## File layout and byte order

Both files use the project's MHC1 container. This is a documented project format,
not ELF and not a standard MARS executable. Integers and instruction words use
big endian order: the most significant byte comes first.

The first 24 bytes contain six unsigned 32 bit values:

| Byte offset | Field | This program |
| --- | --- | --- |
| 0 | Magic/version | 0x4D484331, the ASCII bytes MHC1 |
| 4 | Text start address | 0x00400000 |
| 8 | Data start address | 0x10010000 |
| 12 | Entry address | 0x00400000 |
| 16 | Text length in bytes | 180 |
| 20 | Data length in bytes | 10 |

The text payload follows at file offset 24. The data payload follows at offset 204.
File offsets differ from simulated memory addresses. No large gap or padding is
stored between the two segments. The entry is the main label if present; otherwise
it is the first instruction. Reconstruction names that entry main.

The binary file is 214 bytes: 24 header bytes, 180 instruction bytes and 10 data bytes.
The data is exactly `46 69 7A 7A 00 42 75 7A 7A 00` in hexadecimal.
These are the two strings Fizz and Buzz, each with a null terminator.

The ASCII file contains only binary digits and line breaks. The first six lines
encode the header, the next 45 lines encode the instructions and the last ten
lines encode individual data bytes. It has 1,712 binary digits and 61 LF newline
bytes, for a total of 1,773 bytes. Removing whitespace and converting each eight
digits to a byte reproduces the binary file exactly.

The header preserves the text/data boundary, lengths, base addresses and entry
point. Without that information, arbitrary data bytes could be mistaken for
instructions.

## Scope and errors

This is a subset assembler for the supplied program, not the entire MIPS instruction
set. It uses fixed text and data base addresses. .asciiz accepts one ASCII string
per directive. .byte accepts comma separated values from -128 through 255.
rem accepts a nonzero immediate divisor, reserves $at and omits MARS's additional
runtime division overflow checks. No delay slots are inserted; run with delayed
branching OFF. Pseudoinstruction expansion may differ from MARS's expansion while
producing the same FizzBuzz output.

Unsupported instructions/directives, invalid registers, duplicate or undefined
labels, invalid immediates and malformed operands produce errors with source line
numbers. Invalid headers, incomplete files, unsupported machine words and
nonbinary ASCII input are rejected by the disassembler. Noncanonical encodings
whose bits cannot be preserved are rejected too. Errors use exit status 1.

## Sources and collaboration

Starting files supplied for this assignment: fizzbuzz (2).asm and Assembler (15).java.
The original FizzBuzz source is unchanged. The Java assembler was adapted from the
supplied assembler. ChatGPT assisted with the file input/output changes, the
disassembler, testing and PDF preparation.

MIPS Technologies, MIPS32 Instruction Set, Revision 3.00:
https://pages.hmc.edu/harris/class/e155/11/MIPS32InstructionSet.pdf

MARS command line documentation:
https://dpetersanderson.github.io/Help/MarsHelpCommand.html

MARS official source and releases (the simulator jar is not bundled):
https://github.com/dpetersanderson/MARS
