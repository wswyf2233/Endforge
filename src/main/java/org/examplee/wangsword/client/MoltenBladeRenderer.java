package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.examplee.wangsword.entity.MoltenBladeEntity;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 插在地上的熔核封岩巨剑：直接把物品模型倒过来画。
 *
 * <p>绕 Z 轴 180° 让剑尖朝下，抬高 1 格让剑柄露出地面，缩放 1.35 倍显得更沉。
 * 这套旋转是照着石中剑基座那套调出来的，那边验证过方向不会歪。
 */
public class MoltenBladeRenderer extends EntityRenderer<MoltenBladeEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/item/ws_molten_sealed_greatsword.png");

    private static final float SCALE = 1.3F;
    private static final double CENTER_Y = 0.95D;

    public MoltenBladeRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(MoltenBladeEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.translate(0.0D, CENTER_Y, 0.0D);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.scale(SCALE, SCALE, SCALE);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                new ItemStack(ItemsRegistry.WS_MOLTEN_SEALED_GREATSWORD),
                ModelTransformationMode.NONE,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId());

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(MoltenBladeEntity entity) {
        return TEXTURE;
    }
}
