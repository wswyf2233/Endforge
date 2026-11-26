package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.examplee.wangsword.entity.SentinelSwordEntity;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 环伺之剑的渲染：直接画那把巨剑的物品模型，竖直立着绕自身轴慢慢转。
 *
 * <p>不自发光、不加特效——它本来就该看起来是"一把真的剑浮在你旁边"，
 * 花活交给飞行中的光刃和魂火粒子。
 */
public class SentinelSwordRenderer extends EntityRenderer<SentinelSwordEntity> {

    /** 四阶各一把物品栈，避免每帧 new */
    private static final ItemStack[] VARIANTS = new ItemStack[4];

    private static final float SCALE = 1.5F;
    /** 自旋速度（度/tick） */
    private static final float SPIN_PER_TICK = 4.0F;
    /** 向外侧倾的角度，让六把剑围成一个碗形 */
    private static final float TILT_DEGREES = 12.0F;

    public SentinelSwordRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    private static ItemStack stackFor(int tier) {
        int index = Math.max(0, Math.min(tier, 3));
        if (VARIANTS[index] == null) {
            VARIANTS[index] = new ItemStack(switch (index) {
                case 1 -> ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PLUS;
                case 2 -> ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO;
                case 3 -> ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO_MAX;
                default -> ItemsRegistry.WS_SWORD_TOMB_GREATSWORD;
            });
        }
        return VARIANTS[index];
    }

    @Override
    public void render(SentinelSwordEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        ItemStack stack = stackFor(entity.getTier());
        if (stack.isEmpty()) {
            return;
        }

        // 每把剑起手角度错开一圈，绕自身轴转起来像被同一股气托着
        float spin = (entity.age + tickDelta) * SPIN_PER_TICK + entity.getSlot() * 61.0F;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(TILT_DEGREES));
        matrices.scale(SCALE, SCALE, SCALE);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.FIXED,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId());
        matrices.pop();
    }

    @Override
    public Identifier getTexture(SentinelSwordEntity entity) {
        return new Identifier("textures/misc/shadow.png");
    }
}
