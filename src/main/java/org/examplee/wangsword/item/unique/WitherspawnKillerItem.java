package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 凋灵杀手：对凋灵 / 凋灵骷髅造成双倍伤害，并且持有者免疫凋零效果伤害。
 *
 * <p>免疫部分由 {@code Wangsword} 里的 ALLOW_DAMAGE 事件处理。
 */
public class WitherspawnKillerItem extends SwordItem {

    public WitherspawnKillerItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient()) {
            return result;
        }
        if (!isWitherTarget(target)) {
            return result;
        }

        // 双倍伤害由 WitherDoubleDamageMixin 在伤害结算前乘算，这里只做特效
        // 牵引：命中后 2 秒内把凋灵拉向玩家
        if (target instanceof WitherEntity wither
                && attacker instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            org.examplee.wangsword.util.WitherTetherHandler.mark(wither, serverPlayer);
        }
        if (attacker.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SMOKE,
                    target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        return result;
    }

    private static boolean isWitherTarget(LivingEntity entity) {
        return entity instanceof WitherEntity || entity instanceof WitherSkeletonEntity;
    }

    // ---------------------------------------------------------------- 右键：飞剑

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world, PlayerEntity user,
                                                               net.minecraft.util.Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != net.minecraft.util.Hand.MAIN_HAND) {
            return net.minecraft.util.TypedActionResult.pass(stack);
        }
        if (world.isClient()) {
            return net.minecraft.util.TypedActionResult.success(stack);
        }

        net.minecraft.util.math.Vec3d eye = user.getEyePos();
        net.minecraft.util.math.Vec3d dir = user.getRotationVec(1.0F).normalize();
        float damage = (float) user.getAttributeValue(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);

        // 发射一把飞剑：贴图就是手里这把剑自己
        org.examplee.wangsword.entity.FlyingSwordEntity sword =
                new org.examplee.wangsword.entity.FlyingSwordEntity(
                        org.examplee.wangsword.registry.EntityRegistry.FLYING_SWORD,
                        (ServerWorld) world, user, stack.copy(), damage, dir.multiply(1.4D));
        sword.setPosition(eye.add(dir.multiply(1.2D)));
        world.spawnEntity(sword);

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.2F);
        }

        stack.damage(1, user, p -> p.sendEquipmentBreakStatus(net.minecraft.entity.EquipmentSlot.MAINHAND));
        return net.minecraft.util.TypedActionResult.success(stack);
    }
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.title").formatted(Formatting.DARK_AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.stats").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.immune").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.double").formatted(Formatting.DARK_AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.slash").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.witherspawn_killer.slash_detail").formatted(Formatting.GRAY));
    }
}