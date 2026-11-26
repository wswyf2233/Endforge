package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.CinderColossusEntity;
import org.examplee.wangsword.entity.FlyingSwordEntity;
import org.examplee.wangsword.entity.SwordTombGuardianEntity;
import org.examplee.wangsword.item.unique.MoltenSealedGreatswordItem;
import org.examplee.wangsword.item.unique.SealedRockGreatswordItem;
import org.examplee.wangsword.item.unique.SwordTombGreatswordItem;
import org.examplee.wangsword.registry.ArmorRegistry;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 幽卫钢套（战盔 / 胸甲 / 护腿 / 战靴）的套装技能。
 *
 * <p><b>被动 1 · 幽卫钢·决意</b>：穿齐四件且主手是巨剑时，近战伤害 +15%
 * （{@code GENERIC_ATTACK_DAMAGE} 的 MULTIPLY_TOTAL 修饰符）。换手、收剑、
 * 掉一件都会在下一次 tick 自动撤掉，不残留。
 *
 * <p><b>被动 2 · 幽卫钢·庇护</b>：穿齐四件时免疫一切火焰伤害
 * （{@link DamageTypeTags#IS_FIRE}，含着火、岩浆、火焰弹），
 * 并且受到 BOSS 伤害时减免 25%——这条走 mixin（{@code FulgurBossDamageMixin}），
 * 因为 {@code ALLOW_DAMAGE} 只能取消、改不了数值。
 *
 * <p><b>主动 · 幽卫飞剑</b>：按套装技能键（默认 G）朝准心扇形射出 5 把幽蓝飞剑，
 * 每把为攻击力的 55%，命中炸开 3 点，冷却 25 秒。造型和结算直接复用剑冢守卫的
 * 灵体飞剑（{@link FlyingSwordEntity} 的 spectral 分支）。
 */
public final class FulgurSetHandler {

    /** 主动技能 id（跟 SkillCooldownHud 的注册对得上） */
    public static final String SKILL_VOLLEY = "fulgur_volley";
    /** 客户端按键 → 服务端放技能的包 */
    public static final Identifier VOLLEY_PACKET = new Identifier("wangsword", "fulgur_volley_cast");
    /** 飞剑冷却 25 秒 */
    public static final int VOLLEY_COOLDOWN = 500;
    public static final int VOLLEY_COOLDOWN_SECONDS = VOLLEY_COOLDOWN / 20;

    /** 套装件数上限 */
    public static final int MAX_PIECES = 4;

    /** 被动 1：持大剑时的近战增伤 */
    public static final int GREATSWORD_BONUS_PERCENT = 15;
    private static final double GREATSWORD_BONUS = GREATSWORD_BONUS_PERCENT / 100.0D;

    /** 被动 2：BOSS 伤害减免 */
    public static final int BOSS_REDUCTION_PERCENT = 25;
    public static final float BOSS_DAMAGE_MULTIPLIER = 1.0F - BOSS_REDUCTION_PERCENT / 100.0F;

    /** 主动：飞剑齐射 */
    public static final int VOLLEY_SWORDS = 5;
    public static final int VOLLEY_DAMAGE_PERCENT = 55;
    private static final double VOLLEY_DAMAGE_RATIO = VOLLEY_DAMAGE_PERCENT / 100.0D;
    private static final double VOLLEY_SPEED = 1.05D;
    private static final float VOLLEY_BURST_DAMAGE = 3.0F;
    private static final float VOLLEY_SPREAD_DEG = 12.0F;

    /** 1.20.1 的属性修饰符 id 用 UUID，写死一个，别随机生成 */
    private static final UUID MODIFIER_ID = UUID.fromString("c1a7e5d4-2b93-4f68-8a51-6f0d3c9b7e20");
    private static final String MODIFIER_NAME = "Fulgur Greatsword";

    /** 玩家 → 当前是否挂着增伤修饰符（只在变化时才动属性） */
    private static final Map<UUID, Boolean> BONUS = new HashMap<>();

    private FulgurSetHandler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FulgurSetHandler::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(FulgurSetHandler::onDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(VOLLEY_PACKET,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> castVolley(player)));
    }

    // ------------------------------------------------------------------
    // 套装判定

    /** 身上穿了几件幽卫钢套 */
    public static int pieces(PlayerEntity player) {
        int count = 0;
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorRegistry.FULGUR_HELM)) count++;
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorRegistry.FULGUR_CUIRASS)) count++;
        if (player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorRegistry.FULGUR_GREAVES)) count++;
        if (player.getEquippedStack(EquipmentSlot.FEET).isOf(ArmorRegistry.FULGUR_BOOTS)) count++;
        return count;
    }

    public static boolean isFullSet(PlayerEntity player) {
        return pieces(player) >= MAX_PIECES;
    }

    /** 「大剑」：剑冢巨剑 / 封岩巨剑 / 熔核封岩巨剑 */
    private static boolean isGreatsword(ItemStack stack) {
        return stack.getItem() instanceof SwordTombGreatswordItem
                || stack.getItem() instanceof SealedRockGreatswordItem
                || stack.getItem() instanceof MoltenSealedGreatswordItem;
    }

    /** 这件伤害是不是 BOSS 打的（供 mixin 调用） */
    public static boolean isBossDamage(DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof CinderColossusEntity || attacker instanceof SwordTombGuardianEntity;
    }

    // ------------------------------------------------------------------
    // 被动 1：持大剑增伤

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean want = !player.isDead() && !player.isSpectator()
                    && isFullSet(player) && isGreatsword(player.getMainHandStack());

            Boolean last = BONUS.get(player.getUuid());
            if (last == null || last != want) {
                applyModifier(player, want);
                BONUS.put(player.getUuid(), want);
            }
        }
    }

    /** 挂 / 摘「持大剑」的打伤害修饰符（幂等：每次先移除再加） */
    private static void applyModifier(ServerPlayerEntity player, boolean on) {
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (inst == null) {
            return;
        }
        inst.removeModifier(MODIFIER_ID);
        if (on) {
            inst.addPersistentModifier(new EntityAttributeModifier(
                    MODIFIER_ID, MODIFIER_NAME, GREATSWORD_BONUS,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void clear(ServerPlayerEntity player) {
        BONUS.remove(player.getUuid());
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(MODIFIER_ID);
        }
    }

    // ------------------------------------------------------------------
    // 被动 2：火焰免疫（BOSS 减伤在 mixin 里）

    private static boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player) || !isFullSet(player)) {
            return true;
        }
        // 穿齐四件：一切火焰类伤害（着火、岩浆、火焰弹）全免
        return !source.isIn(DamageTypeTags.IS_FIRE);
    }

    // ------------------------------------------------------------------
    // 主动：幽卫飞剑

    private static void castVolley(ServerPlayerEntity player) {
        if (!isFullSet(player)) {
            player.sendMessage(Text.translatable(
                    "message.wangsword.fulgur_set.need_pieces", MAX_PIECES).formatted(Formatting.RED), true);
            return;
        }
        if (!SkillCooldowns.tryUse(player, SKILL_VOLLEY, "幽卫飞剑", VOLLEY_COOLDOWN)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * (float) VOLLEY_DAMAGE_RATIO;
        Vec3d forward = player.getRotationVec(1.0F);
        Vec3d origin = player.getEyePos().add(forward.multiply(0.9D));
        float baseYaw = player.getYaw();
        float pitch = player.getPitch();

        for (int i = 0; i < VOLLEY_SWORDS; i++) {
            float offset = (i - (VOLLEY_SWORDS - 1) / 2.0F) * VOLLEY_SPREAD_DEG;
            Vec3d dir = Vec3d.fromPolar(pitch, baseYaw + offset);

            FlyingSwordEntity sword = new FlyingSwordEntity(
                    EntityRegistry.FLYING_SWORD, world, player,
                    ItemStack.EMPTY, damage, dir.multiply(VOLLEY_SPEED), true)
                    .setBurstDamage(VOLLEY_BURST_DAMAGE);
            sword.setPosition(origin.x + dir.x * 0.35D, origin.y, origin.z + dir.z * 0.35D);
            world.spawnEntity(sword);
        }

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z,
                36, 0.35D, 0.3D, 0.35D, 0.16D);
        world.spawnParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z,
                10, 0.2D, 0.2D, 0.2D, 0.04D);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.1F, 1.35F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.3F, 1.7F);
    }

    // ------------------------------------------------------------------
    // 物品介绍

    /** 往物品介绍里写幽卫钢套的技能说明：四件共用 */
    public static void appendTooltip(List<Text> tooltip) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.fulgur_set.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.fulgur_set.greatsword", GREATSWORD_BONUS_PERCENT));
        tooltip.add(Text.translatable("tooltip.wangsword.fulgur_set.guard", BOSS_REDUCTION_PERCENT));
        tooltip.add(Text.translatable("tooltip.wangsword.fulgur_set.volley", MAX_PIECES));
        tooltip.add(Text.translatable("tooltip.wangsword.fulgur_set.volley.desc",
                VOLLEY_SWORDS, VOLLEY_DAMAGE_PERCENT, VOLLEY_COOLDOWN_SECONDS));
    }
}
