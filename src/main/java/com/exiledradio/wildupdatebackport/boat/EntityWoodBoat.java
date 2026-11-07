package com.exiledradio.wildupdatebackport.boat;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A boat of any {@link BoatWood}: used for woods vanilla's boat cannot be (mangrove), and as the base of
 * {@link EntityWoodChestBoat}. Everything a boat does - paddling, floating, carrying, breaking - is
 * vanilla's {@link EntityBoat}; this adds the wood (synced to clients, saved as {@code Wood}) and what the
 * wood changes: the item it drops and is picked as, and the planks a crash drops.
 *
 * <p>For the six old woods vanilla's own type is set as well, so vanilla's code that reads it (the planks
 * a crash drops) is right without help. New woods carry oak's type and swap the planks as they drop.
 */
public class EntityWoodBoat extends EntityBoat {

    private static final DataParameter<String> WOOD = EntityDataManager.createKey(EntityWoodBoat.class, DataSerializers.STRING);

    public EntityWoodBoat(World world) {
        super(world);
    }

    public EntityWoodBoat(World world, double x, double y, double z, BoatWood wood) {
        super(world, x, y, z);
        setWood(wood);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(WOOD, "minecraft:oak");
    }

    public BoatWood getWood() {
        return BoatWood.get(this.dataManager.get(WOOD));
    }

    public void setWood(BoatWood wood) {
        this.dataManager.set(WOOD, wood.id);
        this.setBoatType(wood.vanilla != null ? wood.vanilla : Type.OAK);
    }

    @Override
    public Item getItemBoat() {
        Item item = getWood().boatItem();
        return item != null ? item : Items.BOAT;
    }

    /** A crash drops vanilla's planks of the vanilla type; a new wood drops its own instead. */
    @Override
    @Nullable
    public EntityItem entityDropItem(ItemStack stack, float offsetY) {
        BoatWood wood = getWood();
        if (wood.vanilla == null && !stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) == Blocks.PLANKS) {
            ItemStack planks = wood.planks().copy();
            planks.setCount(stack.getCount());
            stack = planks;
        }
        return super.entityDropItem(stack, offsetY);
    }

    @Override
    public ItemStack getPickedResult(net.minecraft.util.math.RayTraceResult target) {
        return new ItemStack(getItemBoat());
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setString("Wood", this.dataManager.get(WOOD));
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("Wood", 8)) {
            setWood(BoatWood.get(compound.getString("Wood")));
        }
    }
}
