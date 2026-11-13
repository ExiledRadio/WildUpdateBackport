package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.JigsawElement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StructureRandoms;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.deepdark.AncientCityLoot;
import com.exiledradio.wildupdatebackport.deepdark.DeepDark;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import com.exiledradio.wildupdatebackport.deepdark.DeepslateCopies;
import com.exiledradio.wildupdatebackport.deepdark.city.AncientCityStructure;
import com.exiledradio.wildupdatebackport.deepdark.city.CityLayout;
import com.exiledradio.wildupdatebackport.deepdark.city.CityPiece;
import com.exiledradio.wildupdatebackport.items.ItemsContent;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkShrieker;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;
import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stream DD: reinforced deepslate, the deepslate copies, the ancient city against vanilla 1.19.2 (tools/deepdark/
 * oracle119.txt: grid chunks, generation stubs and whole layouts for four seeds), its loot, a city built in this world,
 * and the deep dark's features and spawns.
 */
public final class DeepDarkSelfTest implements SelfTest {

    @Override
    public String name() {
        return "deepdark";
    }

    @Override
    public void run(Check c) throws Exception {
        blocks(c);
        oracle(c);
        loot(c);
        world(c);
    }

    // ---- blocks

    private void blocks(Check c) {
        Block r = DeepDarkContent.reinforcedDeepslate();
        c.ok(r != null && r.getRegistryName().toString().equals("wildupdatebackport:reinforced_deepslate"), "reinforced deepslate is registered");
        if (r == null) {
            return;
        }
        IBlockState s = r.getDefaultState();
        c.ok(Math.abs(s.getBlockHardness(c.world, BlockPos.ORIGIN) - 55.0F) < 1e-4, "reinforced deepslate: strength 55");
        c.ok(Math.abs(r.getExplosionResistance(null) - 1200.0F) < 0.01F, "reinforced deepslate: blast resistance 1200 (" + r.getExplosionResistance(null) + ")");
        c.eq(EnumPushReaction.BLOCK, s.getPushReaction(), "reinforced deepslate cannot be pushed");
        c.ok(r.getDrops(c.world, BlockPos.ORIGIN, s, 0).isEmpty(), "reinforced deepslate drops nothing");
        c.ok(net.minecraft.item.Item.getItemFromBlock(r) != Items.AIR, "reinforced deepslate has an item (creative)");
        c.ok(DeepDarkContent.biome() != null && "wildupdatebackport:deep_dark".equals(DeepDarkContent.biome().getRegistryName().toString()),
                "a deep_dark biome is registered for 1.18 terrain to report");
        c.ok(DeepDarkContent.biome().getSpawnableList(net.minecraft.entity.EnumCreatureType.MONSTER).isEmpty(), "the deep dark biome spawns nothing");

        boolean cc = Owners.present(Owners.CAVES_AND_CLIFFS);
        c.ok(DeepslateCopies.registered() != cc, "deepslate copies registered exactly when Caves & Cliffs is absent");
        String[] names = {"deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "chiseled_deepslate",
                "cracked_deepslate_bricks", "cracked_deepslate_tiles", "cobbled_deepslate_stairs", "cobbled_deepslate_slab",
                "cobbled_deepslate_double_slab", "cobbled_deepslate_wall", "polished_deepslate_stairs", "polished_deepslate_slab",
                "polished_deepslate_double_slab", "polished_deepslate_wall", "deepslate_brick_stairs", "deepslate_brick_slab",
                "deepslate_brick_double_slab", "deepslate_brick_wall", "deepslate_tile_stairs", "deepslate_tile_slab",
                "deepslate_tile_double_slab", "deepslate_tile_wall"};
        int found = 0;
        for (String n : names) {
            if (Owners.block(Owners.CAVES_AND_CLIFFS, n) != null) {
                found++;
            }
        }
        c.eq(names.length, found, "the whole deepslate set resolves (owner's or ours)");
        if (!cc) {
            Block deepslate = Owners.ourBlock("deepslate");
            c.ok(Math.abs(deepslate.getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN) - 3.0F) < 1e-4, "deepslate hardness 3");
            c.ok(Math.abs(Owners.ourBlock("deepslate_tiles").getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN) - 3.5F) < 1e-4,
                    "deepslate tiles hardness 3.5");
            c.eq(Item(Owners.ourBlock("cobbled_deepslate")), deepslate.getItemDropped(deepslate.getDefaultState(), new Random(1), 0),
                    "deepslate drops cobbled deepslate");
        }
    }

    private static net.minecraft.item.Item Item(Block b) {
        return net.minecraft.item.Item.getItemFromBlock(b);
    }

    // ---- vanilla 1.19.2

    private void oracle(Check c) throws Exception {
        File file = new File("../tools/deepdark/oracle119.txt");
        c.ok(file.exists(), "the 1.19.2 oracle file is there (" + file.getAbsolutePath() + ")");
        if (!file.exists()) {
            return;
        }
        int cands = 0, grid = 0, stubs = 0, valid = 0, worldCands = 0, worldSame = 0;
        boolean modern = DeepDark.modern(c.world);
        Map<String, List<String[]>> pieces = new LinkedHashMap<String, List<String[]>>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String[] a = line.split(" ");
            if (a[0].equals("cand")) {
                cands++;
                long seed = Long.parseLong(a[1]);
                int cx = Integer.parseInt(a[2]), cz = Integer.parseInt(a[3]);
                ChunkPos ours = AncientCityStructure.PLACEMENT.candidate(seed, cx, cz);
                if (ours.x == cx && ours.z == cz) {
                    grid++;
                } else {
                    c.fail("grid: seed " + seed + " vanilla " + cx + "," + cz + " ours " + ours);
                }
                if (a[4].equals("none")) {
                    continue;
                }
                CityLayout.Stub stub = CityLayout.start(new BlockPos(cx << 4, CityLayout.START_Y_119, cz << 4), StructureRandoms.start(seed, cx, cz));
                BlockPos want = new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
                if (stub != null && stub.position.equals(want)) {
                    stubs++;
                } else {
                    c.fail("stub: seed " + seed + " chunk " + cx + "," + cz + " vanilla " + want + " ours " + (stub == null ? null : stub.position));
                }
                if (a[7].equals("deep_dark")) {
                    valid++;
                }
                if (modern && seed == c.world.getSeed()) {
                    worldCands++;
                    boolean starts = ScatteredStructure.of(c.world, Structures.ANCIENT_CITY).startsAt(c.world, cx, cz);
                    if (starts == a[7].equals("deep_dark")) {
                        worldSame++;
                    } else {
                        c.fail("1.18 terrain: chunk " + cx + "," + cz + " vanilla " + a[7] + ", ours " + (starts ? "a city" : "none"));
                    }
                }
            } else if (a[0].equals("piece")) {
                String key = a[1] + " " + a[2] + " " + a[3];
                List<String[]> list = pieces.get(key);
                if (list == null) {
                    pieces.put(key, list = new ArrayList<String[]>());
                }
                list.add(a);
            }
        }
        c.ok(cands > 0, "oracle candidates read: " + cands + " (" + valid + " in the deep dark)");
        c.eq(cands, grid, "every grid chunk is vanilla's (spacing 24, separation 8, salt 20083232)");
        c.eq(cands, stubs, "every generation stub is vanilla's (start piece, rotation, city_anchor)");
        if (modern) {
            c.ok(worldCands > 0, "1.18 terrain world on an oracle seed: " + worldCands + " candidates");
            c.eq(worldCands, worldSame, "1.18 terrain: a city starts exactly where 1.19.2 has one (" + worldSame + "/" + worldCands + ")");
        }

        int cities = 0, same = 0;
        for (Map.Entry<String, List<String[]>> e : pieces.entrySet()) {
            String[] k = e.getKey().split(" ");
            long seed = Long.parseLong(k[0]);
            int cx = Integer.parseInt(k[1]), cz = Integer.parseInt(k[2]);
            List<CityPiece> ours = CityLayout.assemble(new BlockPos(cx << 4, CityLayout.START_Y_119, cz << 4), StructureRandoms.start(seed, cx, cz));
            List<String[]> want = e.getValue();
            cities++;
            boolean match = ours.size() == want.size();
            String first = match ? null : "piece count vanilla " + want.size() + " ours " + ours.size();
            for (int i = 0; i < Math.min(ours.size(), want.size()); i++) {
                String[] w = want.get(i);
                CityPiece p = ours.get(i);
                StructureBoundingBox b = p.getBoundingBox();
                String got = describe(p.element()) + " " + p.rotation().name() + " " + b.minX + " " + b.minY + " " + b.minZ + " " + b.maxX + " "
                        + b.maxY + " " + b.maxZ;
                String exp = w[5] + " " + w[6] + " " + w[7] + " " + w[8] + " " + w[9] + " " + w[10] + " " + w[11] + " " + w[12];
                if (!got.equals(exp)) {
                    match = false;
                    if (first == null) {
                        first = "piece " + i + " vanilla [" + exp + "] ours [" + got + "]";
                    }
                    break;
                }
            }
            if (match) {
                same++;
            } else {
                c.fail("layout seed " + seed + " chunk " + cx + "," + cz + ": " + first);
            }
        }
        c.ok(cities > 0, "oracle layouts read: " + cities);
        c.eq(cities, same, "every city lays out piece for piece as 1.19.2's (" + same + "/" + cities + ")");
    }

    static String describe(JigsawElement e) {
        String type = e.json() != null && e.json().has("element_type") ? e.json().get("element_type").getAsString() : "";
        if (type.endsWith("list_pool_element")) {
            return "list:" + describe(e.parts().get(0));
        }
        if (type.endsWith("feature_pool_element")) {
            return "feature";
        }
        return e.template() == null ? "empty" : e.template().toString();
    }

    // ---- loot

    private void loot(Check c) {
        LootTable table = c.world.getLootTableManager().getLootTableFromLocation(AncientCityLoot.CITY);
        c.ok(table.getPool("wildupdatebackport:ancient_city") != null, "the ancient city table has its pool");
        Random random = new Random(5);
        int chests = 4000, shards = 0, fragments = 0, books = 0, apples = 0, stacks = 0;
        for (int i = 0; i < chests; i++) {
            for (ItemStack s : table.generateLootForPools(random, new LootContext.Builder(c.world).build())) {
                stacks++;
                if (s.getItem() == ItemsContent.echoShard()) {
                    shards += s.getCount();
                } else if (s.getItem() == ItemsContent.discFragment()) {
                    fragments += s.getCount();
                } else if (s.getItem() == Items.ENCHANTED_BOOK
                        && net.minecraft.enchantment.EnchantmentHelper.getEnchantments(s).containsKey(ItemsContent.swiftSneak())) {
                    books++;
                } else if (s.getItem() == Items.GOLDEN_APPLE && s.getMetadata() == 1) {
                    apples++;
                }
            }
        }
        double rolls = 7.5D, total = 88.0D;
        double wantShards = rolls * 4 / total * 2, wantBooks = rolls * 3 / total, wantApples = rolls / total;
        c.ok(Math.abs(shards / (double) chests - wantShards) < 0.06, "echo shards per chest " + shards / (double) chests + " (1.19: " + wantShards + ")");
        c.ok(Math.abs(fragments / (double) chests - wantShards) < 0.06, "disc fragments per chest " + fragments / (double) chests);
        c.ok(Math.abs(books / (double) chests - wantBooks) < 0.03, "Swift Sneak books per chest " + books / (double) chests + " (1.19: " + wantBooks + ")");
        c.ok(Math.abs(apples / (double) chests - wantApples) < 0.025, "enchanted golden apples per chest " + apples / (double) chests);
        c.ok(stacks / (double) chests <= 10.0D, "at most 10 rolls");
        LootTable ice = c.world.getLootTableManager().getLootTableFromLocation(AncientCityLoot.ICE_BOX);
        int snow = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack s : ice.generateLootForPools(random, new LootContext.Builder(c.world).build())) {
                if (s.getItem() == Items.SNOWBALL) {
                    snow++;
                }
            }
        }
        c.ok(snow > 0, "the ice box has snowballs");
    }

    // ---- this world

    private void world(Check c) {
        WorldServer world = c.world;
        ScatteredStructure cities = ScatteredStructure.of(world, Structures.ANCIENT_CITY);
        c.ok(cities instanceof AncientCityStructure, "ancient cities are in the overworld");
        boolean modern = DeepDark.modern(world);
        c.eq(modern ? -27 : 32, AncientCityStructure.startY(world), "the start height (1.19's -27; moved up 59 in a 0-255 world)");
        BlockPos found;
        try {
            found = StructureLocate.nearest(world, BlockPos.ORIGIN, "minecraft:ancient_city").pos;
        } catch (Exception e) {
            c.fail("/locate structure minecraft:ancient_city: " + e);
            return;
        }
        c.ok(found != null, "/locate finds an ancient city: " + found);
        if (found == null) {
            return;
        }
        int cx = found.getX() >> 4, cz = found.getZ() >> 4;
        c.ok(cities.startsAt(world, cx, cz), "a city starts in the located chunk");
        CityLayout.Stub stub = AncientCityStructure.stub(world, cx, cz);
        c.ok(stub != null && DeepDark.at(world, stub.position), "its stub is in the deep dark");
        List<CityPiece> layout = AncientCityStructure.layout(world, cx, cz);
        StructureBoundingBox all = new StructureBoundingBox(layout.get(0).getBoundingBox());
        for (CityPiece p : layout) {
            all.expandTo(p.getBoundingBox());
        }
        c.ok(layout.size() > 20, "the city has " + layout.size() + " pieces");
        int lo = modern ? -63 : 1, hi = modern ? 4 : 64;
        c.ok(all.minY >= lo && all.maxY < hi, "the city sits in y " + all.minY + "-" + all.maxY);
        // Generate every chunk of it, and the ring around, so each is populated.
        for (int x = (all.minX >> 4) - 1; x <= (all.maxX >> 4) + 1; x++) {
            for (int z = (all.minZ >> 4) - 1; z <= (all.maxZ >> 4) + 1; z++) {
                world.getChunk(x, z);
            }
        }
        Block reinforced = DeepDarkContent.reinforcedDeepslate();
        List<String> otherChests = new ArrayList<String>();
        int portal = 0, chests = 0, cityChests = 0, tiles = 0, shriekers = 0, summoners = 0, sculk = 0;
        Block tileBlock = Owners.block(Owners.CAVES_AND_CLIFFS, "deepslate_tiles");
        for (BlockPos p : BlockPos.getAllInBoxMutable(new BlockPos(all.minX, all.minY, all.minZ), new BlockPos(all.maxX, all.maxY, all.maxZ))) {
            IBlockState s = world.getBlockState(p);
            Block b = s.getBlock();
            if (b == reinforced) {
                portal++;
            } else if (b == Blocks.CHEST) {
                chests++;
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileEntityChest) {
                    NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
                    String table = tag.getString("LootTable");
                    if (table.startsWith("wildupdatebackport:chests/ancient_city")) {
                        cityChests++;
                    } else {
                        otherChests.add(table.isEmpty() ? "none at " + p.toImmutable() : table);
                    }
                }
            } else if (b == tileBlock) {
                tiles++;
            } else if (b == SculkContent.shriekerBlock()) {
                shriekers++;
                if (s.getValue(BlockSculkShrieker.CAN_SUMMON)) {
                    summoners++;
                }
            } else if (b == SculkContent.sculkBlock()) {
                sculk++;
            }
        }
        c.ok(portal >= 50, "the city's portal frame is reinforced deepslate (" + portal + ")");
        // One of 1.19.2's 15 template chests has no loot table (vanilla's too); another structure's chest inside the
        // city's box (a dungeon, a mineshaft) has its own. None may name a city table we failed to map.
        boolean noneEmpty = true;
        for (String o : otherChests) {
            noneEmpty &= !o.contains("ancient_city");
        }
        c.ok(cityChests >= 10 && noneEmpty, "its chests hold ancient city loot (" + cityChests + "/" + chests + ", others " + otherChests + ")");
        c.ok(tiles > 1000, "deepslate tiles: " + tiles);
        c.ok(sculk > 50, "sculk patches on the city: " + sculk);
        c.ok(shriekers == 0 || summoners == shriekers, "the city's shriekers can summon (" + summoners + "/" + shriekers + ")");
        List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(all.minX, all.minY, all.minZ, all.maxX + 1, all.maxY + 1, all.maxZ + 1));
        Map<String, Integer> dropped = new LinkedHashMap<String, Integer>();
        for (EntityItem item : drops) {
            String n = item.getItem().getItem().getRegistryName() + " at " + new BlockPos(item);
            dropped.merge(item.getItem().getItem().getRegistryName().toString(), 1, Integer::sum);
            if (dropped.size() < 4) {
                BlockPos ip = new BlockPos(item);
                StringBuilder around = new StringBuilder();
                for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.VALUES) {
                    around.append(f).append('=').append(world.getBlockState(ip.offset(f))).append(' ');
                }
                String piece = "";
                for (CityPiece p : layout) {
                    if (p.getBoundingBox().isVecInside(ip)) {
                        piece += describe(p.element()) + " " + p.rotation() + " " + p.getBoundingBox() + "; ";
                    }
                }
                com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info("SELFTEST dropped {} here={} {} in {}", n, world.getBlockState(ip), around, piece);
            }
        }
        // 1.12.2's own populate (water springs' first tick) still updates blocks next to ladders whose wall 1.19's
        // block_rot took, which 1.19's world generation never does; anything more is ours.
        c.ok(drops.size() <= layout.size() / 4, "few item entities dropped while the city was built " + dropped);
        com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info(
                "SELFTEST city at {}: {} pieces, box {}, portal {}, chests {}/{}, tiles {}, sculk {}, shriekers {}/{}", found, layout.size(), all,
                portal, cityChests, chests, tiles, sculk, summoners, shriekers);

        // The deep dark: no natural spawns at the stub.
        EntityZombie zombie = new EntityZombie(world);
        BlockPos at = stub.position;
        zombie.setPosition(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        LivingSpawnEvent.CheckSpawn event = new LivingSpawnEvent.CheckSpawn(zombie, world, at.getX() + 0.5F, at.getY(), at.getZ() + 0.5F, null);
        MinecraftForge.EVENT_BUS.post(event);
        if (modern) {
            c.ok(world.getChunkProvider().getPossibleCreatures(net.minecraft.entity.EnumCreatureType.MONSTER, at).isEmpty(),
                    "nothing spawns naturally in the deep dark (1.18 terrain spawns from our biome)");
        } else {
            c.eq(Event.Result.DENY, event.getResult(), "nothing spawns naturally in the deep dark");
        }
        c.ok(!DeepDark.at(world, new BlockPos(at.getX(), 100, at.getZ())), "y 100 above it is not the deep dark");
        if (modern) {
            c.ok(world.getBiomeProvider() != null, "1.18 terrain reports " + DeepDark.modernBiome(world, at) + " at the stub");
        }

        // Sculk veins and patches in the deep dark around the city's chunks.
        int veins = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(new BlockPos(all.minX - 16, lo, all.minZ - 16), new BlockPos(all.maxX + 16, all.maxY, all.maxZ + 16))) {
            if (world.getBlockState(p).getBlock() == SculkContent.veinBlock()) {
                veins++;
            }
        }
        c.ok(veins > 0, "sculk veins grow in the deep dark: " + veins);
        com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info("SELFTEST veins {}", veins);
    }
}
