package org.examplee.wangsword.item.unique;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.util.SkillCooldowns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 血帆弯刀：死神系列的快刀。
 *
 * <p>被动「吸血」：每次命中回复面板攻击力的 20%（最低 0.5）。
 * <p>右键「血刃突袭」：朝准心方向短距突进，途中斩击经过的敌人并回血。
 * <p>X「血债」：12 秒血怒，攻速 +25%、吸血翻倍到 40%、每次命中额外 3 点魔法伤害。
 *
 * <p>突进要每 tick 重新推速度（玩家速度有阻力，给一次就衰减），所以状态放在静态表里，
 * 由 {@link #init()} 注册的服务端 tick 驱动，不占用实体。
 */
public class BloodsailCutlassItem extends SwordItem implements ISkillWeapon {

    // ---------------------------------------------------------------- 被动

    /** 普通吸血比例 */
    public static final float LIFESTEAL_RATIO = 0.20F;
    /** 单次吸血下限 */
    private static final float LIFESTEAL_MIN = 0.5F;

    // ---------------------------------------------------------------- 右键：血刃突袭

    public static final String SKILL_RUSH = "cutlass_rush";
    public static final int RUSH_COOLDOWN = 100;      // 5 秒
    /** 突进持续 tick */
    private static final int RUSH_TICKS = 5;
    /** 每 tick 推进距离（格） */
    private static final double RUSH_SPEED = 1.1D;
    /** 突进斩击伤害 */
    private static final float RUSH_DAMAGE = 8.0F;
    /** 每个目标额外回血 */
    private static final float RUSH_HEAL = 2.0F;

    // ---------------------------------------------------------------- X：血债

    public static final String SKILL_FURY = "cutlass_fury";
    public static final int FURY_COOLDOWN = 900;      // 45 秒
    /** 血怒持续 tick */
    private static final int FURY_DURATION = 240;     // 12 秒
    /** 血怒期间的吸血比例 */
    private static final float FURY_LIFESTEAL = 0.40F;
    /** 血怒期间的额外魔法伤害 */
    private static final float FURY_BONUS_DAMAGE = 3.0F;
    /** 血怒攻速加成（乘算） */
    private static final double FURY_SPEED_BONUS = 0.25D;
    /** 攻速修改器的固定 UUID，用来移除 */
    private static final UUID FURY_SPEED_UUID = UUID.fromString("6f1c9a3e-2b47-4c8a-9d51-3e0f7a2b6c14");

    /** 突进中的玩家 */
    private static final Map<UUID, Rush> RUSHING = new HashMap<>();
    /** 血怒中的玩家 → 结束时刻 */
    private static final Map<UUID, Long> FURY_UNTIL = new HashMap<>();

    public BloodsailCutlassItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    /** 注册服务端 tick 驱动：推进突进、清理到期的血怒 */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (RUSHING.isEmpty() && FURY_UNTIL.isEmpty()) {
                return;
            }
            long now = server.getOverworld().getTime();

            // ---- 血怒到期
            Iterator<Map.Entry<UUID, Long>> furyIt = FURY_UNTIL.entrySet().iterator();
            while (furyIt.hasNext()) {
                Map.Entry<UUID, Long> entry = furyIt.next();
                if (now < entry.getValue()) {
                    continue;
                }
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    removeFurySpeed(player);
                }
                furyIt.remove();
            }

            // ---- 突进推进
            Iterator<Map.Entry<UUID, Rush>> rushIt = RUSHING.entrySet().iterator();
            while (rushIt.hasNext()) {
                Map.Entry<UUID, Rush> entry = rushIt.next();
                Rush rush = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || !player.isAlive()) {
                    rushIt.remove();
                    continue;
                }

                player.setVelocity(rush.dir.x * RUSH_SPEED, 0.0D, rush.dir.z * RUSH_SPEED);
                player.velocityModified = true;
                player.fallDistance = 0.0F;

                if (player.getWorld() instanceof ServerWorld sw) {
                    // 刀光残影：每 tick 在身后留两片，位置稍散开，速度给 0
                    double backX = player.getX() - rush.dir.x * 0.6D;
                    double backZ = player.getZ() - rush.dir.z * 0.6D;
                    sw.spawnParticles(org.examplee.wangsword.registry.ParticleRegistry.CUTLASS_SLASH,
                            backX, player.getY() + 0.95D, backZ,
                            2, 0.35D, 0.12D, 0.35D, 0.0D);
                }

                strikeAlong(player, rush);

                if (--rush.ticksLeft <= 0) {
                    rushIt.remove();
                }
            }
        });
    }

    // ---------------------------------------------------------------- 右键

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }
        if (!SkillCooldowns.tryUse(player, SKILL_RUSH, "血刃突袭", RUSH_COOLDOWN)) {
            return TypedActionResult.fail(stack);
        }
        startRush(player);
        return TypedActionResult.success(stack);
    }

    private static void startRush(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        Vec3d dir = flat.lengthSquared() < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();

        RUSHING.put(player.getUuid(), new Rush(dir, RUSH_TICKS));
        player.setVelocity(dir.x * RUSH_SPEED, 0.0D, dir.z * RUSH_SPEED);
        player.velocityModified = true;

        if (player.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.9F, 1.35F);
        }
    }

    /** 突进途中对身边还没打过的目标各结算一次斩击 */
    private static void strikeAlong(ServerPlayerEntity player, Rush rush) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Box area = player.getBoundingBox().expand(1.6D, 1.0D, 1.6D);
        List<LivingEntity> targets = new ArrayList<>();
        for (var entity : world.getOtherEntities(player, area)) {
            if (entity instanceof LivingEntity living
                    && living.isAlive() && living.canHit()
                    && !rush.hit.contains(living.getUuid())) {
                targets.add(living);
            }
        }
        for (LivingEntity target : targets) {
            rush.hit.add(target.getUuid());
            target.damage(world.getDamageSources().playerAttack(player), RUSH_DAMAGE);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 20, 1, false, false, true));
            player.heal(RUSH_HEAL);
            world.spawnParticles(org.examplee.wangsword.registry.ParticleRegistry.BLOOD_BURST,
                    target.getX(), target.getY() + target.getHeight() * 0.55D, target.getZ(),
                    10, 0.3D, 0.3D, 0.3D, 0.18D);
        }
        if (!targets.isEmpty()) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 1.1F);
        }
    }

    // ---------------------------------------------------------------- X

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (!SkillCooldowns.tryUse(player, SKILL_FURY, "血债", FURY_COOLDOWN)) {
            return;
        }
        FURY_UNTIL.put(player.getUuid(), player.getWorld().getTime() + FURY_DURATION);
        applyFurySpeed(player);

        if (player.getWorld() instanceof ServerWorld world) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.8F, 1.25F);
            world.spawnParticles(org.examplee.wangsword.registry.ParticleRegistry.BLOOD_BURST,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    28, 0.9D, 0.5D, 0.9D, 0.25D);

            // 脚下血环：跟随玩家，寿命正好覆盖血怒，到期自己消失
            org.examplee.wangsword.entity.ThunderRingEntity ring =
                    new org.examplee.wangsword.entity.ThunderRingEntity(
                            org.examplee.wangsword.registry.EntityRegistry.THUNDER_RING, world);
            ring.setStyle(org.examplee.wangsword.entity.ThunderRingEntity.STYLE_BLOOD);
            ring.setRadius(2.2F);
            ring.setOwner(player);
            ring.setLifeTicks(FURY_DURATION + 10);
            ring.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            world.spawnEntity(ring);
        }
        player.sendMessage(Text.translatable("message.wangsword.cutlass.fury_start"), true);
    }

    private static void applyFurySpeed(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        instance.removeModifier(FURY_SPEED_UUID);
        instance.addTemporaryModifier(new EntityAttributeModifier(
                FURY_SPEED_UUID, "cutlass_fury", FURY_SPEED_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void removeFurySpeed(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (instance != null) {
            instance.removeModifier(FURY_SPEED_UUID);
        }
    }

    private static boolean isFury(ServerPlayerEntity player) {
        Long until = FURY_UNTIL.get(player.getUuid());
        return until != null && player.getWorld().getTime() < until;
    }

    // ---------------------------------------------------------------- 被动

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient()) {
            return result;
        }

        boolean fury = attacker instanceof ServerPlayerEntity sp && isFury(sp);
        ServerWorld world = (ServerWorld) attacker.getWorld();

        // 血怒的额外魔法伤害：无视护甲
        if (fury) {
            target.damage(world.getDamageSources().magic(), FURY_BONUS_DAMAGE);
        }

        if (attacker.getHealth() < attacker.getMaxHealth()) {
            float damage = (float) attacker.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            float ratio = fury ? FURY_LIFESTEAL : LIFESTEAL_RATIO;
            attacker.heal(Math.max(LIFESTEAL_MIN, damage * ratio));
            world.spawnParticles(ParticleTypes.HEART,
                    attacker.getX(), attacker.getY() + attacker.getHeight() * 0.6D, attacker.getZ(),
                    1, 0.25D, 0.25D, 0.25D, 0.0D);
        }
        return result;
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.bloodsail_cutlass.lifesteal",
                (int) (LIFESTEAL_RATIO * 100)));
        tooltip.add(Text.translatable("tooltip.wangsword.cutlass.rush.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.cutlass.rush.desc",
                (int) RUSH_DAMAGE, (int) RUSH_HEAL));
        tooltip.add(Text.translatable("tooltip.wangsword.cutlass.fury.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.cutlass.fury.desc",
                (int) (FURY_LIFESTEAL * 100), (int) FURY_BONUS_DAMAGE));
    }

    /** 一次突进的过程状态 */
    private static final class Rush {
        final Vec3d dir;
        int ticksLeft;
        final Set<UUID> hit = new HashSet<>();

        Rush(Vec3d dir, int ticksLeft) {
            this.dir = dir;
            this.ticksLeft = ticksLeft;
        }
    }
}
