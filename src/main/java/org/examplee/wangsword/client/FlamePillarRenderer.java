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
import org.examplee.wangsword.entity.FlamePillarEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 通天火柱的渲染器：不是粒子堆，而是三层同心圆柱 + 贴地火环。
 *
 * <p>贴图 {@code textures/entity/flame_pillar.png} 是手绘的无缝火焰噪声（竖条火舌）。
 * 圆柱的 V 坐标按"层"向上滚动，看起来就是火焰一直在往上舔；每层的半径和透明度
 * 带正弦抖动，柱子边缘不会是一条死板的直线。
 *
 * <p>三层各走一套渲染层：
 * <ul>
 *   <li><b>柱身</b>：半径 1.55→2.00，走 cutout（<b>写深度</b>），是真正挡住背后地形的那一层；</li>
 *   <li><b>光晕</b>：半径 2.45→3.10，半透明，负责火焰外缘的柔光和体量，不透明度由 {@link #OPACITY} 控制；</li>
 *   <li><b>烧红边</b>：半径 2.02→2.36，加色混合，把柱身轮廓提亮，读作"这柱子是烫的"。</li>
 * </ul>
 */
public class FlamePillarRenderer extends EntityRenderer<FlamePillarEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_pillar.png");

    private static final int SEGMENTS = 36;
    private static final int LAYERS = 16;

    /**
     * 整体不透明度：柱身（外层 / 中层）和贴地火环的 alpha 都乘这个值。
     *
     * <p>只想让柱子更实或更透，改这一个数就够，不用动下面三层各自的参数：
     * <ul>
     *   <li>{@code 1.0} —— 柱子基本不透光，读作一根实心火柱（当前值）；</li>
     *   <li>{@code 0.85} —— 稍微能看到背后的地形，火焰感更"虚"；</li>
     *   <li>{@code 0.6} 以下 —— 明显半透明，像一层火幕。</li>
     * </ul>
     *
     * <p>注意：贴图 {@code flame_pillar.png} 本身几乎全不透明（平均 alpha 244），
     * 所以"看起来透"全部来自这里的顶点 alpha，与贴图无关。
     */
    private static final float OPACITY = 1.0F;

    public FlamePillarRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FlamePillarEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FlamePillarEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;
        float t = MathHelper.clamp(life / FlamePillarEntity.LIFE, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }

        // 起手 4 tick 长满，最后 12 tick 靠透明度收掉
        float appear = MathHelper.clamp(life / 4.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((FlamePillarEntity.LIFE - life) / 12.0F, 0.0F, 1.0F);
        float envelope = appear * remain;
        float scale = 0.72F + 0.28F * appear;
        float height = FlamePillarEntity.HEIGHT * (0.55F + 0.45F * appear);
        float time = life * 0.06F;

        // 三层各走各的渲染层，这才是"实不实"的关键：
        //
        // solid —— cutout，**会写深度**。贴图本身全不透明（所有像素 alpha>=200），
        //          所以不会有"剪纸边"的问题；但近侧墙面会真正挡住远侧内壁和背后的地形，
        //          柱子才有体积感。之前全用半透明层，它不写深度，怎么调 alpha 都只是
        //          "把背景混进来多少"，永远挡不住背后 —— 这就是调了也看不出变化的原因。
        // halo  —— 半透明，铺在 solid 外面一圈，负责火焰外缘的柔光和体量。
        // glow  —— 加色，夹在 solid 外侧，读作"柱身烧红了"。
        VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        VertexConsumer halo = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 三层圆柱的粗细全部按杀伤半径换算（比值沿用半径 2.2 格时的手感）。
        // 这样调 FlamePillarEntity.RADIUS 时，判定范围和柱子外形会一起变，
        // 不会出现"打得比看着远"或者"看着很粗却打不到"。
        final float R = FlamePillarEntity.RADIUS;

        // 外层光晕：半透明，比柱身再大一圈，只在柱身轮廓之外露出一条边
        drawCylinder(halo, posMat, normMat, R * 1.11F * scale, R * 1.41F * scale, height,
                LAYERS, 0.50F, 1.60F, 0.55F * OPACITY * envelope, time,
                255, 205, 130, 235, 78, 14);
        // 柱身：真正挡光的那一层。高度在收尾时缩回地面（cutout 不支持 alpha 淡出）
        drawCylinder(solid, posMat, normMat, R * 0.70F * scale, R * 0.91F * scale,
                height * (0.06F + 0.94F * remain),
                1.20F, 1.00F, 0.35F, 1.00F, time,
                255, 240, 196, 255, 126, 22);
        // 烧红的边：加色层夹在柱身外侧，柱身的轮廓会被这一圈"提亮"
        drawCylinder(glow, posMat, normMat, R * 0.92F * scale, R * 1.07F * scale, height * 0.98F,
                LAYERS, 1.75F, 3.00F, 0.85F * envelope, time,
                255, 226, 150, 255, 168, 60);

        // 贴地火环：让柱子有"从地里烧起来"的根
        drawGroundRing(halo, posMat, normMat, R * 0.95F, R * 1.82F, 0.95F * OPACITY * envelope, time);
    }

    /**
     * 画一段圆台（上下半径可不同）。
     *
     * @param tiles  整根柱子上贴图重复几次
     * @param scroll 每秒向上滚多少个贴图单位
     * @param twist  整根柱子的扭转量（弧度）
     */
    private void drawCylinder(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                              float baseRadius, float topRadius, float height,
                              float tiles, float scroll, float twist, float alpha,
                              float time,
                              int br, int bg, int bb, int tr, int tg, int tb) {
        float y0 = -0.3F;

        for (int layer = 0; layer < LAYERS; layer++) {
            float p1 = (float) layer / LAYERS;
            float p2 = (float) (layer + 1) / LAYERS;

            float ly1 = y0 + height * p1;
            float ly2 = y0 + height * p2;
            float r1 = MathHelper.lerp(p1, baseRadius, topRadius);
            float r2 = MathHelper.lerp(p2, baseRadius, topRadius);

            // 每层的抖动相位不同，柱身才有"翻腾"感；幅度压小，否则 16 层会看到明显横条
            float flick = 0.93F + 0.07F * MathHelper.sin(time * 12.0F + p1 * 9.0F);
            // 顶部渐隐，避免柱子被一刀切断
            float topFade = 1.0F - 0.45F * MathHelper.clamp((p1 - 0.72F) / 0.28F, 0.0F, 1.0F);
            int a = (int) (MathHelper.clamp(alpha * flick * topFade, 0.0F, 1.0F) * 255.0F);
            if (a <= 2) {
                continue;
            }

            int cr = (int) MathHelper.lerp(p1, br, tr);
            int cg = (int) MathHelper.lerp(p1, bg, tg);
            int cb = (int) MathHelper.lerp(p1, bb, tb);

            float tw1 = twist * p1 - time * 1.6F;
            float tw2 = twist * p2 - time * 1.6F;
            // 贴图在竖直方向滚动：V 用连续量，不能取小数部分，
            // 否则两顶点之间一旦发生 1→0 回绕，整张贴图会被插值拉成一条横向色带
            float v1 = p1 * tiles + time * scroll * 6.0F;
            float v2 = p2 * tiles + time * scroll * 6.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float ang1 = (float) (Math.PI * 2.0D * i / SEGMENTS) + tw1;
                float ang2 = (float) (Math.PI * 2.0D * (i + 1) / SEGMENTS) + tw1;
                float ang3 = (float) (Math.PI * 2.0D * (i + 1) / SEGMENTS) + tw2;
                float ang4 = (float) (Math.PI * 2.0D * i / SEGMENTS) + tw2;

                float w1 = wobble(ang1, p1, time);
                float w2 = wobble(ang2, p1, time);
                float w3 = wobble(ang3, p2, time);
                float w4 = wobble(ang4, p2, time);

                float x1 = MathHelper.cos(ang1) * r1 * w1;
                float z1 = MathHelper.sin(ang1) * r1 * w1;
                float x2 = MathHelper.cos(ang2) * r1 * w2;
                float z2 = MathHelper.sin(ang2) * r1 * w2;
                float x3 = MathHelper.cos(ang3) * r2 * w3;
                float z3 = MathHelper.sin(ang3) * r2 * w3;
                float x4 = MathHelper.cos(ang4) * r2 * w4;
                float z4 = MathHelper.sin(ang4) * r2 * w4;

                float u1 = (float) i / SEGMENTS;
                float u2 = (float) (i + 1) / SEGMENTS;

                // 外表面
                vertex(vc, posMat, normMat, x1, ly1, z1, u1, v1, a, cr, cg, cb, x1, 0.0F, z1);
                vertex(vc, posMat, normMat, x2, ly1, z2, u2, v1, a, cr, cg, cb, x2, 0.0F, z2);
                vertex(vc, posMat, normMat, x3, ly2, z3, u2, v2, a, cr, cg, cb, x3, 0.0F, z3);
                vertex(vc, posMat, normMat, x4, ly2, z4, u1, v2, a, cr, cg, cb, x4, 0.0F, z4);

                // 内表面不再画：cutout 层会写深度，背面自然被正面挡住，
                // 再画一遍纯属白扔一半顶点（原来还正是它让柱子看起来是个空心筒）
            }
        }
    }

    /** 半径的细碎抖动，让圆柱边缘不是完美圆形 */
    private float wobble(float angle, float p, float time) {
        return 1.0F
                + 0.10F * MathHelper.sin(angle * 5.0F + p * 9.0F + time * 15.0F)
                + 0.05F * MathHelper.sin(angle * 9.0F - p * 4.0F + time * 23.0F);
    }

    /** 贴地的一圈火：由内向外铺，UV 沿径向 */
    private void drawGroundRing(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                                float inner, float outer, float alpha, float time) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (a <= 2) {
            return;
        }
        float y = 0.06F;
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float ang1 = (float) (Math.PI * 2.0D * i / segments);
            float ang2 = (float) (Math.PI * 2.0D * (i + 1) / segments);
            float pulse = 1.0F + 0.08F * MathHelper.sin(time * 18.0F + ang1 * 4.0F);
            float inR = inner * pulse;
            float outR = outer * pulse;

            float ix1 = MathHelper.cos(ang1) * inR;
            float iz1 = MathHelper.sin(ang1) * inR;
            float ox1 = MathHelper.cos(ang1) * outR;
            float oz1 = MathHelper.sin(ang1) * outR;
            float ix2 = MathHelper.cos(ang2) * inR;
            float iz2 = MathHelper.sin(ang2) * inR;
            float ox2 = MathHelper.cos(ang2) * outR;
            float oz2 = MathHelper.sin(ang2) * outR;

            float u1 = (float) i / segments;
            float u2 = (float) (i + 1) / segments;
            // 滚动让地面火环也在烧（同样是连续 V，避免半路回绕）
            float vIn = 0.15F + time * 1.5F;
            float vOut = 0.85F + time * 1.5F;

            vertex(vc, posMat, normMat, ix1, y, iz1, u1, vIn, a, 255, 214, 140, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, ox1, y, oz1, u1, vOut, a, 210, 60, 6, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, ox2, y, oz2, u2, vOut, a, 210, 60, 6, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, ix2, y, iz2, u2, vIn, a, 255, 214, 140, 0.0F, 1.0F, 0.0F);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        int r, int g, int b,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, nx, ny, nz)
                .next();
    }
}
