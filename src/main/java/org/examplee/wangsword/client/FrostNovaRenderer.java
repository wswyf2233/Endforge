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
import org.examplee.wangsword.entity.FrostNovaEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 霜环的渲染器：贴图铺在 XZ 平面上随 {@code age} 向外扩散、同时淡出。
 *
 * <p>为什么不堆粒子：原版雪花粒子碎、小、没有方向感，撒一圈只会像「地上落了霜」，
 * 读不出「冲击波从脚下推出去」。用自绘贴图 + 扩散动画，一下就有形了。
 *
 * <p>五层叠出体积和层次：
 * <ol>
 *   <li><b>起爆光团</b>：脚下先炸开一团软白光，只活前 45%，解决「环从无到有发虚」；</li>
 *   <li><b>贴地主环</b>：判定线本身，最亮最实；</li>
 *   <li><b>抬升环</b>：抬高 0.30 格、略小一圈，让侧面看也有高度，不然只是一条线；</li>
 *   <li><b>内层第二波</b>：半径只到 0.55 倍，读作「第二道环」而不是同一圈的影子；</li>
 *   <li><b>环形冰壁</b>：竖起来的圆柱面，立在主环上，先起后落，是「华丽」的主要来源。</li>
 * </ol>
 *
 * <p>实体本身 {@code noClip} 且不参与碰撞，所以这里不处理朝向，环永远是水平圆。
 */
