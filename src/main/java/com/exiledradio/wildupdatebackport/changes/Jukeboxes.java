package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockJukebox.TileEntityJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 1.19.4's jukebox, on 1.12.2's: it knows when a disc is playing and when the song is over, shows a note above itself
 * every second while playing, gives a redstone signal of 15 while playing (patch P5 reads {@link #isPlaying}), and
 * hoppers and droppers put discs in and take them out.
 *
 * <p>1.12.2's jukebox tile does not tick, so every jukebox tile is remembered when Forge gathers its capabilities and
 * ticked from the world tick, as 1.19.4's {@code JukeboxBlockEntity.tick} does. Its state is 1.19.4's three fields
 * ({@code IsPlaying}, {@code RecordStartTick}, {@code TickCount}) plus whether the current disc has been seen yet,
 * kept in the tile's Forge data so it is saved with the jukebox. A disc put in by any means (a player, a hopper,
 * another mod) starts playing on the tick it is seen.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class Jukeboxes {

    static final String KEY = WildUpdateBackport.MOD_ID + ":jukebox";
    private static final ResourceLocation CAPABILITY = new ResourceLocation(WildUpdateBackport.MOD_ID, "jukebox_disc");

    /** 1.19.4's {@code RecordItem} lengths in seconds (later discs from their versions' {@code jukebox_song} files). */
    private static final Map<String, Integer> SECONDS = new HashMap<String, Integer>();

    static {
        Object[] lengths = {"13", 178, "cat", 185, "blocks", 345, "chirp", 185, "far", 174, "mall", 197, "mellohi", 96, "stal", 150,
                "strad", 188, "ward", 251, "11", 71, "wait", 238, "otherside", 195, "5", 178, "pigstep", 149, "relic", 218,
                "creator", 176, "creator_music_box", 73, "precipice", 299};
        for (int i = 0; i < lengths.length; i += 2) {
            SECONDS.put((String) lengths[i], (Integer) lengths[i + 1]);
        }
    }

    /** 1.19.4's {@code SONG_END_PADDING}. */
    private static final int SONG_END_PADDING = 20;

    private static final Set<TileEntityJukebox> TILES = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<TileEntityJukebox, Boolean>()));

    private Jukeboxes() {
    }

    static boolean active() {
        return ChangesHooks.on(ModConfig.changes.jukeboxes);
    }

    /**
     * @return the song's length in ticks, or -1 for a disc whose length is unknown (it plays until taken out)
     */
    public static int lengthInTicks(Item item) {
        ResourceLocation id = item.getRegistryName();
        if (id == null) {
            return -1;
        }
        String path = id.getPath();
        for (String prefix : new String[] {"music_disc_", "record_"}) {
            if (path.startsWith(prefix)) {
                Integer s = SECONDS.get(path.substring(prefix.length()));
                return s == null ? -1 : s * 20;
            }
        }
        return -1;
    }

    /** @return true while the tile's disc is playing (1.19.4's {@code isRecordPlaying}) */
    public static boolean isPlaying(@Nullable TileEntity te) {
        if (!(te instanceof TileEntityJukebox) || ((TileEntityJukebox) te).getRecord().isEmpty()) {
            return false;
        }
        NBTTagCompound data = te.getTileData();
        return data.hasKey(KEY) && data.getCompoundTag(KEY).getBoolean("IsPlaying");
    }

    private static NBTTagCompound data(TileEntityJukebox te) {
        NBTTagCompound forge = te.getTileData();
        if (!forge.hasKey(KEY, 10)) {
            forge.setTag(KEY, new NBTTagCompound());
        }
        return forge.getCompoundTag(KEY);
    }

    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<TileEntity> event) {
        if (!ModConfig.changes.enabled || !(event.getObject() instanceof TileEntityJukebox)) {
            return;
        }
        TileEntityJukebox te = (TileEntityJukebox) event.getObject();
        TILES.add(te);
        event.addCapability(CAPABILITY, new Provider(te));
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !active()) {
            return;
        }
        List<TileEntityJukebox> tiles;
        synchronized (TILES) {
            tiles = new ArrayList<TileEntityJukebox>(TILES);
        }
        for (TileEntityJukebox te : tiles) {
            if (te.getWorld() == event.world && !te.isInvalid() && event.world.isBlockLoaded(te.getPos())
                    && event.world.getTileEntity(te.getPos()) == te) {
                tick(te);
            }
        }
    }

    /** One tick of 1.19.4's {@code JukeboxBlockEntity.tick} (which only runs while the jukebox has a disc). */
    public static void tick(TileEntityJukebox te) {
        ItemStack record = te.getRecord();
        NBTTagCompound d = data(te);
        if (record.isEmpty()) {
            if (d.getBoolean("Started")) {
                boolean was = d.getBoolean("IsPlaying");
                d.setBoolean("Started", false);
                d.setBoolean("IsPlaying", false);
                if (was) {
                    notifyNeighbors(te);
                }
            }
            return;
        }
        long tickCount = d.getLong("TickCount");
        if (!d.getBoolean("Started")) {
            start(te, d, tickCount);
        }
        int sinceEvent = d.getInteger("TicksSinceLastEvent") + 1;
        if (d.getBoolean("IsPlaying") && record.getItem() instanceof ItemRecord) {
            int length = lengthInTicks(record.getItem());
            if (length >= 0 && tickCount >= d.getLong("RecordStartTick") + length + SONG_END_PADDING) {
                d.setBoolean("IsPlaying", false);
                notifyNeighbors(te);
            } else if (sinceEvent >= 20) {
                sinceEvent = 0;
                particles(te);
            }
        }
        d.setInteger("TicksSinceLastEvent", sinceEvent);
        d.setLong("TickCount", tickCount + 1);
        te.markDirty();
    }

    private static void start(TileEntityJukebox te, NBTTagCompound d, long tickCount) {
        d.setBoolean("Started", true);
        d.setBoolean("IsPlaying", true);
        d.setLong("RecordStartTick", tickCount);
        notifyNeighbors(te);
    }

    private static void notifyNeighbors(TileEntityJukebox te) {
        te.getWorld().notifyNeighborsOfStateChange(te.getPos(), Blocks.JUKEBOX, false);
    }

    /** 1.19.4's {@code spawnMusicParticles}: one note 1.2 above the block's bottom center, its color one of four. */
    private static void particles(TileEntityJukebox te) {
        World world = te.getWorld();
        if (world instanceof WorldServer) {
            BlockPos p = te.getPos();
            float color = (float) world.rand.nextInt(4) / 24.0F;
            ((WorldServer) world).spawnParticle(EnumParticleTypes.NOTE, p.getX() + 0.5, p.getY() + 1.2F, p.getZ() + 0.5, 0, color,
                    0.0, 0.0, 1.0);
        }
    }

    // ------------------------------------------------------------------------------------------

    private static final class Provider implements ICapabilityProvider {

        private final Discs handler;

        Provider(TileEntityJukebox te) {
            this.handler = new Discs(te);
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && active();
        }

        @Nullable
        @Override
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
            return hasCapability(capability, facing) ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.handler) : null;
        }
    }

    /**
     * 1.19.4's jukebox as a one-slot container: a disc goes in only when it is empty ({@code canPlaceItem}) and starts
     * playing ({@code setItem}); taking it out stops it ({@code removeItem}) without popping it into the world.
     */
    static final class Discs implements IItemHandler {

        private final TileEntityJukebox te;

        Discs(TileEntityJukebox te) {
            this.te = te;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.te.getRecord();
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            World world = this.te.getWorld();
            if (!active() || slot != 0 || stack.isEmpty() || !(stack.getItem() instanceof ItemRecord) || !this.te.getRecord().isEmpty()
                    || world == null) {
                return stack;
            }
            IBlockState state = world.getBlockState(this.te.getPos());
            if (state.getBlock() != Blocks.JUKEBOX) {
                return stack;
            }
            if (!simulate) {
                ItemStack one = stack.copy();
                one.setCount(1);
                ((BlockJukebox) Blocks.JUKEBOX).insertRecord(world, this.te.getPos(), state, one);
                world.playEvent(null, 1010, this.te.getPos(), Item.getIdFromItem(one.getItem()));
                NBTTagCompound d = data(this.te);
                start(this.te, d, d.getLong("TickCount"));
            }
            ItemStack rest = stack.copy();
            rest.shrink(1);
            return rest;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            World world = this.te.getWorld();
            ItemStack record = this.te.getRecord();
            if (!active() || slot != 0 || amount <= 0 || record.isEmpty() || world == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = record.copy();
            if (!simulate) {
                BlockPos pos = this.te.getPos();
                this.te.setRecord(ItemStack.EMPTY);
                IBlockState state = world.getBlockState(pos);
                if (state.getBlock() == Blocks.JUKEBOX) {
                    world.setBlockState(pos, state.withProperty(BlockJukebox.HAS_RECORD, false), 2);
                }
                world.playEvent(1010, pos, 0);
                world.playRecord(pos, null);
                tick(this.te);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof ItemRecord;
        }
    }
}
