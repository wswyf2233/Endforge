package org.examplee.wangsword.item.unique;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.HammerShockwaveEntity;

/**
 * 巨锤（ws_greathammer）。
 *
 * <p>右键蓄力后砸地，产生环形冲击波：
 * <ul>
 *   <li>半径随蓄力 3 → 6 格</li>
 *   <li>伤害倍率随蓄力 1.0 → 1.8</li>
 *   <li>中心 40% 范围内伤害翻倍，并附带缓慢 + 虚弱（破甲感）</li>
 *   <li>不破坏方块，只有粒子和音效</li>
 * </ul>
 */
public class GreatHammerItem extends SwordItem implements org.examplee.wangsword.item.ISkillWeapon {

    /** X 键大招蓄满所需 tick（1.5 秒） */
    public static final int ULT_CHARGE_TICKS = 30;

    /** 蓄满所需 tick（1 秒） */
    public static final int MAX_CHARGE = 20;
    /** 最低蓄力 */
    private static final int MIN_CHARGE = 1;

    /** 冲击波半径范围 */
    private static final double MIN_RADIUS = 3.0;
    private static final double MAX_RADIUS = 6.0;

    /** 伤害倍率范围 */
    private static final float MIN_MULT = 1.0F;
    private static final float MAX_MULT = 1.8F;

    /** 基础冲击伤害（未乘倍率） */
    private static final float BASE_DAMAGE = 20.0F;

    /** 冷却（tick）：测试阶段设为 0，正式版建议 160~200 */
    private static final int COOLDOWN = 0;

    public GreatHammerItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    /** X 键：地震波 */
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GreatHammerItem)) return;

        ServerWorld world = (ServerWorld) player.getWorld();

        HammerShockwaveEntity wave = new HammerShockwaveEntity(
                org.examplee.wangsword.registry.EntityRegistry.HAMMER_SHOCKWAVE, world, player);
        wave.setPosition(player.getX(), player.getY(), player.getZ());
        world.spawnEntity(wave);

        // 砸地僵直
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 4, false, false), player);
    }
    /** 蓄力时做出"举起武器"的姿势，让玩家看到动作 */
    @Override
    public net.minecraft.util.UseAction getUseAction(ItemStack stack) {
        return net.minecraft.util.UseAction.SPEAR;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        if (!world.isClient()) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.5F, 0.8F);
        }
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    /** 蓄力过程中：脚下冒火星（不用白烟，避免遮挡视线） */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof PlayerEntity player)) return;

        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        if (used < 0 || used > MAX_CHARGE) return;
        if (used % 4 != 0) return;

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    2, 0.35, 0.05, 0.35, 0.01);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) return;

        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        if (used < MIN_CHARGE) return;

        float ratio = Math.min(1.0F, used / (float) MAX_CHARGE);
        slam(player, (ServerWorld) world, ratio);

        if (COOLDOWN > 0) {
            player.getItemCooldownManager().set(this, COOLDOWN);
        }
    }

    /** 砸地：环形冲击波 */
    private void slam(ServerPlayerEntity player, ServerWorld world, float ratio) {
        double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * ratio;
        float mult = MIN_MULT + (MAX_MULT - MIN_MULT) * ratio;
        double inner = radius * 0.4;

        Vec3d center = player.getPos();

        // ---------- 粒子：地面冲击环 ----------
        int ringPoints = 56;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (Math.PI * 2.0) * i / ringPoints;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.FLAME, x, center.y + 0.25, z, 2, 0.08, 0.12, 0.08, 0.01);
        }

        // 中心火焰（不用白烟，避免挡住特效）
        world.spawnParticles(ParticleTypes.FLAME,
                center.x, center.y + 0.2, center.z,
                50, radius * 0.5, 0.2, radius * 0.5, 0.03);
        world.spawnParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.3, center.z,
                12, inner * 0.8, 0.1, inner * 0.8, 0.0);

        // ---------- 伤害 ----------
        Box area = new Box(
                center.x - radius, center.y - 2.0, center.z - radius,
                center.x + radius, center.y + 3.0, center.z + radius);

        for (Entity entity : world.getOtherEntities(player, area)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 20.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;
            if (target == player) continue;

            double distance = target.getPos().distanceTo(center);
            if (distance > radius) continue;

            boolean inCore = distance <= inner;
            float damage = BASE_DAMAGE * mult * (inCore ? 2.0F : 1.0F);

            target.damage(world.getDamageSources().playerAttack(player), damage);

            // 击飞
            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4) {
                push = push.normalize().multiply(0.85);
                target.addVelocity(push.x, inCore ? 1.0 : 0.6, push.z);
                target.velocityModified = true;
            }

            // 中心区域：破甲（缓慢 + 虚弱）
            if (inCore) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 2), player);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 1), player);
            }
        }

        // ---------- 自身：砸地僵直 ----------
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 4, false, false), player);

        // ---------- 音效 ----------
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2F, 0.7F);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 0.6F);
    }
}
