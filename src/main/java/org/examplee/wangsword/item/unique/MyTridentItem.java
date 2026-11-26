package org.examplee.wangsword.item.unique;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.TridentItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.DeepSeaStormEntity;
import org.examplee.wangsword.entity.ThunderBoltEntity;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.List;
import java.util.UUID;

/**
 * 雷霆三叉戟。
 *
 * <p>继承原版 {@link TridentItem}，所以右键保留原版三叉戟的全部行为
 * （投掷 / 水中或雨天的激流冲刺）。
 *
 * <p>自制技能"雷霆风暴"绑定在 X 键上（通过 {@link ISkillWeapon}）。
 */
public class MyTridentItem extends TridentItem implements ISkillWeapon {

    /** 技能冷却（tick）：6 秒 */
    public static final int SKILL_COOLDOWN = 120;

    /**
     * 技能 ID：X 键雷霆风暴。
     *
     * <p>冷却必须按技能记。原先是物品冷却，等于把「原版投掷」也一起锁了 6 秒——
     * 玩家放完技能想把三叉戟扔出去，会发现按不动，而且完全查不出是 X 造成的。
     */
    public static final String SKILL_THUNDER = "trident_thunder";

    /** 自定义攻击力/攻速，替代原版三叉戟写死的数值 */
    private final Multimap<EntityAttribute, EntityAttributeModifier> attributes;

    public MyTridentItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(settings);

        // 注意：必须用原版的 ATTACK_DAMAGE_MODIFIER_ID / ATTACK_SPEED_MODIFIER_ID，
        // 否则物品提示里的数值只会显示修正值（比如 -2），而不是"基础值 + 修正值"（2.0）
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier",
                        (double) attackDamage + material.getAttackDamage(),
                        EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier",
                        attackSpeed,
                        EntityAttributeModifier.Operation.ADDITION));
        this.attributes = builder.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(
            net.minecraft.entity.EquipmentSlot slot) {
        return slot == net.minecraft.entity.EquipmentSlot.MAINHAND
                ? this.attributes
                : super.getAttributeModifiers(slot);
    }

    /**
     * 合成出来时自带忠诚 III。
     *
     * <p>只在“合成”这一下生效，之后玩家可以用砂轮正常洗掉换成别的附魔，
     * 洗掉后也不会被重新加回来。
     */
    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        super.onCraft(stack, world, player);
        if (!world.isClient() && !stack.hasEnchantments()) {
            stack.addEnchantment(net.minecraft.enchantment.Enchantments.LOYALTY, 3);
        }
    }

    /**
     * 右键投掷：换成"雷霆贯穿"。
     *
     * <p>激流（水里/雨里的三叉戟冲刺）仍然交给原版；其余情况自己生成
     * {@link ThunderTridentEntity}，初速 3.0（原版 2.5），射程从约 25 格拉到 78 格。
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) {
            super.onStoppedUsing(stack, world, user, remainingUseTicks);
            return;
        }
        int useTicks = this.getMaxUseTime(stack) - remainingUseTicks;
        if (useTicks < 10) {
            return;
        }

        // 激流保留原版：那是冲刺不是投掷，改掉会让水下位移手感全变
        int riptide = net.minecraft.enchantment.EnchantmentHelper.getRiptide(stack);
        if (riptide > 0 && (player.isTouchingWaterOrRain() || player.isSubmergedInWater())) {
            super.onStoppedUsing(stack, world, user, remainingUseTicks);
            return;
        }

        if (world.isClient()) {
            return;
        }

        player.incrementStat(net.minecraft.stat.Stats.USED.getOrCreateStat(this));
        Hand hand = player.getActiveHand();
        stack.damage(1, player, p -> p.sendToolBreakStatus(hand));

        ServerWorld serverWorld = (ServerWorld) world;
        // 基础伤害跟着玩家攻击力走，装备越好扔得越疼；+3 是这把武器的固定加成
        float baseDamage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) + 3.0F;
        org.examplee.wangsword.entity.ThunderTridentEntity thrown =
                new org.examplee.wangsword.entity.ThunderTridentEntity(
                        EntityRegistry.THUNDER_TRIDENT, serverWorld, player, stack, baseDamage);
        serverWorld.spawnEntity(thrown);
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THROW,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

        if (!player.getAbilities().creativeMode) {
            player.getInventory().removeOne(stack);
        }
    }

    /** X 键技能：雷霆风暴（三连闪 + 范围伤害 + 击飞 + 漩涡） */
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof MyTridentItem)) return;
        // 只拦雷霆风暴自己，不碰物品冷却——否则原版投掷会被一起锁住
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_THUNDER)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player, SKILL_THUNDER, "雷霆风暴");
            return;
        }

        Hand hand = player.getActiveHand();
        ServerWorld world = (ServerWorld) player.getWorld();

        // 1. 射线检测，找到玩家准星指向的位置
        HitResult hitResult = player.raycast(30.0D, 0.0F, false);
        Vec3d targetPos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            targetPos = hitResult.getPos();
        } else {
            targetPos = player.getPos().add(player.getRotationVec(1.0F).multiply(10.0));
        }

        // 2. 降下 3 道蓝电（real 落雷，伤害沿用原版，只是换成蓝色贴图）
        for (int i = 0; i < 3; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 3.0;
            double offsetZ = (world.random.nextDouble() - 0.5) * 3.0;
            ThunderBoltEntity.spawn(world,
                    new Vec3d(targetPos.x + offsetX, targetPos.y, targetPos.z + offsetZ),
                    ThunderBoltEntity.BLUE, false);
        }

        // 3. 范围真实伤害 + 击飞
        Box damageBox = new Box(
                targetPos.x - 6.0, targetPos.y - 3.0, targetPos.z - 6.0,
                targetPos.x + 6.0, targetPos.y + 3.0, targetPos.z + 6.0
        );
        for (Entity entity : world.getOtherEntities(player, damageBox)) {
            // 末地水晶不是 LivingEntity，需要单独引爆
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                crystal.damage(entity.getDamageSources().magic(), 30.0F);
                continue;
            }
            if (entity instanceof LivingEntity target && target != player) {
                target.damage(world.getDamageSources().magic(), 30.0F);
                target.addVelocity(0, 0.8, 0);
                target.velocityModified = true;
            }
        }

        // 4. 深海漩涡实体（持续聚怪 + 持续伤害）
        DeepSeaStormEntity storm = new DeepSeaStormEntity(EntityRegistry.DEEP_SEA_STORM, world, player);
        storm.setPosition(targetPos.x, targetPos.y, targetPos.z);
        world.spawnEntity(storm);

        // 5. 音效
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THUNDER,
                player.getSoundCategory(), 1.0F, 1.0F);

        // 6. 消耗耐久 + 冷却
        stack.damage(10, player, (p) -> p.sendToolBreakStatus(hand));
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_THUNDER, SKILL_COOLDOWN);
    }

    @Override
    public void appendTooltip(ItemStack itemStack, World world, List<Text> tooltip, TooltipContext tooltipContext) {
        super.appendTooltip(itemStack, world, tooltip, tooltipContext);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.trident.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.trident.desc"));
        tooltip.add(Text.translatable("tooltip.wangsword.trident.hint"));
    }
}
