package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.asm.RulesTransformer;
import com.exiledradio.wildupdatebackport.changes.RulesEvents;
import com.exiledradio.wildupdatebackport.changes.RulesHooks;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.GameRules;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The low-impact 1.19.x changes, each also with its switch off: the 1.19.3 and 1.19.4 game rules (patches P12 to P16
 * and the fluid source event), no spawns on chorus flowers or scaffolding, empty spawners (P17), armor stand names
 * (P18), mangrove logs in bonus chests and waterlogged leaves.
 */
public final class RulesSelfTest implements SelfTest {

    @Override
    public String name() {
        return "rules";
    }

    @Override
    public void run(Check c) throws Exception {
        ModConfig.Changes s = ModConfig.changes;
        boolean[] saved = {s.gameRules, s.spawnBlocks, s.emptySpawners, s.armorStandNames, s.leavesWaterlog};
        GameRules rules = c.world.getGameRules();
        String[] values = new String[RulesHooks.RULES.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = rules.hasRule(RulesHooks.RULES[i][0]) ? rules.getString(RulesHooks.RULES[i][0]) : null;
        }
        try {
            patches(c);
            defaults(c);
            explosions(c);
            snow(c);
            sounds(c);
            vines(c);
            blockLimit(c);
            fluidSources(c);
            spawnBlocks(c);
            spawners(c);
            armorStands(c);
            bonusChest(c);
            leaves(c);
        } finally {
            s.gameRules = saved[0];
            s.spawnBlocks = saved[1];
            s.emptySpawners = saved[2];
            s.armorStandNames = saved[3];
            s.leavesWaterlog = saved[4];
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    rules.setOrCreateGameRule(RulesHooks.RULES[i][0], values[i]);
                }
            }
        }
    }

    private static void rule(World world, String name, String value) {
        world.getGameRules().setOrCreateGameRule(name, value);
    }

    // ------------------------------------------------------------------------------------------

    /** Each patch on the dev classes and Forge's SRG-named jar, and applied to this server's classes. */
    private static void patches(Check c) throws IOException {
        int[] sites = {1, 1, 1, 1, 2, 2, 3, 1, 1};
        for (int i = 0; i < RulesTransformer.TARGETS.length; i++) {
            String t = RulesTransformer.TARGETS[i];
            byte[] dev = classBytes(t);
            RulesTransformer.Result r = dev == null ? null : RulesTransformer.patch(t, dev);
            c.ok(r != null && r.hooks >= sites[i], "dev " + t + " patched, " + (r == null ? "-" : r.hooks) + " sites");
        }
        File srg = new File(System.getProperty("user.home"),
                ".gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar");
        if (c.ok(srg.isFile(), "Forge's SRG jar is in the Gradle cache")) {
            ZipFile zip = new ZipFile(srg);
            try {
                for (int i = 0; i < RulesTransformer.TARGETS.length; i++) {
                    String t = RulesTransformer.TARGETS[i];
                    ZipEntry entry = zip.getEntry(t.replace('.', '/') + ".class");
                    byte[] bytes = entry == null ? null : read(zip.getInputStream(entry));
                    RulesTransformer.Result r = bytes == null ? null : RulesTransformer.patch(t, bytes);
                    // The SRG jar is the merged one: the spawner's client-only getCachedEntity is there too.
                    c.ok(r != null && r.hooks >= sites[i], "SRG " + t + " patched, " + (r == null ? "-" : r.hooks) + " sites");
                }
            } finally {
                zip.close();
            }
        }
        Object[] load = {Explosion.class, WorldServer.class, World.class, net.minecraft.block.BlockVine.class,
                net.minecraft.command.CommandFill.class, net.minecraft.command.CommandClone.class, MobSpawnerBaseLogic.class,
                net.minecraft.item.ItemArmorStand.class, EntityArmorStand.class};
        c.eq(RulesTransformer.TARGETS.length, load.length, "every target loaded");
        for (String t : RulesTransformer.TARGETS) {
            c.ok(RulesTransformer.APPLIED.containsKey(t), "patched on this server: " + t);
        }
    }

    private static void defaults(Check c) {
        ModConfig.Changes m = ModConfig.changes;
        c.ok(m.leavesWaterlog && m.gameRules && m.spawnBlocks && m.emptySpawners && m.armorStandNames && m.mangroveBonusChest,
                "the low-impact changes are on by default");
        GameRules rules = c.world.getGameRules();
        for (String[] rule : RulesHooks.RULES) {
            c.ok(rules.hasRule(rule[0]), "the world has " + rule[0]);
        }
        GameRules fresh = new GameRules();
        RulesEvents.addRules(fresh);
        for (String[] rule : RulesHooks.RULES) {
            c.eq(rule[1], fresh.getString(rule[0]), "default " + rule[0]);
        }
        c.ok(fresh.areSameType(RulesHooks.SNOW_HEIGHT, GameRules.ValueType.NUMERICAL_VALUE)
                && fresh.areSameType(RulesHooks.TNT_DECAY, GameRules.ValueType.BOOLEAN_VALUE), "rule types");
    }

    // ------------------------------------------------------------------------------------------

    private static int dropped(WorldServer world, BlockPos at, Item item) {
        int n = 0;
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(8))) {
            if (!e.isDead && e.getItem().getItem() == item) {
                n += e.getItem().getCount();
                e.setDead();
            }
        }
        return n;
    }

    /** A 3x3x3 of dirt blown up by an explosion of size 4 at its center: how many blocks went, how many dropped. */
    private static int[] blast(Check c, Entity exploder) {
        WorldServer world = c.world;
        BlockPos center = c.area().add(16, 4, 16);
        c.fill(center.add(-1, -1, -1), center.add(1, 1, 1), Blocks.DIRT.getDefaultState());
        Explosion e = new Explosion(world, exploder, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4.0F, false, true);
        e.doExplosionA();
        int gone = 0;
        for (BlockPos p : e.getAffectedBlockPositions()) {
            gone += world.getBlockState(p).getBlock() == Blocks.DIRT ? 1 : 0;
        }
        e.doExplosionB(false);
        return new int[] {gone, dropped(world, center, Item.getItemFromBlock(Blocks.DIRT))};
    }

    private static void explosions(Check c) {
        WorldServer world = c.world;
        ModConfig.changes.gameRules = true;
        EntityTNTPrimed tnt = new EntityTNTPrimed(world);
        EntityCreeper creeper = new EntityCreeper(world);
        c.eq(RulesHooks.TNT_DECAY, RulesHooks.decayRule(new Explosion(world, tnt, 0, 0, 0, 4, false, true)), "TNT follows tntExplosionDropDecay");
        c.eq(RulesHooks.MOB_DECAY, RulesHooks.decayRule(new Explosion(world, creeper, 0, 0, 0, 3, false, true)), "a creeper mobExplosionDropDecay");
        c.eq(RulesHooks.BLOCK_DECAY, RulesHooks.decayRule(new Explosion(world, null, 0, 0, 0, 5, true, true)), "a bed blockExplosionDropDecay");

        int[] r = blast(c, tnt);
        c.ok(r[0] > 0 && r[0] == r[1], "TNT with tntExplosionDropDecay false: every block drops, " + r[1] + " of " + r[0]);
        rule(world, RulesHooks.TNT_DECAY, "true");
        c.eq(0.25F, RulesHooks.dropChance(0.25F, new Explosion(world, tnt, 0, 0, 0, 4, false, true)), "with it true: one in four");
        rule(world, RulesHooks.TNT_DECAY, "false");
        c.eq(1.0F / 3.0F, RulesHooks.dropChance(1.0F / 3.0F, new Explosion(world, creeper, 0, 0, 0, 3, false, true)), "creepers decay");
        rule(world, RulesHooks.MOB_DECAY, "false");
        c.eq(1.0F, RulesHooks.dropChance(1.0F / 3.0F, new Explosion(world, creeper, 0, 0, 0, 3, false, true)), "mobExplosionDropDecay false");
        rule(world, RulesHooks.MOB_DECAY, "true");
        ModConfig.changes.gameRules = false;
        c.eq(0.25F, RulesHooks.dropChance(0.25F, new Explosion(world, tnt, 0, 0, 0, 4, false, true)), "switch off: TNT decays as in 1.12.2");
        ModConfig.changes.gameRules = true;
    }

    private static void snow(Check c) {
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 0, 8);
        Chunk chunk = world.getChunk(at);
        byte[] biomes = chunk.getBiomeArray();
        byte[] old = biomes.clone();
        java.util.Arrays.fill(biomes, (byte) Biome.getIdForBiome(Biomes.ICE_PLAINS));
        try {
            ModConfig.changes.gameRules = true;
            rule(world, RulesHooks.SNOW_HEIGHT, "1");
            c.ok(RulesHooks.snowAt(world, at, true), "height 1: snow falls on the floor as in 1.12.2");
            world.setBlockState(at, Blocks.SNOW_LAYER.getDefaultState(), 2);
            c.ok(!RulesHooks.snowAt(world, at, true), "height 1: one layer at most");
            c.eq(1, world.getBlockState(at).getValue(BlockSnow.LAYERS), "... still one");
            rule(world, RulesHooks.SNOW_HEIGHT, "3");
            RulesHooks.snowAt(world, at, true);
            c.eq(2, world.getBlockState(at).getValue(BlockSnow.LAYERS), "height 3: a second layer");
            RulesHooks.snowAt(world, at, true);
            RulesHooks.snowAt(world, at, true);
            c.eq(3, world.getBlockState(at).getValue(BlockSnow.LAYERS), "height 3: no fourth");
            rule(world, RulesHooks.SNOW_HEIGHT, "0");
            c.ok(!RulesHooks.snowAt(world, at.east(2), true), "height 0: no snow at all");
            rule(world, RulesHooks.SNOW_HEIGHT, "3");
            ModConfig.changes.gameRules = false;
            world.setBlockState(at, Blocks.SNOW_LAYER.getDefaultState(), 2);
            RulesHooks.snowAt(world, at, true);
            c.eq(1, world.getBlockState(at).getValue(BlockSnow.LAYERS), "switch off: one layer as in 1.12.2");
            ModConfig.changes.gameRules = true;
            rule(world, RulesHooks.SNOW_HEIGHT, "1");
        } finally {
            System.arraycopy(old, 0, biomes, 0, old.length);
        }
    }

    /** Records which way a world event reached a listener. */
    private static final class Ear implements IWorldEventListener {
        final List<String> heard = new ArrayList<String>();

        @Override public void notifyBlockUpdate(World w, BlockPos p, IBlockState a, IBlockState b, int f) { }
        @Override public void notifyLightSet(BlockPos p) { }
        @Override public void markBlockRangeForRenderUpdate(int a, int b, int c, int d, int e, int f) { }
        @Override public void playSoundToAllNearExcept(EntityPlayer p, SoundEvent s, SoundCategory c, double x, double y, double z, float v, float pi) { }
        @Override public void playRecord(SoundEvent s, BlockPos p) { }
        @Override public void spawnParticle(int id, boolean f, double x, double y, double z, double a, double b, double c, int... p) { }
        @Override public void spawnParticle(int id, boolean f, boolean g, double x, double y, double z, double a, double b, double c, int... p) { }
        @Override public void onEntityAdded(Entity e) { }
        @Override public void onEntityRemoved(Entity e) { }
        @Override public void broadcastSound(int id, BlockPos p, int d) { this.heard.add("global " + id); }
        @Override public void playEvent(EntityPlayer pl, int id, BlockPos p, int d) { this.heard.add("local " + id); }
        @Override public void sendBlockBreakProgress(int id, BlockPos p, int pr) { }
    }

    private static void sounds(Check c) {
        WorldServer world = c.world;
        Ear ear = new Ear();
        world.addEventListener(ear);
        try {
            ModConfig.changes.gameRules = true;
            world.playBroadcastSound(1023, BlockPos.ORIGIN, 0);
            rule(world, RulesHooks.GLOBAL_SOUNDS, "false");
            world.playBroadcastSound(1023, BlockPos.ORIGIN, 0);
            rule(world, RulesHooks.GLOBAL_SOUNDS, "true");
            c.eq("[global 1023, local 1023]", ear.heard.toString(), "the wither's spawn: heard everywhere, then (globalSoundEvents false) only nearby");
        } finally {
            world.removeEventListener(ear);
        }
    }

    private static void vines(Check c) {
        WorldServer world = c.world;
        c.eq(0, RulesHooks.vineRoll(0, world), "doVinesSpread true: the vine's roll as it was");
        rule(world, RulesHooks.VINES, "false");
        c.eq(1, RulesHooks.vineRoll(0, world), "doVinesSpread false: a roll that never spreads");
        // A real vine: hanging under a block, with room below, it never grows down.
        BlockPos at = c.area().add(8, 10, 8);
        world.setBlockState(at.up(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(at, Blocks.VINE.getDefaultState().withProperty(net.minecraft.block.BlockVine.NORTH, Boolean.TRUE), 2);
        world.setBlockState(at.north(), Blocks.STONE.getDefaultState(), 2);
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) {
            Blocks.VINE.updateTick(world, at, world.getBlockState(at), r);
        }
        c.ok(world.getBlockState(at.down()).getBlock() != Blocks.VINE, "doVinesSpread false: 200 ticks, no new vine");
        rule(world, RulesHooks.VINES, "true");
        for (int i = 0; i < 200; i++) {
            Blocks.VINE.updateTick(world, at, world.getBlockState(at), r);
        }
        c.ok(world.getBlockState(at.down()).getBlock() == Blocks.VINE, "doVinesSpread true: the vine grows down");
    }

    private static void blockLimit(Check c) {
        WorldServer world = c.world;
        MinecraftServer server = world.getMinecraftServer();
        BlockPos a = c.area().add(0, 1, 0);
        BlockPos b = a.add(31, 39, 31);
        String cmd = "fill " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " minecraft:glass";
        server.getCommandManager().executeCommand(server, cmd);
        c.ok(world.getBlockState(a).getBlock() != Blocks.GLASS, "commandModificationBlockLimit 32768: /fill of 40960 blocks refused");
        rule(world, RulesHooks.BLOCK_LIMIT, "50000");
        c.eq(50000, RulesHooks.blockLimit(server), "the limit follows the rule");
        server.getCommandManager().executeCommand(server, cmd);
        c.ok(world.getBlockState(a).getBlock() == Blocks.GLASS && world.getBlockState(b).getBlock() == Blocks.GLASS,
                "commandModificationBlockLimit 50000: the same /fill done");
        rule(world, RulesHooks.BLOCK_LIMIT, "32768");
        c.fill(a, b, Blocks.AIR.getDefaultState());
    }

    private static void fluidSources(Check c) {
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 0, 8);
        IBlockState water = Blocks.FLOWING_WATER.getDefaultState();
        IBlockState lava = Blocks.FLOWING_LAVA.getDefaultState();
        c.ok(ForgeEventFactory.canCreateFluidSource(world, at, water, true), "waterSourceConversion true: water forms sources");
        c.ok(!ForgeEventFactory.canCreateFluidSource(world, at, lava, false), "lavaSourceConversion false: lava does not");
        rule(world, RulesHooks.WATER_SOURCES, "false");
        rule(world, RulesHooks.LAVA_SOURCES, "true");
        c.ok(!ForgeEventFactory.canCreateFluidSource(world, at, water, true), "waterSourceConversion false: no new water sources");
        c.ok(ForgeEventFactory.canCreateFluidSource(world, at, lava, false), "lavaSourceConversion true: lava forms sources");
        // A real one: lava between two lava sources on stone becomes a source.
        world.setBlockState(at.west(), Blocks.LAVA.getDefaultState(), 2);
        world.setBlockState(at.east(), Blocks.LAVA.getDefaultState(), 2);
        world.setBlockState(at, Blocks.FLOWING_LAVA.getDefaultState().withProperty(net.minecraft.block.BlockLiquid.LEVEL, 1), 2);
        Blocks.FLOWING_LAVA.updateTick(world, at, world.getBlockState(at), new Random(1));
        c.eq(0, world.getBlockState(at).getValue(net.minecraft.block.BlockLiquid.LEVEL), "lava between two sources became a source");
        rule(world, RulesHooks.WATER_SOURCES, "true");
        rule(world, RulesHooks.LAVA_SOURCES, "false");
        c.fill(at.west(), at.east(), Blocks.AIR.getDefaultState());
    }

    // ------------------------------------------------------------------------------------------

    private static void spawnBlocks(Check c) {
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 1, 8);
        world.setBlockState(at.down(), Blocks.CHORUS_FLOWER.getDefaultState(), 2);
        EntityZombie zombie = new EntityZombie(world);
        zombie.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ModConfig.changes.spawnBlocks = true;
        c.eq(Event.Result.DENY, ForgeEventFactory.canEntitySpawn(zombie, world, (float) zombie.posX, (float) zombie.posY,
                (float) zombie.posZ, false), "no spawning on a chorus flower");
        c.eq(Event.Result.DEFAULT, ForgeEventFactory.canEntitySpawn(zombie, world, (float) zombie.posX, (float) zombie.posY,
                (float) zombie.posZ, true), "from a spawner, as in 1.19.3");
        Block scaffolding = Block.REGISTRY.getObject(new ResourceLocation(Owners.VILLAGE_AND_PILLAGE, "scaffolding"));
        if (scaffolding != Blocks.AIR) {
            world.setBlockState(at.down(), scaffolding.getDefaultState(), 2);
            c.eq(Event.Result.DENY, ForgeEventFactory.canEntitySpawn(zombie, world, (float) zombie.posX, (float) zombie.posY,
                    (float) zombie.posZ, false), "no spawning on scaffolding");
        }
        world.setBlockState(at.down(), Blocks.CHORUS_FLOWER.getDefaultState(), 2);
        ModConfig.changes.spawnBlocks = false;
        c.eq(Event.Result.DEFAULT, ForgeEventFactory.canEntitySpawn(zombie, world, (float) zombie.posX, (float) zombie.posY,
                (float) zombie.posZ, false), "switch off: left to 1.12.2");
    }

    private static boolean activated(MobSpawnerBaseLogic logic) throws Exception {
        Method m = ObfuscationReflectionHelper.findMethod(MobSpawnerBaseLogic.class, "func_98279_f", boolean.class);
        return (Boolean) m.invoke(logic);
    }

    private static void spawners(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 1, 8);
        EntityPlayer player = c.player("spawners", at.add(2, 0, 2));
        world.playerEntities.add(player);
        try {
            ModConfig.changes.emptySpawners = true;
            world.setBlockState(at, Blocks.MOB_SPAWNER.getDefaultState(), 2);
            TileEntity te = world.getTileEntity(at);
            c.ok(te instanceof TileEntityMobSpawner, "a spawner placed");
            MobSpawnerBaseLogic logic = ((TileEntityMobSpawner) te).getSpawnerBaseLogic();
            c.ok(RulesHooks.isEmpty(logic), "it starts with no mob");
            c.ok(!activated(logic), "an empty spawner is never active, a player beside it or not");
            for (int i = 0; i < 1000; i++) {
                logic.updateSpawner();
            }
            c.eq(0, world.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, new AxisAlignedBB(at).grow(9)).size(),
                    "1000 ticks: nothing spawned");
            c.eq(null, RulesHooks.spawnerEntity(new net.minecraft.nbt.NBTTagCompound(), world, false), "no mob drawn inside");
            logic.setEntityId(new ResourceLocation("minecraft:zombie"));
            c.ok(activated(logic), "with a spawn egg's mob: active with a player near");
            world.setBlockToAir(at);

            ModConfig.changes.emptySpawners = false;
            world.setBlockState(at, Blocks.MOB_SPAWNER.getDefaultState(), 2);
            MobSpawnerBaseLogic pig = ((TileEntityMobSpawner) world.getTileEntity(at)).getSpawnerBaseLogic();
            c.ok(!RulesHooks.isEmpty(pig), "switch off: a pig spawner as in 1.12.2");
            world.setBlockToAir(at);
        } finally {
            world.playerEntities.remove(player);
        }
    }

    private static void armorStands(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos base = c.area().add(8, 0, 8);
        EntityPlayer player = c.player("stands", base.add(0, 1, 3));
        for (boolean on : new boolean[] {true, false}) {
            BlockPos at = on ? base : base.add(8, 0, 0);
            ModConfig.changes.armorStandNames = on;
            ItemStack stack = new ItemStack(Items.ARMOR_STAND);
            stack.setStackDisplayName("Bob");
            player.setHeldItem(EnumHand.MAIN_HAND, stack);
            Items.ARMOR_STAND.onItemUse(player, world, at, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
            List<EntityArmorStand> stands = new ArrayList<EntityArmorStand>();
            for (EntityArmorStand e : world.getEntitiesWithinAABB(EntityArmorStand.class, new AxisAlignedBB(at).grow(2))) {
                if (!e.isDead) {
                    stands.add(e);
                }
            }
            if (!c.eq(1, stands.size(), "an armor stand placed")) {
                continue;
            }
            EntityArmorStand stand = stands.get(0);
            c.eq(on ? "Bob" : "", stand.getCustomNameTag(), on ? "the stand takes the item's name" : "switch off: no name");
            c.ok(!stand.getAlwaysRenderNameTag(), "the name is not shown unless set to be");
            stand.setCustomNameTag("Bob");
            Method drop = ObfuscationReflectionHelper.findMethod(EntityArmorStand.class, "func_175421_A", void.class);
            drop.invoke(stand);
            stand.setDead();
            String name = null;
            for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(2))) {
                if (e.getItem().getItem() == Items.ARMOR_STAND) {
                    name = e.getItem().hasDisplayName() ? e.getItem().getDisplayName() : "";
                    e.setDead();
                }
            }
            c.eq(on ? "Bob" : "", name, on ? "the broken stand drops an item with its name" : "switch off: the item has no name");
        }
    }

    // ------------------------------------------------------------------------------------------

    private static void bonusChest(Check c) {
        WorldServer world = c.world;
        LootTable table = world.getLootTableManager().getLootTableFromLocation(LootTableList.CHESTS_SPAWN_BONUS_CHEST);
        Item log = Item.getItemFromBlock(Owners.ourBlock("mangrove_log"));
        int found = 0;
        Random r = new Random(7);
        for (int i = 0; i < 200; i++) {
            for (ItemStack s : table.generateLootForPools(r, new LootContext.Builder(world).build())) {
                found += s.getItem() == log ? 1 : 0;
            }
        }
        c.ok(found > 0, "bonus chests hold mangrove logs, " + found + " times in 200 chests");
    }

    private static void leaves(Check c) {
        if (!Waterlogging.isAvailable()) {
            c.ok(true, "waterlogged leaves: Fluidlogged API not installed, no block holds water");
            return;
        }
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 1, 8);
        IBlockState[] all = {Blocks.LEAVES.getDefaultState(), Blocks.LEAVES2.getDefaultState(),
                Owners.ourBlock("mangrove_leaves").getDefaultState()};
        ModConfig.changes.leavesWaterlog = true;
        for (int i = 0; i < all.length; i++) {
            BlockPos p = at.add(i * 2, 0, 0);
            world.setBlockState(p, all[i], 2);
            c.ok(Waterlogging.canHoldWater(all[i], world, p) && Waterlogging.setWater(world, p, true, 2)
                    && Waterlogging.hasWaterSource(world, p), "waterlogged: " + all[i].getBlock().getRegistryName());
            Waterlogging.setWater(world, p, false, 2);
        }
        Block azalea = Block.REGISTRY.getObject(new ResourceLocation("cavesandcliffsbackport", "azalea_leaves"));
        if (azalea != Blocks.AIR) {
            BlockPos p = at.add(0, 0, 4);
            world.setBlockState(p, azalea.getDefaultState(), 2);
            c.ok(Waterlogging.canHoldWater(azalea.getDefaultState(), world, p), "Caves & Cliffs' azalea leaves too");
        }
        ModConfig.changes.leavesWaterlog = false;
        BlockPos p = at.add(0, 0, 8);
        world.setBlockState(p, Blocks.LEAVES.getDefaultState(), 2);
        c.ok(!Waterlogging.canHoldWater(Blocks.LEAVES.getDefaultState(), world, p), "switch off: oak leaves hold no water");
        ModConfig.changes.leavesWaterlog = true;
    }

    // ------------------------------------------------------------------------------------------

    private static byte[] classBytes(String name) throws IOException {
        InputStream in = RulesSelfTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
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
