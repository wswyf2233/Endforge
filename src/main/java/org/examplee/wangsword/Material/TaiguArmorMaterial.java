package org.examplee.wangsword.Material;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 末地合金盔甲材质。
 *
 * <p>整体高于紫金套：
 * <pre>
 *              末地合金   紫金
 * 护甲值       12/24/12/12   10/20/10/10
 * 韧性         5.0           4.0
 * 击退抗性     0.35          0.25
 * 附魔能力     30            25
 * 耐久系数     50/66         40/56
 * </pre>
 *
 * <p>getName() 不带命名空间，原版拼成
 * {@code minecraft:textures/models/armor/ws_taiguarmor_layer_1.png}。
 */
public class TaiguArmorMaterial implements ArmorMaterial {

    public static final TaiguArmorMaterial INSTANCE = new TaiguArmorMaterial();

    public static final String NAME = "ws_taiguarmor";

    @Override
    public int getDurability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 50 * 150;
            case CHESTPLATE -> 50 * 220;
            case LEGGINGS -> 50 * 150;
            case BOOTS -> 50 * 150;
        };
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 12;
            case CHESTPLATE -> 24;
            case LEGGINGS -> 12;
            case BOOTS -> 12;
        };
    }

    @Override
    public int getEnchantability() {
        return 30;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
    }

    /** 修复材料：末地合金锭 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_TAIGU_INGOT);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public float getToughness() {
        return 5.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.35F;
    }
}
