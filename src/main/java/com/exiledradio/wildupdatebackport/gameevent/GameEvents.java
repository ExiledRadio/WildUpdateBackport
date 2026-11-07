package com.exiledradio.wildupdatebackport.gameevent;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The game event dispatcher: 1.19.2's {@code ServerLevel.gameEvent} and its per-section listener lists.
 *
 * <ul>
 *   <li>Listeners are kept per world, by chunk; an entity listener calls {@link #move} as it moves.</li>
 *   <li>An event reaches every listener within the listener's own radius, looked for in the chunks within
 *       the event's radius.</li>
 *   <li>{@link GameEventListener#immediate} listeners hear it at once. All others' messages wait until the end
 *       of the world's tick, then are handed out closest first, which is how a vibration listener (busy after
 *       its first) ends up with the nearest vibration of the tick. Events emitted while handling wait for the
 *       next tick's round, as in 1.19.</li>
 * </ul>
 * Server only: emitting on a client does nothing. With {@code vibrations.enabled} off nothing is heard.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class GameEvents {

    private static final Map<World, Dispatcher> WORLDS = new WeakHashMap<World, Dispatcher>();

    private GameEvents() {
    }

    private static Dispatcher of(World world) {
        Dispatcher d = WORLDS.get(world);
        if (d == null) {
            d = new Dispatcher();
            WORLDS.put(world, d);
        }
        return d;
    }

    public static void add(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).add(world, listener);
        }
    }

    public static void remove(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).remove(listener);
        }
    }

    /** For entity listeners: call as the entity moves (cheap when it stays in its chunk). */
    public static void move(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).move(world, listener);
        }
    }

    public static void emit(World world, GameEvent event, BlockPos pos, GameEventContext context) {
        emit(world, event, new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D), context);
    }

    public static void emit(World world, GameEvent event, Vec3d pos, GameEventContext context) {
        if (world.isRemote || !(world instanceof WorldServer) || !ModConfig.vibrations.enabled) {
            return;
        }
        of(world).emit((WorldServer) world, event, pos, context);
    }

    /** @return how many listeners a world has, for the tests */
    public static int listenerCount(World world) {
        Dispatcher d = WORLDS.get(world);
        return d == null ? 0 : d.where.size();
    }

    /** Hands out the tick's queued messages, closest first. Public for the tests, which run it directly. */
    public static void flush(WorldServer world) {
        Dispatcher d = WORLDS.get(world);
        if (d != null) {
            d.flush(world);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer) {
            flush((WorldServer) event.world);
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        WORLDS.remove(event.getWorld());
    }

    /** One queued delivery: 1.19's {@code GameEvent.Message}, ordered by squared distance. */
    private static final class Message implements Comparable<Message> {
        final GameEvent event;
        final Vec3d origin;
        final GameEventContext context;
        final GameEventListener listener;
        final double distanceSq;

        Message(GameEvent event, Vec3d origin, GameEventContext context, GameEventListener listener, double distanceSq) {
            this.event = event;
            this.origin = origin;
            this.context = context;
            this.listener = listener;
            this.distanceSq = distanceSq;
        }

        @Override
        public int compareTo(Message o) {
            return Double.compare(this.distanceSq, o.distanceSq);
        }
    }

    private static final class Dispatcher {
        final Map<Long, List<GameEventListener>> byChunk = new HashMap<Long, List<GameEventListener>>();
        final Map<GameEventListener, Long> where = new IdentityHashMap<GameEventListener, Long>();
        List<Message> queue = new ArrayList<Message>();

        private static long key(Vec3d p) {
            return ChunkPos.asLong(((int) Math.floor(p.x)) >> 4, ((int) Math.floor(p.z)) >> 4);
        }

        void add(World world, GameEventListener l) {
            Vec3d p = l.position().position(world);
            if (p == null || this.where.containsKey(l)) {
                return;
            }
            long k = key(p);
            this.where.put(l, k);
            List<GameEventListener> list = this.byChunk.get(k);
            if (list == null) {
                list = new ArrayList<GameEventListener>(2);
                this.byChunk.put(k, list);
            }
            list.add(l);
        }

        void remove(GameEventListener l) {
            Long k = this.where.remove(l);
            if (k != null) {
                List<GameEventListener> list = this.byChunk.get(k);
                if (list != null) {
                    list.remove(l);
                    if (list.isEmpty()) {
                        this.byChunk.remove(k);
                    }
                }
            }
        }

        void move(World world, GameEventListener l) {
            Vec3d p = l.position().position(world);
            Long k = this.where.get(l);
            if (p == null) {
                remove(l);
            } else if (k == null || k != key(p)) {
                remove(l);
                add(world, l);
            }
        }

        void emit(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            if (this.byChunk.isEmpty()) {
                return;
            }
            int r = event.radius();
            int x0 = ((int) Math.floor(origin.x) - r) >> 4;
            int x1 = ((int) Math.floor(origin.x) + r) >> 4;
            int z0 = ((int) Math.floor(origin.z) - r) >> 4;
            int z1 = ((int) Math.floor(origin.z) + r) >> 4;
            List<Message> now = null;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    List<GameEventListener> list = this.byChunk.get(ChunkPos.asLong(cx, cz));
                    if (list == null) {
                        continue;
                    }
                    for (GameEventListener l : new ArrayList<GameEventListener>(list)) {
                        Vec3d p = l.position().position(world);
                        if (p == null) {
                            continue;
                        }
                        double d = p.squareDistanceTo(origin);
                        if (d > (double) l.radius() * l.radius()) {
                            continue;
                        }
                        Message m = new Message(event, origin, context, l, d);
                        if (l.immediate()) {
                            if (now == null) {
                                now = new ArrayList<Message>();
                            }
                            now.add(m);
                        } else {
                            this.queue.add(m);
                        }
                    }
                }
            }
            if (now != null) {
                deliver(world, now);
            }
        }

        void flush(WorldServer world) {
            if (this.queue.isEmpty()) {
                return;
            }
            List<Message> round = this.queue;
            this.queue = new ArrayList<Message>();
            deliver(world, round);
        }

        private static void deliver(WorldServer world, List<Message> messages) {
            Collections.sort(messages);
            for (Message m : messages) {
                try {
                    m.listener.handle(world, m.event, m.origin, m.context);
                } catch (RuntimeException e) {
                    WildUpdateBackport.LOGGER.error("A game event listener failed on {}", m.event, e);
                }
            }
        }
    }
}
