package com.exiledradio.wildupdatebackport.allay;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Where allays generate.
 *
 * <h2>Woodland mansions (vanilla 1.12.2)</h2>
 * 1.19 added four "Group of Allays" markers to the mansion room {@code 2x2_a1} (its four cells), each
 * spawning 1-3 persistent allays. 1.12.2's room is the same room, with air in those four places. After a
 * chunk is populated, every mansion piece of that room inside it gets the allays at the transformed marker
 * positions, exactly where 1.19's markers would be: found through the chunk generator's own mansion
 * generator (any {@code MapGenStructure} named {@code Mansion}), so any generator that builds vanilla's
 * mansions gets them.
 *
 * <h2>Pillager outposts (Village &amp; Pillage)</h2>
 * 1.19's outpost features pool has a third cage, {@code feature_cage_with_allays} (weight 1, after
 * {@code feature_cage2}), holding two allays. With Village &amp; Pillage installed its features pool is
 * given that element (its own template loader reads our converted template), and after its outposts are
 * built in a chunk, the cages' two allays are spawned. Found by name; if anything is missing the outposts
 * are left as they are and the log says so.
 */
public final class AllayStructures {

    /** 1.19's {@code 2x2_a1} "Group of Allays" markers, template coordinates. */
    static final BlockPos[] MANSION_MARKERS = {new BlockPos(2, 2, 8), new BlockPos(4, 2, 2), new BlockPos(5, 2, 12),
            new BlockPos(12, 2, 12)};
    static final String MANSION_ROOM = "2x2_a1";

    static final String OUTPOST_POOL = "pillager_outpost/features";
    static final String OUTPOST_CAGE = "pillager_outpost/feature_cage_with_allays";
    /** The two allays of 1.19's {@code feature_cage_with_allays}: x, y, z, yaw, pitch; both in block (4, 1, 4). */
    static final double[][] CAGE_ALLAYS = {{4.5355692304166855D, 1.0508262449273857D, 4.5D, 270.0D, -40.0D},
            {4.532911080022236D, 1.9317999950649707D, 4.483273631254721D, 270.0D, 40.0D}};
    static final BlockPos CAGE_ALLAY_BLOCK = new BlockPos(4, 1, 4);

    private static final String VP = "com.exiledradio.villagerbackport.village.modern.";

    /** The cage's index in Village &amp; Pillage's pool, or -1 when not added. */
    static int cageIndex = -1;

    private static final Map<IChunkGenerator, List<MapGenStructure>> MANSIONS = new WeakHashMap<IChunkGenerator, List<MapGenStructure>>();
    @Nullable
    private static Field structureMap;

    private AllayStructures() {
    }

