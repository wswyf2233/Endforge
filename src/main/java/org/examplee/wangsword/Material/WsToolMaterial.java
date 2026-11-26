package org.examplee.wangsword.Material;

import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/**
 * 紫金工具材质。
 *
 * <p>各项数值都比下界合金高一大截：
 * 下界合金 → 耐久 2031 / 挖速 9 / 攻击加成 4 / 附魔 15 / 挖掘等级 4
 */
public class WsToolMaterial implements ToolMaterial {

    public static final WsToolMaterial INSTANCE = new WsToolMaterial();

    @Override
    public int getDurability() {
        return 3600;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 18.0F;
    }

    @Override
    public float getAttackDamage() {
        return 10.0F;
    }

    @Override
    public int getMiningLevel() {
        return 4;
    }

    @Override
    public int getEnchantability() {
        return 25;
    }

    /** 用紫水晶碎片或紫水晶之星修理 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(
                Items.AMETHYST_SHARD,
                org.examplee.wangsword.registry.ItemsRegistry.WS_AMETHYST_CRYSTAL);
    }
}