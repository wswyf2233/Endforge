package org.examplee.wangsword.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.FlamePatchEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 火焰法杖的地面特效，一张贴地圆盘。
 *
 * <p>同一个实体两种外观，按 {@link FlamePatchEntity#getStyle()} 区分：
 * <ul>
 *   <li><b>0 焦土</b>：{@code flame_ground.png}，烈焰弹落点那片火地，静止不动；</li>
 *   <li><b>1 法阵</b>：{@code flame_field.png}，X 熔火领域的地面符文，缓慢旋转并呼吸。</li>
 * </ul>
 *
 * <p>圆盘大小直接取实体的半径，所以火地和领域共用一套代码，改数值就跟着变。
 */
public class FlamePatchRenderer extends EntityRenderer<FlamePatchEntity> {

    private static final Identifier GROUND_TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_ground.png");
    private static final Identifier FIELD_TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_field.png");

    private static final RenderLayer GROUND = RenderLayer.getEntityTranslucentEmissive(GROUND_TEXTURE);
    private static final RenderLayer FIELD = RenderLayer.getEntityTranslucentEmissive(FIELD_TEXTURE);
    private static final RenderLayer FIELD_GLOW = RenderLayer.getEyes(FIELD_TEXTURE);

    /** 圆盘离地高度：贴太高会和方块顶面之间出现缝，太低会被方块顶面吃掉 */
    private static final float LIFT_GROUND = 0.04F;
    /** 法阵压得比焦土再高一点，两块叠在一起时不会 z-fighting */
    private static final float LIFT_FIELD = 0.07F;

    public FlamePatchRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FlamePatchEntity entity) {
        return entity.getStyle() == 1 ? FIELD_TEXTURE : GROUND_TEXTURE;
    }

    @Override
    public void render(FlamePatchEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int maxLife = Math.max(1, entity.getLife());
        float life = entity.age + tickDelta;
        if (life >= maxLife) {
            return;
        }

        // 起手 6 tick 淡入，最后 16 tick 淡出
        float fadeIn = MathHelper.clamp(life / 6.0F, 0.0F, 1.0F);
        float fadeOut = MathHelper.clamp((maxLife - life) / 16.0F, 0.0F, 1.0F);
        float envelope = fadeIn * fadeOut;

        boolean field = entity.getStyle() == 1;
        float radius = entity.getRadius();
        // 焦土贴图边缘是碎的，稍微铺开一点才盖满伤害范围
        if (!field) {
            radius *= 1.12F;
        }

        // 法阵缓慢旋转，火地不动
        double rot = field ? life * 0.010D : 0.0D;
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);

        float alpha = field
                ? envelope * (0.86F + 0.14F * (float) Math.sin(life * 0.14F))
                : envelope * 0.92F;

        matrices.push();
        matrices.translate(0.0D, field ? LIFT_FIELD : LIFT_GROUND, 0.0D);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();

        if (field) {
            disc(vertexConsumers.getBuffer(FIELD_GLOW), m, n, radius * 0.98F, cos, sin, 0.30F * envelope);
            disc(vertexConsumers.getBuffer(FIELD), m, n, radius, cos, sin, alpha);
        } else {
            disc(vertexConsumers.getBuffer(GROUND), m, n, radius, cos, sin, alpha);
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 平铺的四边形（xz 平面），按 rot 绕 Y 轴旋转 */
    private void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float r,
                      float cos, float sin, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255);
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        // 四角 (-r,-r) (r,-r) (r,r) (-r,r)
        float x0 = -r * cos + r * sin;
        float z0 = -r * sin - r * cos;
        float x1 = r * cos + r * sin;
        float z1 = r * sin - r * cos;
        float x2 = r * cos - r * sin;
        float z2 = r * sin + r * cos;
        float x3 = -r * cos - r * sin;
        float z3 = -r * sin + r * cos;

        vc.vertex(m, x0, 0.0F, z0).color(255, 255, 255, a).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 1.0F, 0.0F).next();
        vc.vertex(m, x1, 0.0F, z1).color(255, 255, 255, a).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 1.0F, 0.0F).next();
        vc.vertex(m, x2, 0.0F, z2).color(255, 255, 255, a).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 1.0F, 0.0F).next();
        vc.vertex(m, x3, 0.0F, z3).color(255, 255, 255, a).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 1.0F, 0.0F).next();
    }
}
