package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.allay.AllaySelfTestAccess;
import com.exiledradio.wildupdatebackport.allay.EntityAllay;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockJukebox;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.block.BlockJukebox.TileEntityJukebox;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.WoodlandMansionPieces;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;
import java.util.Random;

/** Stream A: the allay. */
public final class AllaySelfTest implements SelfTest {

    @Override
    public String name() {
        return "allay";
    }

    @Override
    public void run(Check c) {
        WorldServer world = c.world;
        ResourceLocation id = Content.id("allay");

        // registration
        c.eq(EntityAllay.class, EntityList.getClass(id), "the allay is registered as wildupdatebackport:allay");
        c.ok(EntityList.ENTITY_EGGS.containsKey(id), "the allay has a spawn egg");
        for (String s : new String[]{"ambient_with_item", "ambient_without_item", "death", "hurt", "item_given", "item_taken", "item_thrown"}) {
            c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("entity.allay." + s)), "sound entity.allay." + s);
        }
        c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("block.amethyst_block.chime")), "our chime without Caves & Cliffs");
        c.ok(CriteriaTriggers.get(Content.id("thrown_item_picked_up_by_player")) != null, "trigger thrown_item_picked_up_by_player");
        c.ok(CriteriaTriggers.get(Content.id("allay_drop_item_on_block")) != null, "trigger allay_drop_item_on_block");
        c.ok(world.getMinecraftServer().getAdvancementManager()
                .getAdvancement(Content.id("husbandry/allay_deliver_item_to_player")) != null, "You've Got a Friend in Me loads");
        c.ok(world.getMinecraftServer().getAdvancementManager()
                .getAdvancement(Content.id("husbandry/allay_deliver_cake_to_note_block")) != null, "Birthday Song loads");

        // attributes and size
        BlockPos area = c.area();
        BlockPos at = area.add(8, 2, 8);
        EntityAllay allay = spawn(world, at);
        c.eq(20.0D, allay.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue(), "health 20");
        c.eq(0.1D, round(allay.getEntityAttribute(SharedMonsterAttributes.FLYING_SPEED).getBaseValue()), "flying speed 0.1");
        c.eq(0.1D, round(allay.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue()), "movement speed 0.1");
        c.eq(2.0D, allay.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue(), "attack damage 2");
        c.eq(48.0D, allay.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getBaseValue(), "follow range 48");
        c.eq(0.35F, allay.width, "width 0.35");
        c.eq(0.6F, allay.height, "height 0.6");
        c.eq(0.36F, round(allay.getEyeHeight()), "eyes at 0.6 of its height");
        c.ok(!AllaySelfTestAccess.canDespawn(allay), "never despawns");

        // giving and taking
        EntityPlayerMP player = Players.real(world, "allay");
        player.setPosition(at.getX() + 0.5, at.getY() - 1, at.getZ() + 2.5);
        world.playerEntities.add(player);
        try {
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.COOKIE, 3));
            c.ok(AllaySelfTestAccess.interact(allay, player, EnumHand.MAIN_HAND), "right-click with an item gives it");
            c.eq(1, allay.getHeldItemMainhand().getCount(), "the allay holds one");
            c.eq(2, player.getHeldItemMainhand().getCount(), "the player has one less");
            c.eq(player.getUniqueID(), AllaySelfTestAccess.likedPlayer(allay), "the allay likes the player now");
            c.ok(!allay.attackEntityFrom(DamageSource.causePlayerDamage(player), 4.0F), "the liked player cannot hurt it");
            c.ok(allay.isOnSameTeam(player), "the liked player is an ally");
            EntityAllay hurt = spawn(world, at.add(4, 0, 0));
            c.ok(hurt.attackEntityFrom(DamageSource.GENERIC, 2.0F), "anything else can");
            hurt.onUpdate();
            c.ok(AllaySelfTestAccess.panicking(hurt), "and it panics");
            hurt.setDead();

            // what it wants
            c.ok(allay.wantsToPickUp(new ItemStack(Items.COOKIE)), "it wants cookies while holding one");
            c.ok(!allay.wantsToPickUp(new ItemStack(Items.BREAD)), "and not bread");
            allay.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Blocks.WOOL, 1, EnumDyeColor.RED.getMetadata()));
            c.ok(allay.wantsToPickUp(new ItemStack(Blocks.WOOL, 1, EnumDyeColor.RED.getMetadata())), "red wool for red wool");
            c.ok(!allay.wantsToPickUp(new ItemStack(Blocks.WOOL, 1, EnumDyeColor.BLUE.getMetadata())), "not blue wool for red");
            allay.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD, 1, 0));
            c.ok(allay.wantsToPickUp(new ItemStack(Items.IRON_SWORD, 1, 40)), "a sword however damaged");
            allay.setHeldItem(EnumHand.MAIN_HAND, PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.HEALING));
            c.ok(allay.wantsToPickUp(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.HEALING)), "the same potion");
            c.ok(!allay.wantsToPickUp(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.SWIFTNESS)),
                    "not a different potion (current Java)");
            allay.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.COOKIE));

            // pickup: up to a stack
            EntityItem pile = new EntityItem(world, allay.posX, allay.posY, allay.posZ, new ItemStack(Items.COOKIE, 64));
            world.spawnEntity(pile);
            AllaySelfTestAccess.pickUp(allay, pile);
            c.eq(64, allay.getInventory().getStackInSlot(0).getCount(), "it picks up a whole stack");
            EntityItem more = new EntityItem(world, allay.posX, allay.posY, allay.posZ, new ItemStack(Items.COOKIE, 5));
            world.spawnEntity(more);
            c.ok(!allay.wantsToPickUp(more.getItem()), "but no more than one stack");
            more.setDead();
            allay.getInventory().setInventorySlotContents(0, new ItemStack(Items.COOKIE, 3));

            // delivering to the player, 3 blocks from its eyes
            allay.setPosition(player.posX, player.posY + player.getEyeHeight() + 0.5, player.posZ + 1.0);
            tick(world, allay);
            c.eq(2, allay.getInventory().getStackInSlot(0).getCount(), "it throws one item to its player");
            List<EntityItem> thrown = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(allay.getPosition()).grow(3));
            boolean marked = false;
            for (EntityItem e : thrown) {
                marked |= !e.isDead && e.getEntityData().hasUniqueId("wildupdatebackport:thrower");
            }
            c.ok(marked, "the thrown item remembers the allay");
            c.eq(60, AllaySelfTestAccess.pickupCooldown(allay), "and it waits 60 ticks before picking up again");
            for (EntityItem e : thrown) {
                e.setDead();
            }
            allay.getInventory().clear();

            // taking the item back
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            allay.getInventory().setInventorySlotContents(0, new ItemStack(Items.COOKIE, 2));
            c.ok(AllaySelfTestAccess.interact(allay, player, EnumHand.MAIN_HAND), "an empty hand takes the item back");
            c.ok(allay.getHeldItemMainhand().isEmpty(), "the allay's hand is empty");
            c.ok(player.inventory.hasItemStack(new ItemStack(Items.COOKIE)), "the player has the cookie");
            c.ok(allay.getInventory().isEmpty(), "and its inventory is thrown out");
            c.eq(null, AllaySelfTestAccess.likedPlayer(allay), "it likes nobody now");

            // note blocks: heard through vibrations, liked for 600 ticks
            BlockPos note = at.add(5, -1, 0);
            world.setBlockState(note, Blocks.NOTEBLOCK.getDefaultState());
            allay.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            AllaySelfTestAccess.setLikedPlayer(allay, player.getUniqueID());
            c.ok(GameEvents.listenerCount(world) >= 1, "the allay listens");
            GameEvents.emit(world, GameEvent.NOTE_BLOCK_PLAY, note, GameEventContext.NONE);
            GameEvents.flush(world);
            for (int i = 0; i < 8; i++) {
                AllaySelfTestAccess.tickListener(allay);
            }
            c.eq(note, AllaySelfTestAccess.likedNoteBlock(allay), "it heard the note block");
            c.eq(600, AllaySelfTestAccess.noteBlockCooldown(allay), "for 600 ticks");
            c.eq(note.up(), AllaySelfTestAccess.depositBlock(allay), "and delivers to it (above it) rather than the player");
            BlockPos other = at.add(-5, -1, 0);
            world.setBlockState(other, Blocks.NOTEBLOCK.getDefaultState());
            GameEvents.emit(world, GameEvent.NOTE_BLOCK_PLAY, other, GameEventContext.NONE);
            GameEvents.flush(world);
            for (int i = 0; i < 8; i++) {
                AllaySelfTestAccess.tickListener(allay);
            }
            c.eq(note, AllaySelfTestAccess.likedNoteBlock(allay), "another note block is ignored while it likes one");
            AllaySelfTestAccess.expireNoteBlock(allay);
            c.eq(new BlockPos(player), AllaySelfTestAccess.depositBlock(allay), "after 600 ticks it goes back to its player");
            c.eq(null, AllaySelfTestAccess.likedNoteBlock(allay), "and forgets the note block");

            // dancing and duplication
            BlockPos box = at.add(3, -1, 3);
            world.setBlockState(box, Blocks.JUKEBOX.getDefaultState().withProperty(BlockJukebox.HAS_RECORD, true));
            TileEntityJukebox te = (TileEntityJukebox) world.getTileEntity(box);
            if (te != null) {
                te.setRecord(new ItemStack(Items.RECORD_CAT));
            }
            for (int i = 0; i < 21 && !allay.isDancing(); i++) {
                tick(world, allay);
                allay.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            }
            c.ok(allay.isDancing(), "it dances near a jukebox holding a disc");
            ItemStack shard = new ItemStack(Items.PRISMARINE_SHARD, 2);
            c.ok(!com.exiledradio.wildupdatebackport.allay.AllayContent.isDuplicationItem(shard), "a prismarine shard is no amethyst");
            OreDictionary.registerOre("gemAmethyst", new ItemStack(Items.PRISMARINE_SHARD));
            c.ok(com.exiledradio.wildupdatebackport.allay.AllayContent.isDuplicationItem(shard), "gemAmethyst counts without Caves & Cliffs");
            player.setHeldItem(EnumHand.MAIN_HAND, shard);
            int before = world.getEntities(EntityAllay.class, e -> !e.isDead).size();
            c.ok(AllaySelfTestAccess.interact(allay, player, EnumHand.MAIN_HAND), "giving a dancing allay an amethyst shard");
            c.eq(before + 1, world.getEntities(EntityAllay.class, e -> !e.isDead).size(), "duplicates it");
            c.eq(1, player.getHeldItemMainhand().getCount(), "using up the shard");
            c.ok(!allay.canDuplicate(), "then it cannot duplicate");
            c.eq((long) EntityAllay.DUPLICATION_COOLDOWN, allay.getDuplicationCooldown(), "for 6000 ticks");
            AllaySelfTestAccess.interact(allay, player, EnumHand.MAIN_HAND);
            c.eq(before + 1, world.getEntities(EntityAllay.class, e -> !e.isDead).size(), "a second shard does nothing on cooldown");
            for (EntityAllay a : world.getEntities(EntityAllay.class, e -> e != allay && !e.isDead)) {
                c.ok(!a.canDuplicate() && a.isNoDespawnRequired(), "the copy is persistent and on cooldown too");
                a.setDead();
            }
            world.setBlockState(box, Blocks.JUKEBOX.getDefaultState());
            for (int i = 0; i < 21 && allay.isDancing(); i++) {
                tick(world, allay);
            }
            c.ok(!allay.isDancing(), "it stops dancing when the disc comes out");

            // saving
            allay.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.COOKIE));
            allay.getInventory().setInventorySlotContents(0, new ItemStack(Items.COOKIE, 7));
            NBTTagCompound tag = new NBTTagCompound();
            allay.writeToNBTOptional(tag);
            EntityAllay back = (EntityAllay) EntityList.createEntityFromNBT(tag, world);
            c.ok(back != null && back.getInventory().getStackInSlot(0).getCount() == 7, "the inventory survives saving");
            c.ok(back != null && player.getUniqueID().equals(AllaySelfTestAccess.likedPlayer(back)), "the liked player survives saving");
            c.ok(back != null && back.getDuplicationCooldown() == allay.getDuplicationCooldown() && !back.canDuplicate(),
                    "the duplication cooldown survives saving");

            // death drops: inventory and held item, but not a cursed one
            for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(allay.getPosition()).grow(4))) {
                e.setDead();
            }
            allay.attackEntityFrom(DamageSource.OUT_OF_WORLD, 100.0F);
            int cookies = 0;
            for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(allay.getPosition()).grow(2))) {
                if (!e.isDead && e.getItem().getItem() == Items.COOKIE) {
                    cookies += e.getItem().getCount();
                }
                e.setDead();
            }
            c.eq(8, cookies, "dying drops its inventory and its item");
            EntityAllay cursed = spawn(world, at);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.addEnchantment(Enchantments.VANISHING_CURSE, 1);
            c.ok(EnchantmentHelper.hasVanishingCurse(sword), "a cursed sword");
            cursed.setHeldItem(EnumHand.MAIN_HAND, sword);
            cursed.attackEntityFrom(DamageSource.OUT_OF_WORLD, 100.0F);
            boolean swordDropped = false;
            for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(cursed.getPosition()).grow(2))) {
                swordDropped |= !e.isDead && e.getItem().getItem() == Items.IRON_SWORD;
                e.setDead();
            }
            c.ok(!swordDropped, "Curse of Vanishing keeps the item from dropping");
        } finally {
            world.playerEntities.remove(player);
        }

        mansion(c, world);
        if (net.minecraftforge.fml.common.Loader.isModLoaded("villagerbackport")) {
            outpost(c, world);
        } else {
            c.eq(-1, AllaySelfTestAccess.cageIndex(), "without Village & Pillage no outpost cage is added");
        }
        c.ok(AllaySelfTest.class.getResource("/assets/villagerbackport/structures/pillager_outpost/feature_cage_with_allays.nbt") != null,
                "the converted outpost cage is in the jar");
    }

    /** 1.19's four "Group of Allays" markers in a placed 2x2_a1 room, every rotation. */
    private static void mansion(Check c, WorldServer world) {
        for (Rotation rotation : Rotation.values()) {
            BlockPos corner = c.area().add(8, 0, 8);
            WoodlandMansionPieces.MansionTemplate room = new WoodlandMansionPieces.MansionTemplate(
                    world.getStructureTemplateManager(), "2x2_a1", corner, rotation);
            StructureBoundingBox all = new StructureBoundingBox(corner.getX() - 32, 0, corner.getZ() - 32, corner.getX() + 32, 255, corner.getZ() + 32);
            room.addComponentParts(world, new Random(1), all);
            List<BlockPos> markers = AllaySelfTestAccess.mansionMarkers(room);
            c.eq(4, markers.size(), rotation + ": four cells");
            boolean air = true;
            boolean floored = true;
            for (BlockPos m : markers) {
                air &= world.isAirBlock(m);
                floored &= !world.isAirBlock(m.down(2)) || !world.isAirBlock(m.down());
            }
            c.ok(air, rotation + ": every marker is in the air of a cell");
            c.ok(floored, rotation + ": over a floor");
            int n = AllaySelfTestAccess.spawnMansionAllays(world, room, all, new Random(7));
            c.ok(n >= 4 && n <= 12, rotation + ": 1-3 allays per cell, got " + n);
            List<EntityAllay> found = world.getEntitiesWithinAABB(EntityAllay.class, new AxisAlignedBB(corner).grow(20));
            c.eq(n, found.size(), rotation + ": the allays are there");
            boolean persistent = true;
            for (EntityAllay a : found) {
                persistent &= a.isNoDespawnRequired();
                a.setDead();
            }
            c.ok(persistent, rotation + ": and persistent");
        }
    }

    /** With Village & Pillage: its features pool has the cage, the cage builds, and its two allays sit inside. */
    private static void outpost(Check c, WorldServer world) {
        int index = AllaySelfTestAccess.cageIndex();
        c.ok(index >= 0, "Village & Pillage's features pool has the allay cage");
        for (Rotation rotation : Rotation.values()) {
            BlockPos at = c.area().add(12, 0, 12);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", "VBP_ModernPiece");
            tag.setIntArray("BB", new int[]{at.getX() - 8, at.getY(), at.getZ() - 8, at.getX() + 8, at.getY() + 4, at.getZ() + 8});
            tag.setInteger("O", -1);
            tag.setInteger("GD", 0);
            tag.setString("Pool", "pillager_outpost/features");
            tag.setInteger("Element", index);
            tag.setInteger("PosX", at.getX());
            tag.setInteger("PosY", at.getY());
            tag.setInteger("PosZ", at.getZ());
            tag.setInteger("GroundLevelDelta", 0);
            tag.setString("Rotation", rotation.name());
            net.minecraft.world.gen.structure.StructureComponent piece =
                    net.minecraft.world.gen.structure.MapGenStructureIO.getStructureComponent(tag, world);
            if (!c.ok(piece != null, rotation + ": a Village & Pillage piece of the cage")) {
                return;
            }
            StructureBoundingBox all = new StructureBoundingBox(at.getX() - 16, 0, at.getZ() - 16, at.getX() + 16, 255, at.getZ() + 16);
            piece.addComponentParts(world, new Random(3), all);
            int fences = 0;
            for (BlockPos p : BlockPos.getAllInBox(at.add(-7, 0, -7), at.add(7, 4, 7))) {
                if (world.getBlockState(p).getBlock() == Blocks.DARK_OAK_FENCE) {
                    fences++;
                }
            }
            c.ok(fences > 20, rotation + ": the cage is built (" + fences + " fences)");
            int n = AllaySelfTestAccess.spawnCageAllays(world, piece, all);
            c.eq(2, n, rotation + ": two allays");
            List<EntityAllay> found = world.getEntitiesWithinAABB(EntityAllay.class, new AxisAlignedBB(at).grow(8));
            c.eq(2, found.size(), rotation + ": in the cage");
            for (EntityAllay a : found) {
                BlockPos p = new BlockPos(a);
                c.ok(world.isAirBlock(p),
                        rotation + ": an allay in the open at " + p.subtract(at));
                int fencesAround = 0;
                for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
                    for (int d = 1; d <= 3; d++) {
                        if (world.getBlockState(p.offset(f, d)).getBlock() == Blocks.DARK_OAK_FENCE) {
                            fencesAround++;
                            break;
                        }
                    }
                }
                c.eq(4, fencesAround, rotation + ": bars on all four sides");
                a.setDead();
            }
        }
    }

    private static EntityAllay spawn(WorldServer world, BlockPos at) {
        EntityAllay allay = new EntityAllay(world);
        allay.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        world.spawnEntity(allay);
        return allay;
    }

    /** One world tick of the allay, as the world gives it (which also counts its age). */
    private static void tick(WorldServer world, EntityAllay allay) {
        world.updateEntityWithOptionalForce(allay, true);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }

    private static float round(float v) {
        return Math.round(v * 1000.0F) / 1000.0F;
    }
}
