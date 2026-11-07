package com.exiledradio.wildupdatebackport.boat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 1.19's boat with chest: a boat with one seat and a 27-slot chest.
 *
 * <ul>
 *   <li><b>Opening</b>: right-click while sneaking, or when the seat is taken; otherwise right-click rides.
 *       The chest screen is vanilla's single chest, titled with the boat's name.</li>
 *   <li><b>One seat</b>, the rider 0.15 blocks forward of the middle (1.19's single-passenger offset).</li>
 *   <li><b>Breaking</b> it drops the boat with chest and everything inside, as 1.19's; so does a crash and
 *       {@code /kill}. A creative player's break drops neither, as in 1.19.</li>
 *   <li><b>Hoppers</b> fill and empty it, as 1.19's: being an {@link IInventory} entity is how a 1.12.2 hopper
 *       finds minecarts with chests, and it finds this the same way.</li>
 * </ul>
 */
public class EntityWoodChestBoat extends EntityWoodBoat implements IInventory {

    public static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean dropped;

    public EntityWoodChestBoat(World world) {
        super(world);
    }

    public EntityWoodChestBoat(World world, double x, double y, double z, BoatWood wood) {
        super(world, x, y, z, wood);
    }

    @Override
    public Item getItemBoat() {
        Item item = getWood().chestBoatItem();
        return item != null ? item : Items.BOAT;
    }

    @Override
    protected boolean canFitPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public void updatePassenger(Entity passenger) {
        super.updatePassenger(passenger);
        if (this.isPassenger(passenger)) {
            Vec3d offset = new Vec3d(0.15D, 0.0D, 0.0D).rotateYaw(-this.rotationYaw * 0.017453292F - ((float) Math.PI / 2F));
            passenger.setPosition(passenger.posX + offset.x, passenger.posY, passenger.posZ + offset.z);
        }
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (!player.isSneaking() && canFitPassenger(player)) {
            return super.processInitialInteract(player, hand);
        }
        if (!this.world.isRemote) {
            player.displayGUIChest(this);
        }
        return true;
    }

    // --- breaking ----------------------------------------------------------------------------

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean creative = source.getTrueSource() instanceof EntityPlayer
                && ((EntityPlayer) source.getTrueSource()).capabilities.isCreativeMode;
        boolean hit = super.attackEntityFrom(source, amount);
        if (this.isDead && !creative) {
            dropContents();
        }
        return hit;
    }

    @Override
    protected void updateFallState(double y, boolean onGround, IBlockState state, BlockPos pos) {
        super.updateFallState(y, onGround, state, pos);
        if (this.isDead) {
            dropContents();
        }
    }

    @Override
    public void onKillCommand() {
        dropContents();
        super.onKillCommand();
    }

    private void dropContents() {
        if (!this.world.isRemote && !this.dropped && this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.dropped = true;
            InventoryHelper.dropInventoryItems(this.world, this, this);
        }
    }

    // --- saving -------------------------------------------------------------------------------

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        ItemStackHelper.saveAllItems(compound, this.items);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(compound, this.items);
    }

    // --- the chest ----------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : this.items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return this.items.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(this.items, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(this.items, index);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        this.items.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return !this.isDead && player.getDistanceSq(this) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        this.items.clear();
    }
}
