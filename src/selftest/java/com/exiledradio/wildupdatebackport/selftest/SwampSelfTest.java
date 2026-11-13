package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.asm.SwampTransformer;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.frog.EntityFrog;
import com.exiledradio.wildupdatebackport.frog.FrogSpawns;
import com.exiledradio.wildupdatebackport.frog.FrogVariant;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mud.MudContent;
import com.exiledradio.wildupdatebackport.swamp.BiomeMangroveSwamp;
import com.exiledradio.wildupdatebackport.swamp.MangroveSwampDecorator;
import com.exiledradio.wildupdatebackport.swamp.SwampContent;
import com.exiledradio.wildupdatebackport.swamp.SwampHooks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerShore;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Stream Sw: the mangrove swamp. */
public final class SwampSelfTest implements SelfTest {

    @Override
    public String name() {
        return "swamp";
    }

    @Override
    public void run(Check c) throws Exception {
        BiomeMangroveSwamp biome = SwampContent.biome();
        if (!c.ok(biome != null, "the mangrove swamp is registered")) {
            return;
        }
        c.eq(biome, ForgeRegistries.BIOMES.getValue(new ResourceLocation("wildupdatebackport", "mangrove_swamp")),
                "registered as wildupdatebackport:mangrove_swamp");
        properties(c, biome);
        spawns(c, biome);
        placement(c, biome);
        shore(c, biome);
        surface(c, biome);
        plants(c);
        features(c);
        world(c, biome);
        cavesAndCliffs(c, biome);
    }

    private static void properties(Check c, BiomeMangroveSwamp biome) {
        c.ok(Math.abs(biome.getDefaultTemperature() - 0.8F) < 1e-6, "temperature 0.8");
        c.ok(Math.abs(biome.getRainfall() - 0.9F) < 1e-6, "downfall 0.9");
        c.ok(biome.canRain() && !biome.getEnableSnow(), "it rains, never snows");
        c.ok(Math.abs(biome.getBaseHeight() + 0.2F) < 1e-6 && Math.abs(biome.getHeightVariation() - 0.1F) < 1e-6,
                "the swamp's height (-0.2, 0.1)");
        c.ok(BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP) && BiomeDictionary.hasType(biome, BiomeDictionary.Type.WET),
                "a swamp in Forge's biome dictionary (Update Aquatic gives every swamp 1.19's swamp seagrass)");

        // 0x3A7A6A over 1.13's blue 0x3F76E4: 58*255/63, min(255, 122*255/118), 106*255/228.
        c.eq(0xEAFF76, BiomeMangroveSwamp.WATER_TINT, "the 1.12.2 water tint");
        c.eq(BiomeMangroveSwamp.WATER_TINT, biome.getWaterColorMultiplier(), "the biome's water color is the tint");
        BiomeEvent.GetWaterColor modern = new BiomeEvent.GetWaterColor(biome, 0x3F76E4);
        MinecraftForge.EVENT_BUS.post(modern);
        c.eq(0x3A7A6A, modern.getNewColor(), "with 1.13's water texture in place, 1.19's water color 0x3A7A6A");
        BiomeEvent.GetWaterColor other = new BiomeEvent.GetWaterColor(Biomes.PLAINS, 0xFFFFFF);
        MinecraftForge.EVENT_BUS.post(other);
        c.ok(other.getNewColor() != 0x3A7A6A, "other biomes' water is left alone");

