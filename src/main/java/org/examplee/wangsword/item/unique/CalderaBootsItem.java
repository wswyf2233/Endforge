package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 熔岩战靴：3D 模型靴子，数值跟紫金靴子一致，不带技能。
 *
 * <p>没有 walk 动画，移动时也播 idle。
 *
 * <p>模型层级 {@code bipedRightLeg/LeftLeg → armorRightBoot/LeftBoot}，
 * FEET 槽点亮的是两条 boot 骨骼。
 */
public class CalderaBootsItem extends CalderaSetArmorItem {

    public CalderaBootsItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.CalderaBootsRenderer();
    }

    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
