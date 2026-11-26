package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 末地合金盔甲。
 *
 * <p>首次进入背包自动附魔“保护 VI”（比紫金套的 V 高一档），
 * 用 NBT 标记 taigu_init 保证只执行一次。
 * 套装被动（抗性 III、免摔落、创造飞行、濒死护盾）由 TaiguSetHandler 处理。
 */
public class TaiguArmorItem extends ArmorItem {

    private static final int PROTECTION_LEVEL = 6;
    private static final String INIT_FLAG = "taigu_init";

    public TaiguArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.part").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.protection").formatted(Formatting.GRAY));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.resistance").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.flight").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.fall").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu.shield").formatted(Formatting.GRAY));

        super.appendTooltip(stack, world, tooltip, context);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient()) return;

        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.getBoolean(INIT_FLAG)) return;
        nbt.putBoolean(INIT_FLAG, true);

        NbtList enchantments = nbt.getList(ItemStack.ENCHANTMENTS_KEY, 10);
        addEnchantment(enchantments, "minecraft:protection", PROTECTION_LEVEL);
        nbt.put(ItemStack.ENCHANTMENTS_KEY, enchantments);
    }

    private static void addEnchantment(NbtList list, String id, int level) {
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (id.equals(entry.getString("id"))) {
                return;
            }
        }
        NbtCompound enchantment = new NbtCompound();
        enchantment.putString("id", id);
        enchantment.putShort("lvl", (short) level);
        list.add(enchantment);
    }
}