package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import net.minecraft.text.Text;
import org.examplee.wangsword.util.FulgurSetHandler;

import java.util.List;

/**
 * 幽卫钢套四件的公共父类：只负责把套装说明写进物品介绍，
 * 文案集中在 {@link FulgurSetHandler#appendTooltip(List)}。
 */
public abstract class FulgurSetArmorItem extends WsGeoArmorItem {

    protected FulgurSetArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected void appendSetTooltip(List<Text> tooltip) {
        FulgurSetHandler.appendTooltip(tooltip);
    }
}
