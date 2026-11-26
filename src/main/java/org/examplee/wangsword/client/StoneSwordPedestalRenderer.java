package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.examplee.wangsword.block.StoneSwordPedestalBlock;
import org.examplee.wangsword.block.StoneSwordPedestalBlockEntity;

/**
 * 石中剑基座的渲染器：把方块实体里那件物品当作「插在石头里的剑」画出来。
 *
 * <p><b>为什么走这条路而不是把剑写进方块模型 JSON：</b>方块模型只能引用方块图集里的贴图，
 * 手写面片的 UV 永远对不准物品贴图，之前那几版黑块、透明、纸片都是这个原因。
 * 直接用 {@code ItemRenderer} 渲染真实的 {@link ItemStack}，画的是原版物品模型本身——
 * 有厚度、有完整轮廓，换物品也不用改任何模型文件。
 */
public class StoneSwordPedestalRenderer implements BlockEntityRenderer<StoneSwordPedestalBlockEntity> {

    /** 整把剑的缩放。1.0 就正好一格高，0.85 留出露在台面外的比例 */
    private static final float SCALE = 0.85F;

    /** 绕 Y 轴转 45°，让剑斜对着台座的对角线方向，而不是正对着某个面 */
    private static final float YAW = 45.0F;

    /** 剑身中心的高度。0.62 时剑尖扎进台座里，剑柄露在台面之上 */
    private static final double BASE_Y = 0.62D;

    /** 翻转 180°，让剑尖朝下扎进石头、剑柄朝上 */
    private static final float FLIP = 180.0F;

    /** 绕 Z 轴倾斜，视觉上像被斜插进石缝 */
    private static final float TILT = 22.5F;

    /** 渲染器工厂需要一个接收 Context 的构造器，即使这里什么都用不上 */
    public StoneSwordPedestalRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(StoneSwordPedestalBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // 方块状态是最终裁判：has_item 为 false 就绝对不画剑，
        // 哪怕方块实体里因为任何原因还留着物品，也不会出现「拔了还在」
        if (!entity.getCachedState().get(StoneSwordPedestalBlock.HAS_ITEM)) {
            return;
        }
        ItemStack stack = entity.getStoredItem();
        if (stack.isEmpty()) {
            return;
        }

        matrices.push();

        // 1. 移到剑身在方块里的中心高度
        matrices.translate(0.5D, BASE_Y, 0.5D);
        // 2. 朝向：斜对台座
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(YAW));
        // 3. 倒过来：剑尖朝下，插进石头里
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(FLIP + TILT));
        matrices.scale(SCALE, SCALE, SCALE);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.NONE,
                light,
                overlay,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                0);

        matrices.pop();
    }

    /** 剑尖会超出方块顶面，不做边界裁剪 */
    @Override
    public boolean rendersOutsideBoundingBox(StoneSwordPedestalBlockEntity blockEntity) {
        return true;
    }
}
