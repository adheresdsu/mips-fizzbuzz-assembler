"""Run from this folder: python3 test_project.py --mars /path/to/Mars4_5.jar"""
import argparse
import hashlib
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mars', required=True, help='Path to the MARS 4.5 jar')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    mars = Path(args.mars).resolve()
    if not mars.is_file():
        parser.error('MARS jar does not exist')
    evidence = root / 'evidence'
    evidence.mkdir(exist_ok=True)
    log = ['MIPS assembler/disassembler verification', '']

    def run(command, expected_code=0):
        result = subprocess.run(command, cwd=root, capture_output=True,
                                text=True, timeout=60)
        assert result.returncode == expected_code, (
            command, result.returncode, result.stdout, result.stderr)
        return result

    def passed(message):
        log.append('PASS: ' + message)

    compiler = [shutil.which('javac')] if shutil.which('javac') else [
        'java', '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    run(compiler + ['Assembler.java', 'MhcFile.java', 'Disassembler.java'])
    passed('All three Java source files compile.')
    log.append('Compiler command: ' + ' '.join(compiler))
    log.append(run(['java', '-version']).stderr.splitlines()[0])
    log.append(run(['java', 'Assembler', 'fizzbuzz.asm']).stdout.strip())
    binary = (root / 'fizzbuzz.mhc-bin').read_bytes()
    ascii_text = (root / 'fizzbuzz.mhc-ascii').read_text()
    bits = ''.join(ascii_text.split())
    decoded = bytes(int(bits[i:i + 8], 2) for i in range(0, len(bits), 8))
    assert decoded == binary
    passed('ASCII bits decode to exactly the binary file bytes.')
    header = struct.unpack('>6I', binary[:24])
    assert header == (0x4D484331, 0x00400000, 0x10010000, 0x00400000, 180, 10)
    assert binary[-10:] == b'Fizz\x00Buzz\x00'
    passed('Header, entry address, 45 instructions and 10 data bytes are correct.')

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        for kind, filename in [('ascii', 'reconstructed_ascii.asm'),
                               ('bin', 'reconstructed.asm')]:
            log.append(run(['java', 'Disassembler', 'fizzbuzz.mhc-' + kind,
                            filename]).stdout.strip())
            prefix = tmp / ('roundtrip_' + kind)
            run(['java', 'Assembler', filename, str(prefix)])
            assert Path(str(prefix) + '.mhc-bin').read_bytes() == binary
            assert Path(str(prefix) + '.mhc-ascii').read_text() == ascii_text
            passed(kind.upper() + ' reconstruction reassembles to identical files.')
        assert (root / 'reconstructed.asm').read_bytes() == (
            root / 'reconstructed_ascii.asm').read_bytes()

        hand = tmp / 'hand.asm'
        hand.write_text('.text\naddi $t0, $zero, 3\naddi $t1, $zero, 5\n'
                        'add $t2, $t0, $t1\nsub $t3, $t1, $t0\n')
        run(['java', 'Assembler', str(hand), str(tmp / 'hand')])
        expected_words = bytes.fromhex('20080003 20090005 01095020 01285822')
        assert (tmp / 'hand.mhc-bin').read_bytes()[24:] == expected_words
        passed('Part 1 encodings match all four independently calculated words.')

        # Cover immediate expansion boundaries and exact data preservation.
        feature = tmp / 'features.asm'
        feature.write_text('.data\ns: .asciiz "A#B:C\\n"\n.byte 0, 255, -128\n'
                           '.text\n.globl main\nmain:\n'
                           'li $t0, -32768\nli $t1, 32767\nli $t2, 32768\n'
                           'li $t3, 65535\nli $t4, 65536\nli $t5, -32769\n'
                           'la $a0, s\nnop\nbeq $zero, $zero, done\n'
                           'j main\ndone:\nli $v0, 10\nsyscall\n')
        feature_prefix = tmp / 'features'
        run(['java', 'Assembler', str(feature), str(feature_prefix)])
        rebuilt = tmp / 'features_rebuilt.asm'
        run(['java', 'Disassembler', str(feature_prefix) + '.mhc-bin', str(rebuilt)])
        passed('Immediate boundaries, labels, escaped strings and .byte round-trip.')

        invalid_source = {
            'unsupported instruction': 'mul $t0, $t1, $t2',
            'unknown register': 'addi $bad, $zero, 1',
            'undefined label': 'j missing',
            'immediate range': 'addi $t0, $zero, 32768',
            'operand count': 'add $t0, $t1',
            'duplicate label': 'x: nop\nx: nop',
            'zero remainder divisor': 'rem $t0, $t1, 0',
            'reserved $at in rem': 'rem $t0, $at, 3',
            'unsupported directive': '.word 1',
            'unclosed quote': "li $a0, '\\n",
            'data branch target': '.data\nx: .asciiz "x"\n.text\nj x',
        }
        for name, source in invalid_source.items():
            path = tmp / 'bad.asm'
            path.write_text('.text\n' + source + '\n')
            result = run(['java', 'Assembler', str(path), str(tmp / 'bad')], 1)
            assert 'Assembly failed: Line ' in result.stderr
            passed('Rejects ' + name + ': ' + result.stderr.strip())

        cases = {
            'truncated header': ('bin', binary[:10]),
            'bad magic': ('bin', b'BAD!' + binary[4:]),
            'truncated payload': ('bin', binary[:-1]),
            'unknown opcode': ('bin', binary[:24] + b'\xFC\0\0\0' + binary[28:]),
            'reserved encoding bits': ('bin', binary[:24] + b'\x00\x20\x00\x00'
                                       + binary[28:]),
            'nonbinary ASCII': ('ascii', b'010100X1'),
            'partial byte': ('ascii', b'0101'),
        }
        for name, (kind, content) in cases.items():
            path = tmp / ('bad.mhc-' + kind)
            path.write_bytes(content)
            result = run(['java', 'Disassembler', str(path), str(tmp / 'bad-out.asm')], 1)
            assert 'Disassembly failed:' in result.stderr
            passed('Rejects ' + name + ': ' + result.stderr.strip())

        expected = ''.join(('FizzBuzz' if n % 15 == 0 else 'Fizz' if n % 3 == 0
                            else 'Buzz' if n % 5 == 0 else str(n)) + '\n'
                           for n in range(1, 101))
        (evidence / 'expected_output.txt').write_text(expected)
        for source, output in [('fizzbuzz.asm', 'original_output.txt'),
                               ('reconstructed.asm', 'reconstructed_output.txt'),
                               ('reconstructed_ascii.asm', 'reconstructed_ascii_output.txt')]:
            command = ['java', '-jar', str(mars), 'nc', 'me', 'ae1', 'se2',
                       'sm', '100000', source]
            result = run(command)
            assert result.stdout == expected, (source, result.stdout, result.stderr)
            (evidence / output).write_text(result.stdout)
            passed(source + ': MARS prints all 100 expected lines exactly.')
        dump = tmp / 'mars_hex.txt'
        run(['java', '-jar', str(mars), 'nc', 'me', 'ae1', 'a', 'dump', '.text',
             'HexText', str(dump), 'reconstructed.asm'])
        assert bytes.fromhex(dump.read_text()) == binary[24:204]
        passed('MARS independently assembles reconstructed .text to our 180 text bytes.')

    log.extend(['', 'File sizes:',
                'fizzbuzz.mhc-bin: ' + str(len(binary)) + ' bytes',
                'fizzbuzz.mhc-ascii: ' + str(len(ascii_text.encode('ascii'))) + ' bytes',
                'SHA-256 fizzbuzz.mhc-bin:', hashlib.sha256(binary).hexdigest(),
                'SHA-256 original/reconstructed/expected output:',
                hashlib.sha256(expected.encode()).hexdigest(),
                '', 'MARS uses its default settings: delayed branching OFF.',
                'All checks passed.'])
    report = '\n'.join(log) + '\n'
    (evidence / 'test_results.txt').write_text(report)
    (evidence / 'binary_hex.txt').write_text('\n'.join(
        f'{i:04X}: ' + ' '.join(f'{b:02X}' for b in binary[i:i + 16])
        for i in range(0, len(binary), 16)) + '\n')
    print(report)


if __name__ == '__main__':
    main()
