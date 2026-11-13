package com.exiledradio.wildupdatebackport.warden;

import net.minecraft.util.SoundEvent;

/** 1.19's {@code AngerLevel}: calm below 40, agitated from 40, angry from 80, each with its ambient and listening sound. */
public enum AngerLevel {
    CALM(0),
    AGITATED(40),
    ANGRY(80);

    private final int minimumAnger;

    AngerLevel(int minimumAnger) {
        this.minimumAnger = minimumAnger;
    }

    public int getMinimumAnger() {
        return this.minimumAnger;
    }

    public SoundEvent getAmbientSound() {
        switch (this) {
            case ANGRY:
                return WardenContent.ANGRY;
            case AGITATED:
                return WardenContent.AGITATED;
            default:
                return WardenContent.AMBIENT;
        }
    }

    public SoundEvent getListeningSound() {
        return this == CALM ? WardenContent.LISTENING : WardenContent.listeningAngry();
    }

    public static AngerLevel byAnger(int anger) {
        if (anger >= ANGRY.minimumAnger) {
            return ANGRY;
        }
        return anger >= AGITATED.minimumAnger ? AGITATED : CALM;
    }

    public boolean isAngry() {
        return this == ANGRY;
    }
}
