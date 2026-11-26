package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
import org.examplee.wangsword.entity.CinderColossusEntity;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.util.SkillCooldowns;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 封岩巨剑：熔核巨兽的特攻武器。
 *
 * <p>数值上是巨剑档（总伤 20、攻速 1.0），额外的都是「破硬壳」这套：
 * <ul>
 *   <li><b>被动 · 碎岩</b>：命中护甲 ≥ 8 的目标追加真实伤害，对熔核巨兽再叠一层。
 *       重甲目标越多，这把剑越值。</li>
 *   <li><b>右键 · 岩崩</b>：前方 6.5 格扇形的地面炸开，把敌人掀起来并短暂封住。</li>
 *   <li><b>X · 封岩镇岳</b>：以自身为圆心 8 格震一圈，全场减速虚弱，自己获得抗性护体。</li>
 * </ul>
 *
 * <p>BOSS 那边对非封岩巨剑的来源一律 ×0.2，这条规则在
 * {@code CinderColossusEntity#damage} 里，武器这边只负责把伤害打得更高。
 */
public class SealedRockGreatswordItem extends SwordItem implements ISkillWeapon {

    // ---------------------------------------------------------------- 被动

    /** 目标护甲达到这个值就算「硬壳」，开始吃碎岩的额外伤害 */
    private static final int ARMOR_THRESHOLD = 8;
    /** 碎岩追加伤害 = 攻击力 × 这个比例（真实伤害） */
    public static final float SHATTER_RATIO = 0.25F;
    /** 对熔核巨兽额外追加的比例 */
    public static final float BOSS_SHATTER_RATIO = 0.20F;

    // ---------------------------------------------------------------- 右键：岩崩

    public static final String SKILL_ROCKFALL = "sealed_rock_rockfall";
    /** 6 秒 */
    public static final int ROCKFALL_COOLDOWN = 120;
    /** 前方扇形半径 */
    public static final double ROCKFALL_RANGE = 6.5D;
    /** 扇形半角（度） */
    private static final double ROCKFALL_HALF_ANGLE = 55.0D;
    /** 伤害倍率：20 × 1.15 = 23 */
    public static final float ROCKFALL_DAMAGE_RATIO = 1.15F;
    /** 命中后的「封足」时长 */
    private static final int ROCKFALL_SLOW_TICKS = 50;

    // ---------------------------------------------------------------- X：封岩镇岳

    public static final String SKILL_SEAL = "sealed_rock_seal";
    /** 30 秒 */
    public static final int SEAL_COOLDOWN = 600;
    /** 全向半径 */
    public static final double SEAL_RADIUS = 8.0D;
    /** 伤害倍率：20 × 1.8 = 36 */
    public static final float SEAL_DAMAGE_RATIO = 1.8F;
    /** 敌人被定住的时长 */
    private static final int SEAL_CONTROL_TICKS = 80;
    /** 自己抗性护体的时长 */
    private static final int SEAL_GUARD_TICKS = 100;
    private static final float SEAL_ABSORPTION = 8.0F;

    public SealedRockGreatswordItem(ToolMaterial material, int attackDamage, float attackSpeed,
                                    Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    // ---------------------------------------------------------------- 被动

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient() || !(attacker instanceof ServerPlayerEntity player)) {
            return result;
        }

        float attack = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float bonus = 0.0F;
        if (target.getArmor() >= ARMOR_THRESHOLD) {
            bonus += attack * SHATTER_RATIO;
        }
        if (target instanceof CinderColossusEntity) {
            bonus += attack * BOSS_SHATTER_RATIO;
        }
        if (bonus <= 0.0F) {
            return result;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        // 追加伤害和主伤害同一 tick，不把无敌帧清掉的话会被吞
        target.timeUntilRegen = 0;
        if (target.damage(world.getDamageSources().playerAttack(player), bonus)) {
            world.spawnParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                    12, 0.35D, 0.35D, 0.35D, 0.15D);
            world.spawnParticles(ParticleTypes.CLOUD,
                    target.getX(), target.getY() + target.getHeight() * 0.45D, target.getZ(),
                    4, 0.3D, 0.2D, 0.3D, 0.02D);
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.7F, 0.55F);
        }
        return result;
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
        if (!SkillCooldowns.tryUse(player, SKILL_ROCKFALL, "岩崩", ROCKFALL_COOLDOWN)) {
            return TypedActionResult.fail(stack);
        }
        rockfall(player);
        return TypedActionResult.success(stack);
    }

    /** 岩崩：前方扇形掀地 */
    private static void rockfall(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d look = player.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        Vec3d dir = flat.lengthSquared() < 1.0E-4D
                ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
        float attack = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float damage = attack * ROCKFALL_DAMAGE_RATIO;
        double minDot = Math.cos(Math.toRadians(ROCKFALL_HALF_ANGLE));

        Box area = player.getBoundingBox().expand(ROCKFALL_RANGE, 2.0D, ROCKFALL_RANGE);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target.isSpectator()) {
                continue;
            }
            Vec3d to = new Vec3d(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
            double dist = to.length();
            if (dist > ROCKFALL_RANGE) {
                continue;
            }
            if (dist > 0.4D && dir.dotProduct(to.normalize()) < minDot) {
                continue;
            }

            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().playerAttack(player), damage);
            Vec3d push = dist < 0.1D ? dir : to.normalize();
            target.addVelocity(push.x * 0.8D, 0.45D, push.z * 0.8D);
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    ROCKFALL_SLOW_TICKS, 2), player);
        }

        // 地面裂开的一条尘带
        for (int i = 0; i < 48; i++) {
            double d = 0.8D + (ROCKFALL_RANGE - 0.8D) * (i / 48.0D);
            double side = (world.random.nextDouble() - 0.5D) * 4.2D;
            double px = player.getX() + dir.x * d - dir.z * side;
            double pz = player.getZ() + dir.z * d + dir.x * side;
            world.spawnParticles(ParticleTypes.CLOUD,
                    px, player.getY() + 0.15D, pz, 1, 0.12D, 0.05D, 0.12D, 0.02D);
            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        px, player.getY() + 0.3D, pz, 1, 0.15D, 0.12D, 0.15D, 0.03D);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION,
                player.getX() + dir.x * 2.0D, player.getY() + 0.3D, player.getZ() + dir.z * 2.0D,
                2, 1.4D, 0.15D, 1.4D, 0.0D);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.4F, 0.5F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.1F, 0.55F);
    }

    // ---------------------------------------------------------------- X

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (!SkillCooldowns.tryUse(player, SKILL_SEAL, "封岩镇岳", SEAL_COOLDOWN)) {
            return;
        }
        sealPeak(player);
    }

    /** 封岩镇岳：全向震场 + 定身 + 自身护体 */
    private static void sealPeak(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        float attack = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float damage = attack * SEAL_DAMAGE_RATIO;

        Box area = player.getBoundingBox().expand(SEAL_RADIUS, 3.0D, SEAL_RADIUS);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target.isSpectator()) {
                continue;
            }
            if (target.squaredDistanceTo(player) > SEAL_RADIUS * SEAL_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().playerAttack(player), damage);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    SEAL_CONTROL_TICKS, 3), player);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,
                    SEAL_CONTROL_TICKS, 0), player);
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,
                SEAL_GUARD_TICKS, 1), player);
        // 吸收每级 4 点，amplifier 1 = 8 点
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION,
                SEAL_GUARD_TICKS, 1), player);

        // 三圈向外推的石屑尘环
        for (int ring = 0; ring < 3; ring++) {
            double radius = 2.2D + ring * 2.6D;
            int points = 52;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0) * i / points + ring * 0.3D;
                double px = player.getX() + Math.cos(angle) * radius;
                double pz = player.getZ() + Math.sin(angle) * radius;
                world.spawnParticles(ParticleTypes.CLOUD,
                        px, player.getY() + 0.2D, pz, 1, 0.14D, 0.06D, 0.14D, 0.03D);
                if (i % 5 == 0) {
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            px, player.getY() + 0.4D, pz, 1, 0.16D, 0.25D, 0.16D, 0.04D);
                }
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 0.6D, player.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4F, 0.5F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 0.9F, 0.55F);
        player.sendMessage(Text.translatable("message.wangsword.sealed_rock.seal"), true);
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.bane"));
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.shatter",
                (int) (SHATTER_RATIO * 100)));
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.rockfall.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.rockfall.desc",
                (int) ROCKFALL_RANGE, (int) (ROCKFALL_DAMAGE_RATIO * 100)));
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.seal.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.seal.desc",
                (int) SEAL_RADIUS, (int) (SEAL_DAMAGE_RATIO * 100)));
    }
}
