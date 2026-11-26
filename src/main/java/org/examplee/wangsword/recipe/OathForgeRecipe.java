package org.examplee.wangsword.recipe;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.examplee.wangsword.item.unique.SwordTombGreatswordItem;
import org.examplee.wangsword.item.unique.SwordTombOathItem;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 守誓锻造：上下左右各一枚守卫核心围住一把 Pro Max，锻出 {@code ws_sword_tomb_oath}。
 *
 * <p>为什么不用普通 {@code crafting_shaped}：原版配方给的是全新物品，
 * 玩家拿去合的那把 Pro Max 大概率已经满附魔、改过名字、打剩半条耐久，
 * 走普通配方等于把这一整把剑当材料烧掉。这里自己拼产物：
 * 附魔照抄、自定义名照抄、剩余耐久照抄，葬魂层数保留但压到新上限。
 *
 * <p>配方书里不显示（{@link SpecialCraftingRecipe#isIgnoredInRecipeBook()} 恒真），
 * 想锻就得先知道十字摆了四枚核心这件事——跟成就线"不剧透配方"的口径一致。
 */
public class OathForgeRecipe extends SpecialCraftingRecipe {

    /** 上下左右四个格 */
    private static final int[] CORE_SLOTS = {1, 3, 5, 7};
    /** 四个角：必须空着 */
    private static final int[] CORNER_SLOTS = {0, 2, 6, 8};

    public OathForgeRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        if (inventory.size() != 9) {
            return false;
        }

        ItemStack center = inventory.getStack(4);
        if (!(center.getItem() instanceof SwordTombGreatswordItem sword)
                || sword.getTier() != SwordTombGreatswordItem.MAX_TIER) {
            return false;
        }

        // 上下左右各一枚核心
        for (int slot : CORE_SLOTS) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || stack.getItem() != ItemsRegistry.WS_GUARDIANCORE) {
                return false;
            }
        }
        // 四角必须空着，别让随便塞的东西也凑成这一格
        for (int slot : CORNER_SLOTS) {
            if (!inventory.getStack(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack blade = inventory.getStack(4);
        if (!(blade.getItem() instanceof SwordTombGreatswordItem sword)
                || sword.getTier() != SwordTombGreatswordItem.MAX_TIER) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(ItemsRegistry.WS_SWORD_TOMB_OATH);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 1. 附魔原样搬过去
        EnchantmentHelper.get(blade).forEach(result::addEnchantment);

        // 2. 自定义名保留
        if (blade.hasCustomName()) {
            result.setCustomName(blade.getName());
        }

        // 3. 剩余耐久照抄（两把剑同一档上限，直接按剩余量算回去）
        if (result.isDamageable() && blade.isDamageable()) {
            int remaining = Math.max(0, blade.getMaxDamage() - blade.getDamage());
            result.setDamage(Math.max(0, result.getMaxDamage() - remaining));
        }

        // 4. 葬魂层数：保留，但压到第五阶的上限
        int soul = blade.getNbt() == null ? 0 : blade.getNbt().getInt(SwordTombGreatswordItem.NBT_SOUL);
        if (soul > 0) {
            int cap = 6 + SwordTombOathItem.OATH_TIER * 2;
            result.getOrCreateNbt().putInt(SwordTombGreatswordItem.NBT_SOUL, Math.min(soul, cap));
        }

        return result;
    }

    /** 必须占满整个 3x3：十字摆四枚核心就是这个形状的意义，少一格都不算 */
    @Override
    public boolean fits(int width, int height) {
        return width == 3 && height == 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    /** 只有一个 type 字段，读写都无需额外参数 */
    public static class Serializer implements RecipeSerializer<OathForgeRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public OathForgeRecipe read(Identifier id, JsonObject json) {
            return new OathForgeRecipe(id, CraftingRecipeCategory.EQUIPMENT);
        }

        @Override
        public OathForgeRecipe read(Identifier id, PacketByteBuf buf) {
            return new OathForgeRecipe(id, buf.readEnumConstant(CraftingRecipeCategory.class));
        }

        @Override
        public void write(PacketByteBuf buf, OathForgeRecipe recipe) {
            buf.writeEnumConstant(recipe.getCategory());
        }
    }
}
