package org.examplee.wangsword.item.unique;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 迅捷骑枪（ws_lance）——参考新版本 Minecraft 的"动能武器"（KineticWeapon）。
 *
 * <p>右键机制：
 * <ul>
 *   <li>按住右键 → 持续向前突进（冲刺感）</li>
 *   <li>按住 0.4 秒后开始判定，沿视线一条长线命中敌人</li>
 *   <li>每 8 tick 最多命中一次</li>
 *   <li>伤害 = 攻击力 × 1.6；需要移动速度达标</li>
 * </ul>
 *
 * <p>X 键终极技「骑士冲锋·破阵之枪」：锁定视线方向的超远冲锋，
 * 沿途犁开地表，撞到第一个敌人时爆发冲击波，把敌人挑飞并给周围施加虚弱/缓慢；
 * 对 BOSS 额外定身。
 */
public class LanceItem extends SwordItem implements org.examplee.wangsword.item.ISkillWeapon {

    /** 冲锋速度（格/tick） */
    private static final double CHARGE_SPEED = 1.0;
    /** 冲锋最长持续（tick）：1.5 秒 */
    private static final int CHARGE_TICKS = 30;
    /** 冲锋撞击伤害倍率 */
    private static final float CHARGE_DAMAGE_MULT = 2.2F;
    /** 技能冷却（tick）：15 秒 */
    public static final int ULT_COOLDOWN = 300;

    /**
     * 技能 ID：X 键骑士冲锋。
     *
     * <p>以前它占物品冷却槽，放完冲锋后右键「蓄力突进」15 秒内按不出来。
     * 拆成技能冷却后，右键那条（`use` 里检查物品冷却 + 松手设 10 tick 防抖）保持原样即可。
     */
    public static final String SKILL_CHARGE = "lance_charge";

    /** 正在冲锋的玩家：UUID -> 剩余 tick */
    private static final Map<UUID, Integer> CHARGING = new HashMap<>();

