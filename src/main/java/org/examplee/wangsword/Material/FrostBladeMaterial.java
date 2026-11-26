package org.examplee.wangsword.Material;

import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/**
 * 冰霜之刃的材质：前期过渡档，刚好压过钻石剑一头。
 *
 * <pre>
 *          冰霜之刃   钻石剑   铁剑
 * 耐久      1200      1561    250
 * 挖速      7.5       8.0     6.0
 * 攻击加成  3.0       3.0     2.0
 * 附魔      12        10      14
 * 挖掘等级  3         3       2
 * </pre>
 *
 * <p>追加伤害给到 4（总伤 8），攻速保持钻石剑的 1.6：它是"比钻石剑好用一点点的前期武器"，
 * 真正的强度来自命中附带的冰封，而不是面板。
 */
public class FrostBladeMaterial implements ToolMaterial {

    public static final FrostBladeMaterial INSTANCE = new FrostBladeMaterial();

    @Override
    public int getDurability() {
        return 1200;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 7.5F;
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
        return 12;
    }

    /** 修复材料：蓝冰或浮冰，跟配方同源 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.BLUE_ICE, Items.PACKED_ICE);
    }
}
