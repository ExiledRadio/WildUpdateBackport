package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.items.Discovery;
import com.exiledradio.wildupdatebackport.items.EnchantmentSwiftSneak;
import com.exiledradio.wildupdatebackport.items.GoatHorns;
import com.exiledradio.wildupdatebackport.items.Instrument;
import com.exiledradio.wildupdatebackport.items.ItemGoatHorn;
import com.exiledradio.wildupdatebackport.items.ItemMusicDisc;
import com.exiledradio.wildupdatebackport.items.ItemsContent;
import com.exiledradio.wildupdatebackport.items.ItemsLoot;
import com.exiledradio.wildupdatebackport.items.LastDeathLocation;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStone;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Stream I: recovery compass and last death location, echo shards, disc 5 and fragments, Swift Sneak, goat horns. */
public final class ItemsSelfTest implements SelfTest {

    @Override
    public String name() {
        return "items";
    }

    @Override
    public void run(Check c) throws Exception {
        registry(c);
        recipes(c);
        lastDeath(c);
        swiftSneak(c);
        discovery(c);
        goatHorn(c);
        loot(c);
    }

    private static void registry(Check c) {
        for (String n : new String[] {"echo_shard", "recovery_compass", "music_disc_5", "disc_fragment_5", "goat_horn"}) {
            c.ok(Owners.ours(n) != null, n + " is registered");
        }
        c.ok(ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("wildupdatebackport:swift_sneak")) == ItemsContent.swiftSneak(),
                "swift_sneak is registered");
        for (String s : new String[] {"item.goat_horn.sound.0", "item.goat_horn.sound.7", "entity.goat.horn_break", "music_disc.5"}) {
            c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("wildupdatebackport", s)), "sound " + s);
        }
        c.eq(64, new ItemStack(ItemsContent.echoShard()).getMaxStackSize(), "echo shards stack to 64");
        c.eq(64, new ItemStack(ItemsContent.discFragment()).getMaxStackSize(), "fragments stack to 64");
        c.eq(64, new ItemStack(ItemsContent.recoveryCompass()).getMaxStackSize(), "recovery compasses stack to 64");
        ItemMusicDisc disc = ItemsContent.musicDisc5();
        c.eq(1, new ItemStack(disc).getMaxStackSize(), "disc 5 stacks to 1");
        c.eq(EnumRarity.RARE, disc.getRarity(new ItemStack(disc)), "disc 5 is rare");
        c.eq(15, disc.comparatorSignal(), "disc 5's comparator signal");
        c.ok(disc.music() == ItemsContent.MUSIC_DISC_5, "disc 5 plays music_disc.5");
        c.eq(1, new ItemStack(ItemsContent.goatHorn()).getMaxStackSize(), "goat horns stack to 1");
    }

    private static void recipes(Check c) {
        ItemStack s = new ItemStack(ItemsContent.echoShard());
        Crafting.expect(c, Crafting.grid(3, 3, s, s, s, s, new ItemStack(Items.COMPASS), s, s, s, s),
                new ItemStack(ItemsContent.recoveryCompass()), "recovery compass from a compass and 8 echo shards");
        ItemStack f = new ItemStack(ItemsContent.discFragment());
        Crafting.expect(c, Crafting.grid(3, 3, f, f, f, f, f, f, f, f, f), new ItemStack(ItemsContent.musicDisc5()),
                "disc 5 from 9 fragments");
    }

    // --- last death location -------------------------------------------------------------------

    private static void lastDeath(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        EntityPlayerMP player = Players.real(world, "death");
        player.setPosition(area.getX() + 3.7, area.getY(), area.getZ() + 5.2);
        c.ok(LastDeathLocation.get(player) == null, "no last death location before dying");
        ForgeHooks.onLivingDeath(player, DamageSource.GENERIC);
        LastDeathLocation where = LastDeathLocation.get(player);
        c.eq(new LastDeathLocation(0, new BlockPos(area.getX() + 3, area.getY(), area.getZ() + 5)), where,
                "dying sets the last death location to the player's block");

        EntityPlayerMP respawned = Players.real(world, "death2");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
        c.eq(where, LastDeathLocation.get(respawned), "the respawned player keeps it");

        NBTTagCompound saved = new NBTTagCompound();
        respawned.writeToNBT(saved);
        EntityPlayerMP loaded = Players.real(world, "death3");
        loaded.readFromNBT(saved);
        c.eq(where, LastDeathLocation.get(loaded), "it is saved with the player");

        ByteBuf buf = Unpooled.buffer();
        new LastDeathLocation.Packet(where).toBytes(buf);
        LastDeathLocation.Packet back = new LastDeathLocation.Packet();
        back.fromBytes(buf);
        c.eq(where, back.where, "the packet carries it");
        buf = Unpooled.buffer();
        new LastDeathLocation.Packet(null).toBytes(buf);
        back.fromBytes(buf);
        c.ok(back.where == null, "the packet carries none");

        FakePlayer fake = c.player("deathfake", area);
        ForgeHooks.onLivingDeath(fake, DamageSource.GENERIC);
        c.ok(LastDeathLocation.get(fake) == null, "fake players keep no death location");
    }

    // --- Swift Sneak --------------------------------------------------------------------------

    private static void swiftSneak(Check c) {
        EnchantmentSwiftSneak e = ItemsContent.swiftSneak();
        c.eq(3, e.getMaxLevel(), "Swift Sneak max level");
        c.eq(Enchantment.Rarity.VERY_RARE, e.getRarity(), "Swift Sneak is very rare");
        c.eq(EnumEnchantmentType.ARMOR_LEGS, e.type, "Swift Sneak is for leggings");
        c.eq(50, e.getMinEnchantability(2), "min enchantability 25 per level");
        c.eq(100, e.getMaxEnchantability(2), "max enchantability +50");
        c.ok(e.isTreasureEnchantment(), "treasure");
        c.ok(Discovery.hidden(e), "undiscoverable and untradeable");
        c.ok(e.canApply(new ItemStack(Items.DIAMOND_LEGGINGS)), "an anvil puts it on leggings");
        c.ok(!e.canApply(new ItemStack(Items.DIAMOND_CHESTPLATE)), "not on a chestplate");
        boolean offered = false;
        for (int level = 1; level <= 100; level++) {
            for (ItemStack stack : new ItemStack[] {new ItemStack(Items.BOOK), new ItemStack(Items.IRON_LEGGINGS)}) {
                for (EnchantmentData d : EnchantmentHelper.getEnchantmentDatas(level, stack, true)) {
                    offered |= d.enchantment == e;
                }
            }
        }
        c.ok(!offered, "never offered by tables or enchant_with_levels, treasure or not");

        BlockPos area = c.area();
        FakePlayer player = c.player("sneak", area);
        c.ok(Math.abs(EnchantmentSwiftSneak.sneakSpeed(e, player) - 0.3F) < 1e-6, "sneaking speed 0.3 without it");
        float[] want = {0.45F, 0.6F, 0.75F};
        for (int level = 1; level <= 3; level++) {
            ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
            legs.addEnchantment(e, level);
            player.setItemStackToSlot(EntityEquipmentSlot.LEGS, legs);
            c.ok(Math.abs(EnchantmentSwiftSneak.sneakSpeed(e, player) - want[level - 1]) < 1e-6,
                    "sneaking speed with Swift Sneak " + level + " is " + want[level - 1]);
        }
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.addEnchantment(e, 3);
        player.setItemStackToSlot(EntityEquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemStackToSlot(EntityEquipmentSlot.FEET, boots);
        c.ok(Math.abs(EnchantmentSwiftSneak.sneakSpeed(e, player) - 0.3F) < 1e-6, "only leggings count");
        player.setItemStackToSlot(EntityEquipmentSlot.FEET, ItemStack.EMPTY);
    }

    private static void discovery(Check c) {
        Enchantment e = ItemsContent.swiftSneak();
        // Random enchanted books in loot: a mineshaft's list-less enchant_randomly.
        LootTable mineshaft = c.world.getLootTableManager().getLootTableFromLocation(LootTableList.CHESTS_ABANDONED_MINESHAFT);
        Random random = new Random(5);
        int books = 0;
        boolean sneak = false;
        for (int i = 0; i < 3000; i++) {
            for (ItemStack stack : mineshaft.generateLootForPools(random, new LootContext.Builder(c.world).build())) {
                if (stack.getItem() == Items.ENCHANTED_BOOK) {
                    books++;
                    sneak |= names(stack, e);
                }
            }
        }
        c.ok(books > 100, "mineshaft chests still give random enchanted books (" + books + ")");
        c.ok(!sneak, "never Swift Sneak");

        // Trades: an offer naming it is re-rolled in place.
        EntityVillager villager = new EntityVillager(c.world);
        MerchantRecipeList list = new MerchantRecipeList();
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantedBook.addEnchantment(book, new EnchantmentData(e, 2));
        list.add(new MerchantRecipe(new ItemStack(Items.EMERALD, 30), new ItemStack(Items.BOOK), book));
        list.add(new MerchantRecipe(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.BREAD)));
        int changed = Discovery.rerollTrades(villager, list, new Random(1));
        c.eq(1, changed, "one offer re-rolled");
        ItemStack sold = list.get(0).getItemToSell();
        c.ok(sold.getItem() == Items.ENCHANTED_BOOK && !names(sold, e) && ItemEnchantedBook.getEnchantments(sold).tagCount() == 1,
                "the book now names another enchantment");
        int price = list.get(0).getItemToBuy().getCount();
        c.ok(price >= 2 && price <= 64, "with 1.12.2's book price (" + price + ")");
        c.eq(Items.BREAD, list.get(1).getItemToSell().getItem(), "other offers untouched");
    }

    private static boolean names(ItemStack book, Enchantment e) {
        NBTTagList tags = ItemEnchantedBook.getEnchantments(book);
        for (int i = 0; i < tags.tagCount(); i++) {
            if (Enchantment.getEnchantmentByID(tags.getCompoundTagAt(i).getShort("id")) == e) {
                return true;
            }
        }
        return false;
    }

    // --- goat horns ---------------------------------------------------------------------------

    private static void goatHorn(Check c) {
        ItemGoatHorn horn = ItemsContent.goatHorn();
        c.eq(Instrument.PONDER, Instrument.of(new ItemStack(horn)), "a horn without an instrument plays Ponder");
        ItemStack call = horn.create(Instrument.CALL);
        c.eq("minecraft:call_goat_horn", call.getTagCompound().getString("instrument"), "the instrument tag is 1.19's");
        c.eq(Instrument.CALL, Instrument.of(call), "and reads back");
        NBTTagCompound bare = new NBTTagCompound();
        bare.setString("instrument", "dream_goat_horn");
        ItemStack dream = new ItemStack(horn);
        dream.setTagCompound(bare);
        c.eq(Instrument.DREAM, Instrument.of(dream), "a bare name reads too (the hook's format)");
        c.eq(140, horn.getMaxItemUseDuration(call), "used for 140 ticks");

        // The goat's horn: seeded by its UUID, regular or screaming.
        UUID id = UUID.fromString("0b6b1a3c-5d0e-4b8f-9c1a-1f2e3d4c5b6a");
        Instrument expected = Instrument.REGULAR[new Random(id.hashCode()).nextInt(4)];
        c.eq(expected, Instrument.of(GoatHorns.create(false, id)), "a goat's horn is 1.19's, seeded by its UUID");
        c.eq(Instrument.of(GoatHorns.create(false, id)), Instrument.of(GoatHorns.create(false, id)), "the same every time");
        for (int i = 0; i < 20; i++) {
            Instrument s = Instrument.of(GoatHorns.create(true, UUID.randomUUID()));
            c.ok(s.ordinal() >= 4, "a screaming goat's horn is a screaming instrument (" + s + ")");
        }
        c.ok(GoatHorns.snapsHorn(Blocks.STONE.getDefaultState()), "stone snaps horns");
        c.ok(!GoatHorns.snapsHorn(Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.GRANITE)), "granite does not");
        c.ok(GoatHorns.snapsHorn(Blocks.LOG.getDefaultState()), "logs do");
        c.ok(!GoatHorns.snapsHorn(Blocks.LOG.getDefaultState().withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.NONE)), "six-sided wood does not");
        c.ok(GoatHorns.snapsHorn(Blocks.PACKED_ICE.getDefaultState()) && GoatHorns.snapsHorn(Blocks.EMERALD_ORE.getDefaultState()),
                "packed ice and emerald ore do");
        c.ok(!GoatHorns.snapsHorn(Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.OAK)), "planks do not");
        c.ok(GoatHorns.snapsHorn(Owners.ourBlock("mangrove_log").getDefaultState()), "mangrove logs do");

        // Playing one: in use, and every goat horn on cooldown for 140 ticks.
        BlockPos area = c.area();
        EntityPlayerMP player = Players.real(c.world, "horn");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(area.getX() + 2, area.getY(), area.getZ() + 2);
        ItemStack seek = horn.create(Instrument.SEEK);
        player.setHeldItem(EnumHand.MAIN_HAND, seek);
        player.interactionManager.processRightClick(player, c.world, seek, EnumHand.MAIN_HAND);
        c.ok(player.isHandActive(), "a played horn is in use");
        c.ok(player.getCooldownTracker().hasCooldown(horn), "and on cooldown");
        float progress = player.getCooldownTracker().getCooldown(horn, 0.0F);
        c.ok(progress > 0.99F, "a full cooldown (" + progress + ")");
        player.stopActiveHand();
        ItemStack other = horn.create(Instrument.DREAM);
        player.setHeldItem(EnumHand.MAIN_HAND, other);
        player.interactionManager.processRightClick(player, c.world, other, EnumHand.MAIN_HAND);
        c.ok(!player.isHandActive(), "another horn waits for the same cooldown");
        for (int i = 0; i < 140; i++) {
            player.getCooldownTracker().tick();
        }
        c.ok(!player.getCooldownTracker().hasCooldown(horn), "the cooldown is over after 140 ticks");
    }

    // --- loot ---------------------------------------------------------------------------------

    private static void loot(Check c) throws Exception {
        c.ok(LootFunctionManager.getSerializerForName(new ResourceLocation("wildupdatebackport:set_instrument")) != null,
                "set_instrument is a loot function for JSON tables");

        // Outposts: 1.19's pool, on an empty table (Village & Pillage is not in the dev run).
        LootTable outpost = new LootTable(new LootPool[0]);
        Method add = ItemsLoot.class.getDeclaredMethod("addOutpostHorns", LootTable.class);
        add.setAccessible(true);
        add.invoke(null, outpost);
        Random random = new Random(7);
        int horns = 0;
        boolean regular = true;
        for (int i = 0; i < 2000; i++) {
            for (ItemStack stack : outpost.generateLootForPools(random, new LootContext.Builder(c.world).build())) {
                if (stack.getItem() == ItemsContent.goatHorn()) {
                    horns++;
                    regular &= Instrument.of(stack).ordinal() < 4;
                }
            }
        }
        c.ok(horns > 900 && horns < 1100, "an outpost chest has a goat horn half the time (" + horns + "/2000)");
        c.ok(regular, "always a regular one");

        // The ancient-city fallback, in strongholds and dungeons.
        Enchantment e = ItemsContent.swiftSneak();
        for (ResourceLocation name : ItemsLoot.FALLBACK) {
            LootTable table = c.world.getLootTableManager().getLootTableFromLocation(name);
            c.ok(table.getPool("wildupdatebackport:ancient_city_fallback") != null, name + " has the ancient-city fallback");
        }
        LootTable dungeon = c.world.getLootTableManager().getLootTableFromLocation(LootTableList.CHESTS_SIMPLE_DUNGEON);
        LootTable only = new LootTable(new LootPool[] {dungeon.getPool("wildupdatebackport:ancient_city_fallback")});
        int shards = 0, fragments = 0, books = 0;
        int chests = 4000;
        boolean levels = true;
        for (int i = 0; i < chests; i++) {
            for (ItemStack stack : only.generateLootForPools(random, new LootContext.Builder(c.world).build())) {
                if (stack.getItem() == ItemsContent.echoShard()) {
                    shards += stack.getCount();
                } else if (stack.getItem() == ItemsContent.discFragment()) {
                    fragments += stack.getCount();
                } else if (stack.getItem() == Items.ENCHANTED_BOOK && names(stack, e)) {
                    books++;
                    int lvl = ItemEnchantedBook.getEnchantments(stack).getCompoundTagAt(0).getShort("lvl");
                    levels &= lvl >= 1 && lvl <= 3;
                }
            }
        }
        // The city chest: 7.5 rolls on average over a weight of 88; shards and fragments 4 each (2 per draw), books 3.
        double wantShards = 7.5D * 4 / 88 * 2, wantBooks = 7.5D * 3 / 88;
        double gotShards = shards / (double) chests, gotFragments = fragments / (double) chests, gotBooks = books / (double) chests;
        c.ok(Math.abs(gotShards - wantShards) < 0.06, "echo shards per chest " + gotShards + " (ancient city: " + wantShards + ")");
        c.ok(Math.abs(gotFragments - wantShards) < 0.06, "disc fragments per chest " + gotFragments);
        c.ok(Math.abs(gotBooks - wantBooks) < 0.03, "Swift Sneak books per chest " + gotBooks + " (ancient city: " + wantBooks + ")");
        c.ok(levels, "Swift Sneak books are level I-III");
        List<ItemStack> sample = only.generateLootForPools(new Random(3), new LootContext.Builder(c.world).build());
        c.ok(sample.size() <= 10, "at most 10 rolls");
    }
}
