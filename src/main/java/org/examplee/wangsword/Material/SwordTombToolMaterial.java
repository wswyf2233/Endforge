package org.examplee.wangsword.Material;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 剑冢巨剑的材质，按锻造阶数分四档。
 *
 * <p>定位：守卫掉落的整把大剑，比赤金剑更慢更重，单下更疼。
 * <pre>
 *          巨剑     Plus     Pro      Pro Max   赤金剑
 * 耐久     3200     4200     5400     7000      2500
 * 挖速     9.0      9.0      9.5      10.0      10.0
 * 附魔     22       24       26       28        20
 * </pre>
 * 攻击力不在这里加：四个阶级的额外伤害写在物品注册里（+6/+8/+10/+12），
 * 材质只负责耐久、挖速、附魔这三样"整体手感"。
 *
 * <p>修复材料用紫金水晶（守卫原本的掉落物），打完一场正好能修一点。
 */
public class SwordTombToolMaterial implements ToolMaterial {

    /** 一阶：守卫直接掉落的那把 */
    public static final SwordTombToolMaterial INSTANCE = new SwordTombToolMaterial(3200, 9.0F, 22);
    /** 二阶：两把一阶合成 */
    public static final SwordTombToolMaterial PLUS = new SwordTombToolMaterial(4200, 9.0F, 24);
    /** 三阶 */
    public static final SwordTombToolMaterial PRO = new SwordTombToolMaterial(5400, 9.5F, 26);
    /** 四阶：两把三阶合成 */
    public static final SwordTombToolMaterial PRO_MAX = new SwordTombToolMaterial(7000, 10.0F, 28);

    private final int durability;
    private final float miningSpeed;
    private final int enchantability;

    public SwordTombToolMaterial(int durability, float miningSpeed, int enchantability) {
        this.durability = durability;
        this.miningSpeed = miningSpeed;
        this.enchantability = enchantability;
    }

    @Override
    public int getDurability() {
        return durability;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return miningSpeed;
    }

    @Override
    public float getAttackDamage() {
        return 9.0F;
    }

    @Override
    public int getMiningLevel() {
        return 4;
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_AMETHYST_CRYSTAL);
    }
}