public class FrostNovaRenderer extends EntityRenderer<FrostNovaEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/frost_nova.png");
    /** 冰壁：横向无缝、底部实、上部碎成锯齿 */
    private static final Identifier TEXTURE_WALL =
            new Identifier("wangsword", "textures/entity/frost_nova_wall.png");
    /** 起爆光团：纯径向衰减的软圆 */
    private static final Identifier TEXTURE_FLASH =
            new Identifier("wangsword", "textures/entity/frost_nova_flash.png");

    /**
     * 贴图上环带的中心半径（占半张贴图的比例）。
     *
     * <p>贴图里的实心环带在 0.695~0.83 之间（外缘羽化到 0.885），重心约 0.76。
     * 四边形是 -r~r 铺满 UV 0~1，所以按 r 直接画的话，玩家看到的环只有判定半径的
     * 0.76 倍——站到环外一点却照样吃到伤害，观感上就是「打到了空气」。这里把四边形
     * 放大 1/0.76，让可见环带的正中落在判定半径上，环内外的感觉和实际判定就对得上了。
     */
    private static final float RING_CENTER = 0.76F;

    /** 冰壁的圆周分段数 */
    private static final int WALL_SEGMENTS = 40;

    public FrostNovaRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FrostNovaEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FrostNovaEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float maxRadius = entity.getRadius();
        if (maxRadius < 0.05F) {
            return;
        }

        float life = entity.age + tickDelta;
        float p = MathHelper.clamp(life / FrostNovaEntity.LIFE, 0.0F, 1.0F);
        if (p >= 1.0F) {
            return;
        }

        // 起手 2 tick 淡入，之后按 (1-p)^0.8 淡出。
        // 指数刻意压到 1 以下：环推到最外圈时还留着四成亮度，边界才看得清
        float appear = MathHelper.clamp(life / 2.0F, 0.0F, 1.0F);
        float fade = (float) Math.pow(1.0F - p, 0.8D);
        float envelope = appear * fade;

        // 扩散：0.6 的进度就推到最外圈，剩下 40% 原地消散。
        // 之前用 1-(1-p)^3 一路匀着推，环真正到 7 格时 alpha 已经见底，
        // 玩家根本看不到"边界在哪"，只觉得范围比实际小。
        float t = MathHelper.clamp(p / 0.6F, 0.0F, 1.0F);
        // t^0.7：起步就快，快到边缘时才减速，读作"一记甩出去再定住"
        float ease = (float) Math.pow(t, 0.7D);
        // 除了 RING_CENTER 的补偿，可见环带就落在 maxRadius 上：环内吃伤害，环外不吃
        float radius = maxRadius * (0.08F + 0.92F * ease) / RING_CENTER;
        /** 可见环带的实际半径——冰壁就立在这条线上 */
        float visible = radius * RING_CENTER;

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 注意：每一层必须「取完 buffer 就把顶点写完」。
        // Immediate 的 getBuffer 在换 layer 时会把上一个 builder flush 掉，
        // 跨 layer 一直攥着旧引用写，顶点会落到错误的层里。

        // ---------- 1. 起爆光团：只活前 45% ----------
        if (p < 0.45F) {
            float fp = p / 0.45F;
            float flashA = (1.0F - fp) * (1.0F - fp) * 0.95F;
            float flashR = maxRadius * (0.18F + 0.50F * (1.0F - (1.0F - fp) * (1.0F - fp)));
            if (flashA > 0.01F) {
                VertexConsumer vc = vertexConsumers.getBuffer(
                        RenderLayer.getEntityTranslucentEmissive(TEXTURE_FLASH));
                plane(vc, posMat, normMat, 0.04F, flashR,
                        (int) (flashA * 255.0F), 236, 250, 255);
            }
        }

        // ---------- 2~4. 三张环：贴地主环 / 抬升环 / 内层第二波 ----------
        VertexConsumer main = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));

        // 贴着方块顶面画，抬高 3 厘米避免和地面 Z-fighting
        plane(main, posMat, normMat, 0.03F, radius,
                (int) (envelope * 255.0F), 208, 238, 255);

        // 抬升环：给冲击波一点体积
        float riseEnv = envelope * 0.42F * MathHelper.clamp((life - 2.0F) / 4.0F, 0.0F, 1.0F);
        if (riseEnv > 0.01F) {
            plane(main, posMat, normMat, 0.30F, radius * 0.95F,
                    (int) (riseEnv * 255.0F), 150, 208, 250);
        }

        // 内层第二波：半径跟不上外圈
        float innerEnv = appear
                * (float) Math.pow(Math.max(0.0F, 1.0F - p * 1.15F), 1.3D)
                * MathHelper.clamp((life - 3.0F) / 4.0F, 0.0F, 1.0F);
        if (innerEnv > 0.01F) {
            plane(main, posMat, normMat, 0.05F, radius * 0.55F,
                    (int) (innerEnv * 0.72F * 255.0F), 240, 250, 255);
        }

        // ---------- 5. 环形冰壁：竖起来的圆柱面 ----------
        // 高度先起后落：sin 曲线在扩散中期最高，末尾跟着衰减一起塌掉。
        // 上限压到 1.45 格是故意的——7 格外的冰壁如果再高，站在圆心的玩家
        // 会被自己放的一圈墙挡住视野，打起来很别扭。
        float rise = (float) Math.sin(Math.PI * Math.min(1.0F, p * 1.25F));
        float wallHeight = MathHelper.clamp(visible * 0.30F, 0.20F, 1.45F) * rise;
        float wallA = envelope * (0.80F + 0.20F * rise);
        if (wallHeight > 0.03F && wallA > 0.02F) {
            VertexConsumer vc = vertexConsumers.getBuffer(
                    RenderLayer.getEntityTranslucentEmissive(TEXTURE_WALL));
            // 分段按半径给：半径小的环不需要 40 段，白烧顶点
            int segs = MathHelper.clamp((int) Math.round(visible * 6.0F), 14, WALL_SEGMENTS);
            wall(vc, posMat, normMat, visible, wallHeight, segs,
                    (int) (Math.min(1.0F, wallA) * 255.0F), 178, 226, 255);

            // 内壁薄层：错开半径和高度，让冰壁看起来有厚度，而不是一张纸
            float innerH = wallHeight * 0.72F;
            if (innerH > 0.03F) {
                wall(vc, posMat, normMat, visible * 0.965F, innerH, segs,
                        (int) (Math.min(1.0F, wallA * 0.50F) * 255.0F), 214, 240, 255);
            }
        }
    }

    /** 水平四边形，UV 铺满整张贴图 */
    private void plane(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                       float y, float r, int a, int cr, int cg, int cb) {
        if (a <= 2 || r <= 0.01F) {
            return;
        }
        vertex(vc, posMat, normMat, -r, y, -r, 0.0F, 0.0F, a, cr, cg, cb, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, r, y, -r, 1.0F, 0.0F, a, cr, cg, cb, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, r, y, r, 1.0F, 1.0F, a, cr, cg, cb, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, -r, y, r, 0.0F, 1.0F, a, cr, cg, cb, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 环形冰壁：绕一圈的竖直四边形。
     *
     * <p>内外两面各写一遍——{@code entity_translucent_emissive} 的剔除开关在不同
     * 光影包下表现不一致，双面都画就不用赌，代价只是多一倍顶点。
     */
    private void wall(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                      float r, float h, int segments, int a, int cr, int cg, int cb) {
        float step = (float) (Math.PI * 2.0D / segments);
        // 横向平铺次数跟着圆周长走：贴图横向约 1.8 格一遍，
        // 否则 7 格半径那一圈会被拉成 40 多格宽，锯齿全部糊平
        float repeat = Math.max(1.0F, (float) (Math.PI * 2.0D * r) / 1.8F);
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            float cos0 = MathHelper.cos(a0);
            float sin0 = MathHelper.sin(a0);
            float cos1 = MathHelper.cos(a1);
            float sin1 = MathHelper.sin(a1);

            float x0 = cos0 * r;
            float z0 = sin0 * r;
            float x1 = cos1 * r;
            float z1 = sin1 * r;

            float u0 = i / (float) segments * repeat;
            float u1 = (i + 1) / (float) segments * repeat;

            float nx = MathHelper.cos((a0 + a1) * 0.5F);
            float nz = MathHelper.sin((a0 + a1) * 0.5F);

            // 外面（法线朝外）
            vertex(vc, posMat, normMat, x0, 0.0F, z0, u0, 1.0F, a, cr, cg, cb, nx, 0.0F, nz);
            vertex(vc, posMat, normMat, x1, 0.0F, z1, u1, 1.0F, a, cr, cg, cb, nx, 0.0F, nz);
            vertex(vc, posMat, normMat, x1, h, z1, u1, 0.0F, a, cr, cg, cb, nx, 0.0F, nz);
            vertex(vc, posMat, normMat, x0, h, z0, u0, 0.0F, a, cr, cg, cb, nx, 0.0F, nz);

            // 内面（法线朝内），绕向反转
            vertex(vc, posMat, normMat, x1, 0.0F, z1, u1, 1.0F, a, cr, cg, cb, -nx, 0.0F, -nz);
            vertex(vc, posMat, normMat, x0, 0.0F, z0, u0, 1.0F, a, cr, cg, cb, -nx, 0.0F, -nz);
            vertex(vc, posMat, normMat, x0, h, z0, u0, 0.0F, a, cr, cg, cb, -nx, 0.0F, -nz);
            vertex(vc, posMat, normMat, x1, h, z1, u1, 0.0F, a, cr, cg, cb, -nx, 0.0F, -nz);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        int r, int g, int b, float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, nx, ny, nz)
                .next();
    }
}
