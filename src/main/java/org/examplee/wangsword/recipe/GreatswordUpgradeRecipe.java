package org.examplee.wangsword.recipe;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.examplee.wangsword.item.unique.SwordTombGreatswordItem;

import java.util.HashMap;
import java.util.Map;

/**
 * 剑冢巨剑的升阶配方：两把同阶巨剑 -> 一把下一阶。
 *
 * <p>为什么不用普通 {@code crafting_shapeless}：原版普通配方产物是全新物品，
 * 两把剑的附魔、耐久、自定义名全会被抹掉，玩家拿一把满附魔的剑去升级等于自杀。
 * 这里走 {@link SpecialCraftingRecipe}，产物由 {@link #craft} 自己拼，
 * 附魔取两把的最高等级、剩余耐久相加、自定义名保留、葬魂层数取高。
 */
public class GreatswordUpgradeRecipe extends SpecialCraftingRecipe {

    public GreatswordUpgradeRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        ItemStack first = ItemStack.EMPTY;
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof SwordTombGreatswordItem sword)) {
                return false;
            }
            // 已经顶阶了就别再合，否则会刷出不存在的下一阶
            if (sword.getTier() >= SwordTombGreatswordItem.MAX_TIER) {
                return false;
            }
            if (count == 0) {
                first = stack;
            } else if (first.getItem() != stack.getItem()) {
                // 必须同阶同种，巨剑 + Plus 不算一对
                return false;
            }
            if (++count > 2) {
                return false;
            }
        }
        return count == 2;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else if (second.isEmpty()) {
                second = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (first.isEmpty() || second.isEmpty()
                || !(first.getItem() instanceof SwordTombGreatswordItem sword)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(upgradedItem(sword.getTier()));
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 1. 附魔：两把逐条取最高等级（锋利 V + 锋利 III = 锋利 V，不叠成 VIII）
        Map<Enchantment, Integer> merged = new HashMap<>(EnchantmentHelper.get(first));
        EnchantmentHelper.get(second).forEach((enchantment, level) ->
                merged.merge(enchantment, level, Math::max));

        // 2. 耐久：两把剩余耐久相加，但不能超过新阶级的上限
        int newMax = result.getMaxDamage();
        if (newMax > 0) {
            int remaining = Math.max(0, first.getMaxDamage() - first.getDamage())
                    + Math.max(0, second.getMaxDamage() - second.getDamage());
            result.setDamage(Math.max(0, newMax - Math.min(remaining, newMax)));
        }

        // 3. 自定义名：优先保留第一把的（铁砧命名出来的名字不该凭空消失）
        if (first.hasCustomName()) {
            Text name = first.getName();
            result.setCustomName(name);
        }

        // 4. 葬魂层数：取两把里高的那个，但不能超过新阶级上限
        int soul = Math.max(soulOf(first), soulOf(second));
        soul = Math.min(soul, 6 + sword.getTier() * 2 + 2);
        if (soul > 0) {
            result.getOrCreateNbt().putInt(SwordTombGreatswordItem.NBT_SOUL, soul);
        }

        // 附魔要在 NBT 其他操作之后写，避免被 getOrCreateNbt 的副作用覆盖
        merged.forEach((enchantment, level) -> result.addEnchantment(enchantment, level));

        return result;
    }

    private static int soulOf(ItemStack stack) {
        return stack.getNbt() == null ? 0 : stack.getNbt().getInt(SwordTombGreatswordItem.NBT_SOUL);
    }

    /** 阶数 -> 下一阶物品 */
    public static net.minecraft.item.Item upgradedItem(int tier) {
        return switch (tier) {
            case 0 -> org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PLUS;
            case 1 -> org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO;
            case 2 -> org.examplee.wangsword.registry.ItemsRegistry.WS_SWORD_TOMB_GREATSWORD_PRO_MAX;
            default -> net.minecraft.item.Items.AIR;
        };
    }

    @Override
    public boolean fits(int width, int height) {
        // 两把剑摆得下就行，横着竖着都算数
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    /** 只有一个 json 字段是 type，读的时候不需要额外参数 */
    public static class Serializer implements RecipeSerializer<GreatswordUpgradeRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public GreatswordUpgradeRecipe read(Identifier id, JsonObject json) {
            return new GreatswordUpgradeRecipe(id, CraftingRecipeCategory.EQUIPMENT);
        }

        @Override
        public GreatswordUpgradeRecipe read(Identifier id, PacketByteBuf buf) {
            return new GreatswordUpgradeRecipe(id, buf.readEnumConstant(CraftingRecipeCategory.class));
        }

        @Override
        public void write(PacketByteBuf buf, GreatswordUpgradeRecipe recipe) {
            buf.writeEnumConstant(recipe.getCategory());
        }
    }
}
