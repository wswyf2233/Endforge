package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 幽卫钢战靴：守卫战利品打造的 3D 战靴。
 *
 * <p>数值跟赤金靴子一致（材质用 {@code RedGoldArmorMaterial}），套装技能见
 * {@code FulgurSetHandler}。
 *
 * <p>模型层级 {@code bipedRightLeg → armorRightLeg(空) + armorRightBoot}，
 * 左边同理。那两个空的腿骨骼不是摆设：GeckoLib 的 {@code applyBaseTransformations}
 * 把靴子的旋转写在 {@code if (rightLeg != null)} 分支里，少了它靴子不会跟腿摆动。
 */
public class FulgurBootsItem extends FulgurSetArmorItem {

    public FulgurBootsItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.FulgurBootsRenderer();
    }

    /** 这套没做 walk 动画，移动时继续播 idle */
    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
