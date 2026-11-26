package org.examplee.wangsword.item.unique;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.MinecraftServer;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 紫金剑：连击 + 处决。
 *
 * <p>两者都通过「攻击力属性修改器」实现，而不是补一刀伤害——
 * 因为同一 tick 里再调用 damage() 会被目标的受伤无敌帧挡掉，看起来就像技能没生效。
 */
public class PurpleSwordItem extends SwordItem {

    /** 连击：每层 +8% 攻击力 */
    private static final double COMBO_BONUS = 0.08D;
    private static final int MAX_COMBO = 5;
    /** 连击保持时间（tick） */
    private static final int COMBO_TIMEOUT = 60;
    /** 处决：低血量额外 +50% */
    private static final double EXECUTE_THRESHOLD = 0.25D;
    private static final double EXECUTE_BONUS = 0.5D;

    private static final UUID COMBO_ID = UUID.fromString("6d1f2a3c-1b2e-4c3d-9e8f-0a1b2c3d4e5f");
    private static final UUID EXECUTE_ID = UUID.fromString("7e2f3b4d-2c3f-4d4e-8f90-1b2c3d4e5f60");

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();

    private static final class Combo {
        int stacks;
        long lastHitTick;
        UUID targetId;
    }

    public PurpleSwordItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    /** 命中后：刷新连击层数 */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (!(attacker instanceof PlayerEntity player)) return result;
        if (player.getWorld().isClient()) return result;

        long now = player.getWorld().getTime();
        Combo combo = COMBOS.computeIfAbsent(player.getUuid(), id -> new Combo());

        if (now - combo.lastHitTick > COMBO_TIMEOUT || !target.getUuid().equals(combo.targetId)) {
            combo.stacks = 0;
        }
        combo.stacks = Math.min(MAX_COMBO, combo.stacks + 1);
        combo.lastHitTick = now;
        combo.targetId = target.getUuid();

        applyCombo(player, combo.stacks);
        clearModifier(player, EXECUTE_ID);   // 处决加成用完就移除

        // 每次攻击都带紫色粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            Vec3d pos = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            boolean executing = target.getHealth() <= target.getMaxHealth() * EXECUTE_THRESHOLD;

            // 基础紫色能量粒子
            serverWorld.spawnParticles(ParticleTypes.WITCH,
                    pos.x, pos.y, pos.z, 8, 0.25, 0.3, 0.25, 0.02);
            serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x, pos.y, pos.z, (int) Math.max(2, combo.stacks * 2),
                    0.2, 0.2, 0.2, 0.03);

            if (executing) {
                // 处决：紫色爆发
                serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH,
                        pos.x, pos.y, pos.z, 16, 0.35, 0.35, 0.35, 0.06);
                serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                        SoundCategory.PLAYERS, 1.0F, 1.6F);
            } else if (combo.stacks >= MAX_COMBO) {
                // 满连击：额外一圈火花
                serverWorld.spawnParticles(ParticleTypes.END_ROD,
                        pos.x, pos.y, pos.z, 10, 0.3, 0.3, 0.3, 0.04);
            }
        }

        return result;
    }

    /** 攻击前：目标残血时临时加攻击力（处决） */
    public static void beforeAttack(PlayerEntity player, LivingEntity target) {
        if (player.getWorld().isClient()) return;
        if (!(player.getMainHandStack().getItem() instanceof PurpleSwordItem)) return;
        if (target.getHealth() > target.getMaxHealth() * EXECUTE_THRESHOLD) return;

        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) return;
        remove(instance, EXECUTE_ID);
        instance.addTemporaryModifier(new EntityAttributeModifier(
                EXECUTE_ID, "ws_execute", EXECUTE_BONUS, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void applyCombo(PlayerEntity player, int stacks) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) return;
        remove(instance, COMBO_ID);
        if (stacks <= 0) return;
        instance.addTemporaryModifier(new EntityAttributeModifier(
                COMBO_ID, "ws_combo", COMBO_BONUS * stacks, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void clearModifier(PlayerEntity player, UUID id) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance != null) remove(instance, id);
    }

    private static void remove(EntityAttributeInstance instance, UUID id) {
        EntityAttributeModifier existing = instance.getModifier(id);
        if (existing != null) instance.removeModifier(existing);
    }

    /** 每 tick：超时的连击清零 */
    public static void tick(MinecraftServer server) {
        if (COMBOS.isEmpty()) return;
        long now = server.getOverworld().getTime();

        Iterator<Map.Entry<UUID, Combo>> it = COMBOS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Combo> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Combo combo = entry.getValue();

            if (player == null) { it.remove(); continue; }

            if (now - combo.lastHitTick > COMBO_TIMEOUT) {
                if (combo.stacks > 0) {
                    applyCombo(player, 0);
                    combo.stacks = 0;
                }
                it.remove();
            }
        }
    }
}