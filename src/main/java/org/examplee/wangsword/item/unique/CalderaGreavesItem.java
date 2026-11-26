package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 熔岩护腿：3D 模型护腿，数值跟紫金护腿一致，不带技能。
 *
 * <p>这套模型没做 walk 动画，移动时也播 idle，避免 GeckoLib 刷 "Unable to find animation"。
 *
 * <p>模型层级 {@code bipedBody → armorBody}、{@code bipedRightLeg/LeftLeg → armorRightLeg/LeftLeg}，
 * 腰甲挂在 armorBody 上，所以 LEGS 槽会连 body 一起点亮。
 */
public class CalderaGreavesItem extends CalderaSetArmorItem {

    public CalderaGreavesItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.CalderaGreavesRenderer();
    }

    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
