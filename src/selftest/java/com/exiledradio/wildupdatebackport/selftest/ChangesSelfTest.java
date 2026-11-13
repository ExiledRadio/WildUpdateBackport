package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.asm.ChangesTransformer;
import com.exiledradio.wildupdatebackport.changes.ChangesHooks;
import com.exiledradio.wildupdatebackport.changes.ItemChanges;
import com.exiledradio.wildupdatebackport.changes.Jukeboxes;
import com.exiledradio.wildupdatebackport.changes.MobChanges;
import com.exiledradio.wildupdatebackport.changes.ShulkerBoxes;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Stream C: the other 1.19.x changes, and patches P4 to P7. */
public final class ChangesSelfTest implements SelfTest {

    @Override
    public String name() {
        return "changes";
    }

    @Override
    public void run(Check c) throws Exception {
        defaults(c);
        patches(c);
        minecarts(c);
        explosions(c);
        golemEggs(c);
        creepers(c);
        endermen(c);
        rabbits(c);
        jukeboxes(c);
        armorSwap(c);
        foals(c);
        potions(c);
        shulkerBoxes(c);
        netherLight(c);
        scaffolding(c);
        doorModels(c);
        lang(c);
    }

    // ------------------------------------------------------------------------------------------

    private static void defaults(Check c) {
        ModConfig.Changes m = ModConfig.changes;
        c.ok(m.enabled && m.minecarts && m.explosionExperience && m.golemSpawnEggs && m.creeperFireCharge && m.endermanBlockDrops
                && m.rabbitDrops && m.jukeboxes && m.armorSwap && m.foalStats && m.potionColors && m.potionGlint && m.doorModels
                && m.shulkerBoxes && m.netherSpawnLight && m.scaffoldingFuel, "every change is on by default");
    }

    /** P4 to P7 on the dev jar's MCP-named classes and Forge's SRG-named jar, hook counts and ASM's stack check. */
    private static void patches(Check c) throws IOException {
        Map<String, Object[]> want = new HashMap<String, Object[]>();
        want.put("net.minecraft.entity.item.EntityMinecart", new Object[] {"cartItem", 1});
        want.put("net.minecraft.entity.item.EntityMinecartChest", new Object[] {"cartBlockItem", 1});
        want.put("net.minecraft.entity.item.EntityMinecartHopper", new Object[] {"cartBlockItem", 1});
        want.put("net.minecraft.entity.item.EntityMinecartFurnace", new Object[] {"cartBlock", 1});
        want.put("net.minecraft.entity.item.EntityMinecartTNT", new Object[] {"cartBlock", 1});
        want.put("net.minecraft.block.BlockJukebox", new Object[] {"jukebox", 3});
        want.put("net.minecraft.item.ItemPotion", new Object[] {"potionGlint", -1});
        want.put("net.minecraft.block.BlockShulkerBox", new Object[] {"shulkerFace", -1});
        for (String t : ChangesTransformer.TARGETS) {
            byte[] dev = classBytes(t);
            check(c, "dev " + t, dev == null ? null : ChangesTransformer.patchForTest(t, dev), want.get(t));
        }
        File srg = new File(System.getProperty("user.home"),
                ".gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar");
        if (c.ok(srg.isFile(), "Forge's SRG jar is in the Gradle cache: " + srg)) {
            ZipFile zip = new ZipFile(srg);
            try {
                for (String t : ChangesTransformer.TARGETS) {
                    ZipEntry entry = zip.getEntry(t.replace('.', '/') + ".class");
                    byte[] bytes = entry == null ? null : read(zip.getInputStream(entry));
                    byte[] patched = bytes == null ? null : ChangesTransformer.patchForTest(t, bytes);
                    check(c, "SRG " + t, patched, want.get(t));
                    if (patched != null && t.endsWith("BlockJukebox")) {
                        ClassNode n = node(patched);
                        boolean srgNames = false;
                        for (MethodNode m : n.methods) {
                            srgNames |= "func_149744_f".equals(m.name) || "func_180656_a".equals(m.name);
                        }
                        c.ok(srgNames, "the SRG jukebox's new methods have SRG names");
                    }
                }
            } finally {
                zip.close();
            }
        }
        c.eq(5, ChangesTransformer.appliedP4, "P4 patched the five minecart classes of this server");
        c.ok(ChangesTransformer.appliedP5, "P5 patched this server's jukebox");
        c.ok(!ChangesTransformer.appliedP6, "P6 has nothing to patch on a dedicated server (hasEffect is client-only)");
        c.ok(ChangesTransformer.appliedP7, "P7 patched this server's shulker box");
    }

