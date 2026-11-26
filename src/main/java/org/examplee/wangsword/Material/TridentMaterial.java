package org.examplee.wangsword.Material;



import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public class TridentMaterial implements ToolMaterial {
    public static final TridentMaterial INSTANCE = new TridentMaterial();

    @Override
    public int getDurability() { return 5000; }
    @Override
    public float getMiningSpeedMultiplier() { return 1.0F; }
    @Override
    public float getAttackDamage() { return 11.0F; }
    @Override
    public int getMiningLevel() { return 3; }
    @Override
    public int getEnchantability() { return 20; }
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.PRISMARINE_SHARD);
    }
}