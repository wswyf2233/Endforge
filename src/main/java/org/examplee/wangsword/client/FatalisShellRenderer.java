package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.FatalisShellEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 黑龙炮弹的渲染：小型紫黑球，引信越接近 0 闪得越快。
 */
public class FatalisShellRenderer extends EntityRenderer<FatalisShellEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/fatalis_shell.png");

    public FatalisShellRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FatalisShellEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        int fuse = entity.getFuse();
        // 引信越短，球越大、闪得越快
        float pulse = fuse < 40 ? 1.0F + 0.12F * MathHelper.sin(entity.age * 0.8F) : 1.0F;
        float size = 0.55F * pulse;

        matrices.push();
        matrices.scale(size, size, size);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        quad(vc, posMat, normMat, light,
                -1, -1, 0,  1, -1, 0,  1, 1, 0,  -1, 1, 0,  0, 0, 1);
        quad(vc, posMat, normMat, light,
                -1, 0, -1,  1, 0, -1,  1, 0, 1,  -1, 0, 1,  0, 1, 0);
        quad(vc, posMat, normMat, light,
                0, -1, -1,  0, 1, -1,  0, 1, 1,  0, -1, 1,  1, 0, 0);

        matrices.pop();
    }

    private void quad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz) {
        vertex(vc, posMat, normMat, light, x1, y1, z1, 0, 1, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x2, y2, z2, 1, 1, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x3, y3, z3, 1, 0, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x4, y4, z4, 0, 0, nx, ny, nz);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normMat, nx, ny, nz)
                .next();
    }

    @Override
    public Identifier getTexture(FatalisShellEntity entity) {
        return TEXTURE;
    }
}
