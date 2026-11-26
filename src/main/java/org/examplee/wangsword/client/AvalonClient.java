package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 阿瓦隆展开时的护罩视觉：包在玩家身上的一层金色半透明球壳。
 *
 * <p>激活状态由服务端包驱动（{@link org.examplee.wangsword.util.AvalonHandler}），
 * 这里只负责按剩余时间画壳。
 */
public final class AvalonClient {

    private static final Identifier GOLD = ArcSlashEntityRenderer.GOLD_TEXTURE;
    private static final int LAT = 10;
    private static final int LON = 28;
    private static final float RADIUS = 1.35F;

    private static long activeUntil;
    private static long activatedAt;

    private AvalonClient() {
    }

    public static void start(int durationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        long now = client.world.getTime();
        activatedAt = now;
        activeUntil = now + durationTicks;
    }

    public static void stop() {
        activeUntil = 0;
        activatedAt = 0;
    }

    public static boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || activeUntil <= 0) {
            return false;
        }
        if (client.world.getTime() >= activeUntil) {
            activeUntil = 0;
            return false;
        }
        return true;
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera) {
        if (!isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        long now = client.world.getTime();
        float remain = (float) (activeUntil - now);
        // 最后 20 tick 慢慢淡出
        float alpha = Math.min(1.0F, remain / 20.0F) * 0.30F;

        Vec3d center = player.getPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);

        matrices.push();
        matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        VertexConsumer vc = immediate.getBuffer(RenderLayer.getEntityTranslucentEmissive(GOLD));
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        float spin = (now + client.getTickDelta()) * 0.02F;

        for (int lat = 0; lat < LAT; lat++) {
            double phi1 = Math.PI * lat / LAT;
            double phi2 = Math.PI * (lat + 1) / LAT;
            for (int lon = 0; lon < LON; lon++) {
                float u1 = (float) lon / LON;
                float u2 = (float) (lon + 1) / LON;
                double th1 = Math.PI * 2.0D * lon / LON + spin;
                double th2 = Math.PI * 2.0D * (lon + 1) / LON + spin;

                float x1 = (float) (Math.sin(phi1) * Math.cos(th1) * RADIUS);
                float y1 = (float) (Math.cos(phi1) * RADIUS);
                float z1 = (float) (Math.sin(phi1) * Math.sin(th1) * RADIUS);
                float x2 = (float) (Math.sin(phi1) * Math.cos(th2) * RADIUS);
                float y2 = (float) (Math.cos(phi1) * RADIUS);
                float z2 = (float) (Math.sin(phi1) * Math.sin(th2) * RADIUS);
                float x3 = (float) (Math.sin(phi2) * Math.cos(th2) * RADIUS);
                float y3 = (float) (Math.cos(phi2) * RADIUS);
                float z3 = (float) (Math.sin(phi2) * Math.sin(th2) * RADIUS);
                float x4 = (float) (Math.sin(phi2) * Math.cos(th1) * RADIUS);
                float y4 = (float) (Math.cos(phi2) * RADIUS);
                float z4 = (float) (Math.sin(phi2) * Math.sin(th1) * RADIUS);

                quad(vc, mat, normMat, a, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, u2);
                // 内面
                quad(vc, mat, normMat, a, x2, y2, z2, x1, y1, z1, x4, y4, z4, x3, y3, z3, u2, u1);
            }
        }

        matrices.pop();
        immediate.draw();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f normMat, int a,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float u1, float u2) {
        vertex(vc, mat, normMat, x1, y1, z1, u1, 1.0F, a);
        vertex(vc, mat, normMat, x2, y2, z2, u2, 1.0F, a);
        vertex(vc, mat, normMat, x3, y3, z3, u2, 0.0F, a);
        vertex(vc, mat, normMat, x4, y4, z4, u1, 0.0F, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f normMat,
                               float x, float y, float z, float u, float v, int a) {
        vc.vertex(mat, x, y, z)
                .color(255, 255, 255, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, x, y, z)
                .next();
    }
}
