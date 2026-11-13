package com.exiledradio.wildupdatebackport.items;

import net.minecraft.item.ItemRecord;
import net.minecraft.util.SoundEvent;

/**
 * Music disc 5 on 1.12.2's own record item, so the jukebox, the "Now Playing" line, the tooltip, the rare name
 * color and the record stat are vanilla's. Its description line is {@code item.record.5.desc}, as 1.12.2's
 * records name theirs.
 *
 * <p>1.19's disc 5 gives a comparator signal of 15. 1.12.2's jukebox works the signal out from the item id, right
 * only for vanilla's twelve discs; the value is kept here under the name the family's discs use
 * ({@code comparatorSignal()}), for the jukebox patch that reads it.
 */
public final class ItemMusicDisc extends ItemRecord {

    private final SoundEvent music;
    private final int signal;

    public ItemMusicDisc(String name, SoundEvent sound, int signal) {
        super(name, sound);
        this.music = sound;
        this.signal = signal;
    }

    /** @return the comparator signal from a jukebox playing this disc (1.19's {@code analogOutput}) */
    public int comparatorSignal() {
        return this.signal;
    }

    /** @return the disc's sound event (vanilla's getter is client-only) */
    public SoundEvent music() {
        return this.music;
    }
}