    private static void check(Check c, String what, byte[] patched, Object[] want) {
        if (!c.ok(patched != null, what + " was patched")) {
            return;
        }
        ClassNode node = node(patched);
        int calls = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.endsWith("changes/ChangesHooks")) {
                    String name = ((MethodInsnNode) i).name;
                    c.ok(name.startsWith((String) want[0]), what + ": calls ChangesHooks." + name);
                    calls++;
                }
            }
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (Exception e) {
                c.fail(what + "." + m.name + " fails ASM's stack check: " + e);
            }
        }
        int n = (Integer) want[1];
        c.ok(n < 0 ? calls > 0 : calls == n, what + ": " + calls + " hook calls");
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    // ------------------------------------------------------------------------------------------

    private static void minecarts(Check c) {
        WorldServer w = c.world;
        BlockPos at = c.area().add(4, 0, 4);
        Object[][] carts = {
                {new EntityMinecartChest(w), Items.CHEST_MINECART, DamageSource.GENERIC},
                {new EntityMinecartFurnace(w), Items.FURNACE_MINECART, DamageSource.causeExplosionDamage((net.minecraft.entity.EntityLivingBase) null)},
                {new EntityMinecartTNT(w), Items.TNT_MINECART, DamageSource.GENERIC},
                {new EntityMinecartHopper(w), Items.HOPPER_MINECART, DamageSource.GENERIC},
                {new EntityMinecartEmpty(w), Items.MINECART, DamageSource.GENERIC},
        };
        for (Object[] row : carts) {
            EntityMinecart cart = (EntityMinecart) row[0];
            cart.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            cart.setCustomNameTag("Named");
            w.spawnEntity(cart);
            if (cart instanceof EntityMinecartChest) {
                ((EntityMinecartChest) cart).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 3));
            }
            cart.killMinecart((DamageSource) row[2]);
            List<EntityItem> items = w.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(2));
            int self = 0;
            int other = 0;
            for (EntityItem e : items) {
                if (e.isDead) {
                    continue;
                }
                ItemStack s = e.getItem();
                if (s.getItem() == row[1]) {
                    self += s.getCount();
                    c.eq("Named", s.getDisplayName(), cart.getName() + "'s drop keeps the cart's name");
                    e.setDead();
                } else if (s.getItem() != Items.DIAMOND) {
                    other += s.getCount();
                    e.setDead();
                }
            }
            c.eq(1, self, cart.getClass().getSimpleName() + " drops one " + row[1]);
            c.eq(0, other, cart.getClass().getSimpleName() + " drops no separate minecart or block");
            if (cart instanceof EntityMinecartChest) {
                c.eq(3, Drops.count(w, at, Items.DIAMOND), "the chest cart still drops what it holds");
            }
        }
        for (String name : new String[] {"chest_minecart", "furnace_minecart", "tnt_minecart", "hopper_minecart"}) {
            IRecipe r = CraftingManager.getRecipe(new ResourceLocation("minecraft", name));
            c.ok(r instanceof ShapelessRecipes, "minecraft:" + name + " is shapeless: " + r);
        }
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(Items.MINECART), new ItemStack(Blocks.CHEST)),
                new ItemStack(Items.CHEST_MINECART), "minecart then chest side by side");
        Crafting.expect(c, Crafting.grid(1, 2, new ItemStack(Blocks.HOPPER), new ItemStack(Items.MINECART)),
                new ItemStack(Items.HOPPER_MINECART), "hopper over minecart");
        Crafting.expect(c, Crafting.grid(2, 2, ItemStack.EMPTY, new ItemStack(Items.MINECART), new ItemStack(Blocks.TNT), ItemStack.EMPTY),
                new ItemStack(Items.TNT_MINECART), "TNT and minecart diagonally");
    }

    private static void explosions(Check c) {
        WorldServer w = c.world;
        BlockPos base = c.area();
        EntityPlayerMP player = c.player("tnt", base);
        Object[][] cases = {
                {Blocks.DIAMOND_ORE, player, 3, 7},
                {Blocks.MOB_SPAWNER, player, 15, 43},
                {Blocks.DIAMOND_ORE, null, 0, 0},
                {Blocks.STONE, player, 0, 0},
        };
        int i = 0;
        for (Object[] k : cases) {
            BlockPos pos = base.add(4 + 8 * i++, 0, 16);
            w.setBlockState(pos, ((net.minecraft.block.Block) k[0]).getDefaultState());
            EntityTNTPrimed tnt = new EntityTNTPrimed(w, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    (net.minecraft.entity.EntityLivingBase) k[1]);
            w.newExplosion(tnt, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 2.0F, false, true);
            int xp = 0;
            for (EntityXPOrb orb : w.getEntitiesWithinAABB(EntityXPOrb.class, new AxisAlignedBB(pos).grow(3))) {
                xp += orb.getXpValue();
                orb.setDead();
            }
            Drops.count(w, pos, Items.DIAMOND);
            c.ok(w.isAirBlock(pos), k[0] + " was blown up");
            c.ok(xp >= (Integer) k[2] && xp <= (Integer) k[3], k[0] + (k[1] == null ? " (TNT nobody lit)" : " (TNT a player lit)")
                    + " drops " + xp + " experience, expected " + k[2] + "-" + k[3]);
        }
    }

    private static void golemEggs(Check c) {
        Object[][] eggs = {{"villager_golem", 14405058, 7643954, EntityIronGolem.class}, {"snowman", 14283506, 8496292, EntitySnowman.class}};
        BlockPos at = c.area().add(8, 0, 8);
        for (Object[] e : eggs) {
            ResourceLocation id = new ResourceLocation("minecraft", (String) e[0]);
            EntityList.EntityEggInfo info = EntityList.ENTITY_EGGS.get(id);
            if (!c.ok(info != null, id + " has a spawn egg")) {
                continue;
            }
            c.eq(e[1], info.primaryColor, id + "'s egg color");
            c.eq(e[2], info.secondaryColor, id + "'s spot color");
            c.ok(info.killEntityStat != null && info.entityKilledByStat != null, id + "'s kill statistics exist");
            Entity spawned = ItemMonsterPlacer.spawnCreature(c.world, id, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            c.ok(((Class<?>) e[3]).isInstance(spawned), id + "'s egg spawns one: " + spawned);
            if (spawned != null) {
                spawned.setDead();
            }
        }
    }

    private static void creepers(Check c) {
        BlockPos at = c.area().add(8, 0, 8);
        EntityPlayerMP player = Players.real(c.world, "creeper");
        player.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        EntityCreeper creeper = new EntityCreeper(c.world);
        creeper.setPosition(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        c.world.spawnEntity(creeper);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 3));
        EnumActionResult r = player.interactOn(creeper, EnumHand.MAIN_HAND);
        c.eq(EnumActionResult.SUCCESS, r, "using a fire charge on a creeper");
        c.ok(creeper.hasIgnited(), "a fire charge ignites the creeper");
        c.eq(2, player.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and is used up");
        creeper.setDead();
    }

    private static void endermen(Check c) {
        BlockPos at = c.area().add(8, 0, 8);
        Object[][] cases = {
                {Blocks.CLAY.getDefaultState(), Blocks.CLAY, 1},
                {Blocks.MELON_BLOCK.getDefaultState(), Blocks.MELON_BLOCK, 1},
                {Blocks.RED_FLOWER.getStateFromMeta(2), Blocks.RED_FLOWER, 1},
                {Blocks.DIRT.getStateFromMeta(1), Blocks.DIRT, 1},
        };
        for (Object[] k : cases) {
            NonNullList<ItemStack> drops = MobChanges.carriedBlockDrops(c.world, at, (IBlockState) k[0]);
            c.ok(drops.size() == 1 && drops.get(0).getItem() == net.minecraft.item.Item.getItemFromBlock((net.minecraft.block.Block) k[1]),
                    k[0] + " drops as silk-touched: " + drops);
        }
        c.eq(1, MobChanges.carriedBlockDrops(c.world, at, Blocks.DIRT.getStateFromMeta(1)).get(0).getMetadata(), "coarse dirt stays coarse");
        EntityEnderman enderman = new EntityEnderman(c.world);
        enderman.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        c.world.spawnEntity(enderman);
        enderman.setHeldBlockState(Blocks.CLAY.getDefaultState());
        enderman.attackEntityFrom(DamageSource.OUT_OF_WORLD, 1000.0F);
        c.eq(1, Drops.count(c.world, at, net.minecraft.item.Item.getItemFromBlock(Blocks.CLAY)), "a killed enderman drops the clay block it held");
        c.eq(0, Drops.count(c.world, at, Items.CLAY_BALL), "not clay balls");
    }

    private static void rabbits(Check c) {
        LootTable table = c.world.getLootTableManager().getLootTableFromLocation(LootTableList.ENTITIES_RABBIT);
        EntityRabbit rabbit = new EntityRabbit(c.world);
        Random random = new Random(19);
        int raw = 0;
        for (int i = 0; i < 100; i++) {
            for (ItemStack s : table.generateLootForPools(random, new LootContext.Builder(c.world).withLootedEntity(rabbit).build())) {
                if (s.getItem() == Items.RABBIT) {
                    raw += s.getCount();
                }
            }
        }
        c.eq(100, raw, "100 rabbits drop exactly 100 raw rabbit");
        rabbit.setFire(5);
        int cooked = 0;
        for (ItemStack s : table.generateLootForPools(random, new LootContext.Builder(c.world).withLootedEntity(rabbit).build())) {
            if (s.getItem() == Items.COOKED_RABBIT) {
                cooked += s.getCount();
            }
        }
        c.eq(1, cooked, "a burning rabbit drops one cooked rabbit");
    }

    private static void jukeboxes(Check c) {
        WorldServer w = c.world;
        BlockPos pos = c.area().add(8, 0, 8);
        w.setBlockState(pos, Blocks.JUKEBOX.getDefaultState());
        TileEntity te = w.getTileEntity(pos);
        c.ok(Blocks.JUKEBOX.getDefaultState().canProvidePower(), "a jukebox is a signal source");
        c.ok(Blocks.JUKEBOX.getDefaultState().isSideSolid(w, pos, EnumFacing.NORTH), "and its sides stay solid");
        IItemHandler h = te == null ? null : te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (!c.ok(h != null, "a jukebox takes items from hoppers")) {
            return;
        }
        c.eq(0, w.getRedstonePower(pos, EnumFacing.EAST), "an empty jukebox gives no signal");
        ItemStack stick = new ItemStack(Items.STICK);
        c.eq(1, h.insertItem(0, stick, false).getCount(), "a stick does not go in");
        ItemStack rest = h.insertItem(0, new ItemStack(Items.RECORD_11, 2), false);
        c.eq(1, rest.getCount(), "one disc of two goes in");
        c.ok(w.getBlockState(pos).getValue(BlockJukebox.HAS_RECORD), "the jukebox shows a disc");
        c.ok(Jukeboxes.isPlaying(te), "and plays it");
        c.eq(15, w.getRedstonePower(pos, EnumFacing.EAST), "a playing jukebox gives a signal of 15");
        c.eq(1, h.insertItem(0, new ItemStack(Items.RECORD_13), false).getCount(), "a full jukebox takes no second disc");
        // 11 is 71 seconds: it stops 71 * 20 + 20 ticks after it started.
        for (int i = 0; i < 71 * 20 + 19; i++) {
            Jukeboxes.tick((BlockJukebox.TileEntityJukebox) te);
        }
        c.ok(Jukeboxes.isPlaying(te), "still playing one tick before the end");
        Jukeboxes.tick((BlockJukebox.TileEntityJukebox) te);
        Jukeboxes.tick((BlockJukebox.TileEntityJukebox) te);
        c.ok(!Jukeboxes.isPlaying(te), "stopped after 11's 71 seconds and 20 ticks");
        c.eq(0, w.getRedstonePower(pos, EnumFacing.EAST), "and the signal is gone");
        ItemStack out = h.extractItem(0, 1, false);
        c.eq(Items.RECORD_11, out.getItem(), "a hopper takes the disc out");
        c.ok(!w.getBlockState(pos).getValue(BlockJukebox.HAS_RECORD), "the jukebox shows no disc");

        // Through real hoppers: one above puts a disc in, one below takes it out.
        BlockPos above = pos.up();
        w.setBlockState(above, Blocks.HOPPER.getDefaultState());
        TileEntityHopper top = (TileEntityHopper) w.getTileEntity(above);
        top.setInventorySlotContents(0, new ItemStack(Items.RECORD_CAT));
        top.update();
        c.eq(Items.RECORD_CAT, ((BlockJukebox.TileEntityJukebox) te).getRecord().getItem(), "a hopper above puts a disc in");
        c.ok(Jukeboxes.isPlaying(te), "which plays");
        w.setBlockToAir(above);
        BlockPos below = pos.down();
        w.setBlockState(below, Blocks.HOPPER.getDefaultState());
        TileEntityHopper bottom = (TileEntityHopper) w.getTileEntity(below);
        // As in 1.19.4, the playing jukebox's signal locks the hopper under it.
        c.ok(w.isBlockPowered(below), "a hopper under a playing jukebox is powered (locked), as in 1.19.4");
        bottom.update();
        c.ok(bottom.getStackInSlot(0).isEmpty(), "and takes nothing while it plays");
        for (int i = 0; i < 185 * 20 + 21; i++) {
            Jukeboxes.tick((BlockJukebox.TileEntityJukebox) te);
        }
        c.ok(!Jukeboxes.isPlaying(te), "cat ends after 185 seconds");
        w.setBlockState(below, Blocks.STONE.getDefaultState());
        w.setBlockState(below, Blocks.HOPPER.getDefaultState());
        bottom = (TileEntityHopper) w.getTileEntity(below);
        bottom.update();
        c.eq(Items.RECORD_CAT, bottom.getStackInSlot(0).getItem(), "a hopper below takes the disc out");
        c.ok(((BlockJukebox.TileEntityJukebox) te).getRecord().isEmpty() && !Jukeboxes.isPlaying(te), "the jukebox is empty and silent");
        w.setBlockState(below, Blocks.STONE.getDefaultState());

        // Still a conductor: a lever on its side powers it, and it passes that on.
        w.setBlockState(pos.east(), Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST)
                .withProperty(BlockLever.POWERED, true));
        c.eq(15, w.getRedstonePower(pos, EnumFacing.WEST), "a jukebox powered by a lever passes the power on");
        w.setBlockToAir(pos.east());
        c.eq(178 * 20, Jukeboxes.lengthInTicks(Items.RECORD_13), "13 is 178 seconds long");
        c.eq(-1, Jukeboxes.lengthInTicks(Items.STICK), "an unknown disc plays until it is taken out");
    }

    private static void armorSwap(Check c) {
        BlockPos at = c.area().add(8, 0, 8);
        EntityPlayerMP p = c.player("armor", at);
        p.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_CHESTPLATE));
        EnumActionResult r = ForgeHooks.onItemRightClick(p, EnumHand.MAIN_HAND);
        c.eq(EnumActionResult.SUCCESS, r, "using a chestplate over another");
        c.eq(Items.DIAMOND_CHESTPLATE, p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem(), "the used one is worn");
        c.eq(Items.IRON_CHESTPLATE, p.getHeldItem(EnumHand.MAIN_HAND).getItem(), "the worn one is in the hand");

        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.ELYTRA));
        ForgeHooks.onItemRightClick(p, EnumHand.MAIN_HAND);
        c.eq(Items.ELYTRA, p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem(), "an elytra swaps with a chestplate");
        c.eq(Items.DIAMOND_CHESTPLATE, p.getHeldItem(EnumHand.MAIN_HAND).getItem(), "the chestplate comes to the hand");

        ItemStack cursed = new ItemStack(Items.IRON_HELMET);
        cursed.addEnchantment(Enchantments.BINDING_CURSE, 1);
        p.setItemStackToSlot(EntityEquipmentSlot.HEAD, cursed);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.GOLDEN_HELMET));
        ForgeHooks.onItemRightClick(p, EnumHand.MAIN_HAND);
        c.ok(EnchantmentHelper.hasBindingCurse(p.getItemStackFromSlot(EntityEquipmentSlot.HEAD)), "a cursed helmet stays on");
        c.eq(Items.GOLDEN_HELMET, p.getHeldItem(EnumHand.MAIN_HAND).getItem(), "and the other stays in the hand");
    }

    private static void foals(Check c) {
        Random r = new Random(4);
        double min = 1;
        double max = 0;
        for (int i = 0; i < 1000; i++) {
            double v = MobChanges.createOffspringAttribute(0.9, 0.9, 0.4, 1.0, r);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        c.ok(max <= 1.0 && min >= 0.4 && min > 0.75, "foals of two 0.9 jumpers stay near 0.9 within the range: " + min + "-" + max);
        double reflected = MobChanges.createOffspringAttribute(1.0, 1.0, 0.4, 1.0, new FixedRandom(1.0));
        c.ok(Math.abs(reflected - 0.91) < 1e-9, "1.0 + 0.09 past the top reflects back to 0.91: " + reflected);
        c.eq(15.0, MobChanges.MIN_HEALTH, "1.19.4's lowest horse health");
        c.eq(30.0, MobChanges.MAX_HEALTH, "1.19.4's highest");
        c.eq((double) 0.4F, MobChanges.MIN_JUMP, "lowest jump");
        c.eq((double) 0.1125F, MobChanges.MIN_SPEED, "lowest speed");
        c.eq((double) 0.3375F, MobChanges.MAX_SPEED, "highest speed");

        BlockPos at = c.area().add(8, 0, 8);
        EntityHorse a = new EntityHorse(c.world);
        EntityHorse b = new EntityHorse(c.world);
        a.setPosition(at.getX(), at.getY(), at.getZ());
        b.setPosition(at.getX(), at.getY(), at.getZ());
        a.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0);
        b.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0);
        double sum = 0;
        int n = 200;
        for (int i = 0; i < n; i++) {
            EntityHorse child = (EntityHorse) a.createChild(b);
            BabyEntitySpawnEvent e = new BabyEntitySpawnEvent(a, b, child);
            MinecraftForge.EVENT_BUS.post(e);
            double h = child.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue();
            c.ok(h >= 15.0 && h <= 30.0, "a foal's health is in 15-30: " + h);
            sum += h;
        }
        // 1.12.2's formula averages the parents with one random horse: about 27.5 here. 1.19.4's stays near 30.
        c.ok(sum / n > 29.0, "foals of two 30-health horses average " + sum / n + ", 1.19.4's spread (1.12.2's would be about 27.5)");
    }

    private static void potions(Check c) {
        c.eq(3402751, MobEffects.SPEED.getLiquidColor(), "Speed's 1.19.4 color");
        c.eq(16762624, MobEffects.STRENGTH.getLiquidColor(), "Strength's 1.19.4 color");
        c.eq(5882118, MobEffects.LUCK.getLiquidColor(), "Luck's 1.19.4 color");
        c.eq(3484199, MobEffects.WITHER.getLiquidColor(), "Wither keeps its color (changed after 1.19.x)");
        c.eq(3402751, PotionUtils.getPotionColor(PotionTypes.SWIFTNESS), "a Potion of Swiftness takes the new color");
        ItemStack potion = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.SWIFTNESS);
        c.ok(!ChangesHooks.potionGlint(true, potion), "a potion has no glint");
        potion.addEnchantment(Enchantments.UNBREAKING, 1);
        c.ok(ChangesHooks.potionGlint(true, potion), "an enchanted potion still has one");
    }

    private static void shulkerBoxes(Check c) {
        WorldServer w = c.world;
        BlockPos pos = c.area().add(8, 1, 8);
        // A box opening upward, redstone dust on its lid; a box opening downward, dust on its base. (1.12.2 never lets
        // torches, buttons or ladders onto a shulker box at all; dust asks only the face shape, as 1.19.3's does.)
        w.setBlockState(pos, Blocks.PURPLE_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, EnumFacing.UP));
        w.setBlockState(pos.up(), Blocks.REDSTONE_WIRE.getDefaultState());
        BlockPos box2 = pos.add(6, 0, 0);
        w.setBlockState(box2, Blocks.PURPLE_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, EnumFacing.DOWN));
        w.setBlockState(box2.up(), Blocks.REDSTONE_WIRE.getDefaultState());
        IBlockState s1 = w.getBlockState(pos);
        c.eq(BlockFaceShape.SOLID, s1.getBlockFaceShape(w, pos, EnumFacing.UP), "a closed box's lid holds dust");
        c.eq(BlockFaceShape.SOLID, s1.getBlockFaceShape(w, pos, EnumFacing.EAST), "and its sides");
        tickShulkers(w);
        TileEntityShulkerBox t1 = (TileEntityShulkerBox) w.getTileEntity(pos);
        TileEntityShulkerBox t2 = (TileEntityShulkerBox) w.getTileEntity(box2);
        t1.receiveClientEvent(1, 1);
        t2.receiveClientEvent(1, 1);
        c.eq(BlockFaceShape.UNDEFINED, s1.getBlockFaceShape(w, pos, EnumFacing.NORTH), "an opening box's side holds nothing");
        c.eq(BlockFaceShape.SOLID, s1.getBlockFaceShape(w, pos, EnumFacing.DOWN), "its base still does (1.12.2: it did not)");
        tickShulkers(w);
        c.ok(w.isAirBlock(pos.up()), "opening pops the dust on the lid");
        c.eq(Blocks.REDSTONE_WIRE, w.getBlockState(box2.up()).getBlock(), "dust on the base stays");
        for (int i = 0; i < 12; i++) {
            t1.update();
            t2.update();
        }
        c.eq(TileEntityShulkerBox.AnimationStatus.OPENED, t1.getAnimationStatus(), "the box is open");
        c.eq(BlockFaceShape.UNDEFINED, s1.getBlockFaceShape(w, pos, EnumFacing.EAST), "an open box's side holds nothing (1.12.2: it did)");
        t1.receiveClientEvent(1, 0);
        for (int i = 0; i < 12; i++) {
            t1.update();
        }
        c.eq(BlockFaceShape.SOLID, s1.getBlockFaceShape(w, pos, EnumFacing.UP), "closed again, the lid holds again");
        w.setBlockToAir(box2.up());
    }

    private static void tickShulkers(WorldServer w) {
        ShulkerBoxes.tick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, w));
    }

    private static void netherLight(Check c) {
        WorldServer nether = c.world.getMinecraftServer().getWorld(-1);
        if (!c.ok(nether != null, "the Nether is loaded")) {
            return;
        }
        BlockPos base = new BlockPos(20000, 60, 20000);
        // Light only spreads where every chunk within 17 blocks is loaded.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                nether.getChunk(base.add(dx * 16, 0, dz * 16));
            }
        }
        for (BlockPos p : BlockPos.getAllInBox(base.add(-12, 0, -2), base.add(12, 4, 2))) {
            nether.setBlockState(p, p.getY() == base.getY() ? Blocks.NETHERRACK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
        }
        for (BlockPos p : BlockPos.getAllInBox(base.add(-13, 0, -3), base.add(13, 5, 3))) {
            if (Math.abs(p.getX() - base.getX()) == 13 || Math.abs(p.getZ() - base.getZ()) == 3 || p.getY() == base.getY() + 5) {
                nether.setBlockState(p, Blocks.NETHERRACK.getDefaultState(), 2);
            }
        }
        BlockPos stand = base.up();
        EntityEnderman e = new EntityEnderman(nether);
        e.setPosition(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        c.ok(nether.getLightFromNeighbors(stand) == 0, "the test spot is dark: " + nether.getLightFromNeighbors(stand));
        c.ok(MobChanges.netherSpawnAllowed(e), "an enderman may spawn in the Nether's dark");
        // Glowstone 8 blocks away: light 7, always allowed (1.12.2: one try in eight).
        nether.setBlockState(stand.east(8), Blocks.GLOWSTONE.getDefaultState());
        c.eq(7, nether.getLightFromNeighbors(stand), "light at the spot");
        c.ok(MobChanges.netherSpawnAllowed(e), "at light 7, an enderman always may");
        LivingSpawnEvent.CheckSpawn ev = new LivingSpawnEvent.CheckSpawn(e, nether, (float) e.posX, (float) e.posY, (float) e.posZ, false);
        MinecraftForge.EVENT_BUS.post(ev);
        c.eq(Event.Result.ALLOW, ev.getResult(), "the spawn check allows it");
        nether.setBlockToAir(stand.east(8));
        nether.setBlockState(stand.east(7), Blocks.GLOWSTONE.getDefaultState());
        c.eq(8, nether.getLightFromNeighbors(stand), "light at the spot");
        c.ok(!MobChanges.netherSpawnAllowed(e), "at light 8, it may not");
        EntitySkeleton skeleton = new EntitySkeleton(nether);
        skeleton.setPosition(e.posX, e.posY, e.posZ);
        ev = new LivingSpawnEvent.CheckSpawn(skeleton, nether, (float) e.posX, (float) e.posY, (float) e.posZ, false);
        MinecraftForge.EVENT_BUS.post(ev);
        c.eq(Event.Result.DENY, ev.getResult(), "nor a skeleton");
        nether.setBlockToAir(stand.east(7));
    }

    private static void scaffolding(Check c) {
        c.ok(ItemChanges.isScaffolding(new ResourceLocation("villagerbackport", "scaffolding")), "Village & Pillage's scaffolding is recognized");
        c.ok(!ItemChanges.isScaffolding(new ResourceLocation("minecraft", "stick")), "a stick is not scaffolding");
        c.eq(50, ItemChanges.SCAFFOLDING_BURN_TIME, "scaffolding burns 50 ticks, as bamboo");
    }

    private static void doorModels(Check c) throws IOException {
        String[] doors = {"oak_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door", "iron_door"};
        for (String d : doors) {
            String bs = text("/assets/wildupdatebackport/blockstates/" + d + ".json");
            if (!c.ok(bs != null, d + "'s 1.19 blockstate is in the jar")) {
                continue;
            }
            int variants = bs.split("\"facing=").length - 1;
            c.eq(32, variants, d + "'s variants");
            for (String part : new String[] {"bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left",
                    "top_left_open", "top_right", "top_right_open"}) {
                c.ok(text("/assets/wildupdatebackport/models/block/" + d + "_" + part + ".json") != null, d + "_" + part + " model");
            }
        }
    }

    private static void lang(Check c) throws IOException {
        String en = text("/assets/wildupdatebackport/lang/en_us.lang");
        for (java.lang.reflect.Field f : ModConfig.Changes.class.getFields()) {
            String key = "config.wildupdatebackport.changes." + f.getName();
            c.ok(en != null && en.contains("\n" + key + "=") && en.contains("\n" + key + ".tooltip="), "en_us has " + key);
        }
    }

    // ------------------------------------------------------------------------------------------

    /** A random whose doubles are all one value. */
    private static final class FixedRandom extends Random {
        private final double value;

        FixedRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return this.value;
        }
    }

    private static String text(String path) throws IOException {
        InputStream in = ChangesSelfTest.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        return new String(read(in), StandardCharsets.UTF_8);
    }

    private static byte[] classBytes(String name) throws IOException {
        InputStream in = ChangesSelfTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
        return in == null ? null : read(in);
    }

    private static byte[] read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
