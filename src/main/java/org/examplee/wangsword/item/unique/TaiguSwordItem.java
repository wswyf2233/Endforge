package org.examplee.wangsword.item.unique;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.item.ISkillWeapon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 末地合金剑：连击 + 处决 + 主动技能「虚空裂隙」。
 *
 * <p>比紫金剑更强的数值：
 * <ul>
 *   <li>连击每层 +10%（紫金 8%），最多 5 层</li>
 *   <li>处决阈值 35%（紫金 25%），额外 +75%（紫金 50%）</li>
 *   <li>X 键裂隙斩：前方 8 格穿透，40% 伤害无视护甲，命中附加缓慢 II + 虚弱 I</li>
 *   <li>穿齐末地合金四件时，裂隙斩每命中一个目标回复 2 点生命</li>
 * </ul>
 */
public class TaiguSwordItem extends SwordItem implements ISkillWeapon {

    /** 连击：每层 +10% 攻击力 */
    private static final double COMBO_BONUS = 0.10D;
    private static final int MAX_COMBO = 5;
    /** 连击保持时间（tick） */
    private static final int COMBO_TIMEOUT = 60;

    /** 处决：目标血量低于 35% 时额外 +75% */
    private static final double EXECUTE_THRESHOLD = 0.35D;
    private static final double EXECUTE_BONUS = 0.75D;

    /**
     * 技能冷却（tick）：20 秒。
     *
     * <p>这里原来写的是 160（8 秒），但 {@code triggerUltimate} 里实际用的是写死的 400。
     * 以实际行为为准取 400，免得改了冷却时长还以为是修 bug。
     */
    public static final int ULT_COOLDOWN = 400;
    /** 技能 ID：X 键姿态 */
    public static final String SKILL_STANCE = "taigu_stance";
    /** 裂隙长度（格） */
    private static final double ULT_RANGE = 8.0D;
    /** 判定半径 */
    private static final double ULT_RADIUS = 1.2D;
    /** 无视护甲的比例 */
    private static final float ARMOR_PIERCE_RATIO = 0.40F;
    /** 控制时长（tick） */
    private static final int ULT_DEBUFF_DURATION = 60;

    private static final UUID COMBO_ID = UUID.fromString("1a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d");
    private static final UUID EXECUTE_ID = UUID.fromString("2b3c4d5e-6f7a-4b2c-9d3e-4f5a6b7c8d9e");

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();

    private static final class Combo {
        int stacks;
        long lastHitTick;
        UUID targetId;
    }

    public TaiguSwordItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    // ---------------------------------------------------------------- 被动

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (!(attacker instanceof PlayerEntity player)) {
            return result;
        }
        if (player.getWorld().isClient()) {
            return result;
        }

        long now = player.getWorld().getTime();
        Combo combo = COMBOS.computeIfAbsent(player.getUuid(), id -> new Combo());

        if (now - combo.lastHitTick > COMBO_TIMEOUT || !target.getUuid().equals(combo.targetId)) {
            combo.stacks = 0;
        }
        combo.stacks = Math.min(MAX_COMBO, combo.stacks + 1);
        combo.lastHitTick = now;
        combo.targetId = target.getUuid();

        applyCombo(player, combo.stacks);
        clearModifier(player, EXECUTE_ID);
        // 终焉姿态：命中附加 4 点虚空伤害（无视护甲），并刷新攻击时间
        if (org.examplee.wangsword.util.TaiguStanceHandler.isActive(player)) {
            org.examplee.wangsword.util.TaiguStanceHandler.onAttack(player);
            target.damage(player.getDamageSources().magic(), 4.0F);
        }

        if (player.getWorld() instanceof ServerWorld serverWorld) {
            Vec3d pos = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            boolean executing = target.getHealth() <= target.getMaxHealth() * EXECUTE_THRESHOLD;

            serverWorld.spawnParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y, pos.z, 6, 0.25, 0.3, 0.25, 0.03);
            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, (int) Math.max(2, combo.stacks * 2), 0.2, 0.2, 0.2, 0.03);

            if (executing) {
                serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH,
                        pos.x, pos.y, pos.z, 18, 0.35, 0.35, 0.35, 0.06);
                serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.4F);
            }
        }
        return result;
    }

    /** 攻击前：目标残血时临时加攻击力（处决） */
    public static void beforeAttack(PlayerEntity player, LivingEntity target) {
        if (player.getWorld().isClient()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof TaiguSwordItem)) {
            return;
        }
        if (target.getHealth() > target.getMaxHealth() * EXECUTE_THRESHOLD) {
            return;
        }
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        remove(instance, EXECUTE_ID);
        instance.addTemporaryModifier(new EntityAttributeModifier(
                EXECUTE_ID, "ws_taigu_execute", EXECUTE_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 每 tick：清理超时的连击 */
    public static void tick(MinecraftServer server) {
        if (COMBOS.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        Iterator<Map.Entry<UUID, Combo>> it = COMBOS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Combo> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Combo combo = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            if (now - combo.lastHitTick > COMBO_TIMEOUT) {
                if (combo.stacks > 0) {
                    applyCombo(player, 0);
                    combo.stacks = 0;
                }
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- 主动技能

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_STANCE)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_STANCE, "太古道·姿态");
            return;
        }
        if (org.examplee.wangsword.util.TaiguStanceHandler.isActive(player)) {
            return;
        }
        org.examplee.wangsword.util.TaiguStanceHandler.start(player);

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        Vec3d start = player.getEyePos();
        for (double d = 0.0D; d <= 3.0D; d += 0.4D) {
            Vec3d p = start.add(dir.multiply(d));
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.08D, 0.08D, 0.08D, 0.0D);
            world.spawnParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 2, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.4F);

        // 冷却 20 秒（姿态持续 10 秒）
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_STANCE, ULT_COOLDOWN);
    }
    // ---------------------------------------------------------------- 工具

    private static void applyCombo(PlayerEntity player, int stacks) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        remove(instance, COMBO_ID);
        if (stacks <= 0) {
            return;
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(
                COMBO_ID, "ws_taigu_combo", COMBO_BONUS * stacks,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void clearModifier(PlayerEntity player, UUID id) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance != null) {
            remove(instance, id);
        }
    }

    private static void remove(EntityAttributeInstance instance, UUID id) {
        EntityAttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, net.minecraft.world.World world, List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.title").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.stats").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.combo").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.execute").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.ult").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.ult_detail").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.wangsword.taigu_sword.full_set").formatted(Formatting.GOLD));
    }
}
