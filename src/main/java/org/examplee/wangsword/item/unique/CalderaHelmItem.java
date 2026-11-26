package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 熔岩战盔：3D 模型头盔，数值跟紫金头盔一致，不带技能。
 *
 * <p>模型层级必须是 {@code bipedHead → armorHead → 各部件}：GeckoLib 渲染时会用玩家头部
 * 的 pivot 覆盖 armorHead 的位置，外面那层 bipedHead 负责保留 24 像素的头部高度，
 * 少了它整个头盔会掉到脚底。
 */
public class CalderaHelmItem extends CalderaSetArmorItem {

    public CalderaHelmItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new org.examplee.wangsword.client.CalderaHelmRenderer();
    }
}
