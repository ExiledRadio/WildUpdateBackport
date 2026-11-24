package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patches P19 and P20 ({@code docs/PATCHES.md}), stream DD: no springs and no lakes whose liquid could reach an
 * ancient city ({@code CityLiquids}), as 26.3's deep dark has none.
 *
 * <ul>
 *   <li>P19: {@code WorldGenLiquids.generate}'s first {@code worldIn.getBlockState(position.up())} becomes
 *       {@code CityLiquids.springBlock(worldIn, position.up())}: air near a city, so the spring finds no stone over it
 *       and is not placed.</li>
 *   <li>P20: {@code WorldGenLakes.generate}'s {@code position.getY()} in {@code if (position.getY() <= 4) return false}
 *       passes through {@code CityLiquids.lakeHeight(y, position, worldIn)}: 0 near a city. Both locals are read by
 *       vanilla after that point.</li>
 * </ul>
 *
 * <p>Each is one call between a value and its use: no jump, no new local, no new frame. A target of another shape is
 * left alone (logged).
 */
public final class DeepDarkTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/wildupdatebackport/deepdark/city/CityLiquids";
    static final String LIQUIDS = "net.minecraft.world.gen.feature.WorldGenLiquids";
    static final String LAKES = "net.minecraft.world.gen.feature.WorldGenLakes";
    private static final String GENERATE_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/math/BlockPos;)Z";

    /** The targets, for {@code PatchCheck}. */
    public static final String[] TARGETS = {LIQUIDS, LAKES};

    /** Set once each class has been patched; the self-test reads them. */
    public static volatile boolean appliedP19, appliedP20;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if (LIQUIDS.equals(transformedName)) {
            byte[] out = springs(basicClass);
            appliedP19 |= out != basicClass;
            return out;
        }
        if (LAKES.equals(transformedName)) {
            byte[] out = lakes(basicClass);
            appliedP20 |= out != basicClass;
            return out;
        }
        return basicClass;
    }

    /** Public for the self-test, which runs it on the MCP and SRG forms of the class. @return null if not patched */
    public static byte[] patchSprings(byte[] bytes) {
        byte[] out = springs(bytes);
        return out == bytes ? null : out;
    }

    /** Public for the self-test. @return null if not patched */
    public static byte[] patchLakes(byte[] bytes) {
        byte[] out = lakes(bytes);
        return out == bytes ? null : out;
    }

    private static byte[] springs(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "generate", "func_180709_b", GENERATE_DESC);
        MethodInsnNode site = null;
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && i.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "net/minecraft/world/World".equals(((MethodInsnNode) i).owner)
                        && isName((MethodInsnNode) i, "getBlockState", "func_180495_p")
                        && "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;".equals(((MethodInsnNode) i).desc)) {
                    AbstractInsnNode prev = i.getPrevious();
                    if (prev instanceof MethodInsnNode && isName((MethodInsnNode) prev, "up", "func_177984_a")) {
                        site = (MethodInsnNode) i;
                    }
                    break;
                }
            }
        }
        if (site == null) {
            Asm.LOG.warn("P19 springs near ancient cities: {}.generate is not of the expected shape; patch skipped, vanilla behavior kept.", LIQUIDS);
            return bytes;
        }
        m.instructions.set(site, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "springBlock",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;", false));
        Asm.LOG.info("P19 springs near ancient cities: patched {}.", LIQUIDS);
        return Asm.write(node);
    }

    private static byte[] lakes(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "generate", "func_180709_b", GENERATE_DESC);
        MethodInsnNode site = null;
        int found = 0;
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (!(i instanceof MethodInsnNode) || i.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !"net/minecraft/util/math/BlockPos".equals(((MethodInsnNode) i).owner)
                        || !isName((MethodInsnNode) i, "getY", "func_177956_o")) {
                    continue;
                }
                AbstractInsnNode prev = i.getPrevious();
                AbstractInsnNode four = i.getNext();
                AbstractInsnNode jump = four == null ? null : four.getNext();
                if (prev instanceof VarInsnNode && prev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev).var == 3
                        && four != null && four.getOpcode() == Opcodes.ICONST_4
                        && jump instanceof JumpInsnNode && (jump.getOpcode() == Opcodes.IF_ICMPGT || jump.getOpcode() == Opcodes.IF_ICMPLE)) {
                    found++;
                    site = (MethodInsnNode) i;
                }
            }
        }
        if (found != 1) {
            Asm.LOG.warn("P20 lakes near ancient cities: {}.generate is not of the expected shape ({} height tests); patch skipped, vanilla behavior kept.",
                    LAKES, found);
            return bytes;
        }
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 3));
        call.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "lakeHeight",
                "(ILnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/World;)I", false));
        m.instructions.insert(site, call);
        Asm.LOG.info("P20 lakes near ancient cities: patched {}.", LAKES);
        return Asm.write(node);
    }

    private static boolean isName(MethodInsnNode m, String mcp, String srg) {
        return mcp.equals(m.name) || srg.equals(m.name);
    }
}
