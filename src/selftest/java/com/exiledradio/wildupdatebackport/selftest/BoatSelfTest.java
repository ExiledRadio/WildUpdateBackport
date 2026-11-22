package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.boat.BoatWood;
import com.exiledradio.backportcore.boat.EntityWoodBoat;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;
import com.exiledradio.backportcore.boat.ItemWoodBoat;
import com.exiledradio.backportcore.boat.Boats;
import com.exiledradio.wildupdatebackport.boat.BoatContent;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/** Stream B: the mangrove boat and boats with chests. */
public final class BoatSelfTest implements SelfTest {

    @Override
    public String name() {
        return "boats";
    }

    @Override
    public void run(Check c) {
        WorldServer world = c.world;
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"};
        for (String w : woods) {
            Item item = Owners.ours(w + "_chest_boat");
            c.ok(item instanceof ItemWoodBoat, w + "_chest_boat is registered");
            if (item != null) {
                c.eq(1, item.getItemStackLimit(new ItemStack(item)), w + " chest boats do not stack");
                c.eq(1200, TileEntityFurnace.getItemBurnTime(new ItemStack(item)), w + " chest boat burns 1200 ticks");
            }
        }
        Item mangroveBoat = Owners.ours("mangrove_boat");
        c.ok(mangroveBoat instanceof ItemWoodBoat, "the mangrove boat is registered");
        BoatWood mangrove = BoatWood.get("wildupdatebackport:mangrove");
        c.eq("mangrove", mangrove.name, "the mangrove wood is known");
        c.eq("oak", BoatWood.get("nobody:gone").name, "an unknown wood falls back to oak");

        // recipes
        ItemStack planks = new ItemStack(Owners.ourBlock("mangrove_planks"));
        Crafting.expect(c, Crafting.grid(3, 2, planks, ItemStack.EMPTY, planks, planks, planks, planks), new ItemStack(mangroveBoat), "a mangrove boat");
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(Blocks.CHEST), new ItemStack(Items.SPRUCE_BOAT)),
                new ItemStack(Owners.ours("spruce_chest_boat")), "a spruce boat with chest");
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(mangroveBoat), new ItemStack(Blocks.CHEST)),
                new ItemStack(Owners.ours("mangrove_chest_boat")), "a mangrove boat with chest, either order");

        // saving
        BlockPos area = c.area().add(16, 0, 16);
        EntityWoodChestBoat boat = new EntityWoodChestBoat(world, area.getX() + 0.5, area.getY(), area.getZ() + 0.5, mangrove);
        boat.setInventorySlotContents(3, new ItemStack(Items.DIAMOND, 5));
        NBTTagCompound tag = new NBTTagCompound();
        boat.writeToNBTOptional(tag);
        c.eq("backportcore:chest_boat", tag.getString("id"), "the chest boat saves as Backport Core's");
        c.eq("wildupdatebackport:mangrove", tag.getString("Wood"), "with the wood's id");
        c.eq(Boats.BOAT, Boats.renamedTo(new net.minecraft.util.ResourceLocation("wildupdatebackport", "boat")), "our old boats load as Core's");
        c.eq(Boats.CHEST_BOAT, Boats.renamedTo(new net.minecraft.util.ResourceLocation("wildupdatebackport", "chest_boat")), "our old chest boats load as Core's");
        c.eq(8, BoatContent.items().size(), "eight items: the mangrove boat and seven chest boats");
        c.eq(Items.BOAT, BoatWood.get("minecraft:oak").boatItem(), "an oak chest boat is made from vanilla's boat");
        c.ok(BoatWood.get("minecraft:oak").chestBoatTexture() != null && "wildupdatebackport".equals(BoatWood.get("minecraft:oak").chestBoatTexture().getNamespace()), "vanilla woods' chest boats wear our 1.19 textures");
        EntityWoodChestBoat back = (EntityWoodChestBoat) EntityList.createEntityFromNBT(tag, world);
        c.ok(back != null && back.getWood() == mangrove, "the wood survives saving");
        c.ok(back != null && back.getStackInSlot(3).getItem() == Items.DIAMOND && back.getStackInSlot(3).getCount() == 5,
                "the chest's contents survive saving");
        c.eq(EntityWoodChestBoat.SIZE, boat.getSizeInventory(), "27 slots");

        EntityWoodBoat spruce = new EntityWoodChestBoat(world, 0, 0, 0, BoatWood.get("minecraft:spruce"));
        c.eq(EntityBoat.Type.SPRUCE, spruce.getBoatType(), "an old wood keeps vanilla's boat type too");
        c.eq(Owners.ours("spruce_chest_boat"), spruce.getItemBoat(), "a spruce chest boat drops itself");

        // riding and opening
        world.spawnEntity(boat);
        EntityPlayerMP player = Players.real(world, "boats");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(boat.posX + 1, boat.posY, boat.posZ);
        world.spawnEntity(player);
        player.setSneaking(true);
        c.ok(boat.processInitialInteract(player, EnumHand.MAIN_HAND), "sneaking right-click is handled");
        c.ok(player.openContainer instanceof ContainerChest, "sneaking opens the chest");
        player.closeScreen();
        player.setSneaking(false);
        boat.processInitialInteract(player, EnumHand.MAIN_HAND);
        c.ok(player.getRidingEntity() == boat, "right-click rides");
        EntityPlayerMP other = Players.real(world, "boats2");
        other.setPosition(boat.posX - 1, boat.posY, boat.posZ);
        world.spawnEntity(other);
        boat.processInitialInteract(other, EnumHand.MAIN_HAND);
        c.ok(other.getRidingEntity() == null && other.openContainer instanceof ContainerChest,
                "with the seat taken, a second player opens the chest instead");
        other.closeScreen();
        player.dismountRidingEntity();
        world.removeEntity(other);

        // hoppers
        BlockPos hopperAt = area.add(6, 0, 0);
        world.setBlockState(hopperAt, Blocks.HOPPER.getDefaultState());
        EntityWoodChestBoat fed = new EntityWoodChestBoat(world, hopperAt.getX() + 0.5, hopperAt.getY() + 1, hopperAt.getZ() + 0.5, mangrove);
        fed.setInventorySlotContents(0, new ItemStack(Items.APPLE, 2));
        world.spawnEntity(fed);
        TileEntityHopper hopper = (TileEntityHopper) world.getTileEntity(hopperAt);
        c.ok(TileEntityHopper.pullItems(hopper), "a hopper under a chest boat pulls from it");
        c.eq(1, fed.getStackInSlot(0).getCount(), "one item moved");
        fed.setDead();

        // breaking drops the boat and the contents; a crash of a mangrove boat drops mangrove planks
        boat.attackEntityFrom(DamageSource.causePlayerDamage(player), 100.0F);
        c.ok(boat.isDead, "a hard hit breaks it");
        c.eq(1, count(world, boat, Owners.ours("mangrove_chest_boat")), "it drops a mangrove boat with chest");
        c.eq(5, count(world, boat, Items.DIAMOND), "and its contents");

        EntityWoodBoat plain = new EntityWoodBoat(world, area.getX() + 10.5, area.getY() + 5, area.getZ() + 0.5, mangrove);
        world.spawnEntity(plain);
        plain.entityDropItem(new ItemStack(Blocks.PLANKS, 1, 0), 0.0F);
        c.eq(1, count(world, plain, Item.getItemFromBlock(Owners.ourBlock("mangrove_planks"))), "a mangrove boat's crash planks are mangrove");
        c.eq(mangroveBoat, plain.getItemBoat(), "a mangrove boat drops itself");
        plain.setDead();
    }

    private static int count(WorldServer world, net.minecraft.entity.Entity at, Item item) {
        int n = 0;
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at.getPosition()).grow(3))) {
            if (!e.isDead && e.getItem().getItem() == item) {
                n += e.getItem().getCount();
                e.setDead();
            }
        }
        return n;
    }
}