        boolean dark = false;
        boolean light = false;
        for (int x = 0; x < 4000; x += 37) {
            int g = BiomeMangroveSwamp.grassColor(new BlockPos(x, 64, x * 3));
            c.ok(g == 0x4C763C || g == 0x6A7039, "grass is one of the swamp's two colors");
            dark |= g == 0x4C763C;
            light |= g == 0x6A7039;
        }
        c.ok(dark && light, "both swamp grass colors occur");
        c.eq(0x8DB127, BiomeMangroveSwamp.FOLIAGE_COLOR, "foliage 0x8DB127");
    }

    private static void spawns(Check c, BiomeMangroveSwamp biome) {
        List<Biome.SpawnListEntry> monsters = biome.getSpawnableList(EnumCreatureType.MONSTER);
        int slimes = 0;
        for (Biome.SpawnListEntry e : monsters) {
            if (e.entityClass == EntitySlime.class) {
                slimes++;
                c.ok(e.itemWeight == 100 && e.minGroupCount == 4 || e.itemWeight == 1 && e.minGroupCount == 1 && e.maxGroupCount == 1,
                        "slime entries are 100 (4) and 1 (1-1)");
            }
        }
        c.eq(2, slimes, "two slime entries, as 1.19's mangrove swamp");
        c.eq(9, monsters.size(), "the eight common monsters and the extra slime");

        List<Biome.SpawnListEntry> creatures = biome.getSpawnableList(EnumCreatureType.CREATURE);
        for (Biome.SpawnListEntry e : creatures) {
            c.eq(EntityFrog.class, e.entityClass, "the only creature is the frog");
        }
        c.ok(FrogSpawns.spawnsIn(biome), "frogs spawn in the mangrove swamp");
        c.eq(FrogVariant.WARM, FrogSpawns.variantFor(biome), "frogs here are warm");
        if (!creatures.isEmpty()) {
            Biome.SpawnListEntry f = creatures.get(0);
            c.ok(f.itemWeight == 10 && f.minGroupCount == 2 && f.maxGroupCount == 5, "frogs 10, 2-5");
        } else {
            c.fail("frogs are in the creature list");
        }
        c.ok(biome.getSpawnableList(EnumCreatureType.WATER_CREATURE).isEmpty(), "no squid");
        List<Biome.SpawnListEntry> ambient = biome.getSpawnableList(EnumCreatureType.AMBIENT);
        c.ok(ambient.size() == 1 && ambient.get(0).entityClass == EntityBat.class, "bats");

        EntitySlime slime = new EntitySlime(c.world);
        slime.setPosition(0.5, 80, 0.5);
        c.ok(!surfaceRule(slime), "no surface slimes above y 70");
        slime.setPosition(0.5, 45, 0.5);
        c.ok(!surfaceRule(slime), "nor below y 50");
    }

    private static boolean surfaceRule(EntitySlime slime) {
        try {
            java.lang.reflect.Method m = SwampContent.class.getDeclaredMethod("surfaceRule", net.minecraft.world.World.class,
                    net.minecraft.entity.EntityLiving.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, slime.world, slime);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void placement(Check c, BiomeMangroveSwamp biome) {
        c.ok(SwampContent.placed(), "in the hot band");
        c.eq(10, weight(biome), "at the swamp's weight 10");
        c.eq(10, weightIn(BiomeManager.BiomeType.WARM, Biomes.SWAMPLAND), "(the swamp's own weight in the warm band)");
        ModConfig.swamp.enabled = false;
        SwampContent.applyPlacement();
        c.eq(-1, weight(biome), "swamp.enabled off takes it out for the next world");
        ModConfig.swamp.enabled = true;
        SwampContent.applyPlacement();
        c.eq(10, weight(biome), "and on puts it back");
        for (BiomeManager.BiomeType t : new BiomeManager.BiomeType[] {BiomeManager.BiomeType.WARM, BiomeManager.BiomeType.COOL,
                BiomeManager.BiomeType.ICY}) {
            c.eq(-1, weightIn(t, biome), "not in the " + t + " band");
        }
    }

    private static int weight(Biome biome) {
        return weightIn(BiomeManager.BiomeType.DESERT, biome);
    }

    private static int weightIn(BiomeManager.BiomeType type, Biome biome) {
        List<BiomeManager.BiomeEntry> list = BiomeManager.getBiomes(type);
        if (list == null) {
            return -1;
        }
        for (BiomeManager.BiomeEntry e : list) {
            if (e.biome == biome) {
                return e.itemWeight;
            }
        }
        return -1;
    }

    // --- P9 -------------------------------------------------------------------------------------

    private static void shore(Check c, BiomeMangroveSwamp biome) throws IOException {
        c.ok(SwampTransformer.appliedP9, "P9 patched GenLayerShore as the world's layers loaded");
        int ours = Biome.getIdForBiome(biome);
        c.eq(Biome.getIdForBiome(Biomes.SWAMPLAND), SwampHooks.shoreBiome(ours), "the hook answers the swamp for the mangrove swamp");
        c.eq(1, SwampHooks.shoreBiome(1), "and every other id is itself");
        c.eq(ours, shoreOf(ours), "a mangrove swamp cell beside the ocean stays a mangrove swamp (no beach), as the swamp");
        c.eq(Biome.getIdForBiome(Biomes.SWAMPLAND), shoreOf(Biome.getIdForBiome(Biomes.SWAMPLAND)), "the swamp too");
        c.eq(Biome.getIdForBiome(Biomes.BEACH), shoreOf(Biome.getIdForBiome(Biomes.PLAINS)), "plains beside the ocean get a beach");
        c.eq(ours, shoreOf(ours, Biome.getIdForBiome(Biomes.PLAINS)), "inland it is itself");

        byte[] dev = read(SwampSelfTest.class.getResourceAsStream("/net/minecraft/world/gen/layer/GenLayerShore.class"));
        verify(c, "dev GenLayerShore", SwampTransformer.patch(dev));
        File srg = new File(System.getProperty("user.home"),
                ".gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar");
        if (!c.ok(srg.isFile(), "Forge's SRG jar is in the Gradle cache")) {
            return;
        }
        ZipFile zip = new ZipFile(srg);
        try {
            ZipEntry e = zip.getEntry("net/minecraft/world/gen/layer/GenLayerShore.class");
            verify(c, "SRG GenLayerShore", e == null ? null : SwampTransformer.patch(read(zip.getInputStream(e))));
        } finally {
            zip.close();
        }
    }

    private static int shoreOf(int center) {
        return shoreOf(center, Biome.getIdForBiome(Biomes.OCEAN));
    }

    /** GenLayerShore over a 3 x 3 parent: the center and its four neighbors. */
    private static int shoreOf(final int center, final int around) {
        GenLayer parent = new GenLayer(1L) {
            @Override
            public int[] getInts(int x, int z, int w, int h) {
                int[] a = new int[w * h];
                java.util.Arrays.fill(a, around);
                a[(h / 2) * w + w / 2] = center;
                return a;
            }
        };
        GenLayerShore shore = new GenLayerShore(1000L, parent);
        shore.initWorldGenSeed(42L);
        return shore.getInts(0, 0, 1, 1)[0];
    }

    private static void verify(Check c, String what, byte[] patched) {
        if (!c.ok(patched != null, what + " was patched")) {
            return;
        }
        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        int hooks = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.endsWith("swamp/SwampHooks")) {
                    hooks++;
                }
            }
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (Exception e) {
                c.fail(what + "." + m.name + " fails ASM's stack check: " + e);
            }
        }
        c.eq(1, hooks, what + ": one call to SwampHooks.shoreBiome");
    }

    private static byte[] read(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
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

    // --- surface --------------------------------------------------------------------------------

    private static void surface(Check c, BiomeMangroveSwamp biome) {
        Block mud = MudContent.mud();
        int pools = 0;
        int mudFloors = 0;
        int bad = 0;
        Random rand = new Random(5L);
        for (int cx = 0; cx < 8; cx++) {
            ChunkPrimer primer = new ChunkPrimer();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // A floor at y 61 under the sea in the west half, land up to y 70 in the east half.
                    int top = x < 8 ? 61 : 70;
                    for (int y = 0; y <= top; y++) {
                        primer.setBlockState(x, y, z, Blocks.STONE.getDefaultState());
                    }
                    for (int y = top + 1; y < 63; y++) {
                        primer.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
                    }
                }
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    biome.genTerrainBlocks(c.world, rand, primer, cx * 16 + x, 300 + z, 0.0D);
                }
            }
            // Read in the primer's own coordinates, as it was filled (genTerrainBlocks indexes it z, y, x).
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (x < 8) {
                        Block at61 = primer.getBlockState(x, 61, z).getBlock();
                        if (at61 == Blocks.WATER) {
                            pools++;
                            bad += primer.getBlockState(x, 60, z).getBlock() == mud ? 0 : 1;
                        } else {
                            mudFloors += at61 == mud ? 1 : 0;
                            bad += at61 == mud ? 0 : 1;
                        }
                    } else {
                        bad += primer.getBlockState(x, 70, z).getBlock() == mud && primer.getBlockState(x, 68, z).getBlock() == mud ? 0 : 1;
                    }
                }
            }
        }
        c.ok(pools > 0, "floors at y 61 become water where the swamp noise is above 0 (" + pools + ")");
        c.ok(mudFloors > 0, "elsewhere they are mud (" + mudFloors + ")");
        c.eq(0, bad, "mud on the floor and under it, under water and on land");
    }

    // --- plants on mud ---------------------------------------------------------------------------

    private static void plants(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        Block mud = MudContent.mud();
        BlockPos soil = area.add(4, -1, 4);
        world.setBlockState(soil, mud.getDefaultState(), 2);
        BlockPos p = soil.up();
        IBlockState grass = Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
        c.ok(Blocks.TALLGRASS.canBlockStay(world, p, grass), "grass stands on mud (1.19's dirt tag)");
        c.ok(Blocks.DEADBUSH.canBlockStay(world, p, Blocks.DEADBUSH.getDefaultState()), "dead bushes stand on mud");
        c.ok(((net.minecraft.block.BlockBush) Blocks.SAPLING).canBlockStay(world, p, Blocks.SAPLING.getDefaultState()), "saplings stand on mud");
        c.ok(((net.minecraft.block.BlockBush) Blocks.RED_FLOWER).canBlockStay(world, p, Blocks.RED_FLOWER.getDefaultState()), "flowers stand on mud");
        c.ok(!Blocks.CACTUS.canBlockStay(world, p), "cactus does not");
        world.setBlockState(p, grass, 3);
        world.setBlockState(soil.east(), Blocks.STONE.getDefaultState(), 3);
        c.eq(Blocks.TALLGRASS, world.getBlockState(p).getBlock(), "grass on mud survives a neighbor update");
    }

    // --- features on test plots ----------------------------------------------------------------

    private static void features(Check c) {
        WorldServer world = c.world;
        Block mud = MudContent.mud();
        BlockPos area = c.area();
        // A mud floor at y 94-99, water over its west half.
        c.fill(area.add(0, -6, 0), area.add(31, -1, 31), mud.getDefaultState());
        c.fill(area.add(0, 0, 0), area.add(15, 0, 31), Blocks.WATER.getDefaultState());
        BlockPos center = area.add(16, -1, 16);
        invoke("diskGrass", world, new Random(3L), center);
        c.eq(Blocks.GRASS, world.getBlockState(center.add(1, 0, 0)).getBlock(), "disk_grass: open mud becomes grass");
        c.eq(Blocks.DIRT, world.getBlockState(center.add(-1, 0, 0)).getBlock(), "under water it becomes dirt");
        c.eq(Blocks.DIRT, world.getBlockState(center.add(1, -1, 0)).getBlock(), "and under the new block, dirt");
        c.eq(Blocks.DIRT, world.getBlockState(center.add(1, -2, 0)).getBlock(), "down to 2 below its center");
        c.eq(mud, world.getBlockState(center.add(1, -3, 0)).getBlock(), "below that, mud stays");
        c.eq(mud, world.getBlockState(center.add(7, 0, 0)).getBlock(), "outside its radius of at most 6, mud stays");

        world.setBlockState(area.add(4, -1, 4), Blocks.DIRT.getDefaultState(), 2);
        invoke("diskClay", world, new Random(3L), area.add(4, 0, 4));
        c.eq(Blocks.CLAY, world.getBlockState(area.add(4, -1, 4)).getBlock(), "disk_clay: dirt under water becomes clay");
        c.eq(mud, world.getBlockState(area.add(5, -1, 4)).getBlock(), "disk_clay leaves mud alone");

        c.ok(MangroveSwampDecorator.oceanFloor(world, area.add(4, 0, 4)).equals(area.add(4, 0, 4)),
                "the ocean floor heightmap stands on the mud under water");
        c.ok(MangroveSwampDecorator.worldSurface(world, area.add(4, 0, 4)).equals(area.add(4, 1, 4)),
                "the world surface heightmap stands on the water");

        BlockPos dry = c.area();
        c.fill(dry.add(0, -1, 0), dry.add(31, -1, 31), mud.getDefaultState());
        IBlockState grass = Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
        int placed = (Integer) invoke("patch", world, new Random(1L), dry.add(16, 0, 16), grass, 32);
        c.ok(placed > 3 && placed <= 32, "a grass patch places on mud (" + placed + " of 32 tries)");
        int lilies = (Integer) invoke("patch", world, new Random(1L), area.add(8, 1, 16), Blocks.WATERLILY.getDefaultState(), 40);
        c.ok(lilies > 0, "a lily pad patch places on water (" + lilies + " of 40)");
        int none = (Integer) invoke("patch", world, new Random(1L), area.add(8, 1, 16), Blocks.DEADBUSH.getDefaultState(), 4);
        c.eq(0, none, "dead bushes find no ground on water");

        c.ok((Boolean) invoke("propaguleSurvives", world, area.add(4, 0, 4)), "a propagule would stand on the mud under water");
        world.setBlockState(dry.add(2, -1, 2), Blocks.STONE.getDefaultState(), 2);
        c.ok(!(Boolean) invoke("propaguleSurvives", world, dry.add(2, 0, 2)), "not on stone");
    }

    private static Object invoke(String name, Object... args) {
        for (java.lang.reflect.Method m : MangroveSwampDecorator.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    // --- a real mangrove swamp -------------------------------------------------------------------

    private static void world(Check c, BiomeMangroveSwamp biome) {
        WorldServer world = c.world;
        BlockPos found = null;
        Random rand = new Random(world.getSeed());
        for (int range = 512; range <= 6144 && found == null; range *= 2) {
            found = world.getBiomeProvider().findBiomePosition(0, 0, range, Collections.<Biome>singletonList(biome), rand);
        }
        if (!c.ok(found != null, "a mangrove swamp generates within 6,144 blocks of 0, 0 (seed " + world.getSeed() + ")")) {
            return;
        }
        WildUpdateBackportLog.info("Mangrove swamp at " + found);
        int cx = found.getX() >> 4;
        int cz = found.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.getChunk(cx + dx, cz + dz);
            }
        }
        Block mud = MudContent.mud();
        Block log = MangroveContent.log().getBlock();
        Block roots = MangroveContent.roots().getBlock();
        Block leaves = MangroveContent.leaves();
        int columns = 0;
        int mudTops = 0;
        int logs = 0;
        int rootCount = 0;
        int leafCount = 0;
        int propagules = 0;
        int grass = 0;
        int lilies = 0;
        int foreign = 0;
        java.util.Set<String> foreignNames = new java.util.TreeSet<String>();
        int pools = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = world.getChunk(cx + dx, cz + dz);
                c.ok(chunk.isTerrainPopulated(), "chunk " + chunk.x + ", " + chunk.z + " is decorated");
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos col = new BlockPos(chunk.x * 16 + x, 0, chunk.z * 16 + z);
                        if (world.getBiome(col) != biome) {
                            continue;
                        }
                        columns++;
                        // Oak logs only where no other biome's decoration reaches: 1.12.2 decorates 8 blocks into the
                        // next chunk, and a big oak beside it spreads further, so a border tree can stand here.
                        for (int y = 40; y < 140; y++) {
                            Block b = chunk.getBlockState(x, y, z).getBlock();
                            if (b == log) {
                                logs++;
                            } else if (b == roots || b == Owners.ourBlock("muddy_mangrove_roots")) {
                                rootCount++;
                            } else if (b == leaves) {
                                leafCount++;
                            } else if (b == MangroveContent.propagule()) {
                                propagules++;
                            } else if (b == Blocks.TALLGRASS) {
                                grass++;
                            } else if (b == Blocks.WATERLILY) {
                                lilies++;
                            } else if (b == Blocks.LOG && dx == 0 && dz == 0 && x >= 4 && x < 12 && z >= 4 && z < 12
                                    || b == Blocks.REEDS || b == Blocks.PUMPKIN || b == Blocks.RED_FLOWER
                                    || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM) {
                                foreign++;
                                foreignNames.add(b.getRegistryName() + " at " + col.getX() + " " + y + " " + col.getZ());
                            }
                        }
                        // The ground under any tree, roots, water and plants.
                        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(col.getX(), 140, col.getZ());
                        while (p.getY() > 40) {
                            p.setY(p.getY() - 1);
                            IBlockState s = world.getBlockState(p);
                            Block b = s.getBlock();
                            if (s.getMaterial() == Material.WATER && p.getY() >= 60 && p.getY() <= 62
                                    && world.getBlockState(p.down()).getMaterial() != Material.WATER) {
                                pools++;
                            }
                            if (b == mud || b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.CLAY || b == Blocks.SAND
                                    || b == Blocks.GRAVEL || b == Blocks.STONE) {
                                mudTops += b == mud ? 1 : 0;
                                break;
                            }
                        }
                    }
                }
            }
        }
        WildUpdateBackportLog.info("Mangrove swamp 3x3 chunks: " + columns + " columns, " + mudTops + " mud tops, " + logs + " logs, "
                + rootCount + " roots, " + leafCount + " leaves, " + propagules + " propagules, " + grass + " grass, " + lilies
                + " lily pads, " + pools + " pool floors");
        if (!c.ok(columns >= 64, "the 3 x 3 chunks around it are mostly mangrove swamp (" + columns + " columns)")) {
            return;
        }
        c.ok(mudTops * 2 > columns, "most of the ground is mud (" + mudTops + " of " + columns + ")");
        c.ok(logs > 0 && rootCount > 0 && leafCount > 0, "mangrove trees with roots and leaves");
        c.ok(propagules > 0, "hanging propagules");
        c.ok(grass > 0, "grass");
        c.eq(0, foreign, "none of 1.12.2's swamp decoration (oak trees, sugar cane, pumpkins, orchids, mushrooms) " + foreignNames);
        c.ok(lilies > 0 || pools == 0, "lily pads on the water");
    }

    /** Logs through the runner's logger. */
    private static final class WildUpdateBackportLog {
        static void info(String s) {
            com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info("SELFTEST swamp: {}", s);
        }
    }

    // --- Caves & Cliffs' 1.18 terrain -----------------------------------------------------------

    /**
     * With Caves &amp; Cliffs installed: its BiomeMap takes this biome for 1.19's mangrove swamp, and, in a world with
     * its 1.18 terrain and the 1.19 biomes, its mangrove swamps are this biome with our trees.
     */
    private static void cavesAndCliffs(Check c, BiomeMangroveSwamp biome) throws Exception {
        if (!Loader.isModLoaded(Owners.CAVES_AND_CLIFFS)) {
            WildUpdateBackportLog.info("Caves & Cliffs is not installed; its part is skipped");
            return;
        }
        Class<?> map = Class.forName("com.exiledradio.cavesandcliffsbackport.terrain.BiomeMap");
        Class<?> ids = Class.forName("com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes");
        int modern = ids.getField("MANGROVE_SWAMP").getInt(null);
        Object m = map.getConstructor().newInstance();
        c.eq(biome, map.getMethod("biome", int.class).invoke(m, modern), "Caves & Cliffs' BiomeMap finds our mangrove swamp");
        c.ok((Boolean) map.getMethod("registered", int.class).invoke(m, modern), "as a registered modern biome, not a stand-in");
        c.ok(SwampContent.generatorPlaces(c.world, "trees_mangrove") || !(c.world.getChunkProvider().chunkGenerator.getClass()
                .getName().contains("cavesandcliffs")), "its generator says it places trees_mangrove itself");
    }
}
