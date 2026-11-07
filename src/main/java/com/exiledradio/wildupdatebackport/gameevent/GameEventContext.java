package com.exiledradio.wildupdatebackport.gameevent;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;

import javax.annotation.Nullable;

/** Who caused a game event and which block state it touched; either may be null. 1.19's {@code GameEvent.Context}. */
public final class GameEventContext {

    public static final GameEventContext NONE = new GameEventContext(null, null);

    @Nullable
    public final Entity source;
    @Nullable
    public final IBlockState state;

    private GameEventContext(@Nullable Entity source, @Nullable IBlockState state) {
        this.source = source;
        this.state = state;
    }

    public static GameEventContext of(@Nullable Entity source, @Nullable IBlockState state) {
        return source == null && state == null ? NONE : new GameEventContext(source, state);
    }

    public static GameEventContext of(@Nullable Entity source) {
        return of(source, null);
    }

    public static GameEventContext of(@Nullable IBlockState state) {
        return of(null, state);
    }
}
