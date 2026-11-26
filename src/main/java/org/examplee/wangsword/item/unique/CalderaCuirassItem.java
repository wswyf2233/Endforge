package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 熔岩胸甲：3D 模型胸甲，数值跟紫金胸甲一致，不带技能。
 *
 * <p>模型层级 {@code bipedBody / bipedRightArm / bipedLeftArm → armorBody / armorRightArm /
 * armorLeftArm → 各部件}，外面的 biped* 是 GeckoLib 覆盖骨骼位置时的定位壳，别拆。
 */
public class CalderaCuirassItem extends CalderaSetArmorItem {

    public CalderaCuirassItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.CalderaCuirassRenderer();
    }
}
