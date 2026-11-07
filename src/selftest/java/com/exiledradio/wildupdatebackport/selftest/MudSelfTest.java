package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/** Stream D: mud. */
public final class MudSelfTest implements SelfTest {

    @Override
    public String name() {
        return "mud";
    }

    @Override
    public void run(Check c) {
        WorldServer world = c.world;
        for (String n : new String[] {"mud", "packed_mud", "mud_bricks", "mud_brick_stairs", "mud_brick_slab",
                "mud_brick_double_slab", "mud_brick_wall"}) {
            c.ok(Owners.ourBlock(n) != null, n + " is registered");
        }
        Block mud = Owners.ourBlock("mud");
        c.ok(Math.abs(mud.getCollisionBoundingBox(mud.getDefaultState(), world, BlockPos.ORIGIN).maxY - 0.875D) < 1e-6,
                "mud's collision is 14 pixels high");
        c.ok(Math.abs(Owners.ourBlock("mud_bricks").getExplosionResistance(null) - 3.0F) < 0.01F, "mud bricks resistance 3");

        ItemStack m = new ItemStack(mud);
        ItemStack pm = new ItemStack(Owners.ourBlock("packed_mud"));
        Crafting.expect(c, Crafting.grid(2, 1, m, new ItemStack(Items.WHEAT)), pm, "packed mud from mud and wheat");
        Crafting.expect(c, Crafting.grid(2, 2, pm, pm, pm, pm), new ItemStack(Owners.ourBlock("mud_bricks"), 4), "mud bricks");
        Crafting.expect(c, Crafting.grid(2, 1, m, new ItemStack(Owners.ourBlock("mangrove_roots"))),
                new ItemStack(Owners.ourBlock("muddy_mangrove_roots")), "muddy mangrove roots from mud and roots");

        BlockPos area = c.area();
        EntityPlayerMP player = Players.real(world, "mud");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        BlockPos dirt = area.add(4, -1, 4);
        world.setBlockState(dirt, Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.COARSE_DIRT));
        ItemStack bottle = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
        player.setHeldItem(EnumHand.MAIN_HAND, bottle);
        player.interactionManager.processRightClickBlock(player, world, bottle, EnumHand.MAIN_HAND, dirt, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(mud, world.getBlockState(dirt).getBlock(), "a water bottle turns coarse dirt into mud");
        c.eq(Items.GLASS_BOTTLE, player.getHeldItem(EnumHand.MAIN_HAND).getItem(), "and leaves a glass bottle");

        BlockPos podzol = area.add(6, -1, 4);
        world.setBlockState(podzol, Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.PODZOL));
        ItemStack b2 = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
        player.setHeldItem(EnumHand.MAIN_HAND, b2);
        player.interactionManager.processRightClickBlock(player, world, b2, EnumHand.MAIN_HAND, podzol, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(Blocks.DIRT, world.getBlockState(podzol).getBlock(), "podzol is not made into mud");
    }
}
