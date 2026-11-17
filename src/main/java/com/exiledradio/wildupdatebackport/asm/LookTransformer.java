package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches P1 to P3 ({@code docs/PATCHES.md}), stream Lf: three 1.19.4 accessibility options that act inside render
 * code no Forge event reaches. Each passes one vanilla value through a hook: a static call taking the value and
 * returning it scaled, with no jump, no new local and no new frame.
 *
 * <ul>
 *   <li><b>P1</b>, {@code EntityRenderer.hurtCameraEffect}: its one {@code 14.0F} (the tilt in degrees) becomes
 *       {@code LookHooks.damageTilt(14.0F)} (Damage Tilt).</li>
 *   <li><b>P2</b>, {@code RenderItem.renderEffect}: each of its two {@code Minecraft.getSystemTime()} results passes
 *       through {@code LookHooks.glintTime}, and each of its two {@code -8372020} colors through
 *       {@code LookHooks.glintColor} (Glint Speed and Strength on items).</li>
 *   <li><b>P3</b>, {@code LayerArmorBase.renderEnchantedGlint} (armor and elytra): its clock
 *       {@code ticksExisted + partialTicks} passes through {@code LookHooks.glintTicks} before it is stored, and the
 *       glint color's three constants {@code 0.38F, 0.19F, 0.608F} through {@code LookHooks.glintComponent}.</li>
 * </ul>
 * A target of another shape is left alone (logged), so vanilla's behavior stays.
 */
public final class LookTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/wildupdatebackport/lookfeel/LookHooks";
    static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    static final String ITEM = "net.minecraft.client.renderer.RenderItem";
    static final String ARMOR = "net.minecraft.client.renderer.entity.layers.LayerArmorBase";

    /** The targets (all client-only), for {@code PatchCheck}. */
    public static final String[] TARGETS = {RENDERER, ITEM, ARMOR};

    /** Set once each class has been patched; the client self-test reads them. */
    public static volatile boolean appliedP1;
    public static volatile boolean appliedP2;
    public static volatile boolean appliedP3;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (RENDERER.equals(transformedName)) {
            return damageTilt(basicClass);
        }
        if (ITEM.equals(transformedName)) {
            return itemGlint(basicClass);
        }
        if (ARMOR.equals(transformedName)) {
            return armorGlint(basicClass);
        }
        return basicClass;
    }

    private static byte[] damageTilt(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "hurtCameraEffect", "func_78482_e", "(F)V");
        List<AbstractInsnNode> tilt = m == null ? null : floats(m, 14.0F);
        if (tilt == null || tilt.size() != 1) {
            Asm.LOG.warn("P1 damage tilt: {}.hurtCameraEffect is not of the expected shape ({}); patch skipped, vanilla behavior kept.",
                    RENDERER, m == null ? "not found" : tilt.size() + " constants 14.0");
            return bytes;
        }
        m.instructions.insert(tilt.get(0), hook("damageTilt", "(F)F"));
        appliedP1 = true;
        Asm.LOG.info("P1 damage tilt: patched {}.", RENDERER);
        return Asm.write(node);
    }

    private static byte[] itemGlint(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "renderEffect", "func_191966_a", "(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V");
        List<AbstractInsnNode> clocks = new ArrayList<AbstractInsnNode>();
        List<AbstractInsnNode> colors = new ArrayList<AbstractInsnNode>();
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && i.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode c = (MethodInsnNode) i;
                    if ("net/minecraft/client/Minecraft".equals(c.owner) && "()J".equals(c.desc)
                            && ("getSystemTime".equals(c.name) || "func_71386_F".equals(c.name))) {
                        clocks.add(c);
                    }
                } else if (i instanceof LdcInsnNode && Integer.valueOf(-8372020).equals(((LdcInsnNode) i).cst)) {
                    colors.add(i);
                }
            }
        }
        if (m == null || clocks.size() != 2 || colors.size() != 2) {
            Asm.LOG.warn("P2 item glint: {}.renderEffect is not of the expected shape ({} clocks, {} colors); patch skipped, "
                    + "vanilla behavior kept.", ITEM, clocks.size(), colors.size());
            return bytes;
        }
        for (AbstractInsnNode c : clocks) {
            m.instructions.insert(c, hook("glintTime", "(J)J"));
        }
        for (AbstractInsnNode c : colors) {
            m.instructions.insert(c, hook("glintColor", "(I)I"));
        }
        appliedP2 = true;
        Asm.LOG.info("P2 item glint: patched {}.", ITEM);
        return Asm.write(node);
    }

    private static byte[] armorGlint(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "renderEnchantedGlint", "func_188364_a",
                "(Lnet/minecraft/client/renderer/entity/RenderLivingBase;Lnet/minecraft/entity/EntityLivingBase;"
                        + "Lnet/minecraft/client/model/ModelBase;FFFFFFF)V");
        if (m == null) {
            Asm.LOG.warn("P3 armor glint: {}.renderEnchantedGlint not found; patch skipped, vanilla behavior kept.", ARMOR);
            return bytes;
        }
        // The clock: the first FSTORE, which must follow the read of ticksExisted.
        AbstractInsnNode store = null;
        boolean ticks = false;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof FieldInsnNode && i.getOpcode() == Opcodes.GETFIELD
                    && ("ticksExisted".equals(((FieldInsnNode) i).name) || "field_70173_aa".equals(((FieldInsnNode) i).name))) {
                ticks = true;
            }
            if (i.getOpcode() == Opcodes.FSTORE) {
                store = i;
                break;
            }
        }
        List<AbstractInsnNode> r = floats(m, 0.38F);
        List<AbstractInsnNode> g = floats(m, 0.19F);
        List<AbstractInsnNode> b = floats(m, 0.608F);
        if (!ticks || store == null || r.size() != 1 || g.size() != 1 || b.size() != 1) {
            Asm.LOG.warn("P3 armor glint: {}.renderEnchantedGlint is not of the expected shape; patch skipped, vanilla behavior kept.", ARMOR);
            return bytes;
        }
        m.instructions.insertBefore(store, hook("glintTicks", "(F)F"));
        for (AbstractInsnNode c : new AbstractInsnNode[] {r.get(0), g.get(0), b.get(0)}) {
            m.instructions.insert(c, hook("glintComponent", "(F)F"));
        }
        appliedP3 = true;
        Asm.LOG.info("P3 armor glint: patched {}.", ARMOR);
        return Asm.write(node);
    }

    private static List<AbstractInsnNode> floats(MethodNode m, float value) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode && Float.valueOf(value).equals(((LdcInsnNode) i).cst)) {
                out.add(i);
            }
        }
        return out;
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    /** For the self-test: runs the patch for one class on raw bytes without marking it applied. */
    public static byte[] patchForTest(String transformedName, byte[] bytes) {
        boolean p1 = appliedP1;
        boolean p2 = appliedP2;
        boolean p3 = appliedP3;
        byte[] out = RENDERER.equals(transformedName) ? damageTilt(bytes)
                : ITEM.equals(transformedName) ? itemGlint(bytes) : armorGlint(bytes);
        appliedP1 = p1;
        appliedP2 = p2;
        appliedP3 = p3;
        return out;
    }
}
