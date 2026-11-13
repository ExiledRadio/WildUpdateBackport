package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches P4 to P7 ({@code docs/PATCHES.md}), stream C: four 1.19.x changes inside vanilla methods that no Forge event
 * reaches. Each puts a static call to {@code changes.ChangesHooks} between a vanilla value and its use (loading
 * {@code this} or a parameter for it, no jump, no new local, no new frame), or, for the jukebox, adds three
 * overrides that call the superclass and pass its result through a hook.
 *
 * <ul>
 *   <li><b>P4</b>, minecarts: {@code EntityMinecart.killMinecart}'s {@code Items.MINECART} passes through
 *       {@code cartItem}; the chest and hopper carts' {@code Item.getItemFromBlock(...)} through {@code cartBlockItem},
 *       the furnace and TNT carts' {@code Blocks.FURNACE}/{@code Blocks.TNT} through {@code cartBlock}.</li>
 *   <li><b>P5</b>, {@code BlockJukebox}: adds {@code canProvidePower}, {@code getWeakPower} and Forge's
 *       {@code isSideSolid}, each the superclass's result through a hook.</li>
 *   <li><b>P6</b>, {@code ItemPotion.hasEffect} (client): each returned value through {@code potionGlint}.</li>
 *   <li><b>P7</b>, {@code BlockShulkerBox.getBlockFaceShape}: each returned value through {@code shulkerFace}.</li>
 * </ul>
 * A target of another shape is left alone (logged), so vanilla's behavior stays.
 */
