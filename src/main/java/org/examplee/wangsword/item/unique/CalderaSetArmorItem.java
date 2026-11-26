package org.examplee.wangsword.item.unique;

import net.minecraft.item.ArmorMaterial;
import net.minecraft.text.Text;
import org.examplee.wangsword.util.MoltenSetHandler;

import java.util.List;

/**
 * 熔岩套（战盔 / 胸甲 / 护腿 / 战靴）的公共父类。
 *
 * <p>只做一件事：把「熔核层 + 熔岩喷发」的说明写进物品介绍，四件共用
 * {@link MoltenSetHandler#appendTooltip(List)}，改数值不用碰四份文案。
 */
public abstract class CalderaSetArmorItem extends WsGeoArmorItem {

    protected CalderaSetArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    protected void appendSetTooltip(List<Text> tooltip) {
        MoltenSetHandler.appendTooltip(tooltip);
    }
}
