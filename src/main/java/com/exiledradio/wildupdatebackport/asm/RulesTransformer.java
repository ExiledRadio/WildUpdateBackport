package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patches P12 to P17 ({@code docs/PATCHES.md}), the low-impact 1.19.x changes: the 1.19.3 and 1.19.4 game rules, empty
 * spawners and armor stand names. Each puts a static call to {@code changes.RulesHooks} between a vanilla value and its
 * use, or swaps one call for a hook of the same shape (loading {@code this} or a parameter for it, no jump, no new
 * local, no new frame). With its setting off (or the game rule at its 1.12.2 value) each hook gives back 1.12.2's value.
 *
 * <ul>
 *   <li><b>P12</b> {@code Explosion.doExplosionB}: the drop chance {@code 1.0F / this.size} through {@code dropChance}
 *       (the three explosion drop decay rules).</li>
 *   <li><b>P13</b> {@code WorldServer.updateBlocks}: its {@code canSnowAt(pos, true)} becomes {@code snowAt}
 *       (snowAccumulationHeight).</li>
 *   <li><b>P14</b> {@code World.playBroadcastSound}: each listener's {@code broadcastSound} becomes {@code
 *       broadcastSound} (globalSoundEvents).</li>
 *   <li><b>P15</b> {@code BlockVine.updateTick}: its {@code worldIn.rand.nextInt(4)} through {@code vineRoll}
 *       (doVinesSpread).</li>
 *   <li><b>P16</b> {@code CommandFill.execute} and {@code CommandClone.execute}: both {@code 32768}s become {@code
 *       blockLimit(server)} (commandModificationBlockLimit).</li>
 *   <li><b>P17</b> {@code MobSpawnerBaseLogic}: the {@code new WeightedSpawnerEntity()} of its field initializer through
 *       {@code newSpawnData}, {@code isActivated}'s result through {@code spawnerActive}, and (client) {@code
 *       getCachedEntity}'s {@code AnvilChunkLoader.readWorldEntity} becomes {@code spawnerEntity} (empty spawners).</li>
 *   <li><b>P18</b> {@code ItemArmorStand.onItemUse}: its {@code ItemMonsterPlacer.applyItemEntityDataToEntity} becomes
 *       {@code armorStandData}; {@code EntityArmorStand.dropBlock}: the new {@code ItemStack} through {@code
 *       armorStandItem} (armor stand names).</li>
 * </ul>
 * The water and lava source rules need no patch (Forge's {@code CreateFluidSourceEvent}). A target of another shape is
 * left alone (logged), so vanilla's behavior stays.
 */
public final class RulesTransformer implements IClassTransformer {

    private static final String HOOKS = "com/exiledradio/wildupdatebackport/changes/RulesHooks";

    static final String EXPLOSION = "net.minecraft.world.Explosion";
    static final String WORLD_SERVER = "net.minecraft.world.WorldServer";
    static final String WORLD = "net.minecraft.world.World";
    static final String VINE = "net.minecraft.block.BlockVine";
    static final String FILL = "net.minecraft.command.CommandFill";
    static final String CLONE = "net.minecraft.command.CommandClone";
    static final String SPAWNER = "net.minecraft.tileentity.MobSpawnerBaseLogic";
    static final String STAND_ITEM = "net.minecraft.item.ItemArmorStand";
    static final String STAND = "net.minecraft.entity.item.EntityArmorStand";

    /** The targets, for the self-test. */
    public static final String[] TARGETS = {EXPLOSION, WORLD_SERVER, WORLD, VINE, FILL, CLONE, SPAWNER, STAND_ITEM, STAND};

    /** Each target once patched in the running game, with its hook count; the self-test reads it. */
    public static final Map<String, Integer> APPLIED = new ConcurrentHashMap<String, Integer>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        Result r;
        try {
            r = patch(transformedName, basicClass);
        } catch (RuntimeException e) {
            Asm.LOG.error("P12-P18: patching {} threw; the class is left as it was.", transformedName, e);
            return basicClass;
        }
        if (r == null) {
            return basicClass;
        }
        APPLIED.put(transformedName, r.hooks);
        Asm.LOG.info("P12-P18: patched {} ({} sites).", transformedName, r.hooks);
        return r.bytes;
    }

    /** A patched class and how many sites it got. */
    public static final class Result {
        public final byte[] bytes;
        public final int hooks;

        Result(byte[] bytes, int hooks) {
            this.bytes = bytes;
            this.hooks = hooks;
        }
    }

    /** @return the patched class, or null when it is not a target or not of the expected shape */
    public static Result patch(String target, byte[] bytes) {
        if (EXPLOSION.equals(target)) {
            return explosion(bytes);
        }
        if (WORLD_SERVER.equals(target)) {
            return snow(bytes);
        }
        if (WORLD.equals(target)) {
            return sound(bytes);
        }
        if (VINE.equals(target)) {
            return vine(bytes);
        }
        if (FILL.equals(target) || CLONE.equals(target)) {
            return limit(bytes, target);
        }
        if (SPAWNER.equals(target)) {
            return spawner(bytes);
        }
        if (STAND_ITEM.equals(target)) {
            return standItem(bytes);
        }
        if (STAND.equals(target)) {
            return stand(bytes);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------

    private static boolean named(String name, String mcp, String srg) {
        return name.equals(mcp) || name.equals(srg);
    }

    private static boolean call(AbstractInsnNode n, int opcode, String owner, String mcp, String srg, String desc) {
        if (n.getOpcode() != opcode) {
            return false;
        }
        MethodInsnNode m = (MethodInsnNode) n;
        return (owner == null || owner.equals(m.owner)) && named(m.name, mcp, srg) && desc.equals(m.desc);
    }

    private static List<AbstractInsnNode> all(MethodNode m, java.util.function.Predicate<AbstractInsnNode> p) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        if (m != null) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (p.test(i)) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    /** The next real instruction after {@code n}, or null. */
    private static AbstractInsnNode next(AbstractInsnNode n) {
        AbstractInsnNode i = n.getNext();
        while (i != null && i.getOpcode() < 0) {
            i = i.getNext();
        }
        return i;
    }

    private static boolean shape(String what, MethodNode m, List<AbstractInsnNode> found, int count) {
        if (m == null || (count >= 0 ? found.size() != count : found.isEmpty())) {
            Asm.LOG.warn("{} is not of the expected shape ({}); patch skipped, vanilla behavior kept.", what,
                    m == null ? "method not found" : found.size() + " matches");
            return false;
        }
        return true;
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    private static InsnList list(AbstractInsnNode... nodes) {
        InsnList l = new InsnList();
        for (AbstractInsnNode n : nodes) {
            l.add(n);
        }
        return l;
    }

    // ------------------------------------------------------------------------------------------

    /** P12: {@code FCONST_1; ALOAD 0; GETFIELD size; FDIV}, then {@code ALOAD 0; dropChance}. */
    private static Result explosion(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "doExplosionB", "func_77279_a", "(Z)V");
        List<AbstractInsnNode> found = all(m, i -> {
            if (i.getOpcode() != Opcodes.FCONST_1) {
                return false;
            }
            AbstractInsnNode a = next(i);
            AbstractInsnNode f = a == null ? null : next(a);
            AbstractInsnNode d = f == null ? null : next(f);
            return a != null && a.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) a).var == 0 && f != null
                    && f.getOpcode() == Opcodes.GETFIELD && named(((FieldInsnNode) f).name, "size", "field_77280_f")
                    && d != null && d.getOpcode() == Opcodes.FDIV;
        });
        if (!shape("P12 explosion drops: Explosion.doExplosionB", m, found, 1)) {
            return null;
        }
        AbstractInsnNode div = next(next(next(found.get(0))));
        m.instructions.insert(div, list(new VarInsnNode(Opcodes.ALOAD, 0),
                hook("dropChance", "(FLnet/minecraft/world/Explosion;)F")));
        return new Result(Asm.write(node), 1);
    }

    /** P13: {@code canSnowAt(BlockPos, boolean)} on the world becomes the static {@code snowAt(World, BlockPos, boolean)}. */
    private static Result snow(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "updateBlocks", "func_147456_g", "()V");
        List<AbstractInsnNode> found = all(m, i -> call(i, Opcodes.INVOKEVIRTUAL, null, "canSnowAt", "func_175708_f",
                "(Lnet/minecraft/util/math/BlockPos;Z)Z"));
        if (!shape("P13 snow accumulation: WorldServer.updateBlocks", m, found, 1)) {
            return null;
        }
        m.instructions.set(found.get(0), hook("snowAt", "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Z)Z"));
        return new Result(Asm.write(node), 1);
    }

    /** P14: {@code listener.broadcastSound(id, pos, data)} becomes {@code broadcastSound(listener, id, pos, data, this)}. */
    private static Result sound(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "playBroadcastSound", "func_175669_a", "(ILnet/minecraft/util/math/BlockPos;I)V");
        List<AbstractInsnNode> found = all(m, i -> call(i, Opcodes.INVOKEINTERFACE, "net/minecraft/world/IWorldEventListener",
                "broadcastSound", "func_180440_a", "(ILnet/minecraft/util/math/BlockPos;I)V"));
        if (!shape("P14 global sounds: World.playBroadcastSound", m, found, 1)) {
            return null;
        }
        AbstractInsnNode at = found.get(0);
        m.instructions.insertBefore(at, new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.set(at, hook("broadcastSound",
                "(Lnet/minecraft/world/IWorldEventListener;ILnet/minecraft/util/math/BlockPos;ILnet/minecraft/world/World;)V"));
        return new Result(Asm.write(node), 1);
    }

    /** P15: {@code worldIn.rand.nextInt(4)}, then {@code ALOAD 1; vineRoll}. */
    private static Result vine(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "updateTick", "func_180650_b",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V");
        List<AbstractInsnNode> found = all(m, i -> {
            if (i.getOpcode() != Opcodes.GETFIELD || !named(((FieldInsnNode) i).name, "rand", "field_73012_v")) {
                return false;
            }
            AbstractInsnNode four = next(i);
            AbstractInsnNode roll = four == null ? null : next(four);
            return four != null && four.getOpcode() == Opcodes.ICONST_4 && roll != null
                    && call(roll, Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextInt", "nextInt", "(I)I");
        });
        if (!shape("P15 vine spreading: BlockVine.updateTick", m, found, 1)) {
            return null;
        }
        m.instructions.insert(next(next(found.get(0))), list(new VarInsnNode(Opcodes.ALOAD, 1),
                hook("vineRoll", "(ILnet/minecraft/world/World;)I")));
        return new Result(Asm.write(node), 1);
    }

    /** P16: each {@code LDC 32768} becomes {@code ALOAD 1; blockLimit} (the server is the first parameter). */
    private static Result limit(byte[] bytes, String target) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "execute", "func_184881_a",
                "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V");
        List<AbstractInsnNode> found = all(m, i -> i instanceof LdcInsnNode && Integer.valueOf(32768).equals(((LdcInsnNode) i).cst));
        if (!shape("P16 block limit: " + target + ".execute", m, found, 2)) {
            return null;
        }
        for (AbstractInsnNode n : found) {
            m.instructions.insertBefore(n, new VarInsnNode(Opcodes.ALOAD, 1));
            m.instructions.set(n, hook("blockLimit", "(Lnet/minecraft/server/MinecraftServer;)I"));
        }
        return new Result(Asm.write(node), 2);
    }

    /** P17: empty spawners. */
    private static Result spawner(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode init = Asm.method(node, "<init>", "<init>", "()V");
        List<AbstractInsnNode> data = all(init, i -> call(i, Opcodes.INVOKESPECIAL, "net/minecraft/util/WeightedSpawnerEntity",
                "<init>", "<init>", "()V") && next(i) != null && next(i).getOpcode() == Opcodes.PUTFIELD);
        MethodNode active = Asm.method(node, "isActivated", "func_98279_f", "()Z");
        List<AbstractInsnNode> returns = all(active, i -> i.getOpcode() == Opcodes.IRETURN);
        if (!shape("P17 empty spawners: MobSpawnerBaseLogic.<init>", init, data, 1)
                || !shape("P17 empty spawners: MobSpawnerBaseLogic.isActivated", active, returns, -1)) {
            return null;
        }
        String type = "Lnet/minecraft/util/WeightedSpawnerEntity;";
        init.instructions.insert(data.get(0), hook("newSpawnData", "(" + type + ")" + type));
        for (AbstractInsnNode r : returns) {
            active.instructions.insertBefore(r, list(new VarInsnNode(Opcodes.ALOAD, 0),
                    hook("spawnerActive", "(ZLnet/minecraft/tileentity/MobSpawnerBaseLogic;)Z")));
        }
        int hooks = 1 + returns.size();
        // Client only: the mob drawn inside.
        MethodNode cached = Asm.method(node, "getCachedEntity", "func_184994_d", "()Lnet/minecraft/entity/Entity;");
        if (cached != null) {
            String desc = "(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/World;Z)Lnet/minecraft/entity/Entity;";
            List<AbstractInsnNode> read = all(cached, i -> call(i, Opcodes.INVOKESTATIC,
                    "net/minecraft/world/chunk/storage/AnvilChunkLoader", "readWorldEntity", "func_186051_a", desc));
            if (shape("P17 empty spawners: MobSpawnerBaseLogic.getCachedEntity", cached, read, 1)) {
                cached.instructions.set(read.get(0), hook("spawnerEntity", desc));
                hooks++;
            }
        }
        return new Result(Asm.write(node), hooks);
    }

    /** P18: the armor stand item's entity data call becomes {@code armorStandData}, same arguments. */
    private static Result standItem(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "onItemUse", "func_180614_a",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/EnumActionResult;");
        String desc = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;"
                + "Lnet/minecraft/entity/Entity;)V";
        List<AbstractInsnNode> found = all(m, i -> call(i, Opcodes.INVOKESTATIC, "net/minecraft/item/ItemMonsterPlacer",
                "applyItemEntityDataToEntity", "func_185079_a", desc));
        if (!shape("P18 armor stand names: ItemArmorStand.onItemUse", m, found, 1)) {
            return null;
        }
        m.instructions.set(found.get(0), hook("armorStandData", desc));
        return new Result(Asm.write(node), 1);
    }

    /** P18: {@code new ItemStack(Items.ARMOR_STAND)} in {@code dropBlock}, then {@code ALOAD 0; armorStandItem}. */
    private static Result stand(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        MethodNode m = Asm.method(node, "dropBlock", "func_175421_A", "()V");
        List<AbstractInsnNode> found = all(m, i -> call(i, Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemStack", "<init>", "<init>",
                "(Lnet/minecraft/item/Item;)V"));
        if (!shape("P18 armor stand names: EntityArmorStand.dropBlock", m, found, 1)) {
            return null;
        }
        m.instructions.insert(found.get(0), list(new VarInsnNode(Opcodes.ALOAD, 0),
                hook("armorStandItem", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/item/EntityArmorStand;)"
                        + "Lnet/minecraft/item/ItemStack;")));
        return new Result(Asm.write(node), 1);
    }
}
