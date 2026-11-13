package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Frogs, tadpoles, frogspawn, froglights and the bucket of tadpole.
 *
 * <p>Entities {@code wildupdatebackport:frog} (id 5) and {@code tadpole} (id 6), tracked within 160 blocks
 * (1.19's tracking range 10) every 3 ticks, with 1.19's spawn eggs. Where frogs spawn and which variant they
 * are is {@link FrogSpawns}. A small magma cube a frog eats drops the froglight of the frog's variant and
 * nothing else; a small slime drops exactly one slimeball (1.19's loot tables).
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class FrogContent {

    public static final int FROG_EGG_PRIMARY = 13661252;
    public static final int FROG_EGG_SECONDARY = 16762748;
    public static final int TADPOLE_EGG_PRIMARY = 7164733;
    public static final int TADPOLE_EGG_SECONDARY = 1444352;

    private static final List<Block> FROGLIGHTS = new ArrayList<Block>();
    private static Block frogspawn;
    private static ItemTadpoleBucket tadpoleBucket;

    private FrogContent() {
    }

    public static boolean enabled() {
        return ModConfig.frogs.enabled;
    }

    /** @return the three froglights, ochre, verdant, pearlescent */
    public static List<Block> froglights() {
        return Collections.unmodifiableList(FROGLIGHTS);
    }

    @Nullable
    public static Block frogspawn() {
        return frogspawn;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Frogs are off (frogs.enabled).");
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        FROGLIGHTS.clear();
        FROGLIGHTS.add(Content.block(r, "ochre_froglight", new BlockFroglight(MapColor.SAND), null));
        FROGLIGHTS.add(Content.block(r, "verdant_froglight", new BlockFroglight(MapColor.SILVER), null));
        FROGLIGHTS.add(Content.block(r, "pearlescent_froglight", new BlockFroglight(MapColor.PINK), null));
        frogspawn = Content.block(r, "frogspawn", new BlockFrogspawn(), null);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Block b : FROGLIGHTS) {
            Content.registerBlockItem(r, b);
        }
        Content.blockItem(r, frogspawn, new ItemFrogspawn(frogspawn));
        tadpoleBucket = Content.item(r, "tadpole_bucket", new ItemTadpoleBucket());
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityFrog.class).id(Content.id("frog"), 5)
                .name(WildUpdateBackport.MOD_ID + ".frog").tracker(160, 3, true).egg(FROG_EGG_PRIMARY, FROG_EGG_SECONDARY).build());
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityTadpole.class).id(Content.id("tadpole"), 6)
                .name(WildUpdateBackport.MOD_ID + ".tadpole").tracker(160, 3, true).egg(TADPOLE_EGG_PRIMARY, TADPOLE_EGG_SECONDARY).build());
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (enabled()) {
            FrogSounds.register(event.getRegistry());
        }
    }

    /** From the mod's pre-init: <i>Bukkit Bukkit</i>'s and <i>When the Squad Hops into Town</i>'s triggers. */
    public static void registerTriggers() {
        FilledBucketTrigger.register();
        FrogLeashTrigger.register();
    }

    /** From the mod's init: natural spawns and the tadpole bucket's dispenser behavior. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        FrogSpawns.addAll();
        tadpoleBucket.registerDispenser();
    }

    /** 1.19's magma cube and slime loot tables, for a frog's kill. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityFrog) || !(event.getEntityLiving() instanceof EntitySlime)) {
            return;
        }
        EntityFrog frog = (EntityFrog) event.getSource().getTrueSource();
        EntitySlime slime = (EntitySlime) event.getEntityLiving();
        ItemStack drop;
        if (slime instanceof EntityMagmaCube) {
            Block light = Owners.ourBlock(frog.getVariant().froglight);
            if (light == null) {
                return;
            }
            drop = new ItemStack(light);
        } else if (slime.getSlimeSize() == 1) {
            drop = new ItemStack(Items.SLIME_BALL);
        } else {
            return;
        }
        event.getDrops().clear();
        event.getDrops().add(new EntityItem(slime.world, slime.posX, slime.posY, slime.posZ, drop));
    }

    /** Remembers who used a lead on a frog; the frog checks next tick that it is now on that player's lead. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getWorld().isRemote && event.getTarget() instanceof EntityFrog && event.getItemStack().getItem() == Items.LEAD
                && !((EntityFrog) event.getTarget()).getLeashed()) {
            ((EntityFrog) event.getTarget()).leashingPlayer = event.getEntityPlayer().getUniqueID();
        }
    }
}
