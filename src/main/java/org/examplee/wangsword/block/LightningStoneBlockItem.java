package org.examplee.wangsword.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 未激活的雷电宝石（可放置）。
 *
 * <p>激活途径 A：拿它右键<b>高压苦力怕</b>，宝石被充能，苦力怕失去充能。
 */
public class LightningStoneBlockItem extends BlockItem {

    public LightningStoneBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        // 1.20.1 的 Yarn 没有公开的 isCharged()，shouldRenderOverlay() 读的就是 CHARGED 标记
        if (entity instanceof CreeperEntity creeper && creeper.shouldRenderOverlay()) {
            if (!entity.getWorld().isClient()) {
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                ItemStack activated = new ItemStack(ItemsRegistry.WS_LIGHTNINGSTONE);
                if (!user.getInventory().insertStack(activated)) {
                    user.dropItem(activated, false);
                }
                // 苦力怕的充能标记没有公开的 setter，直接消耗掉它，避免在玩家身边爆炸
                if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                    serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                            entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                            40, 0.4, 0.6, 0.4, 0.2);
                }
                creeper.discard();
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.NEUTRAL, 0.8F, 1.0F);
            }
            return ActionResult.success(entity.getWorld().isClient());
        }
        return super.useOnEntity(stack, user, entity, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.lightning_stone.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.lightning_stone.storm"));
        tooltip.add(Text.translatable("tooltip.wangsword.lightning_stone.creeper"));
        tooltip.add(Text.translatable("tooltip.wangsword.lightning_stone.hold"));
        tooltip.add(Text.translatable("tooltip.wangsword.lightning_stone.place"));
    }
}
