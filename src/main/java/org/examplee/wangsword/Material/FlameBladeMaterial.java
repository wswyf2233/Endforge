package org.examplee.wangsword.Material;

import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/**
 * 烈火大刀的材质：中期档，夹在铁与下界合金之间。
 *
 * <pre>
 *          烈火大刀   钻石   下界合金
 * 耐久      1520      1561    2031
 * 挖速      7.0       8.0     9.0
 * 攻击加成  3.0       3.0     4.0
 * 附魔      15        10      15
 * 挖掘等级  3         3       4
 * </pre>
 *
 * <p>耐久故意略低于钻石、附魔略高：它不是毕业装，是"中期拿来收藏、
 * 顺手能用"的那类武器，刀子本身还有追加伤害撑着。
 */
public class FlameBladeMaterial implements ToolMaterial {

    public static final FlameBladeMaterial INSTANCE = new FlameBladeMaterial();

    @Override
    public int getDurability() {
        return 1520;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 7.0F;
    }

    @Override
    public float getAttackDamage() {
        return 3.0F;
    }

    @Override
    public int getMiningLevel() {
        return 3;
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    /** 修复材料：铁锭或烈焰粉都能把刀身补回来 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.IRON_INGOT, Items.BLAZE_POWDER);
    }
}
