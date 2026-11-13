package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.JigsawElement;
import com.exiledradio.backportcore.structure.JigsawPiece;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.exiledradio.backportcore.structure.TemplateProcessor;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkFeatures;
import com.exiledradio.wildupdatebackport.sculk.gen.SculkPatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

/**
 * A piece of an ancient city: Core's jigsaw piece, placed here so the city's processor lists run as 1.19's do
 * ({@link CityProcessors}: Core 1.1.0 reads {@code block_rot} without its {@code rottable_blocks} and has no
 * {@code protected_blocks}), and the {@code ancient_city/sculk} pool's feature element places 1.19's
 * {@code sculk_patch_ancient_city} (stream S's {@link SculkPatch#ANCIENT_CITY}).
 */
public class CityPiece extends JigsawPiece {

    /** For loading. */
    public CityPiece() {
    }

    public CityPiece(JigsawElement element, BlockPos position, int groundLevelDelta, Rotation rotation, StructureBoundingBox box) {
        super(element, position, groundLevelDelta, rotation, box);
    }

    void setPivot(BlockPos pivot) {
        this.pivot = pivot;
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
            // Left out where its spread would generate a new chunk (DeepDarkFeatures.generated).
            if (chunk.isVecInside(this.position) && DeepDarkFeatures.generated(world, this.position, DeepDarkFeatures.PATCH_REACH)) {
                SculkPatch.ANCIENT_CITY.place(world, random, this.position);
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
