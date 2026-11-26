package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.ArcSlashEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ArcSlashEntityRenderer extends EntityRenderer<ArcSlashEntity> {

    private static final Identifier TEXTURE = new Identifier("wangsword", "textures/entity/test_tex.png");
    /** 誓约胜利之剑的金色剑气 */
    public static final Identifier GOLD_TEXTURE = new Identifier("wangsword", "textures/entity/slash_beam_gold.png");
    /** 风王结界的风刃 */
    public static final Identifier WIND_TEXTURE = new Identifier("wangsword", "textures/entity/slash_beam_wind.png");
    /** 右键剑气的黑色光带贴图 */
    private static final Identifier DARK_TEXTURE = new Identifier("wangsword", "textures/entity/slash_beam_dark.png");
    /** 左键剑气的月牙网格贴图：外缘亮、向内渐隐，两端收尖 */
    private static final Identifier CRESCENT_TEXTURE = new Identifier("wangsword", "textures/entity/slash_crescent.png");
    /** 火焰剑气：直接用火柱那张火焰贴图 */
    private static final Identifier FLAME_TEXTURE = new Identifier("wangsword", "textures/entity/flame_pillar.png");

    // 【平面转向】把月牙这张"纸"绕竖直轴转多少度：
    //   0  = 纸面正对飞行方向（从后方能看到完整月牙）
    //   90 = 纸面侧对飞行方向（正后方只见一条竖线，走到侧面才看见完整月牙）

    // 【月牙前后倾倒】对准飞行方向后：
    //   0   = 竖直立起，正面朝向玩家（能看完整月牙），开口朝下
    //   90  = 平躺向前（开口朝向玩家，但从正后方看很薄）
    //   180 = 竖直但上下颠倒，开口朝上

    // 【平面内旋转】月牙在自身平面里转多少度（当前平面是"侧对飞行方向"的竖直面）：
    //   0   = 弧顶朝上、开口朝下
    //   90  = 弧顶朝前、开口朝后（朝玩家）
    //   -90 = 弧顶朝后、开口朝前
    //   180 = 弧顶朝下、开口朝上

    // 【厚度】沿平面法线叠多少层贴图（1 = 原来的零厚度纸片）
    // 每层之间的间距（局部单位，最终会再乘下面 render 里的 scale，单位：格）

    // 【左键月牙的姿态】0 = 正面朝向玩家；90 = 平躺（从上方看是月牙，正面看是一条弧线）

    // 【左键月牙的弧度】角度范围越大越"弯"（180 = 半圆，205~-25 = 跨 230°，两端会往下卷）
    private static final float CRESCENT_ARC_START = 205.0F;
    private static final float CRESCENT_ARC_END = -25.0F;

    private final Identifier forcedTexture;

    public ArcSlashEntityRenderer(EntityRendererFactory.Context context) {
        this(context, null);
    }

    public ArcSlashEntityRenderer(EntityRendererFactory.Context context, Identifier forcedTexture) {
        super(context);
        this.forcedTexture = forcedTexture;
    }

    /**
     * 跳过视锥裁剪：剑气本体只有 0.5 格碰撞盒，但月牙视觉能撑到七八格，
     * 按碰撞盒判断的话，剑气一偏出画面就整块被裁掉。
     */
    @Override
    public boolean shouldRender(ArcSlashEntity entity, net.minecraft.client.render.Frustum frustum,
                                double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(ArcSlashEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // 左键剑气：月牙网格（整片跟着实体走），前端撒白色星屑
        boolean crescent = entity.isParticleMode();

        matrices.push();

        // 【朝向修正】用实体出生时记录的 forward 向量决定朝向：朝哪飞，剑气就朝哪
        // （不能用传进来的 yaw —— 那是实体身体朝向，这个实体从来没有 setYaw 过，永远是 0）
        Vec3d forward = entity.getForwardVector();
        if (forward.lengthSquared() < 1.0E-6) {
            forward = new Vec3d(0.0, 0.0, 1.0);
        } else {
            forward = forward.normalize();
        }

        // 由 forward 反推 Minecraft 的 yaw / pitch（约定：yaw=0 朝 +Z，pitch 正为低头）
        float aimYaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float aimPitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -forward.y))));

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-aimYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(aimPitch));

        float life = entity.age + tickDelta;
        float lifeT = Math.min(1.0F, life / (float) entity.getMaxLife());

        if (crescent) {
            // ===== 左键 / 大招剑气：弧形板（俯视月牙、正面有高度） =====
            float alpha = smoothstep(1.0F, 0.72F, lifeT);
            // 绕飞行轴自转：X 形大招靠这个把两片错开
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getRollDegrees()));
            float s = 1.3F * entity.getScaleMult();
            matrices.scale(s, s, s);
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(CRESCENT_TEXTURE));

            for (int g = 2; g >= 1; g--) {
                matrices.push();
                matrices.translate(0.0, 0.0, -0.6 * g);
                drawWall(vc, matrices, alpha * (0.35F / g), light);
                matrices.pop();
            }

            drawWall(vc, matrices, alpha, light);

            matrices.push();
            matrices.scale(1.08F, 1.08F, 1.08F);
            drawWall(vc, matrices, alpha * 0.40F, light);
            matrices.pop();
        } else {
            // ===== 右键：原来的姿态、尺寸和层级 + 可选的滚转/放大 =====
            float alpha = Math.max(0.0F, 1.0F - lifeT);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getRollDegrees()));
            float s = 2.2F * entity.getScaleMult();
            Identifier tex = entity.isFlame() ? FLAME_TEXTURE
                    : this.forcedTexture != null ? this.forcedTexture
                    : (entity.isGolden() ? GOLD_TEXTURE : TEXTURE);
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(tex));

            // 拖影：沿飞行方向（此时局部 Z 还是飞行方向）往后错开
            for (int g = 2; g >= 1; g--) {
                matrices.push();
                matrices.translate(0.0, 0.0, -0.8 * g);
                drawFlatCrescent(vc, matrices, alpha * (0.35F / g), light, s);
                matrices.pop();
            }

            // 主体
            drawFlatCrescent(vc, matrices, alpha, light, s);
        }

        matrices.pop();
    }

    /** 右键剑气：竖着劈出去——弧面立在"飞行方向 × 竖直"的平面里，从自己视角看是一条竖弧 */
    private void drawFlatCrescent(VertexConsumer vc, MatrixStack matrices, float alpha, int light, float scale) {
        matrices.push();
        // 只绕飞行轴滚 90°：弧线从左右走向变成上下走向，从身后看就是竖着的一道
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
        matrices.scale(scale, scale, scale);
        drawWall(vc, matrices, alpha, light);
        matrices.pop();
    }

    /** 旧的平面月牙（右键剑气用）：半圆环，两端收尖 */
    private void drawFlatArc(VertexConsumer vc, MatrixStack matrices, float alpha, int light,
                             float innerRadius, float outerRadius) {
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();
        int segments = 20;

        for (int i = 0; i < segments; i++) {
            double angle1 = Math.toRadians(180 - (180.0 / segments) * i);
            double angle2 = Math.toRadians(180 - (180.0 / segments) * (i + 1));

            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;
            float inR1 = outerRadius - (outerRadius - innerRadius) * taper(t1);
            float inR2 = outerRadius - (outerRadius - innerRadius) * taper(t2);

            float inX1 = (float) (Math.cos(angle1) * inR1);
            float inY1 = (float) (Math.sin(angle1) * inR1);
            float inX2 = (float) (Math.cos(angle2) * inR2);
            float inY2 = (float) (Math.sin(angle2) * inR2);

            float outX1 = (float) (Math.cos(angle1) * outerRadius);
            float outY1 = (float) (Math.sin(angle1) * outerRadius);
            float outX2 = (float) (Math.cos(angle2) * outerRadius);
            float outY2 = (float) (Math.sin(angle2) * outerRadius);

            float u1 = (float) i / segments;
            float u2 = (float) (i + 1) / segments;

            drawQuad(vc, posMat, normMat, inX1, inY1, outX1, outY1, inX2, inY2, outX2, outY2, u1, u2, alpha, light, 1.0F);
            drawQuad(vc, posMat, normMat, inX1, inY1, outX1, outY1, inX2, inY2, outX2, outY2, u1, u2, alpha, light, -1.0F);
        }
    }

    private void drawQuad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                          float inX1, float inY1, float outX1, float outY1,
                          float inX2, float inY2, float outX2, float outY2,
                          float u1, float u2, float alpha, int light, float normalZ) {
        int a = (int) (alpha * 255);

        vc.vertex(posMat, inX1, inY1, 0).color(255, 255, 255, a).texture(u1, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, 0, 0, normalZ).next();
        vc.vertex(posMat, outX1, outY1, 0).color(255, 255, 255, a).texture(u1, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, 0, 0, normalZ).next();
        vc.vertex(posMat, outX2, outY2, 0).color(255, 255, 255, a).texture(u2, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, 0, 0, normalZ).next();
        vc.vertex(posMat, inX2, inY2, 0).color(255, 255, 255, a).texture(u2, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, 0, 0, normalZ).next();
    }

    /**
     * 画一面"弧形墙"：在水平面上沿一段圆弧立起来。
     *   - 从正上方看：一段月牙弧线
     *   - 从正面看：有高度的墙体，也就是"厚度"
     * 高度在弧的两端收到 0，所以两端是尖的。
     */
    private void drawWall(VertexConsumer vc, MatrixStack matrices, float alpha, int light) {
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        int segments = 24;
        float radius = 3.7F;      // 弧半径（加长，俯视更大）
        float halfWidth = 0.30F;  // 径向厚度的一半（从上面看的带宽）
        float height = 0.9F;      // 墙高（从正面看的厚度，压低）

        int a = (int) (alpha * 255);

        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            double ang1 = Math.toRadians(CRESCENT_ARC_START + (CRESCENT_ARC_END - CRESCENT_ARC_START) * t1);
            double ang2 = Math.toRadians(CRESCENT_ARC_START + (CRESCENT_ARC_END - CRESCENT_ARC_START) * t2);

            float h1 = height * 0.5F * taper(t1);
            float h2 = height * 0.5F * taper(t2);

            float c1 = (float) Math.cos(ang1), s1 = (float) Math.sin(ang1);
            float c2 = (float) Math.cos(ang2), s2 = (float) Math.sin(ang2);

            float ro = radius + halfWidth; // 外半径
            float ri = radius - halfWidth; // 内半径

            // 外表面
            quad(vc, posMat, normMat, a, light,
                    c1 * ro, h1, s1 * ro,
                    c1 * ro, -h1, s1 * ro,
                    c2 * ro, -h2, s2 * ro,
                    c2 * ro, h2, s2 * ro,
                    t1, t2, c1, 0.0F, s1);

            // 内表面
            quad(vc, posMat, normMat, a, light,
                    c2 * ri, h2, s2 * ri,
                    c2 * ri, -h2, s2 * ri,
                    c1 * ri, -h1, s1 * ri,
                    c1 * ri, h1, s1 * ri,
                    t2, t1, -c1, 0.0F, -s1);

            // 顶面（从上面能看到的那条"月牙带"）
            quad(vc, posMat, normMat, a, light,
                    c1 * ri, h1, s1 * ri,
                    c1 * ro, h1, s1 * ro,
                    c2 * ro, h2, s2 * ro,
                    c2 * ri, h2, s2 * ri,
                    t1, t2, 0.0F, 1.0F, 0.0F);
        }
    }

    private void quad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int a, int light,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float u1, float u2, float nx, float ny, float nz) {
        vc.vertex(posMat, x1, y1, z1).color(255, 255, 255, a).texture(u1, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, nx, ny, nz).next();
        vc.vertex(posMat, x2, y2, z2).color(255, 255, 255, a).texture(u1, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, nx, ny, nz).next();
        vc.vertex(posMat, x3, y3, z3).color(255, 255, 255, a).texture(u2, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, nx, ny, nz).next();
        vc.vertex(posMat, x4, y4, z4).color(255, 255, 255, a).texture(u2, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normMat, nx, ny, nz).next();
    }

    /** 平滑插值：v 从 e0 到 e1 时，返回值从 0 平滑过渡到 1 */
    private static float smoothstep(float e0, float e1, float v) {
        float k = Math.max(0.0F, Math.min(1.0F, (v - e0) / (e1 - e0)));
        return k * k * (3.0F - 2.0F * k);
    }

    /** 弧的收尖系数：两端为 0（内圈与外圈重合），中段为 1（正常宽度） */
    private static float taper(float t) {
        float a = Math.min(1.0F, t / 0.14F);
        float b = Math.min(1.0F, (1.0F - t) / 0.14F);
        float k = Math.min(a, b);
        return k * k * (3.0F - 2.0F * k);
    }


    @Override
    public Identifier getTexture(ArcSlashEntity entity) {
        return TEXTURE;
    }
}
