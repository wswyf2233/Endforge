package org.examplee.wangsword.Material;

import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public class BowMaterial implements ToolMaterial {
    // 单例，和三叉戟一样
    public static final BowMaterial INSTANCE = new BowMaterial();

    @Override
    public int getDurability() {
        return 5000; // 弓箭耐久度
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 1.0F; // 凑数用的，因为接口强制要求写，弓用不到挖掘
    }

    @Override
    public float getAttackDamage() {
        return 1.0F; // 拿弓近战敲人的伤害（原版弓是1.0）
    }

    @Override
    public int getMiningLevel() {
        return 0; // 凑数用的，弓不能挖矿
    }

    @Override
    public int getEnchantability() {
        return 55; // 附魔能力
    }
    public double getProjectileDamage() {
        return 15.0;
    }
    public double getChargedProjectileDamage() {
        return 30.0; // 3秒蓄力后的伤害，比普通射箭高
    }
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.GOLD_BLOCK); // 修理材料：金块
    }
}