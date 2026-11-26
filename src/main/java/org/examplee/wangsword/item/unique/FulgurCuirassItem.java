package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 幽卫钢胸甲：击败守卫后可制作的 3D 胸甲。
 *
 * <p>数值跟赤金胸甲完全一致（材质用 {@code RedGoldArmorMaterial}）。
 * 套装技能见 {@code FulgurSetHandler}，四件共一份说明。
 *
 * <p>模型层级 {@code bipedBody → armorBody}、{@code bipedRightArm/LeftArm → armorRightArm/LeftArm}，
 * 胸甲槽会同时点亮身体和两条手臂。
 */
public class FulgurCuirassItem extends FulgurSetArmorItem {

    public FulgurCuirassItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.FulgurCuirassRenderer();
    }

    /** 没有 walk 动画，移动时继续播 idle */
    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
