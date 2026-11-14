package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.backportcore.worldgen.FlatWorlds;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import com.exiledradio.wildupdatebackport.swamp.SwampContent;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.TreeMap;

/**
 * Superflat worlds generate only what vanilla 1.12 does for their preset ({@code tools/flat/selftest.sh}, run by name
 * only, in a superflat world): no ancient cities ever; none of this mod's blocks without {@code decoration}; with it,
 * the mangrove swamp's decoration where the preset's biome is the mangrove swamp and the deep dark's features where
 * it is the deep dark (only there: superflat has one biome, the preset's); no mob of this mod (nor any other
 * but a village's) spawned while chunks generate.
 */
public final class FlatSelfTest implements SelfTest, SelfTest.ByNameOnly {

    /** Fresh chunks, far from the spawn area the server generated before the test. */
    private static final int CX = 600, CZ = 600, R = 6;

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public void run(Check c) throws Exception {
        WorldServer world = c.world;
        String options = world.getWorldInfo().getGeneratorOptions();
        if (!c.ok(FlatWorlds.isFlat(world), "the world is superflat (" + world.getWorldInfo().getTerrainType().getName() + ")")) {
            return;
        }
        boolean decoration = options.contains("decoration");
        c.eq(decoration, FlatWorlds.decoration(world), "decoration as the preset says (" + options + ")");
        Biome biome = world.getBiomeProvider().getBiome(new BlockPos(CX << 4, 64, CZ << 4), null);
        boolean mangroveBiome = biome == SwampContent.biome();
        boolean deepDarkBiome = biome == DeepDarkContent.biome();
        WildUpdateBackport.LOGGER.info("SELFTEST flat: preset {}, biome {} (mangrove swamp id {}, deep dark id {})", options,
                biome.getRegistryName(), Biome.getIdForBiome(SwampContent.biome()),
                Biome.getIdForBiome(DeepDarkContent.biome()));

        // Generate (and so populate) fresh chunks, counting what joins the world meanwhile.
        Spawns spawns = new Spawns();
        MinecraftForge.EVENT_BUS.register(spawns);
        try {
            for (int x = CX - R; x <= CX + R; x++) {
                for (int z = CZ - R; z <= CZ + R; z++) {
                    world.getChunkProvider().provideChunk(x, z);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(spawns);
        }
        WildUpdateBackport.LOGGER.info("SELFTEST flat: joined at generation {}", spawns.joined);
        c.eq(0, spawns.ours, "no mob of this mod spawned at generation " + spawns.joined);
        c.eq(0, spawns.others, "no other mob spawned at generation (a village's aside) " + spawns.joined);

        // The populated chunks' blocks of this mod.
        Map<String, Integer> blocks = new TreeMap<String, Integer>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - R + 1; x <= CX + R - 1; x++) {
            for (int z = CZ - R + 1; z <= CZ + R - 1; z++) {
                Chunk chunk = world.getChunk(x, z);
                c.ok(chunk.isTerrainPopulated(), "chunk " + x + "," + z + " populated");
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < 128; y++) {
                            p.setPos(lx, y, lz);
                            Block block = chunk.getBlockState(p).getBlock();
                            ResourceLocation id = block.getRegistryName();
                            if (id != null && WildUpdateBackport.MOD_ID.equals(id.getNamespace())) {
                                Integer n = blocks.get(id.getPath());
                                blocks.put(id.getPath(), n == null ? 1 : n + 1);
                            }
                        }
                    }
                }
            }
        }
        WildUpdateBackport.LOGGER.info("SELFTEST flat: this mod's blocks {}", blocks);
        int sculk = count(blocks, "sculk"), deepslate = count(blocks, "deepslate"), mangrove = count(blocks, "mangrove"),
                mud = count(blocks, "mud"), frogspawn = count(blocks, "frogspawn");
        c.eq(0, deepslate, "no deepslate or city blocks (the layers are the preset's) " + blocks);
        c.eq(0, mud, "no mud (the surface is the preset's) " + blocks);
        c.eq(0, frogspawn, "no frogspawn " + blocks);
        if (!decoration) {
            c.eq(0, sculk + mangrove, "without decoration none of this mod's features " + blocks);
            c.ok(blocks.isEmpty(), "without decoration none of this mod's blocks " + blocks);
        } else {
            if (mangroveBiome) {
                c.ok(blocks.containsKey("mangrove_log") && blocks.containsKey("mangrove_leaves") && blocks.containsKey("mangrove_roots"),
                        "the mangrove swamp's decoration grows mangroves " + blocks);
            } else {
                c.eq(0, mangrove, "no mangroves outside the mangrove swamp " + blocks);
            }
            // Superflat's one biome: the deep dark's features only in a deep dark preset, at any height.
            if (deepDarkBiome) {
                c.ok(blocks.containsKey("sculk") && blocks.containsKey("sculk_vein"), "the deep dark's features in a deep dark preset " + blocks);
            } else {
                c.eq(0, sculk, "no deep dark features outside a deep dark preset (high ground included) " + blocks);
            }
        }

        // Ancient cities: none, in any preset.
        c.ok(ScatteredStructure.of(world, Structures.ANCIENT_CITY) == null, "no ancient city instance");
        c.ok(Structures.nearest(world, Structures.ANCIENT_CITY, new BlockPos(CX << 4, 0, CZ << 4), 64) == null,
                "the locator finds no ancient city");
        c.ok(StructureLocate.nearest(world, world.getSpawnPoint(), "minecraft:ancient_city") == null, "/locate finds no ancient city");
        c.ok(!FlatWorlds.structure(world, Structures.ANCIENT_CITY), "FlatWorlds has no ancient cities");
    }

    private static int count(Map<String, Integer> blocks, String part) {
        int n = 0;
        for (Map.Entry<String, Integer> e : blocks.entrySet()) {
            if (e.getKey().contains(part)) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Every mob that joins the world while the test generates chunks. */
    public static final class Spawns {
        final Map<String, Integer> joined = new TreeMap<String, Integer>();
        int ours;
        int others;

        @SubscribeEvent
        public void join(EntityJoinWorldEvent event) {
            Entity e = event.getEntity();
            if (!(e instanceof EntityLiving) || e.world.isRemote) {
                return;
            }
            ResourceLocation id = EntityList.getKey(e);
            String name = id == null ? e.getClass().getName() : id.toString();
            Integer n = this.joined.get(name);
            this.joined.put(name, n == null ? 1 : n + 1);
            if (id != null && WildUpdateBackport.MOD_ID.equals(id.getNamespace())) {
                this.ours++;
            } else if (!(e instanceof EntityVillager) && !(e instanceof EntityIronGolem)) {
                this.others++;
            }
        }
    }
}
