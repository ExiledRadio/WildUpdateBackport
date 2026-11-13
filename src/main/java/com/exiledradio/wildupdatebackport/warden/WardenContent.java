package com.exiledradio.wildupdatebackport.warden;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The warden and Darkness: registration and what other streams call.
 *
 * <ul>
 *   <li>Entity {@code wildupdatebackport:warden} (id 15), tracked like 1.19's (up to the view distance, every 3 ticks),
 *       with its spawn egg (1.19's colors). Shriekers (stream S) summon it by this name and mark it with
 *       {@link #SUMMONED} in its Forge data before it spawns, so it emerges.</li>
 *   <li>Potion {@code wildupdatebackport:darkness} ({@link PotionDarkness}); shriekers give it by this name.</li>
 *   <li>Sounds {@code entity.warden.*} and the parrot's imitation. {@code entity.warden.listening_angry} is shared
 *       with the shrieker: whichever stream registers first owns the entry, the other uses it.</li>
 *   <li>Damage type {@code sonic_boom} (1.19's: through armor, shields and protection enchantments, magic).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class WardenContent {

    public static final ResourceLocation WARDEN = Content.id("warden");
    public static final ResourceLocation DARKNESS_ID = Content.id("darkness");
    /** Set to true in a warden's {@code getEntityData()} by the shrieker that summons it (1.19's TRIGGERED spawn). */
    public static final String SUMMONED = "wildupdatebackport:summoned_by_shrieker";
    private static final ResourceLocation LISTENING_ANGRY_ID = Content.id("entity.warden.listening_angry");
    private static final ResourceLocation CATALYST_ID = Content.id("sculk_catalyst");

    public static final SoundEvent AGITATED = sound("entity.warden.agitated");
    public static final SoundEvent AMBIENT = sound("entity.warden.ambient");
    public static final SoundEvent ANGRY = sound("entity.warden.angry");
    public static final SoundEvent ATTACK_IMPACT = sound("entity.warden.attack_impact");
    public static final SoundEvent DEATH = sound("entity.warden.death");
    public static final SoundEvent DIG = sound("entity.warden.dig");
    public static final SoundEvent EMERGE = sound("entity.warden.emerge");
    public static final SoundEvent HEARTBEAT = sound("entity.warden.heartbeat");
    public static final SoundEvent HURT = sound("entity.warden.hurt");
    public static final SoundEvent LISTENING = sound("entity.warden.listening");
    public static final SoundEvent ROAR = sound("entity.warden.roar");
    public static final SoundEvent SNIFF = sound("entity.warden.sniff");
    public static final SoundEvent SONIC_BOOM = sound("entity.warden.sonic_boom");
    public static final SoundEvent SONIC_CHARGE = sound("entity.warden.sonic_charge");
    public static final SoundEvent STEP = sound("entity.warden.step");
    public static final SoundEvent TENDRIL_CLICKS = sound("entity.warden.tendril_clicks");
    public static final SoundEvent PARROT_IMITATE = sound("entity.parrot.imitate.warden");

    /** 1.19's spawn egg colors. */
    private static final int EGG_PRIMARY = 1001033;
    private static final int EGG_SECONDARY = 3790560;

    public static Potion darkness;
    @Nullable
    private static SoundEvent listeningAngry;

    private WardenContent() {
    }

    public static boolean enabled() {
        return ModConfig.warden.enabled;
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    @SubscribeEvent
    public static void registerPotions(RegistryEvent.Register<Potion> event) {
        if (!enabled()) {
            return;
        }
        darkness = new PotionDarkness();
        event.getRegistry().register(darkness);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(AGITATED, AMBIENT, ANGRY, ATTACK_IMPACT, DEATH, DIG, EMERGE, HEARTBEAT, HURT, LISTENING,
                ROAR, SNIFF, SONIC_BOOM, SONIC_CHARGE, STEP, TENDRIL_CLICKS, PARROT_IMITATE);
    }

    /** After the sculk stream's sounds: {@code listening_angry} is registered here only if it is not there yet. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerSharedSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<SoundEvent> r = event.getRegistry();
        if (!r.containsKey(LISTENING_ANGRY_ID)) {
            SoundEvent s = new SoundEvent(LISTENING_ANGRY_ID);
            s.setRegistryName(LISTENING_ANGRY_ID);
            r.register(s);
        }
    }

    @Nullable
    static SoundEvent listeningAngry() {
        if (listeningAngry == null) {
            listeningAngry = ForgeRegistries.SOUND_EVENTS.getValue(LISTENING_ANGRY_ID);
        }
        return listeningAngry;
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityWarden.class).id(WARDEN, 15)
                .name(WildUpdateBackport.MOD_ID + ".warden").tracker(256, 3, true).egg(EGG_PRIMARY, EGG_SECONDARY).build());
    }

    /** From the mod's init: parrots imitate wardens (1.12.2's imitation table, keyed by entity id). */
    public static void init() {
        if (!enabled()) {
            return;
        }
        try {
            Map<Integer, SoundEvent> imitations = ObfuscationReflectionHelper.getPrivateValue(EntityParrot.class, null, "field_192017_bK");
            int id = EntityList.getID(EntityWarden.class);
            if (id >= 0) {
                imitations.put(id, PARROT_IMITATE);
            }
        } catch (RuntimeException e) {
            WildUpdateBackport.LOGGER.warn("Parrots will not imitate wardens: {}", e.toString());
        }
    }

    /** The sculk catalyst the warden drops: the sculk stream's, by name; null when that is off. */
    @Nullable
    static Item sculkCatalyst() {
        return ForgeRegistries.ITEMS.getValue(CATALYST_ID);
    }

    /** 1.19's {@code DamageSource.sonicBoom}: armor, shields and protection enchantments do not stop it. */
    public static DamageSource sonicBoom(Entity warden) {
        return new EntityDamageSource("sonic_boom", warden).setDamageBypassesArmor().setDamageAllowedInCreativeMode().setMagicDamage();
    }

    /**
     * 1.19's {@code Warden.applyDarknessAround}: Darkness for 260 ticks (hidden particles) to every survival or
     * adventure player within the radius who is not on the source's team, unless they have it for 200 ticks more.
     */
    public static void applyDarknessAround(WorldServer world, Vec3d at, @Nullable Entity source, int radius) {
        if (darkness == null) {
            return;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP) || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (source != null && source.isOnSameTeam(player)) {
                continue;
            }
            if (player.getPositionVector().squareDistanceTo(at) >= (double) radius * radius) {
                continue;
            }
            PotionEffect current = player.getActivePotionEffect(darkness);
            if (current == null || current.getAmplifier() < 0 || current.getDuration() < 200) {
                player.addPotionEffect(new PotionEffect(darkness, 260, 0, false, false));
            }
        }
    }
}
