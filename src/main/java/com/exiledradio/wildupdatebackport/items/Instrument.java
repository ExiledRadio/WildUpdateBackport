package com.exiledradio.wildupdatebackport.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * 1.19's eight goat horn instruments ({@code Instruments}): each plays its own sound
 * ({@code item.goat_horn.sound.0} to {@code .7}), for 140 ticks, heard 256 blocks away.
 *
 * <p>The first four are {@code #regular_goat_horns}, the last four {@code #screaming_goat_horns}. A horn
 * keeps its instrument as 1.19's does, in the {@code instrument} tag as a namespaced id
 * ({@code "minecraft:ponder_goat_horn"}); a bare name reads the same way.
 */
public enum Instrument {
    PONDER("ponder_goat_horn"),
    SING("sing_goat_horn"),
    SEEK("seek_goat_horn"),
    FEEL("feel_goat_horn"),
    ADMIRE("admire_goat_horn"),
    CALL("call_goat_horn"),
    YEARN("yearn_goat_horn"),
    DREAM("dream_goat_horn");

    /** 1.19's {@code Instruments.GOAT_HORN_DURATION}: the use time and the cooldown. */
    public static final int DURATION = 140;
    /** 1.19's {@code Instruments.GOAT_HORN_RANGE_BLOCKS}. */
    public static final float RANGE = 256.0F;
    public static final String TAG = "instrument";

    /** 1.19's {@code #regular_goat_horns} and {@code #screaming_goat_horns}, in their tag order. */
    public static final Instrument[] REGULAR = {PONDER, SING, SEEK, FEEL};
    public static final Instrument[] SCREAMING = {ADMIRE, CALL, YEARN, DREAM};

    public final String id;

    Instrument(String id) {
        this.id = id;
    }

    /** @return {@code minecraft:<id>}, as 1.19 writes it */
    public String key() {
        return "minecraft:" + this.id;
    }

    /** @return the sound event's path, {@code item.goat_horn.sound.<n>} */
    public String soundName() {
        return "item.goat_horn.sound." + this.ordinal();
    }

    /** @return the tooltip's translation key, 1.19's {@code instrument.minecraft.<id>} under this mod */
    public String translationKey() {
        return "instrument.wildupdatebackport." + this.id;
    }

    /** @return the instrument named by a string, namespaced or not; null if none */
    @Nullable
    public static Instrument byName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        ResourceLocation id = new ResourceLocation(name);
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }
        for (Instrument i : values()) {
            if (i.id.equals(id.getPath())) {
                return i;
            }
        }
        return null;
    }

    /**
     * @return the stack's instrument; a horn without one (or with an unknown one) is the first of
     * {@code #goat_horns}, Ponder, as 1.19's {@code InstrumentItem.getInstrument}
     */
    public static Instrument of(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        Instrument found = tag == null ? null : byName(tag.getString(TAG));
        return found != null ? found : PONDER;
    }

    public void write(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setString(TAG, this.key());
    }

    /** As 1.19's {@code getRandomElement}: one of the set, uniformly. */
    public static Instrument random(Instrument[] set, Random random) {
        return set[random.nextInt(set.length)];
    }

    /**
     * The horn a goat drops: 1.19's {@code Goat.createHorn} seeds a random with the goat's UUID hash, so one
     * goat always drops the same instrument.
     */
    public static Instrument forGoat(boolean screaming, UUID goat) {
        return random(screaming ? SCREAMING : REGULAR, new Random(goat.hashCode()));
    }
}
