package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patch P9 ({@code docs/PATCHES.md}), stream Sw: in {@code GenLayerShore.getInts}, the swamp is one of the biomes
 * that get no beach where they meet the ocean ({@code k != Biome.getIdForBiome(Biomes.SWAMPLAND)}). The biome id
 * {@code k} read for that one comparison passes through {@code SwampHooks.shoreBiome}, which answers the swamp's id
 * for the mangrove swamp, so it gets no beach either. One static call between a value and its use: no jump, no new
 * local, no new frame. A target of another shape is left alone (logged).
 */
public final class SwampTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/wildupdatebackport/swamp/SwampHooks";
    static final String SHORE = "net.minecraft.world.gen.layer.GenLayerShore";

    /** The target, for {@code PatchCheck}. */
    public static final String[] TARGETS = {SHORE};

    /** Set once the class has been patched; the self-test reads it. */
    public static volatile boolean appliedP9;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !SHORE.equals(transformedName)) {
            return basicClass;
        }
        return shore(basicClass);
    }

    /** Public for the self-test, which runs it on the MCP and SRG forms of the class. @return null if not patched */
    public static byte[] patch(byte[] bytes) {
        byte[] out = shore(bytes);
        return out == bytes ? null : out;
    }

    private static byte[] shore(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "getInts", "func_75904_a", "(IIII)[I");
        VarInsnNode load = null;
        int found = 0;
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (!(i instanceof FieldInsnNode) || i.getOpcode() != Opcodes.GETSTATIC) {
                    continue;
                }
                FieldInsnNode f = (FieldInsnNode) i;
                if (!"net/minecraft/init/Biomes".equals(f.owner) || !("SWAMPLAND".equals(f.name) || "field_76780_h".equals(f.name))) {
                    continue;
                }
                AbstractInsnNode prev = f.getPrevious();
                AbstractInsnNode next = f.getNext();
                AbstractInsnNode jump = next == null ? null : next.getNext();
                boolean shape = prev instanceof VarInsnNode && prev.getOpcode() == Opcodes.ILOAD
                        && next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKESTATIC
                        && ("getIdForBiome".equals(((MethodInsnNode) next).name) || "func_185362_a".equals(((MethodInsnNode) next).name))
                        && jump != null && (jump.getOpcode() == Opcodes.IF_ICMPEQ || jump.getOpcode() == Opcodes.IF_ICMPNE);
                found++;
                if (shape) {
                    load = (VarInsnNode) prev;
                }
            }
        }
        if (m == null || found != 1 || load == null) {
            Asm.LOG.warn("P9 mangrove swamp shore: {}.getInts is not of the expected shape ({}); patch skipped, vanilla behavior kept.",
                    SHORE, m == null ? "not found" : found + " swamp comparisons");
            return bytes;
        }
        m.instructions.insert(load, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "shoreBiome", "(I)I", false));
        appliedP9 = true;
        Asm.LOG.info("P9 mangrove swamp shore: patched {}.", SHORE);
        return Asm.write(node);
    }
}
