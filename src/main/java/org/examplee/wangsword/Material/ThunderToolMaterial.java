package org.examplee.wangsword.Material;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 雷电太刀材质：紫金之上的后期材质，靠雷电宝石把刀身「充电」。
 *
 * <pre>
 *          雷电太刀   末地合金   紫金
 * 耐久      4200      4000     3600
 * 挖速      22.0      20.0     18.0
 * 攻击加成  12.0      12.0     10.0
 * 附魔      34        30       25
 * 挖掘等级  4         4        4
 * </pre>
 *
 * <p>攻击加成没再往上堆，而是把预算挪到攻速上——太刀靠出刀快，不靠单下重。
 */
public class ThunderToolMaterial implements ToolMaterial {

    public static final ThunderToolMaterial INSTANCE = new ThunderToolMaterial();

    @Override
    public int getDurability() {
        return 4200;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 22.0F;
    }

    @Override
    public float getAttackDamage() {
        return 12.0F;
    }

    @Override
    public int getMiningLevel() {
        return 4;
    }

    @Override
    public int getEnchantability() {
        return 34;
    }

    /** 修复材料：雷电宝石（和紫金用紫水晶、末地合金用末地合金锭一个道理） */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_LIGHTNINGSTONE);
    }
}
