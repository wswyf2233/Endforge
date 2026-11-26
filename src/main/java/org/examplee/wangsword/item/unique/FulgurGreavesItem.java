package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 幽卫钢护腿：守卫战利品打造的 3D 护腿。
 *
 * <p>数值跟赤金护腿一致（材质用 {@code RedGoldArmorMaterial}），套装技能见
 * {@code FulgurSetHandler}。
 *
 * <p>模型层级 {@code bipedBody → armorBody}、{@code bipedRightLeg/LeftLeg → armorRightLeg/LeftLeg}，
 * 腰甲挂在 armorBody 上，所以 LEGS 槽会连 body 一起点亮。
 */
public class FulgurGreavesItem extends FulgurSetArmorItem {

    public FulgurGreavesItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.FulgurGreavesRenderer();
    }

    /** 这套没做 walk 动画，移动时继续播 idle */
    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