public final class ChangesTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/wildupdatebackport/changes/ChangesHooks";

    static final String CART = "net.minecraft.entity.item.EntityMinecart";
    static final String CHEST_CART = "net.minecraft.entity.item.EntityMinecartChest";
    static final String HOPPER_CART = "net.minecraft.entity.item.EntityMinecartHopper";
    static final String FURNACE_CART = "net.minecraft.entity.item.EntityMinecartFurnace";
    static final String TNT_CART = "net.minecraft.entity.item.EntityMinecartTNT";
    static final String JUKEBOX = "net.minecraft.block.BlockJukebox";
    static final String POTION = "net.minecraft.item.ItemPotion";
    static final String SHULKER = "net.minecraft.block.BlockShulkerBox";

    /** The targets, for the self-test. */
    public static final String[] TARGETS = {CART, CHEST_CART, HOPPER_CART, FURNACE_CART, TNT_CART, JUKEBOX, POTION, SHULKER};

    private static final String KILL_DESC = "(Lnet/minecraft/util/DamageSource;)V";
    private static final String CART_DESC = "Lnet/minecraft/entity/item/EntityMinecart;";

    /** Set once each patch has been applied to the running game's class; the self-test reads them. */
    public static volatile int appliedP4;
    public static volatile boolean appliedP5;
    public static volatile boolean appliedP6;
    public static volatile boolean appliedP7;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        byte[] out = patch(transformedName, basicClass, true);
        return out == null ? basicClass : out;
    }

    /** @return the patched class, or null when the class is not a target or not of the expected shape */
    private static byte[] patch(String transformedName, byte[] bytes, boolean live) {
        if (CART.equals(transformedName)) {
            return cart(bytes, transformedName, "getstatic", "net/minecraft/init/Items", new String[] {"MINECART", "field_151143_au"},
                    "cartItem", "Lnet/minecraft/item/Item;", live);
        }
        if (CHEST_CART.equals(transformedName) || HOPPER_CART.equals(transformedName)) {
            return cart(bytes, transformedName, "invokestatic", "net/minecraft/item/Item", new String[] {"getItemFromBlock", "func_150898_a"},
                    "cartBlockItem", "Lnet/minecraft/item/Item;", live);
        }
        if (FURNACE_CART.equals(transformedName)) {
            return cart(bytes, transformedName, "getstatic", "net/minecraft/init/Blocks", new String[] {"FURNACE", "field_150460_al"},
                    "cartBlock", "Lnet/minecraft/block/Block;", live);
        }
        if (TNT_CART.equals(transformedName)) {
            return cart(bytes, transformedName, "getstatic", "net/minecraft/init/Blocks", new String[] {"TNT", "field_150335_W"},
                    "cartBlock", "Lnet/minecraft/block/Block;", live);
        }
        if (JUKEBOX.equals(transformedName)) {
            return jukebox(bytes, live);
        }
        if (POTION.equals(transformedName)) {
            return potion(bytes, live);
        }
        if (SHULKER.equals(transformedName)) {
            return shulker(bytes, live);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------

    /** P4: the one matching instruction in {@code killMinecart} is followed by {@code ALOAD 0; INVOKESTATIC hook}. */
    private static byte[] cart(byte[] bytes, String target, String kind, String owner, String[] names, String hook, String type,
                               boolean live) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "killMinecart", "func_94095_a", KILL_DESC);
        List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if ("getstatic".equals(kind) && i.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode f = (FieldInsnNode) i;
                    if (owner.equals(f.owner) && (names[0].equals(f.name) || names[1].equals(f.name))) {
                        found.add(i);
                    }
                } else if ("invokestatic".equals(kind) && i.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (owner.equals(c.owner) && (names[0].equals(c.name) || names[1].equals(c.name))) {
                        found.add(i);
                    }
                }
            }
        }
        if (found.size() != 1) {
            Asm.LOG.warn("P4 minecart drops: {}.killMinecart is not of the expected shape ({}); patch skipped, vanilla behavior kept.",
                    target, m == null ? "not found" : found.size() + " matches");
            return null;
        }
        InsnList add = new InsnList();
        add.add(new VarInsnNode(Opcodes.ALOAD, 0));
        add.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, "(" + type + CART_DESC + ")" + type, false));
        m.instructions.insert(found.get(0), add);
        if (live) {
            appliedP4++;
            Asm.LOG.info("P4 minecart drops: patched {}.", target);
        }
        return Asm.write(node);
    }

    /** P5: three overrides on the jukebox, each {@code return hook(super.x(...), ...)}. */
    private static byte[] jukebox(byte[] bytes, boolean live) {
        ClassNode node = Asm.read(bytes);
        // The class arrives MCP-named in the dev environment and SRG-named in a real instance: name the new methods
        // the way the class names its own.
        boolean mcp = Asm.method(node, "onBlockActivated", "onBlockActivated",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;"
                        + "Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Z") != null;
        boolean srg = Asm.method(node, "func_180639_a", "func_180639_a",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;"
                        + "Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Z") != null;
        String power = mcp ? "canProvidePower" : "func_149744_f";
        String weak = mcp ? "getWeakPower" : "func_180656_a";
        String powerDesc = "(Lnet/minecraft/block/state/IBlockState;)Z";
        String weakDesc = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                + "Lnet/minecraft/util/EnumFacing;)I";
        String solidDesc = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                + "Lnet/minecraft/util/EnumFacing;)Z";
        if (mcp == srg || Asm.method(node, power, power, powerDesc) != null || Asm.method(node, weak, weak, weakDesc) != null
                || Asm.method(node, "isSideSolid", "isSideSolid", solidDesc) != null) {
            Asm.LOG.warn("P5 jukebox signal: {} is not of the expected shape (it already has a redstone method); patch skipped, "
                    + "vanilla behavior kept.", JUKEBOX);
            return null;
        }
        String sup = node.superName;

        MethodNode a = new MethodNode(Opcodes.ACC_PUBLIC, power, powerDesc, null, null);
        a.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        a.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        a.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, sup, power, powerDesc, false));
        a.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "jukeboxIsSource", "(Z)Z", false));
        a.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode b = new MethodNode(Opcodes.ACC_PUBLIC, weak, weakDesc, null, null);
        for (int i = 0; i <= 4; i++) {
            b.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        b.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, sup, weak, weakDesc, false));
        b.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        b.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        b.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "jukeboxPower",
                "(ILnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I", false));
        b.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode c = new MethodNode(Opcodes.ACC_PUBLIC, "isSideSolid", solidDesc, null, null);
        for (int i = 0; i <= 4; i++) {
            c.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        c.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, sup, "isSideSolid", solidDesc, false));
        c.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "jukeboxSideSolid", "(Z)Z", false));
        c.instructions.add(new InsnNode(Opcodes.IRETURN));

        node.methods.add(a);
        node.methods.add(b);
        node.methods.add(c);
        if (live) {
            appliedP5 = true;
            Asm.LOG.info("P5 jukebox signal: patched {}.", JUKEBOX);
        }
        return Asm.write(node);
    }

    /** P6: {@code ALOAD 1; INVOKESTATIC potionBegin} first, and {@code INVOKESTATIC potionGlint} before each {@code IRETURN}. */
    private static byte[] potion(byte[] bytes, boolean live) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "hasEffect", "func_77636_d", "(Lnet/minecraft/item/ItemStack;)Z");
        if (m == null) {
            // A dedicated server strips this client-only method before it reaches us: nothing to do there.
            if (live && !server()) {
                Asm.LOG.warn("P6 potion glint: {}.hasEffect not found; patch skipped, vanilla behavior kept.", POTION);
            }
            return null;
        }
        List<AbstractInsnNode> returns = returns(m, Opcodes.IRETURN);
        if (returns.isEmpty()) {
            Asm.LOG.warn("P6 potion glint: {}.hasEffect has no return; patch skipped, vanilla behavior kept.", POTION);
            return null;
        }
        // The stack is read only at the first line: vanilla's ProGuard frames mark it TOP before the returns.
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "potionBegin", "(Lnet/minecraft/item/ItemStack;)V", false));
        m.instructions.insert(begin);
        for (AbstractInsnNode r : returns) {
            m.instructions.insertBefore(r, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "potionGlint", "(Z)Z", false));
        }
        if (live) {
            appliedP6 = true;
            Asm.LOG.info("P6 potion glint: patched {}.", POTION);
        }
        return Asm.write(node);
    }

    /** P7: {@code ALOAD 1, 2, 3, 4; INVOKESTATIC shulkerBegin} first, and {@code INVOKESTATIC shulkerFace} before each {@code ARETURN}. */
    private static byte[] shulker(byte[] bytes, boolean live) {
        ClassNode node = Asm.read(bytes);
        String desc = "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                + "Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/block/state/BlockFaceShape;";
        MethodNode m = Asm.method(node, "getBlockFaceShape", "func_193383_a", desc);
        List<AbstractInsnNode> returns = m == null ? new ArrayList<AbstractInsnNode>() : returns(m, Opcodes.ARETURN);
        if (returns.isEmpty()) {
            Asm.LOG.warn("P7 shulker box faces: {}.getBlockFaceShape is not of the expected shape; patch skipped, vanilla behavior kept.",
                    SHULKER);
            return null;
        }
        // The parameters are read only at the first line: vanilla's ProGuard frames mark them TOP before the returns.
        InsnList begin = new InsnList();
        for (int i = 1; i <= 4; i++) {
            begin.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "shulkerBegin", desc.substring(0, desc.indexOf(')') + 1) + "V", false));
        m.instructions.insert(begin);
        for (AbstractInsnNode r : returns) {
            m.instructions.insertBefore(r, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "shulkerFace",
                    "(Lnet/minecraft/block/state/BlockFaceShape;)Lnet/minecraft/block/state/BlockFaceShape;", false));
        }
        if (live) {
            appliedP7 = true;
            Asm.LOG.info("P7 shulker box faces: patched {}.", SHULKER);
        }
        return Asm.write(node);
    }

    private static List<AbstractInsnNode> returns(MethodNode m, int opcode) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == opcode) {
                out.add(i);
            }
        }
        return out;
    }

    private static boolean server() {
        try {
            return net.minecraftforge.fml.relauncher.FMLLaunchHandler.side().isServer();
        } catch (Throwable t) {
            return false;
        }
    }

    /** For the self-test: runs the patch for one class on raw bytes without marking it applied. */
    public static byte[] patchForTest(String transformedName, byte[] bytes) {
        return patch(transformedName, bytes, false);
    }
}
