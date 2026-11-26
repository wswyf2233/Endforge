package org.examplee.wangsword.Material;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 末地合金工具材质。
 *
 * <p>比紫金更强一档：
 * <pre>
 *          末地合金   紫金
 * 耐久      4000     3600
 * 挖速      20.0     18.0
 * 攻击加成  12.0     10.0
 * 附魔      30       25
 * 挖掘等级  4        4
 * </pre>
 */
public class TaiguToolMaterial implements ToolMaterial {

    public static final TaiguToolMaterial INSTANCE = new TaiguToolMaterial();

    @Override
    public int getDurability() {
        return 4000;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 20.0F;
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
        return 30;
    }

    /** 修复材料：末地合金锭 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_TAIGU_INGOT);
    }
}