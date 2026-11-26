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
 * 紫金盔甲。
 *
 * <p>第一次进入背包时自动附魔"保护 V"（原版上限是 IV）。
 * 用 NBT 标记 wsarmor_init 保证只执行一次，之后玩家用砂轮洗掉也不会再被加回来。
 *
 * <p>防火由套装被动（穿齐四件给防火 buff）提供。
 */
public class AmethystArmorItem extends ArmorItem {

    /** 保护等级（5 = 保护 V） */
    private static final int PROTECTION_LEVEL = 5;
    /** 只执行一次的标记 */
    private static final String INIT_FLAG = "wsarmor_init";

    public AmethystArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.part").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.protection").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.knockback").formatted(Formatting.GRAY));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.fire").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.full_knockback").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.elytra").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.wsarmor.boost").formatted(Formatting.GRAY));

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

    /** 添加附魔，已存在就跳过 */
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