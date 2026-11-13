package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.block.SoundType;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

/** The frog stream's sound events (current Java's sounds.json) and the froglight and frogspawn sound types. */
public final class FrogSounds {

    private static final List<SoundEvent> ALL = new ArrayList<SoundEvent>();

    public static final SoundEvent FROG_AMBIENT = make("entity.frog.ambient");
    public static final SoundEvent FROG_DEATH = make("entity.frog.death");
    public static final SoundEvent FROG_EAT = make("entity.frog.eat");
    public static final SoundEvent FROG_HURT = make("entity.frog.hurt");
    public static final SoundEvent FROG_LAY_SPAWN = make("entity.frog.lay_spawn");
    public static final SoundEvent FROG_LONG_JUMP = make("entity.frog.long_jump");
    public static final SoundEvent FROG_STEP = make("entity.frog.step");
    public static final SoundEvent FROG_TONGUE = make("entity.frog.tongue");
    public static final SoundEvent TADPOLE_DEATH = make("entity.tadpole.death");
    public static final SoundEvent TADPOLE_FLOP = make("entity.tadpole.flop");
    public static final SoundEvent TADPOLE_GROW_UP = make("entity.tadpole.grow_up");
    public static final SoundEvent TADPOLE_HURT = make("entity.tadpole.hurt");
    public static final SoundEvent BUCKET_EMPTY_TADPOLE = make("item.bucket.empty_tadpole");
    public static final SoundEvent BUCKET_FILL_TADPOLE = make("item.bucket.fill_tadpole");
    public static final SoundEvent FROGSPAWN_HATCH = make("block.frogspawn.hatch");

    /** 1.19's {@code SoundType.FROGLIGHT}. */
    public static final SoundType FROGLIGHT = type("froglight");
    /** 1.19's {@code SoundType.FROGSPAWN}. */
    public static final SoundType FROGSPAWN = type("frogspawn");

    private FrogSounds() {
    }

    private static SoundEvent make(String name) {
        SoundEvent e = new SoundEvent(Content.id(name));
        e.setRegistryName(Content.id(name));
        ALL.add(e);
        return e;
    }

    private static SoundType type(String block) {
        String b = "block." + block + ".";
        return new SoundType(1.0F, 1.0F, make(b + "break"), make(b + "step"), make(b + "place"), make(b + "hit"), make(b + "fall"));
    }

    static void register(IForgeRegistry<SoundEvent> registry) {
        for (SoundEvent e : ALL) {
            registry.register(e);
        }
    }

    /** @return every sound event of this stream, for the tests */
    public static List<SoundEvent> all() {
        return ALL;
    }
}
