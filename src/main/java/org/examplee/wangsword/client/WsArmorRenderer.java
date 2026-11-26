package org.examplee.wangsword.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 熔岩套共用的护甲渲染器基类。
 *
 * <p>除了挂模型，还带一次性自检：第一次真正渲染时把整棵骨骼树（位置、pivot、cube 数、
 * 隐藏状态）和当前矩阵打进日志，用来确认模型到底有没有被画、画在哪个高度。
 */
public abstract class WsArmorRenderer<T extends Item & GeoItem> extends GeoArmorRenderer<T> {

    /** 全局只打一次，避免刷屏 */
    private static boolean dumped = false;

    protected WsArmorRenderer(GeoModel<T> model) {
        super(model);
    }

    /**
     * 只按槽位点亮自家骨骼，不读原版盔甲模型的 visible 状态。
     *
     * <p>GeckoLib 默认实现会用 {@code baseModel.xxx.visible} 决定显隐，某些渲染路径下
     * 那个值是被上一件盔甲改过的，容易出现整套模型被隐藏；这里直接按槽位点亮。
     */
    @Override
    protected void applyBoneVisibilityBySlot(net.minecraft.entity.EquipmentSlot slot) {
        setAllBonesVisible(false);

        switch (slot) {
            case HEAD -> setBoneVisible(getHeadBone(), true);
            case CHEST -> {
                setBoneVisible(getBodyBone(), true);
                setBoneVisible(getRightArmBone(), true);
                setBoneVisible(getLeftArmBone(), true);
            }
            case LEGS -> {
                // 护腿自带腰甲，腰甲挂在 armorBody 上，一并点亮
                setBoneVisible(getBodyBone(), true);
                setBoneVisible(getRightLegBone(), true);
                setBoneVisible(getLeftLegBone(), true);
            }
            case FEET -> {
                setBoneVisible(getRightBootBone(), true);
                setBoneVisible(getLeftBootBone(), true);
            }
            default -> {
            }
        }
    }

    @Override
    public void preRender(MatrixStack poseStack, T animatable, BakedGeoModel model,
                          VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        if (dumped) {
            return;
        }
        dumped = true;

        StringBuilder sb = new StringBuilder("[WSARMOR] ").append(getClass().getSimpleName());
        sb.append(" entity=").append(getCurrentEntity());
        sb.append(" slot=").append(getCurrentSlot());
        sb.append(" stack=").append(getCurrentStack());
        sb.append(" head=").append(getHeadBone());
        sb.append(" body=").append(getBodyBone());
        sb.append(" origin=(")
                .append(poseStack.peek().getPositionMatrix().m30()).append(',')
                .append(poseStack.peek().getPositionMatrix().m31()).append(',')
                .append(poseStack.peek().getPositionMatrix().m32()).append(')');
        sb.append(" scale=(")
                .append(poseStack.peek().getPositionMatrix().m00()).append(',')
                .append(poseStack.peek().getPositionMatrix().m11()).append(')');
        for (GeoBone bone : model.topLevelBones()) {
            dumpBone(bone, sb, 1);
        }
        System.out.println(sb);
    }

    private static void dumpBone(GeoBone bone, StringBuilder sb, int depth) {
        sb.append('\n').append("  ".repeat(depth)).append(bone.getName())
                .append(" pos=(").append(bone.getPosX()).append(',').append(bone.getPosY())
                .append(',').append(bone.getPosZ()).append(')')
                .append(" pivot=(").append(bone.getPivotX()).append(',').append(bone.getPivotY())
                .append(',').append(bone.getPivotZ()).append(')')
                .append(" cubes=").append(bone.getCubes().size())
                .append(" hidden=").append(bone.isHidden())
                .append(" hideChildren=").append(bone.isHidingChildren());
        for (GeoBone child : bone.getChildBones()) {
            dumpBone(child, sb, depth + 1);
        }
    }
}
