package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.ThunderTridentEntity;

/**
 * 雷霆贯穿的渲染：直接把三叉戟的物品模型拉成一根"飞矛"。
 *
 * <p>矛身贴图在画布里是竖直的，所以让模型 +Y 对准飞行方向即可；
 * 再沿长轴慢速自旋，侧对镜头时不会塌成一条线。
 */
public class ThunderTridentRenderer extends EntityRenderer<ThunderTridentEntity> {

    /** 自旋速度（度/tick） */
    private static final float SPIN_PER_TICK = 22.0F;

    public ThunderTridentRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ThunderTridentEntity entity) {
        return new Identifier("wangsword", "textures/item/ws_trident.png");
    }

    @Override
    public void render(ThunderTridentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) {
            return;
        }

        Vec3d velocity = entity.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6D) {
            velocity = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        velocity = velocity.normalize();

        float yawDeg = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitchDeg = (float) -Math.toDegrees(Math.asin(velocity.y));
        float spin = ((entity.age + tickDelta) * SPIN_PER_TICK) % 360.0F;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawDeg));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + pitchDeg));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.scale(2.2F, 2.2F, 2.2F);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.FIXED,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId());
        matrices.pop();
    }
}
