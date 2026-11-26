package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** 残骸搜寻仪的客户端部分：保存服务器发来的坐标，并在世界里画穿墙描边。 */
public final class RelicSeekerClient {

    private static final List<BlockPos> MARKS = new ArrayList<>();
    private static long expireTick;

    private RelicSeekerClient() {
    }

    public static void accept(List<BlockPos> positions, int duration) {
        MinecraftClient client = MinecraftClient.getInstance();
        MARKS.clear();
        MARKS.addAll(positions);
        long now = client.world == null ? 0L : client.world.getTime();
        expireTick = now + duration;
    }

    public static void clear() {
        MARKS.clear();
        expireTick = 0L;
    }

    public static boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MARKS.isEmpty() || client.world == null) {
            return false;
        }
        if (client.world.getTime() > expireTick) {
            MARKS.clear();
            return false;
        }
        return true;
    }

    public static List<BlockPos> marks() {
        return MARKS;
    }

    /** 在世界里画描边（穿墙） */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera) {
        if (!isActive()) {
            return;
        }
        net.minecraft.client.render.VertexConsumer consumer =
                immediate.getBuffer(RelicOutlineLayer.LAYER);
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPos pos : MARKS) {
            net.minecraft.client.render.WorldRenderer.drawBox(matrices, consumer,
                    pos.getX() - 0.002D, pos.getY() - 0.002D, pos.getZ() - 0.002D,
                    pos.getX() + 1.002D, pos.getY() + 1.002D, pos.getZ() + 1.002D,
                    0.35F, 0.95F, 1.0F, 0.9F);
        }
        matrices.pop();
        immediate.draw(RelicOutlineLayer.LAYER);
    }
}