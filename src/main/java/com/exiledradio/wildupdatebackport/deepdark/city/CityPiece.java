package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.JigsawElement;
import com.exiledradio.backportcore.structure.JigsawPiece;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.exiledradio.backportcore.structure.TemplateProcessor;
import com.exiledradio.backportcore.structure.TerrainAdaptation;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkPending;
import com.exiledradio.wildupdatebackport.sculk.gen.SculkPatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.Arrays;
import java.util.Random;

/**
 * A piece of an ancient city: Core's jigsaw piece, placed here so the city's processor lists run as 1.19's do
 * ({@link CityProcessors}: Core 1.1.0 reads {@code block_rot} without its {@code rottable_blocks} and has no
 * {@code protected_blocks}), and the {@code ancient_city/sculk} pool's feature element places 1.19's
 * {@code sculk_patch_ancient_city} (stream S's {@link SculkPatch#ANCIENT_CITY}).
 *
 * <p>As 1.19's {@code PoolElementStructurePiece} it tells the terrain how it meets the ground
 * ({@link TerrainAdaptation.AdaptingPiece}: its projection, ground level delta and jigsaw junctions), so a terrain
 * that adapts to structures carves the city's {@code beard_box} around it.
 */
public class CityPiece extends JigsawPiece implements TerrainAdaptation.AdaptingPiece {

    /** 1.19's {@code JigsawJunction}s: source x, source ground y, source z, three ints each, in the order added. */
    private int[] junctions = new int[0];

    /** For loading. */
    public CityPiece() {
    }

    public CityPiece(JigsawElement element, BlockPos position, int groundLevelDelta, Rotation rotation, StructureBoundingBox box) {
        super(element, position, groundLevelDelta, rotation, box);
    }

    void setPivot(BlockPos pivot) {
        this.pivot = pivot;
    }

    /** {@code PoolElementStructurePiece.addJunction}. */
    void addJunction(int x, int groundY, int z) {
        int n = this.junctions.length;
        this.junctions = Arrays.copyOf(this.junctions, n + 3);
        this.junctions[n] = x;
        this.junctions[n + 1] = groundY;
        this.junctions[n + 2] = z;
    }

    @Override
    public boolean rigid() {
        return this.element == null || this.element.rigid();
    }

    @Override
    public int[] junctions() {
        return this.junctions.clone();
    }

    @Override
    public void offset(int x, int y, int z) {
        super.offset(x, y, z);
        for (int n = 0; n < this.junctions.length; n += 3) {
            this.junctions[n] += x;
            this.junctions[n + 1] += y;
            this.junctions[n + 2] += z;
        }
    }

    @Override
    protected void writeStructureToNBT(NBTTagCompound tag) {
        super.writeStructureToNBT(tag);
        tag.setIntArray("junctions", this.junctions);
    }

    @Override
    protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager manager) {
        super.readStructureFromNBT(tag, manager);
        int[] j = tag.getIntArray("junctions");
        this.junctions = j.length % 3 == 0 ? j : new int[0];
    }

    /** @return true for a feature element (the sculk patches), which take no room */
    public boolean isFeature() {
        return this.element != null && "minecraft:feature_pool_element".equals(type(this.element));
    }

    @Override
    public boolean addComponentParts(World world, Random random, StructureBoundingBox chunk) {
        if (this.element != null) {
            place(this.element, world, random, chunk);
        }
        return true;
    }

    private void place(JigsawElement e, World world, Random random, StructureBoundingBox chunk) {
        String type = type(e);
        if ("minecraft:list_pool_element".equals(type)) {
            for (JigsawElement part : e.parts()) {
                place(part, world, random, chunk);
            }
        } else if ("minecraft:feature_pool_element".equals(type)) {
            // Placed with its populate chunk's deep dark decoration, once its spread cannot generate a chunk.
            if (chunk.isVecInside(this.position)) {
                DeepDarkPending.of(world).cityPatch(this.position);
            }
        } else if (e.template() != null) {
            ModernTemplate template = ModernTemplate.load(JigsawPools.templateId(e.template()));
            TemplatePlacement placement = TemplatePlacement.of(this.pieceRotation, Mirror.NONE).clip(chunk).finalizeEntities(true);
            for (TemplateProcessor p : CityProcessors.forList(processorName(e))) {
                placement.processor(p);
            }
            template.place(world, this.position, placement);
        }
    }

    static String type(JigsawElement e) {
        JsonObject json = e.json();
        JsonElement t = json == null ? null : json.get("element_type");
        return t == null ? "" : CityLayout.full(t.getAsString());
    }

    static String processorName(JigsawElement e) {
        JsonElement p = e.json() == null ? null : e.json().get("processors");
        return p != null && p.isJsonPrimitive() ? CityLayout.full(p.getAsString()) : "minecraft:empty";
    }
}
