package org.examplee.wangsword.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.ThunderRingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 雷怒脚下雷环的渲染器。
 *
 * <p>就一张贴地平面，靠三层时序做出「一直在放电」的感觉：
 * <ol>
 *   <li><b>主环</b>：贴地 2 厘米，亮度按 0.7±0.3 的正弦呼吸——不呼吸的话就是一张静态图；</li>
 *   <li><b>外扩波纹</b>：每 26 tick 从 0.62 倍半径涨到 1 倍再淡掉，读作「能量往外顶」；</li>
 *   <li><b>起手闪光</b>：前 8 tick 多画一层放大 1.15 倍的高亮版，按下的瞬间才有力。</li>
 * </ol>
 *
 * <p>位置不读实体自己的坐标：服务端带着实体跟脚走，但网络包到客户端会慢一两 tick，
 * 直接用实体坐标跑起来会看到环拖在身后。这里改按主人的本地插值坐标重算，
 * 环永远踩在脚下。
 */
public class ThunderRingRenderer extends EntityRenderer<ThunderRingEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/thunder_ring.png");

    /** 万雷落点圈用的贴图：电弧更炸、外缘更靠边 */
    private static final Identifier FIELD_TEXTURE =
            new Identifier("wangsword", "textures/entity/thunder_ring01.png");

    /** 火刀被动「焚身领域」的火焰环 */
    private static final Identifier FIRE_TEXTURE =
            new Identifier("wangsword", "textures/entity/fire_ring.png");

    /** 血帆弯刀血怒的深红血环 */
    private static final Identifier BLOOD_TEXTURE =
            new Identifier("wangsword", "textures/entity/blood_ring.png");

    /**
     * 贴图里可见电弧的外缘（占半张贴图的比例）。
     *
     * <p>图是 128×128，两道亮环在 0.42 与 0.64 半径处，外层零散电弧一直拖到 0.84 才彻底透明。
     * 四边形按 -R~R 铺满 UV 0~1，所以把 R 放大 1/0.84，最外圈的电弧正好落在设定半径上，
     * 玩家看到的边界和「3 格」对得上。
     */
    private static final float GLOW_EDGE = 0.84F;

    /**
     * {@code thunder_ring01.png} 的可见外缘比例。
     *
     * <p>这张图的电弧尖刺一直顶到接近画布边缘，按 0.84 铺会让整个环缩水一圈，
     * 玩家看到的边界就和「7 格杀伤范围」对不上了。取 0.93。
     */
    private static final float FIELD_GLOW_EDGE = 0.93F;

    /** {@code fire_ring.png} 的火焰外缘比例 */
    private static final float FIRE_GLOW_EDGE = 0.90F;

    /** {@code blood_ring.png} 的可见外缘比例（血丝溅到接近画布边缘） */
    private static final float BLOOD_GLOW_EDGE = 0.95F;

    /** 波纹周期（tick） */
    private static final float RIPPLE_PERIOD = 26.0F;
    /** 起手闪光持续（tick） */
    private static final float FLASH_TICKS = 8.0F;

    public ThunderRingRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ThunderRingEntity entity) {
        return switch (entity.getStyle()) {
            case ThunderRingEntity.STYLE_THUNDER_FIELD -> FIELD_TEXTURE;
            case ThunderRingEntity.STYLE_FIRE -> FIRE_TEXTURE;
            case ThunderRingEntity.STYLE_BLOOD -> BLOOD_TEXTURE;
            default -> TEXTURE;
        };
    }

    /** 这圈环的可见外缘比例，决定半径要放大多少才能让边界对上 */
    private static float glowEdge(ThunderRingEntity entity) {
        return switch (entity.getStyle()) {
            case ThunderRingEntity.STYLE_THUNDER_FIELD -> FIELD_GLOW_EDGE;
            case ThunderRingEntity.STYLE_FIRE -> FIRE_GLOW_EDGE;
            case ThunderRingEntity.STYLE_BLOOD -> BLOOD_GLOW_EDGE;
            default -> GLOW_EDGE;
        };
    }

    @Override
    public void render(ThunderRingEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float radius = entity.getRadius();
        if (radius < 0.05F) {
            return;
        }

        int total = Math.max(1, entity.getLifeTicks());
        float life = entity.age + tickDelta;
        if (life > total) {
            return;
        }

        // ---------- 跟随主人的脚（固定环跳过） ----------
        Entity owner = entity.getWorld().getEntityById(entity.getOwnerId());
        boolean pushed = false;
        if (!entity.isFixed() && owner != null) {
            Vec3d target = owner.getLerpedPos(tickDelta);
            Vec3d base = entity.getLerpedPos(tickDelta);
            // 火刀被动环稍微离地：站在草径、雪层上时不会被旁边完整方块的顶面遮住
            double lift = entity.getStyle() == ThunderRingEntity.STYLE_FIRE ? 0.25D
                    : entity.getStyle() == ThunderRingEntity.STYLE_BLOOD ? 0.15D : 0.0D;
            matrices.push();
            pushed = true;
            matrices.translate(target.x - base.x, target.y + lift - base.y, target.z - base.z);
        }

        try {
            // 入场 4 tick 淡入、最后 12 tick 淡出，中间靠脉冲呼吸
            float fadeIn = MathHelper.clamp(life / 4.0F, 0.0F, 1.0F);
            float fadeOut = MathHelper.clamp((total - life) / 12.0F, 0.0F, 1.0F);
            boolean fire = entity.getStyle() == ThunderRingEntity.STYLE_FIRE;
            boolean blood = entity.getStyle() == ThunderRingEntity.STYLE_BLOOD;
            // 常驻型（火刀被动、血怒）不做明显呼吸，否则玩家会以为又触发了一次
            boolean persistent = fire || blood;
            float pulse = persistent
                    ? 0.98F + 0.02F * MathHelper.sin(life * 0.20F)
                    : 0.70F + 0.30F * MathHelper.sin(life * 0.28F);
            float env = fadeIn * fadeOut * pulse;
            if (env <= 0.01F) {
                return;
            }

            float quad = radius / glowEdge(entity);
            Matrix4f posMat = matrices.peek().getPositionMatrix();
            Matrix3f normMat = matrices.peek().getNormalMatrix();
            VertexConsumer vc = vertexConsumers.getBuffer(
                    RenderLayer.getEntityTranslucentEmissive(getTexture(entity)));

            // ---------- 1. 主环 ----------
            // 雷环的贴图是冷白色，用偏蓝的顶点色压一下更"电"；
            // 火环/血环的贴图本身自带颜色，顶点色给纯白，否则会被再压暗一层。
            plane(vc, posMat, normMat, 0.02F, quad,
                    (int) (env * 255.0F),
                    persistent ? 255 : 210,
                    persistent ? 255 : 232,
                    255);

            // 火环/血环叠第二遍：贴图环线本身只有约 62% 不透明度，
            // 同一块画两遍能让有效不透明度升到 1-(1-0.62)² ≈ 86%，
            // 而 alpha 很低的柔边依旧是柔的——比直接调顶点色更可控。
            if (persistent) {
                plane(vc, posMat, normMat, 0.021F, quad,
                        (int) (env * 255.0F), 255, 255, 255);
            }

            // ---------- 1.5 加色层 ----------
            // 半透明混合只会"把背景混进来"，火焰/血光永远亮不过背景；
            // 加色是"把颜色加上去"，才真的会发光。
            if (persistent) {
                VertexConsumer glow = vertexConsumers.getBuffer(
                        RenderLayer.getEyes(getTexture(entity)));
                plane(glow, posMat, normMat, 0.032F, quad,
                        (int) (env * 255.0F), 255, 255, 255);
            }

            // ---------- 2. 外扩波纹 ----------
            // 常驻环周期性往外顶一圈会被误读成「又触发了一次技能」，跳过
            float rp = (life % RIPPLE_PERIOD) / RIPPLE_PERIOD;
            float rippleA = persistent
                    ? 0.0F
                    : (float) Math.pow(1.0F - rp, 1.6D) * 0.45F * fadeIn * fadeOut;
            if (rippleA > 0.01F) {
                plane(vc, posMat, normMat, 0.015F, quad * (0.62F + 0.38F * rp),
                        (int) (rippleA * 255.0F), 236, 246, 255);
            }

            // ---------- 3. 起手闪光 ----------
            if (life < FLASH_TICKS) {
                float fp = 1.0F - life / FLASH_TICKS;
                plane(vc, posMat, normMat, 0.05F, quad * (1.0F + 0.15F * (1.0F - fp)),
                        (int) (fp * fp * 0.85F * 255.0F), 244, 250, 255);
            }
        } finally {
            if (pushed) {
                matrices.pop();
            }
        }
    }

    /** 水平四边形，UV 铺满整张贴图 */
    private void plane(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                       float y, float r, int a, int cr, int cg, int cb) {
        if (a <= 2 || r <= 0.01F) {
            return;
        }
        vertex(vc, posMat, normMat, -r, y, -r, 0.0F, 0.0F, a, cr, cg, cb);
        vertex(vc, posMat, normMat, r, y, -r, 1.0F, 0.0F, a, cr, cg, cb);
        vertex(vc, posMat, normMat, r, y, r, 1.0F, 1.0F, a, cr, cg, cb);
        vertex(vc, posMat, normMat, -r, y, r, 0.0F, 1.0F, a, cr, cg, cb);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        int r, int g, int b) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, 0.0F, 1.0F, 0.0F)
                .next();
    }
}
