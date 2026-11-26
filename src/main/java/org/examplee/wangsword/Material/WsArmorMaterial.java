package org.examplee.wangsword.Material;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * 紫金盔甲的材质。
 *
 * <p>注意 getName() 必须返回"没有命名空间"的纯名字：
 * 原版会拼成 textures/models/armor/<name>_layer_1.png 再转成 Identifier，
 * 带冒号会直接抛 InvalidIdentifierException。
 * 所以层级贴图要放在 assets/minecraft/textures/models/armor/ 下。
 */
public class WsArmorMaterial implements ArmorMaterial {

    public static final WsArmorMaterial INSTANCE = new WsArmorMaterial();

    /** 贴图名（对应 wsarmor_layer_1.png / wsarmor_layer_2.png） */
    public static final String NAME = "wsarmor";

    @Override
    public int getDurability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 40 * 150;
            case CHESTPLATE -> 40 * 220;
            case LEGGINGS -> 40 * 150;
            case BOOTS -> 40 * 150;
        };
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 10;
            case CHESTPLATE -> 20;
            case LEGGINGS -> 10;
            case BOOTS -> 10;
        };
    }

    @Override
    public int getEnchantability() {
        return 25;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
    }

    /** 修复材料：紫水晶碎片 或 紫水晶之星 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(
                Items.AMETHYST_SHARD,
                org.examplee.wangsword.registry.ItemsRegistry.WS_AMETHYST_CRYSTAL);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public float getToughness() {
        return 4.0F;
    }

    /** 每件 0.25，穿满四件 = 1.0，完全免疫击退 */
    @Override
    public float getKnockbackResistance() {
        return 0.25F;
    }
}