    /** 冲锋状态机，由主类每 tick 调用 */
    public static void tickCharges(net.minecraft.server.MinecraftServer server) {
        if (CHARGING.isEmpty()) return;

        var it = CHARGING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

            if (player == null || player.isRemoved() || player.isDead()) {
                it.remove();
                continue;
            }

            int left = entry.getValue();
            if (left <= 0) {
                it.remove();
                continue;
            }

            ServerWorld world = (ServerWorld) player.getWorld();
            Vec3d dir = player.getRotationVector().normalize();

            // 强制覆盖速度：既保持冲锋方向，也顺便免疫击退
            player.setVelocity(dir.x * CHARGE_SPEED, player.getVelocity().y, dir.z * CHARGE_SPEED);
            player.velocityModified = true;

            // 犁地
            plowGround(world, player);

            // 撞击判定
            if (tryImpact(world, player, dir)) {
                it.remove();
                continue;
            }

            entry.setValue(left - 1);
        }
    }

    /** 犁开脚下的松软方块并喷泥土粒子 */
    private static void plowGround(ServerWorld world, PlayerEntity player) {
        int budget = 6;
        for (int dx = -1; dx <= 1 && budget > 0; dx++) {
            for (int dz = -1; dz <= 1 && budget > 0; dz++) {
                BlockPos pos = BlockPos.ofFloored(player.getX() + dx, player.getY() - 0.2, player.getZ() + dz);
                BlockState state = world.getBlockState(pos);

                if (state.isAir()) continue;
                if (state.getHardness(world, pos) < 0) continue;
                if (state.getBlock().getBlastResistance() > 30.0F) continue; // 石头以上不犁
                if (state.getBlock().getHardness() > 1.0F) continue;          // 只犁软方块

                world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(
                                net.minecraft.particle.ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        8, 0.3, 0.2, 0.3, 0.05);

                world.breakBlock(pos, false, player);
                budget--;
            }
        }
    }

    /** 撞击前方敌人：造成伤害、挑飞、给周围上负面效果。返回是否撞到 */
    private static boolean tryImpact(ServerWorld world, PlayerEntity player, Vec3d dir) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(dir.multiply(2.6));
        Box box = new Box(start, end).expand(1.2);

        for (Entity entity : world.getOtherEntities(player, box)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 2.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            if (target.getBoundingBox().expand(0.4).raycast(start, end).isEmpty()) continue;

            float damage = (float) (player.getAttributeValue(
                    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * CHARGE_DAMAGE_MULT)
                    + net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(
                            player.getMainHandStack(), player.getGroup());

            // 撞飞 + 挑起
            target.damage(world.getDamageSources().playerAttack(player), damage);
            target.setVelocity(0.0, 1.25, 0.0);
            target.velocityModified = true;

            // 周围 3x3 的敌人：缓慢 II + 虚弱 II
            Box aoe = target.getBoundingBox().expand(4.0);
            for (Entity near : world.getOtherEntities(player, aoe)) {
                if (!(near instanceof LivingEntity debuffed) || debuffed == player || debuffed == target) continue;
                debuffed.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, 120, 1, true, true), player);
                debuffed.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.WEAKNESS, 120, 1, true, true), player);
            }

            // BOSS（最大生命 >= 100）额外定身 2 秒
            if (target.getMaxHealth() >= 100.0F) {
                target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, 40, 6, true, true), player);
            }

            // 冲击波特效
            world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                    target.getX(), target.getY() + 0.5, target.getZ(), 3, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    target.getX(), target.getY() + 0.5, target.getZ(), 30, 1.2, 0.6, 1.2, 0.15);
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.0F, 0.8F);

            player.setVelocity(0.0, player.getVelocity().y, 0.0);
            player.velocityModified = true;
            return true;
        }
        return false;
    }


    /** 蓄力条满格时间（tick）：0.75 秒 */
    public static final int MAX_CHARGE = 15;
    /** 实际使用时长：和弓一样给个超大值，可以一直按住 */
    private static final int USE_DURATION = 72000;

    /** 按住多久后才开始判定（tick）：0.4 秒 */
    private static final int DELAY_TICKS = 8;
    /** 两次命中之间的最小间隔（tick） */
    private static final int CONTACT_COOLDOWN = 8;

    /** 突刺长度（格） */
    private static final double REACH = 4.5;
    /** 判定半宽（格） */
    private static final double HALF_WIDTH = 1.2;

    /** 伤害倍率：攻击力 × 该值 */
    private static final float DAMAGE_MULTIPLIER = 1.6F;

    /** 造成伤害所需的最低水平速度（格/tick）——放得很宽松，走路就够 */
    private static final double MIN_SPEED = 0.04;
    /** 触发击退所需的最低水平速度 */
    private static final double KNOCKBACK_SPEED = 0.12;

    /** 突进速度（格/tick）：直接覆盖水平速度，0.55 比疾跑（0.28）快一倍 */
    private static final double LUNGE_SPEED = 0.55;

    /** 每个玩家上次命中的游戏刻 */
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    public LanceItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        if (!world.isClient()) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_TRIDENT_RETURN, user.getSoundCategory(), 0.5F, 1.4F);
        }
        return TypedActionResult.consume(stack);
    }

    /** 按住期间：突进 + 沿线命中判定 */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);
        if (world.isClient() || !(user instanceof PlayerEntity player)) return;

        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        if (used < 1) return;

        Vec3d look = player.getRotationVector().normalize();

        // 突进：直接覆盖水平速度，不会被摩擦吃掉
        Vec3d current = player.getVelocity();
        player.setVelocity(look.x * LUNGE_SPEED, current.y, look.z * LUNGE_SPEED);
        player.velocityModified = true;

        if (used < DELAY_TICKS) return;

        // 命中间隔
        long now = world.getTime();
        Long last = LAST_HIT.get(player.getUuid());
        if (last != null && now - last < CONTACT_COOLDOWN) return;

        // 必须动起来才有伤害（宽松版）
        Vec3d vel = player.getVelocity();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (speed < MIN_SPEED) return;

        Vec3d start = player.getEyePos();
        Vec3d end = start.add(look.multiply(REACH));
        Box hitBox = new Box(start, end).expand(HALF_WIDTH);

        boolean hitAny = false;
        for (Entity entity : world.getOtherEntities(player, hitBox)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 2.0F);
                continue;
            }
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            if (target.getBoundingBox().expand(0.4).raycast(start, end).isEmpty()) continue;

            float damage = (float) (player.getAttributeValue(
                    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * DAMAGE_MULTIPLIER)
                    + net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(stack, player.getGroup());
            target.damage(world.getDamageSources().playerAttack(player), damage);

            if (speed >= KNOCKBACK_SPEED) {
                target.addVelocity(look.x * 0.9, 0.25, look.z * 0.9);
                target.velocityModified = true;
            }
            hitAny = true;
        }

        if (!hitAny) return;

        LAST_HIT.put(player.getUuid(), now);
        stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_3, player.getSoundCategory(), 1.0F, 1.0F);
    }

    /**
     * X 键终极技：骑士冲锋·破阵之枪。
     * 记录冲锋状态，由主类每 tick 推进（突进、犁地、撞击判定）。
     */
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof LanceItem)) return;
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_CHARGE)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_CHARGE, "骑士冲锋");
            return;
        }
        if (CHARGING.containsKey(player.getUuid())) return;

        CHARGING.put(player.getUuid(), CHARGE_TICKS);

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d dir = player.getRotationVector().normalize();

        player.setVelocity(dir.x * CHARGE_SPEED, player.getVelocity().y, dir.z * CHARGE_SPEED);
        player.velocityModified = true;

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_3, player.getSoundCategory(), 1.0F, 0.6F);

        stack.damage(8, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_CHARGE, ULT_COOLDOWN);
    }
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof PlayerEntity player)) return;
        player.getItemCooldownManager().set(this, 10);
    }

    /**
     * 第一次进入背包时，自动附带锋利 V。
     * 用 NBT 标记保证只执行一次——之后玩家用砂轮洗掉，不会再被加回来。
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient()) return;

        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.getBoolean("ws_lance_init")) return;
        nbt.putBoolean("ws_lance_init", true);

        if (stack.hasEnchantments()) return;

        stack.addEnchantment(Enchantments.SHARPNESS, 5);
    }
}
