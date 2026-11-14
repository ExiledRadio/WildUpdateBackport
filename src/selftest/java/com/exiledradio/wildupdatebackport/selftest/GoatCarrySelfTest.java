package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.goat.EntityGoat;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Stream Gt, across two runs of one world: a goat of ours, saved without Caves &amp; Cliffs, loads as Caves &amp;
 * Cliffs' goat once it is installed, with its tags. Only run when asked by name ({@code -Pselftest=goatcarry}),
 * never in {@code all}:
 * <ol>
 *   <li>without Caves &amp; Cliffs it saves a screaming, one-horned, named goat and writes its UUID to
 *       {@code eclipse/goatcarry.txt};</li>
 *   <li>with Caves &amp; Cliffs in {@code eclipse/mods} (and {@code -PqueryConfirm}, which answers Forge's prompt for
 *       our deepslate copies, now missing) it finds that goat and checks it.</li>
 * </ol>
 */
public final class GoatCarrySelfTest implements SelfTest, SelfTest.ByNameOnly {

    private static final BlockPos AT = new BlockPos(24008, 101, 24008);

    @Override
    public String name() {
        return "goatcarry";
    }

    @Override
    public void run(Check c) throws Exception {
        WorldServer world = c.world;
        File file = world.getMinecraftServer().getFile("goatcarry.txt");
        if (!Owners.present(Owners.CAVES_AND_CLIFFS)) {
            world.getChunk(AT);
            for (BlockPos p : BlockPos.getAllInBox(AT.add(-3, -1, -3), AT.add(3, -1, 3))) {
                world.setBlockState(p, net.minecraft.init.Blocks.STONE.getDefaultState(), 2);
            }
            EntityGoat goat = new EntityGoat(world);
            goat.setLocationAndAngles(AT.getX() + 0.5D, AT.getY(), AT.getZ() + 0.5D, 0.0F, 0.0F);
            goat.setScreamingGoat(true);
            goat.setHorns(false, true);
            goat.setCustomNameTag("carried");
            goat.enablePersistence();
            c.ok(world.spawnEntity(goat), "a goat of ours is saved at " + AT);
            Files.write(file.toPath(), goat.getUniqueID().toString().getBytes(StandardCharsets.UTF_8));
            WildUpdateBackport.LOGGER.info("SELFTEST goatcarry: saved {}; run again with Caves & Cliffs to check it", goat.getUniqueID());
            return;
        }
        if (!c.ok(file.isFile(), "goatcarry.txt from the run without Caves & Cliffs")) {
            return;
        }
        UUID id = UUID.fromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim());
        world.getChunk(AT);
        Entity e = world.getEntityFromUuid(id);
        if (!c.ok(e != null, "the saved goat " + id + " loaded")) {
            return;
        }
        EntityEntry entry = EntityRegistry.getEntry(e.getClass());
        c.eq(GoatSelfTest.CC_GOAT, entry == null ? null : entry.getRegistryName(), "as Caves & Cliffs' goat");
        c.ok(ForgeRegistries.ENTITIES.getValue(GoatSelfTest.OUR_GOAT) == entry, "our goat's name is now an alias of the owner's");
        c.ok((Boolean) GoatSelfTest.call(e, "isScreamingGoat"), "still screaming");
        c.ok(!(Boolean) GoatSelfTest.call(e, "hasLeftHorn") && (Boolean) GoatSelfTest.call(e, "hasRightHorn"), "still with only its right horn");
        c.eq("carried", e.getCustomNameTag(), "and its name");
        c.ok(file.delete(), "goatcarry.txt removed");
    }
}
