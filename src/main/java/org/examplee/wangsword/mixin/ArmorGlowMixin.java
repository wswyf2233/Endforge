package org.examplee.wangsword.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.registry.TaiguArmorRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末地合金盔甲的自发光层。
 *
 * <p>挂在原版盔甲渲染末尾，用同一个模型与 MatrixStack 再画一遍 glowmask，
 * 自建渲染层：自发光着色、关光照、加色混合、不做深度测试（否则贴合的发光层会被盔甲挡掉）。
 */
@Mixin(ArmorFeatureRenderer.class)
public class ArmorGlowMixin {

    private static final Identifier GLOW_1 = new Identifier(
            "minecraft", "textures/models/armor/ws_taiguarmor_layer_1_glowmask.png");
    private static final Identifier GLOW_2 = new Identifier(
            "minecraft", "textures/models/armor/ws_taiguarmor_layer_2_glowmask.png");

    private static final RenderLayer LAYER_1 = glowLayer("ws_taigu_glow_1", GLOW_1);
    private static final RenderLayer LAYER_2 = glowLayer("ws_taigu_glow_2", GLOW_2);

    private static RenderLayer glowLayer(String name, Identifier texture) {
        return RenderLayer.of(name,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS, 1536, false, true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, false, false))
                        .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
                        .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                        .cull(RenderPhase.ENABLE_CULLING)
                        .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                        .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                        .build(false));
    }

    @Inject(
            method = "renderArmorParts(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I"
                    + "Lnet/minecraft/item/ArmorItem;"
                    + "Lnet/minecraft/client/render/entity/model/BipedEntityModel;"
                    + "ZFFFLjava/lang/String;)V",
            at = @At("TAIL"))
    private void wangsword$renderGlow(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      int light, ArmorItem item, BipedEntityModel<?> model,
                                      boolean secondLayer, float red, float green, float blue,
                                      String suffix, CallbackInfo ci) {
        if (!isTaigu(item)) {
            return;
        }
        boolean thigh = item.getSlotType() == EquipmentSlot.LEGS;

        // 放大 5%：1% 会随视角抖动，5% 才能稳定压在盔甲外表面
        matrices.push();
        matrices.scale(1.05F, 1.05F, 1.05F);

        VertexConsumer consumer = vertexConsumers.getBuffer(thigh ? LAYER_2 : LAYER_1);
        model.render(matrices, consumer,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                1.0F, 1.0F, 1.0F, 1.0F);

        matrices.pop();
    }

    private static boolean isTaigu(Item item) {
        return item == TaiguArmorRegistry.TAIGU_HELMET
                || item == TaiguArmorRegistry.TAIGU_CHESTPLATE
                || item == TaiguArmorRegistry.TAIGU_LEGGINGS
                || item == TaiguArmorRegistry.TAIGU_BOOTS;
    }
}