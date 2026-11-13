package com.exiledradio.wildupdatebackport.deepdark;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Random;

/**
 * 1.19's reinforced deepslate ({@code Blocks.REINFORCED_DEEPSLATE}): deepslate's map color and sound, strength 55 and
 * blast resistance 1200, drops nothing ({@code noLootTable}), cannot be pushed ({@code PushReaction.BLOCK}), and is in
 * {@code #wither_immune} and {@code #dragon_immune}. Breakable in survival, slowly, with any tool.
 */
public class BlockReinforcedDeepslate extends Block {

    public BlockReinforcedDeepslate() {
        super(Material.ROCK, MapColor.STONE);
        setHardness(55.0F);
        // 1.12.2 keeps three times the given value and explosions read a fifth of that: 2000 is today's 1200, as
        // obsidian's 2000 is.
        setResistance(2000.0F);
        setSoundType(DeepslateCopies.deepslateSound());
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        if (entity instanceof EntityWither || entity instanceof EntityWitherSkull || entity instanceof EntityDragon) {
            return false;
        }
        return super.canEntityDestroy(state, world, pos, entity);
    }

    @Override
    public SoundType getSoundType() {
        return DeepslateCopies.deepslateSound();
    }
}
