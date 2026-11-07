package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

/** The shared code every stream builds on. */
public final class ScaffoldSelfTest implements SelfTest {

    @Override
    public String name() {
        return "scaffold";
    }

    @Override
    public void run(Check check) {
        BlockPos area = check.area();
        check.ok(check.world.isAirBlock(area.up()), "test area is clear");
        check.ok(Loader.isModLoaded("backportcore"), "Backport Core is loaded");
        check.ok(Loader.isModLoaded(WildUpdateBackport.MOD_ID), "this mod is loaded");
        check.ok(ModConfig.sculk.enabled && ModConfig.mangrove.enabled, "streams are on by default");
        check.ok(!Owners.present(Owners.CAVES_AND_CLIFFS), "no Caves & Cliffs in the dev environment");
        check.eq(null, Owners.ours("no_such_thing"), "a missing item looks up as null");
    }
}
