package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 幽卫钢战盔：击败守卫后可制作的 3D 头盔。
 *
 * <p>数值跟赤金头盔完全一致（材质直接用 {@code RedGoldArmorMaterial}）。
 * 套装技能见 {@code FulgurSetHandler}，四件共一份说明。
 *
 * <p>模型层级同样是 {@code bipedHead → armorHead → shell/crest/visor/...}：
 * 外面那层空骨骼负责托住 24 像素的头部高度，GeckoLib 只认 armorHead 这个名字。
 */
public class FulgurHelmItem extends FulgurSetArmorItem {

    public FulgurHelmItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.FulgurHelmRenderer();
    }

    /** 这套没做 walk 动画，移动时继续播 idle，省得 GeckoLib 每帧刷找不到动画 */
    @Override
    protected RawAnimation walkAnimation() {
        return idleAnimation();
    }
}
