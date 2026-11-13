package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * The sculk catalyst: 1.19.2's {@code SculkCatalystBlock}. Hardness 3, resistance 3, light 6; with Silk Touch drops
 * itself, otherwise 5 experience (**now:** 1.19.1). Its tile entity hears mobs die within 8 blocks
 * ({@link TileSculkCatalyst}); each one makes it bloom for 8 ticks with souls and a sound.
 */
public class BlockSculkCatalyst extends Block {

    public static final PropertyBool BLOOM = PropertyBool.create("bloom");
    public static final int PULSE_TICKS = 8;

    public BlockSculkCatalyst() {
        super(SculkContent.SCULK_MATERIAL);
        this.setHardness(3.0F);
        this.setResistance(3.0F * 5.0F / 3.0F);
        this.setLightLevel(6.0F / 15.0F);
        this.setSoundType(SculkContent.CATALYST_SOUND);
        this.setDefaultState(this.blockState.getBaseState().withProperty(BLOOM, false));
    }

    /** Blooms: 8 ticks of {@code bloom}, two souls rising off the top, and the bloom sound. */
    public static void bloom(WorldServer world, BlockPos pos, IBlockState state, Random random) {
        world.setBlockState(pos, state.withProperty(BLOOM, true), 3);
        world.scheduleUpdate(pos, state.getBlock(), PULSE_TICKS);
        SculkEffectPacket.send(world, pos, java.util.Collections.singletonList(new SculkEffectPacket.Entry(SculkEffectPacket.SOUL, pos, 2)));
        world.playSound(null, pos, SculkContent.CATALYST_BLOOM, SoundCategory.BLOCKS, 2.0F, 0.6F + random.nextFloat() * 0.4F);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (state.getValue(BLOOM)) {
            world.setBlockState(pos, state.withProperty(BLOOM, false), 3);
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BLOOM);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BLOOM) ? 1 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(BLOOM, (meta & 1) != 0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSculkCatalyst();
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return true;
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        return 5;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }
}
