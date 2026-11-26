package org.examplee.wangsword.Material;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 赤金工具材质。
 *
 * <p>比下界合金高一点：
 * <pre>
 *          赤金    下界合金
 * 耐久     2500    2031
 * 挖速     10.0    9.0
 * 攻击加成 4.5     4.0
 * 附魔     20      15
 * 挖掘等级 4       4
 * </pre>
 */
public class RedGoldToolMaterial implements ToolMaterial {

    public static final RedGoldToolMaterial INSTANCE = new RedGoldToolMaterial();

    @Override
    public int getDurability() {
        return 2500;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 10.0F;
    }

    @Override
    public float getAttackDamage() {
        return 7F;
    }

    @Override
    public int getMiningLevel() {
        return 4;
    }

    @Override
    public int getEnchantability() {
        return 20;
    }

    /** 修复材料：赤金锭 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_REDGOLD_INGOT);
    }
}
