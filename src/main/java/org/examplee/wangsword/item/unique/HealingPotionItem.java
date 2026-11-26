package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * 恢复药：喝下回复最大生命值的一定比例，喝掉后返还一个玻璃瓶。
 *
 * <p>回复量按 {@link LivingEntity#getMaxHealth()} 算，不是固定数值，
 * 所以穿模组盔甲、吃加血上限的东西之后依然按比例走。
 * 普通版 30%、大回复药 50%，比例由构造传入。
 * 只回血，不给任何状态效果。
 */
public class HealingPotionItem extends Item {

    /** 普通恢复药的比例 */
    public static final float HEAL_RATIO = 0.30F;

    /** 饮用耗时（tick），跟原版药水一致 */
    private static final int USE_TICKS = 32;

    private final float healRatio;

    public HealingPotionItem(Settings settings) {
        this(settings, HEAL_RATIO);
    }

    public HealingPotionItem(Settings settings, float healRatio) {
        super(settings);
        this.healRatio = healRatio;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            if (!world.isClient()) {
                player.heal(player.getMaxHealth() * this.healRatio);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS,
                        0.6F, 0.9F + world.random.nextFloat() * 0.2F);
            }

            // 创造模式不扣物品，也不吐瓶子
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, java.util.List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.healing_potion.desc",
                (int) (this.healRatio * 100)));
    }
}
