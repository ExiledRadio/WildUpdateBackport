package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The sculk sensor: 1.19.2's {@code SculkSensorBlock}. Half a block high, hardness 1.5, light 1; with Silk Touch
 * drops itself, otherwise 5 experience. Hears vibrations within 8 blocks ({@link TileSculkSensor}).
 *
 * <p>{@code sculk_sensor_phase}: {@code inactive} -> heard something -> {@code active} for 40 ticks, giving off
 * redstone power from 15 (right next to it) down to 1 (8 blocks away) -> {@code cooldown} for 1 tick -> inactive.
 * A comparator reads the last vibration's frequency (current Java's, 1-15) while it is active. Stepping on it
 * activates it at full power, sneaking or not; a warden stepping on it does not.
 *
 * <p>It holds a water source ({@link Waterloggable}, with Fluidlogged API), and a waterlogged sensor clicks
 * silently: 1.19.2 plays neither {@code sculk_clicking} nor {@code sculk_clicking_stop} for it.
 *
 * <p>The power lives in the tile entity (phase x power is 48 states, more than metadata holds); 1.19's
 * {@code power} property is not part of any model, so nothing is lost on the client.
 */
public class BlockSculkSensor extends Block implements Waterloggable {

    public enum Phase implements IStringSerializable {
        INACTIVE("inactive"), ACTIVE("active"), COOLDOWN("cooldown");

        private final String name;

        Phase(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    public static final PropertyEnum<Phase> PHASE = PropertyEnum.create("sculk_sensor_phase", Phase.class);
    public static final int ACTIVE_TICKS = 40;
    public static final int COOLDOWN_TICKS = 1;
    public static final int RANGE = 8;
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 0.5D, 1);
    private static final ResourceLocation WARDEN = new ResourceLocation("wildupdatebackport", "warden");

    public BlockSculkSensor() {
        super(SculkContent.SCULK_MATERIAL, net.minecraft.block.material.MapColor.CYAN);
        this.setHardness(1.5F);
        this.setResistance(1.5F * 5.0F / 3.0F);
        this.setLightLevel(1.0F / 15.0F);
        this.setSoundType(SculkContent.SENSOR_SOUND);
        this.setDefaultState(this.blockState.getBaseState().withProperty(PHASE, Phase.INACTIVE));
    }

    public static Phase phase(IBlockState state) {
        return state.getValue(PHASE);
    }

    public static boolean canActivate(IBlockState state) {
        return state.getBlock() instanceof BlockSculkSensor && phase(state) == Phase.INACTIVE;
    }

    static boolean isWarden(Entity entity) {
        return WARDEN.equals(EntityList.getKey(entity));
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (phase(state) != Phase.ACTIVE) {
            if (phase(state) == Phase.COOLDOWN) {
                world.setBlockState(pos, state.withProperty(PHASE, Phase.INACTIVE), 3);
            }
        } else {
            deactivate(world, pos, state);
        }
    }

    /** 1.19's {@code stepOn}. Also reached for sneaking players, whom 1.12.2's walking skips (see SculkContent). */
    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        stepOn(world, pos, entity);
    }

    static void stepOn(World world, BlockPos pos, Entity entity) {
        IBlockState state = world.getBlockState(pos);
        if (!world.isRemote && canActivate(state) && !isWarden(entity)) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSculkSensor) {
                ((TileSculkSensor) te).setLastVibrationFrequency(GameEvent.STEP.frequency());
            }
            activate(entity, world, pos, state, 15);
        }
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSculkSensor && ((TileSculkSensor) te).power() > 0 && !world.isUpdateScheduled(pos, this)) {
                ((TileSculkSensor) te).setPower(0);
            }
            world.scheduleUpdate(pos, this, 1);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (phase(state) == Phase.ACTIVE) {
            updateNeighbors(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    private static void updateNeighbors(World world, BlockPos pos) {
        Block block = SculkContent.sensorBlock();
        world.notifyNeighborsOfStateChange(pos, block, false);
        world.notifyNeighborsOfStateChange(pos.down(), block, false);
    }

    public static void deactivate(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileSculkSensor) {
            ((TileSculkSensor) te).setPower(0);
        }
        world.setBlockState(pos, state.withProperty(PHASE, Phase.COOLDOWN), 3);
        world.scheduleUpdate(pos, state.getBlock(), COOLDOWN_TICKS);
        if (!Waterlogging.hasWater(world, pos)) {
            world.playSound(null, pos, SculkContent.SENSOR_CLICKING_STOP, SoundCategory.BLOCKS, 1.0F, world.rand.nextFloat() * 0.2F + 0.8F);
        }
        updateNeighbors(world, pos);
    }

    public static void activate(@Nullable Entity source, World world, BlockPos pos, IBlockState state, int power) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileSculkSensor) {
            ((TileSculkSensor) te).setPower(power);
        }
        world.setBlockState(pos, state.withProperty(PHASE, Phase.ACTIVE), 3);
        world.scheduleUpdate(pos, state.getBlock(), ACTIVE_TICKS);
        updateNeighbors(world, pos);
        GameEvents.emit(world, GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, pos, GameEventContext.of(source));
        if (!Waterlogging.hasWater(world, pos)) {
            world.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SculkContent.SENSOR_CLICKING, SoundCategory.BLOCKS,
                    1.0F, world.rand.nextFloat() * 0.2F + 0.8F);
        }
    }

    /** 1.19's sculk-to-redstone dust while active; 1.12.2's dust takes one color, so it is sculk's cyan. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (phase(state) != Phase.ACTIVE) {
            return;
        }
        EnumFacing dir = EnumFacing.random(rand);
        if (dir == EnumFacing.UP || dir == EnumFacing.DOWN) {
            return;
        }
        double x = pos.getX() + 0.5D + (dir.getXOffset() == 0 ? 0.5D - rand.nextDouble() : dir.getXOffset() * 0.6D);
        double y = pos.getY() + 0.25D;
        double z = pos.getZ() + 0.5D + (dir.getZOffset() == 0 ? 0.5D - rand.nextDouble() : dir.getZOffset() * 0.6D);
        // 1.12.2's redstone dust reads its speed as a color: 0x39D6E0, sculk's particle color.
        world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 0.2235D, 0.8392D, 0.8784D);
    }

    // --- redstone ----------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSculkSensor ? ((TileSculkSensor) te).power() : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSculkSensor && phase(state) == Phase.ACTIVE ? ((TileSculkSensor) te).lastVibrationFrequency() : 0;
    }

    // --- shape -------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public PathNodeType getAiPathNodeType(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EntityLiving entity) {
        return PathNodeType.BLOCKED;
    }

    // --- state, tile entity, drops -------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, PHASE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return phase(state).ordinal();
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        Phase[] all = Phase.values();
        return this.getDefaultState().withProperty(PHASE, all[Math.min(meta, all.length - 1)]);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSculkSensor();
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
}
