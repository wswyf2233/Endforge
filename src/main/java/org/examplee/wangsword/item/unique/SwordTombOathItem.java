package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.SentinelSwordEntity;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.util.OathBarrierHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 剑冢 · 守誓：巨剑的第五阶，上下左右各一枚守卫核心围着一把 Pro Max 锻出来的终阶形态。
 *
 * <p>和前面四阶的区别是"不再只靠右手"——同一把剑同时承担武器与盾：
 * <ol>
 *   <li><b>右键 · 格挡</b>：像盾一样举起来，正面来的伤害原版规则全免，命中面判定和击退也照旧。</li>
 *   <li><b>被动 · 剑冢环伺</b>：只要握在手上，剑冢自己维持六把环伺之剑，不再需要主动召。</li>
 *   <li><b>X 键 · 守誓之阵</b>：插剑成阵，阵内敌人持续掉血并被压慢，阵内玩家获得抗性。</li>
 * </ol>
 *
 * <p>阶数固定为 {@link #OATH_TIER}（4），不参与两把同阶合并的升阶链，
 * 所以 {@link SwordTombGreatswordItem#MAX_TIER} 仍然是 3，融合配方不会把它当成新一阶。
 */
public class SwordTombOathItem extends SwordTombGreatswordItem {

    /** 第五阶：写在父类的 tier 体系里，但不在合并链上 */
    public static final int OATH_TIER = 4;

    /** 常驻环伺剑数量 */
    public static final int BLADE_COUNT = 6;
    /** 每把剑射出的光刃伤害系数（相对 baseDamage） */
    private static final float BLADE_RATIO = 0.28F;
    /** 环伺剑单把寿命：20 分钟后自然散掉再补，避免 life 累积到浮点失精 */
    private static final int BLADE_LIFETIME = 24_000;
    /** 环伺剑渲染用的阶数：3 = 沿用 Pro Max 的旧巨剑模型，剑阵里不出现双头刀 */
    private static final int BLADE_RENDER_TIER = 3;

    public SwordTombOathItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, OATH_TIER, settings);
    }

    // ================== 【右键 · 格挡】 ==================
    // 原版只认 UseAction.BLOCK：只要举起来，PlayerEntity#isBlocking 就是 true，
    // 减伤、击退、格挡音效全部由原版走完，不需要 mixin 也不用手写伤害拦截。

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72_000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        if (!world.isClient()) {
            // 举剑不是举木盾，给一声金属摩擦
            world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
                    SoundCategory.PLAYERS, 0.65F, 0.72F);
        }
        return TypedActionResult.consume(stack);
    }

    // ================== 【X 键 · 守誓之阵】 ==================

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        // 只认主手这把，切手后残留的请求直接丢掉
        if (!(player.getMainHandStack().getItem() instanceof SwordTombOathItem)) {
            return;
        }
        OathBarrierHandler.cast(player);
    }

    // ================== 【被动 · 常驻剑阵】 ==================

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient() || !selected || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        // 剑阵不用每 tick 摸一遍，半秒校对一次足够
        if (world.getTime() % 20L != 0L) {
            return;
        }
        maintainBlades(player);
    }

    /** 补齐环伺剑：按 slot 找空缺，缺几把补几把，已有的不重复召 */
    private void maintainBlades(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        List<SentinelSwordEntity> blades = world.getEntitiesByClass(
                SentinelSwordEntity.class,
                player.getBoundingBox().expand(9.0D),
                e -> e.isOwnedBy(player));

        if (blades.size() >= BLADE_COUNT) {
            return;
        }

        Set<Integer> used = new HashSet<>();
        for (SentinelSwordEntity blade : blades) {
            used.add(blade.getSlot());
        }

        for (int i = 0; i < BLADE_COUNT; i++) {
            if (used.contains(i)) {
                continue;
            }
            SentinelSwordEntity blade = new SentinelSwordEntity(
                    EntityRegistry.SENTINEL_SWORD, world, player, i, BLADE_COUNT,
                    BLADE_RENDER_TIER, BLADE_LIFETIME, baseDamage() * BLADE_RATIO);
            world.spawnEntity(blade);
        }
    }

    // ================== 【说明文字】 ==================

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        int soul = nbt == null ? 0 : Math.min(nbt.getInt(NBT_SOUL), soulCap());

        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.oath.header"));
        // 攻击力/攻速由原版属性提示显示，这里不再重复
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.oath.block.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.block.desc"));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.barrier.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.barrier.desc",
                (int) OathBarrierHandler.RADIUS, OathBarrierHandler.DURATION / 20));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.sentinel.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.sentinel.desc", BLADE_COUNT));
        tooltip.add(Text.translatable("tooltip.wangsword.oath.soul.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.desc", SOUL_DAMAGE_PER_STACK));
        // 注意：五阶的 X 是结界，不吃葬魂层数，所以这里不写"大招吃掉层数"那条
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.count", soul, soulCap()));
        // 不调父类的 appendTooltip：那一套是四阶巨剑的环伺/万剑归宗说明，
        // 五阶的技能组完全不同，两段说明叠在一起只会让玩家以为技能没生效。
    }
}