    static void init() {
        try {
            structureMap = ObfuscationReflectionHelper.findField(MapGenStructure.class, "field_75053_d");
        } catch (RuntimeException e) {
            WildUpdateBackport.LOGGER.error("Cannot read structures; allays will not generate in mansions or outposts.", e);
            return;
        }
        MinecraftForge.TERRAIN_GEN_BUS.register(new AllayStructures.Mansions());
        if (Loader.isModLoaded(Owners.VILLAGE_AND_PILLAGE)) {
            addOutpostCage();
            if (cageIndex >= 0) {
                // After Village & Pillage's outposts (weight 1).
                GameRegistry.registerWorldGenerator(new Outposts(), 10);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mansions
    // ---------------------------------------------------------------------------------------------

    public static final class Mansions {
        @SubscribeEvent
        public void populated(PopulateChunkEvent.Post event) {
            World world = event.getWorld();
            if (world.isRemote || !world.getWorldInfo().isMapFeaturesEnabled()) {
                return;
            }
            StructureBoundingBox box = populationBox(event.getChunkX(), event.getChunkZ());
            for (MapGenStructure gen : mansionGenerators(event.getGenerator())) {
                for (StructureStart start : starts(gen)) {
                    if (!start.isSizeableStructure() || !start.getBoundingBox().intersectsWith(box)) {
                        continue;
                    }
                    for (StructureComponent piece : start.getComponents()) {
                        if (piece.getBoundingBox().intersectsWith(box)) {
                            spawnMansionAllays(world, piece, box, event.getRand());
                        }
                    }
                }
            }
        }
    }

    /** 1.19's {@code WoodlandMansionPieces.MansionTemplate.handleDataMarker("Group of Allays")}. */
    static int spawnMansionAllays(World world, StructureComponent piece, StructureBoundingBox box, Random random) {
        NBTTagCompound tag = piece.createStructureBaseNBT();
        if (!MANSION_ROOM.equals(tag.getString("Template")) || !tag.hasKey("TPX")) {
            return 0;
        }
        BlockPos origin = new BlockPos(tag.getInteger("TPX"), tag.getInteger("TPY"), tag.getInteger("TPZ"));
        PlacementSettings settings = new PlacementSettings().setRotation(enumOr(Rotation.class, tag.getString("Rot"), Rotation.NONE))
                .setMirror(enumOr(Mirror.class, tag.getString("Mi"), Mirror.NONE));
        int spawned = 0;
        for (BlockPos marker : MANSION_MARKERS) {
            BlockPos at = Template.transformedBlockPos(settings, marker).add(origin);
            if (!box.isVecInside(at)) {
                continue;
            }
            int n = random.nextInt(3) + 1;
            for (int i = 0; i < n; i++) {
                EntityAllay allay = new EntityAllay(world);
                allay.enablePersistence();
                allay.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                allay.onInitialSpawn(world.getDifficultyForLocation(at), null);
                world.spawnEntity(allay);
                spawned++;
            }
        }
        return spawned;
    }

    private static List<MapGenStructure> mansionGenerators(IChunkGenerator generator) {
        List<MapGenStructure> found = MANSIONS.get(generator);
        if (found == null) {
            found = new ArrayList<MapGenStructure>();
            for (Class<?> c = generator.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!MapGenStructure.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object gen = f.get(generator);
                        if (gen instanceof MapGenStructure && "Mansion".equals(((MapGenStructure) gen).getStructureName())) {
                            found.add((MapGenStructure) gen);
                        }
                    } catch (Exception ignored) {
                        // not readable: not a generator we can use
                    }
                }
            }
            MANSIONS.put(generator, found);
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------
    // Village & Pillage's outposts
    // ---------------------------------------------------------------------------------------------

    /** Adds 1.19's allay cage to Village &amp; Pillage's features pool, after its second cage. */
    @SuppressWarnings("unchecked")
    static void addOutpostCage() {
        try {
            Class<?> pools = Class.forName(VP + "ModernPools");
            Object pool = pools.getMethod("get", String.class).invoke(null, OUTPOST_POOL);
            if (pool == null) {
                WildUpdateBackport.LOGGER.warn("Village & Pillage has no {} pool; outposts get no allays.", OUTPOST_POOL);
                return;
            }
            Class<?> poolClass = Class.forName(VP + "ModernPools$Pool");
            Class<?> elementClass = Class.forName(VP + "ModernPools$Element");
            Class<?> singleClass = Class.forName(VP + "ModernPools$SingleElement");
            Class<?> projection = Class.forName(VP + "ModernPools$Projection");
            List<Object> expanded = (List<Object>) field(poolClass, "expanded").get(pool);
            List<Object> distinct = (List<Object>) field(poolClass, "distinct").get(pool);
            Field location = field(singleClass, "location");
            for (int i = 0; i < distinct.size(); i++) {
                Object e = distinct.get(i);
                if (singleClass.isInstance(e) && OUTPOST_CAGE.equals(location.get(e))) {
                    cageIndex = i;
                    return;
                }
            }
            Constructor<?> make = singleClass.getDeclaredConstructor(String.class, List.class, projection);
            make.setAccessible(true);
            Object rigid = Enum.valueOf((Class<Enum>) projection, "RIGID");
            Object cage = make.newInstance(OUTPOST_CAGE, new ArrayList<Object>(), rigid);
            field(elementClass, "pool").set(cage, pool);
            field(elementClass, "index").setInt(cage, distinct.size());
            // 1.19's order: cage1, cage2, cage with allays, logs, ...
            int after = expanded.size();
            for (int i = 0; i < expanded.size(); i++) {
                Object e = expanded.get(i);
                if (singleClass.isInstance(e) && "pillager_outpost/feature_cage2".equals(location.get(e))) {
                    after = i + 1;
                }
            }
            expanded.add(after, cage);
            distinct.add(cage);
            cageIndex = distinct.size() - 1;
            WildUpdateBackport.LOGGER.info("Village & Pillage's outposts can have 1.19's allay cage.");
        } catch (Exception e) {
            cageIndex = -1;
            WildUpdateBackport.LOGGER.warn("Could not add the allay cage to Village & Pillage's outposts; they get no allays.", e);
        }
    }

    /** After Village &amp; Pillage builds a chunk of an outpost: the allays of each allay cage in it. */
    static final class Outposts implements IWorldGenerator {
        @Nullable
        private Method of;

        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
            if (cageIndex < 0 || world.isRemote) {
                return;
            }
            MapGenStructure outposts = this.outposts(world);
            if (outposts == null) {
                return;
            }
            StructureBoundingBox box = populationBox(chunkX, chunkZ);
            for (StructureStart start : starts(outposts)) {
                if (!start.getBoundingBox().intersectsWith(box)) {
                    continue;
                }
                for (StructureComponent piece : start.getComponents()) {
                    if (piece.getBoundingBox().intersectsWith(box)) {
                        spawnCageAllays(world, piece, box);
                    }
                }
            }
        }

        @Nullable
        private MapGenStructure outposts(World world) {
            try {
                if (this.of == null) {
                    this.of = Class.forName(VP + "MapGenOutpost").getMethod("of", World.class);
                }
                Object gen = this.of.invoke(null, world);
                return gen instanceof MapGenStructure ? (MapGenStructure) gen : null;
            } catch (Exception e) {
                WildUpdateBackport.LOGGER.warn("Cannot find Village & Pillage's outposts; their cages get no allays.", e);
                cageIndex = -1;
                return null;
            }
        }
    }

    /** The cage's two allays, placed as Village &amp; Pillage places a template's entities. */
    static int spawnCageAllays(World world, StructureComponent piece, StructureBoundingBox box) {
        NBTTagCompound tag = piece.createStructureBaseNBT();
        if (!OUTPOST_POOL.equals(tag.getString("Pool")) || tag.getInteger("Element") != cageIndex || !tag.hasKey("PosX")) {
            return 0;
        }
        BlockPos pos = new BlockPos(tag.getInteger("PosX"), tag.getInteger("PosY"), tag.getInteger("PosZ"));
        Rotation rotation = enumOr(Rotation.class, tag.getString("Rotation"), Rotation.NONE);
        if (!box.isVecInside(pos.add(rotate(CAGE_ALLAY_BLOCK, rotation)))) {
            return 0;
        }
        int spawned = 0;
        for (double[] a : CAGE_ALLAYS) {
            double x;
            double z;
            // 1.14's Template.transformedVec3d, about the far corner of the block
            switch (rotation) {
                case CLOCKWISE_90:
                    x = 1.0D - a[2];
                    z = a[0];
                    break;
                case CLOCKWISE_180:
                    x = 1.0D - a[0];
                    z = 1.0D - a[2];
                    break;
                case COUNTERCLOCKWISE_90:
                    x = a[2];
                    z = 1.0D - a[0];
                    break;
                default:
                    x = a[0];
                    z = a[2];
            }
            EntityAllay allay = new EntityAllay(world);
            allay.enablePersistence();
            allay.setLocationAndAngles(pos.getX() + x, pos.getY() + a[1], pos.getZ() + z, (float) a[3], (float) a[4]);
            allay.rotationYaw = allay.getRotatedYaw(rotation);
            allay.rotationYawHead = allay.rotationYaw;
            allay.renderYawOffset = allay.rotationYaw;
            allay.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(allay)), null);
            world.spawnEntity(allay);
            spawned++;
        }
        return spawned;
    }

    static BlockPos rotate(BlockPos p, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return new BlockPos(-p.getZ(), p.getY(), p.getX());
            case CLOCKWISE_180:
                return new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(p.getZ(), p.getY(), -p.getX());
            default:
                return p;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** The box a chunk's population writes into: {@code MapGenStructure.generateStructure}'s. */
    static StructureBoundingBox populationBox(int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        return new StructureBoundingBox(x, z, x + 15, z + 15);
    }

    @SuppressWarnings("unchecked")
    static Collection<StructureStart> starts(MapGenStructure gen) {
        if (structureMap == null) {
            return Collections.emptyList();
        }
        try {
            Map<Long, StructureStart> map = (Map<Long, StructureStart>) structureMap.get(gen);
            return map == null ? Collections.<StructureStart>emptyList() : new ArrayList<StructureStart>(map.values());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
