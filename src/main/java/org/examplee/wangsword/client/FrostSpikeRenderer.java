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
import net.minecraft.util.math.RotationAxis;
import org.examplee.wangsword.entity.FrostSpikeEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 冰棱的渲染器。
 *
 * <p>用两个互相垂直的平面交叉成十字，正面贴同一张「侧视轮廓」贴图。
 * 这样不论从哪个角度看都是一根有厚度、有尖的冰棱，而不用真做 3D 网格。
 *
 * <p>三层叠亮度：外层辉光给冷蓝色体量，主刃是冰的本体，内芯近白，
 * 让它在雪地、冰原这种亮背景上也能看见。
 *
 * <p>局部坐标系里 +Y 是飞行方向（和光柱、火锥同一套变换），
 * 实体位置记的是**尾部**，所以冰棱从 y≈0 往前长到 {@link #LENGTH}。
 */
public class FrostSpikeRenderer extends EntityRenderer<FrostSpikeEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/frost_spike.png");

    /**
     * 冰棱长度（格）。
     *
     * <p>原来 1.75，配合 27 格/秒的速度，玩家只看到一道细白光闪过。
     * 拉到 2.5 之后长宽一起变大，加上飞行时间接近 1 秒，才看得出是一根冰锥。
     */
    private static final float LENGTH = 2.5F;
    /** 尾部略微探到实体位置之后，接住上一 tick 的位置 */
    private static final float TAIL_BACK = 0.30F;

    public FrostSpikeRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FrostSpikeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FrostSpikeEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;
        if (life >= FrostSpikeEntity.MAX_LIFE) {
            return;
        }

        // 起手 1 tick 淡入、最后 4 tick 淡出；两者取小，避免末段突然跳亮。
        // 存活从 12 tick 拉到 18，淡出也跟着放宽，不然收尾会显得很仓促。
        float appear = MathHelper.clamp(life / 1.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((FrostSpikeEntity.MAX_LIFE - life) / 4.0F, 0.0F, 1.0F);
        float env = Math.min(appear, remain);
        if (env <= 0.02F) {
            return;
        }

        matrices.push();
        // 把局部 +Y 轴转到实体朝向：和光柱、火锥同一套变换
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        float y0 = -TAIL_BACK;
        float y1 = LENGTH;

        // 外层辉光
        cross(vc, posMat, normMat, 0.46F, y0, y1,
                (int) (0.42F * env * 255.0F), 112, 182, 240);
        // 主刃
        cross(vc, posMat, normMat, 0.28F, y0, y1,
                (int) (env * 255.0F), 196, 232, 255);
        // 内芯：近白，负责"刺眼"
        cross(vc, posMat, normMat, 0.12F, y0, y1,
                (int) (env * 255.0F), 250, 253, 255);

        matrices.pop();
    }

    /** 一组十字平面（横向沿 X 一个、沿 Z 一个），正反两面都写 */
    private void cross(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                       float w, float y0, float y1, int a, int cr, int cg, int cb) {
        if (a <= 2) {
            return;
        }
        // 面 A：横向沿 X，法线 ±Z
        quad(vc, posMat, normMat, -w, 0.0F, w, 0.0F, y0, y1, 0.0F, 1.0F, a, cr, cg, cb);
        quad(vc, posMat, normMat, w, 0.0F, -w, 0.0F, y0, y1, 0.0F, -1.0F, a, cr, cg, cb);

        // 面 B：横向沿 Z，法线 ±X
        quad(vc, posMat, normMat, 0.0F, -w, 0.0F, w, y0, y1, 1.0F, 0.0F, a, cr, cg, cb);
        quad(vc, posMat, normMat, 0.0F, w, 0.0F, -w, y0, y1, -1.0F, 0.0F, a, cr, cg, cb);
    }

    /**
     * 一个竖直四边形：{@code (ax,az)} 到 {@code (bx,bz)} 是底边，
     * 从 {@code y0} 长到 {@code y1}。UV 底部 v=1、顶部 v=0——
     * 贴图上方是尖，下方是碎掉的尾。
     */
    private void quad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                      float ax, float az, float bx, float bz,
                      float y0, float y1, float nx, float nz,
                      int a, int cr, int cg, int cb) {
        vtx(vc, posMat, normMat, ax, y0, az, 0.0F, 1.0F, a, cr, cg, cb, nx, nz);
        vtx(vc, posMat, normMat, bx, y0, bz, 1.0F, 1.0F, a, cr, cg, cb, nx, nz);
        vtx(vc, posMat, normMat, bx, y1, bz, 1.0F, 0.0F, a, cr, cg, cb, nx, nz);
        vtx(vc, posMat, normMat, ax, y1, az, 0.0F, 0.0F, a, cr, cg, cb, nx, nz);
    }

    private void vtx(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                     float x, float y, float z, float u, float v, int a,
                     int r, int g, int b, float nx, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, nx, 0.0F, nz)
                .next();
    }
}
