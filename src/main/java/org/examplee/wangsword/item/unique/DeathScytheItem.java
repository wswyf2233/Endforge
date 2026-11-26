package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 死神镰刀：吸血 + 必掉头颅。
 *
 * <p>被动：
 * <ul>
 *   <li>【汲取】每次命中回复相当于伤害 15% 的生命（至少 1 点）</li>
 *   <li>【收魂】击杀可掉头颅的生物时，头颅必定掉落</li>
 * </ul>
 */
public class DeathScytheItem extends SwordItem implements org.examplee.wangsword.item.ISkillWeapon {

    /** 吸血比例 */
    private static final float LIFESTEAL_RATIO = 0.15F;
    /** 单次吸血下限 */
    private static final float LIFESTEAL_MIN = 1.0F;

    /** 大招：扇形半径 */
    private static final double ULT_RADIUS = 5.0D;
    /** 大招：扇形半角（度） */
    private static final double ULT_HALF_ANGLE = 75.0D;
    /** 大招：斩杀阈值 */
    private static final float ULT_EXECUTE_THRESHOLD = 0.30F;
    /** 大招：每命中一个目标回血 */
    private static final float ULT_HEAL_PER_TARGET = 3.0F;
    /** 大招（右键灵魂收割）冷却：15 秒 */
    public static final int ULT_COOLDOWN = 300;
    /** 血契（X 键）冷却：20 秒 */
    public static final int PACT_COOLDOWN = 400;

    /**
     * 技能 ID。两个技能以前共用一个物品冷却槽：放完血契，右键的灵魂收割
     * 会被锁整整 20 秒。拆成两个技能各记各的。
     */
    public static final String SKILL_REAP = "scythe_reap";
    public static final String SKILL_PACT = "scythe_pact";

    public DeathScytheItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient()) {
            return result;
        }

        // 【汲取】按攻击力比例回血
        float damage = (float) attacker.getAttributeValue(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float heal = Math.max(LIFESTEAL_MIN, damage * LIFESTEAL_RATIO);
        if (attacker.getHealth() < attacker.getMaxHealth()) {
            attacker.heal(heal);
            if (attacker.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.HEART,
                        attacker.getX(), attacker.getY() + attacker.getHeight() * 0.6D, attacker.getZ(),
                        1, 0.25D, 0.25D, 0.25D, 0.0D);
            }
        }
        // 【收魂】击杀可掉头颅的生物时，头颅必掉
        if (target.isDead()) {
            ItemStack head = headFor(target);
            if (!head.isEmpty()) {
                target.dropStack(head);
                if (attacker.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.PLAYERS, 0.8F, 1.6F);
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- 右键：灵魂收割

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world, PlayerEntity user,
                                                               net.minecraft.util.Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != net.minecraft.util.Hand.MAIN_HAND) {
            return net.minecraft.util.TypedActionResult.pass(stack);
        }
        if (world.isClient() || !(user instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return net.minecraft.util.TypedActionResult.success(stack);
        }
        performSoulReap(serverPlayer);
        return net.minecraft.util.TypedActionResult.success(stack);
    }

    // ---------------------------------------------------------------- X 键：血契

    @Override
    public void triggerUltimate(net.minecraft.server.network.ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (org.examplee.wangsword.util.BloodPactHandler.isActive(player)) {
            return;
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_PACT)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_PACT, "血契");
            return;
        }
        double bonus = org.examplee.wangsword.util.BloodPactHandler.start(player);
        if (bonus > 0.0D) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 0.8F);
            org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_PACT, PACT_COOLDOWN);
        }
    }

    // ---------------------------------------------------------------- 灵魂收割实现

    private void performSoulReap(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_REAP)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_REAP, "灵魂收割");
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();

        net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0F).normalize();
        net.minecraft.util.math.Vec3d eye = player.getEyePos();
        float baseDamage = (float) player.getAttributeValue(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * 1.5F;

        int hits = 0;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(ULT_RADIUS), entity -> entity != player && entity.isAlive())) {
            // 用目标的身体中心而不是脚底算角度，否则近身目标（尤其脚在前面）会被误判到扇形外
            net.minecraft.util.math.Vec3d targetCenter =
                    target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            net.minecraft.util.math.Vec3d delta = targetCenter.subtract(eye);
            double distance = delta.length();
            if (distance > ULT_RADIUS) {
                continue;
            }
            // 贴身目标（1.5 格内）无条件命中，避免高度差把角度撑爆
            if (distance > 1.5D) {
                double angle = Math.toDegrees(Math.acos(
                        Math.max(-1.0D, Math.min(1.0D, delta.normalize().dotProduct(look)))));
                if (angle > ULT_HALF_ANGLE) {
                    continue;
                }
            }

            boolean execute = target.getHealth() <= target.getMaxHealth() * ULT_EXECUTE_THRESHOLD;
            if (execute) {
                target.kill();
            } else {
                target.damage(world.getDamageSources().playerAttack(player), baseDamage);
            }
            hits++;

            // 大招击杀同样触发【收魂】
            if (target.isDead()) {
                ItemStack head = headFor(target);
                if (!head.isEmpty()) {
                    target.dropStack(head);
                }
            }

            world.spawnParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getHeight() * 0.5D, target.getZ(),
                    12, 0.3D, 0.4D, 0.3D, 0.05D);
        }

        if (hits > 0) {
            player.heal(ULT_HEAL_PER_TARGET * hits);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    10, 0.4D, 0.3D, 0.4D, 0.02D);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 0.5F, 1.4F);

        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_REAP, ULT_COOLDOWN);
        player.sendMessage(Text.translatable("message.wangsword.deathscythe.ult", hits)
                .formatted(Formatting.DARK_RED), true);
    }
    /** 可掉头颅的生物 → 对应头颅 */
    private static ItemStack headFor(LivingEntity entity) {
        if (entity instanceof WitherSkeletonEntity) {
            return new ItemStack(Items.WITHER_SKELETON_SKULL);
        }
        if (entity instanceof SkeletonEntity) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        if (entity instanceof ZombieEntity) {
            return new ItemStack(Items.ZOMBIE_HEAD);
        }
        if (entity instanceof CreeperEntity) {
            return new ItemStack(Items.CREEPER_HEAD);
        }
        if (entity instanceof PiglinEntity) {
            return new ItemStack(Items.PIGLIN_HEAD);
        }
        if (entity instanceof EnderDragonEntity) {
            return new ItemStack(Items.DRAGON_HEAD);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.title").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.stats").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.lifesteal").formatted(Formatting.RED));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.soul").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.ult").formatted(Formatting.RED));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.ult_detail").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.pact").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("tooltip.wangsword.deathscythe.pact_detail").formatted(Formatting.GRAY));
    }
}