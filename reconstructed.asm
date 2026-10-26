# Reconstructed from MHC1. Delayed branching must be OFF.
.data 0x10010000
    .byte 70, 105, 122, 122, 0, 66, 117, 122, 122, 0

.text 0x00400000
.globl main
main:
    addiu $t1, $zero, 1
L00400004:
    addiu $t2, $zero, 101
L00400008:
    jal L0040001C
L0040000C:
    jal L0040009C
L00400010:
    addi $t1, $t1, 1
L00400014:
    bne $t1, $t2, L00400008
L00400018:
    jal L004000AC
L0040001C:
    addi $sp, $sp, -8
L00400020:
    sw $ra, 0($sp)
L00400024:
    addiu $at, $zero, 3
L00400028:
    div $t1, $at
L0040002C:
    mfhi $t3
L00400030:
    addiu $at, $zero, 5
L00400034:
    div $t1, $at
L00400038:
    mfhi $t4
L0040003C:
    bne $t3, $zero, L00400044
L00400040:
    jal L00400064
L00400044:
    bne $t4, $zero, L00400050
L00400048:
    jal L00400078
L0040004C:
    j L00400058
L00400050:
    beq $t3, $zero, L00400058
L00400054:
    jal L0040008C
L00400058:
    lw $ra, 0($sp)
L0040005C:
    addi $sp, $sp, 8
L00400060:
    jr $ra
L00400064:
    addiu $v0, $zero, 4
L00400068:
    lui $a0, 4097
L0040006C:
    ori $a0, $a0, 0
L00400070:
    syscall
L00400074:
    jr $ra
L00400078:
    addiu $v0, $zero, 4
L0040007C:
    lui $a0, 4097
L00400080:
    ori $a0, $a0, 5
L00400084:
    syscall
L00400088:
    jr $ra
L0040008C:
    addiu $v0, $zero, 1
L00400090:
    addu $a0, $t1, $zero
L00400094:
    syscall
L00400098:
    jr $ra
L0040009C:
    addiu $v0, $zero, 11
L004000A0:
    addiu $a0, $zero, 10
L004000A4:
    syscall
L004000A8:
    jr $ra
L004000AC:
    addiu $v0, $zero, 10
L004000B0:
    syscall
L004000B4:
