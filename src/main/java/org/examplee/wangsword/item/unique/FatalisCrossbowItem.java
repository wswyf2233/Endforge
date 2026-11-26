package org.examplee.wangsword.item.unique;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.examplee.wangsword.entity.FatalisCannonEntity;
import org.examplee.wangsword.entity.FatalisBoltEntity;
import org.examplee.wangsword.entity.FatalisShellEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 黑龙弩（fatalis_crossbow）。
 *
 * <p>左键按住即可连发：不需要装填，每发都是满蓄力箭，射速 10 发/秒。
 * 箭矢无重力，带火焰尾迹，命中后点燃目标。
 */
public class FatalisCrossbowItem extends Item implements org.examplee.wangsword.item.ISkillWeapon {

    /** X 键蓄满所需 tick（1.5 秒） */
    public static final int CHARGE_TICKS = 30;

    /** 客户端 -> 服务端：开火请求 */
    public static final Identifier FIRE_PACKET = new Identifier("wangsword", "fatalis_fire");

    /** 【临时开关】是否消耗背包里的箭（现在关闭，之后想恢复改成 true） */
    public static final boolean CONSUME_AMMO = false;
    /** 【临时开关】是否消耗武器耐久（现在关闭） */
    public static final boolean CONSUME_DURABILITY = false;
    /** 两发之间的最小间隔（tick） */
    public static final int FIRE_INTERVAL = 2;

    /** 箭速（原版满蓄力约 3.0） */
    private static final float ARROW_SPEED = 3.0F;
    /** 箭的伤害基数：最终伤害 ≈ ceil(箭速 × 该值) */
    private static final double ARROW_DAMAGE = 3.0D;
    /** 命中后点燃秒数 */
    private static final int BURN_SECONDS = 5;

    /** 服务端限流：每个玩家上次开火的游戏刻 */
    private static final Map<UUID, Long> LAST_FIRE = new HashMap<>();

    /**
     * 黑龙炮的最小间隔（tick）。
     *
     * <p>这发弹没有原版冷却，威力全靠蓄力时间；伤害翻上去之后必须补一道闸，
     * 否则按住 X 连点就是一秒几发两百点真伤。
     */
    public static final int CANNON_COOLDOWN = 20;
    /** 技能 ID：炮击的冷却。1 秒低于图标门槛，所以只记冷却、不画格子 */
    public static final String SKILL_CANNON = "fatalis_cannon";

    public FatalisCrossbowItem(Settings settings) {
        super(settings);
    }

    /** X 键：蓄力后发射黑龙炮 */
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof FatalisCrossbowItem)) return;

        ServerWorld world = (ServerWorld) player.getWorld();

        // 伤害提高之后必须有间隔，不然连点 X 就是无限两百点真伤
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_CANNON)) {
            return;
        }
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_CANNON, CANNON_COOLDOWN);

        Vec3d dir = player.getRotationVector().normalize();
        Vec3d spawn = player.getEyePos().add(dir.multiply(1.8));

        FatalisCannonEntity cannon = new FatalisCannonEntity(
                org.examplee.wangsword.registry.EntityRegistry.FATALIS_CANNON, world, player, dir);
        cannon.setPosition(spawn.x, spawn.y, spawn.z);
        cannon.setPower(charged ? 1.0F : 0.5F);
        world.spawnEntity(cannon);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE,
                SoundCategory.PLAYERS, 0.9F, 1.35F);

        if (CONSUME_DURABILITY) stack.damage(25, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
    }
    /** 右键蓄力满所需 tick */
    public static final int MAX_CHARGE = 20;
    /** 低于这个蓄力不发射 */
    private static final int MIN_CHARGE = 5;
    /** TNT 冷却（tick） */
    private static final int TNT_COOLDOWN = 60;

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world, PlayerEntity user, net.minecraft.util.Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return net.minecraft.util.TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        if (!world.isClient()) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 1.0F, 1.2F);
        }
        return net.minecraft.util.TypedActionResult.consume(stack);
    }

    /** 松开右键：按蓄力时长发射 TNT */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) return;

        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        if (used < MIN_CHARGE) return;

        float ratio = Math.min(1.0F, used / (float) MAX_CHARGE);

        ServerWorld sw = (ServerWorld) world;
        Vec3d dir = player.getRotationVector().normalize();
        double speed = 1.1 + 1.2 * ratio;

        FatalisShellEntity shell = new FatalisShellEntity(
                org.examplee.wangsword.registry.EntityRegistry.FATALIS_SHELL, sw, player, (int) (80 - 40 * ratio));
        shell.setPosition(player.getX(), player.getEyeY(), player.getZ());
        shell.setVelocity(dir.x * speed, dir.y * speed + 0.25, dir.z * speed);
        sw.spawnEntity(shell);

        // 右键：自定义炮击音效
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.CROSSBOW_SHOOT,
                SoundCategory.PLAYERS, 1.2F, 0.95F);

        if (CONSUME_DURABILITY) stack.damage(10, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
        player.getItemCooldownManager().set(this, TNT_COOLDOWN);
    }
    /** 收到客户端开火请求：校验手持 + 限流 + 消耗箭 + 发射 */
    public static void handleFire(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) return;
        if (!(player.getMainHandStack().getItem() instanceof FatalisCrossbowItem)) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        Long last = LAST_FIRE.get(player.getUuid());
        if (last != null && now - last < FIRE_INTERVAL) return;

        if (!consumeArrow(player)) return;

        LAST_FIRE.put(player.getUuid(), now);
        shoot(player, world);
    }

    /** 发射一发黑龙矢（无重力、不破坏方块） */
    private static void shoot(ServerPlayerEntity player, ServerWorld world) {
        ItemStack stack = player.getMainHandStack();

        Vec3d dir = player.getRotationVector().normalize();

        FatalisBoltEntity bolt = new FatalisBoltEntity(
                org.examplee.wangsword.registry.EntityRegistry.FATALIS_BOLT, world, player, dir);
        // 生成在身前 1.2 格，避免贴脸/出现在肩膀位置
        bolt.setPosition(
                player.getX() + dir.x * 1.2,
                player.getEyeY() - 0.15 + dir.y * 1.2,
                player.getZ() + dir.z * 1.2);
        world.spawnEntity(bolt);

        // 连发音效：短促、低音量、音调轻微浮动，避免 10 发/秒叠加成噪音
        // 左键：自定义连发音效，音调轻微浮动避免机械感
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.CROSSBOW_FIRE,
                SoundCategory.PLAYERS, 0.9F, 0.95F + world.random.nextFloat() * 0.1F);

        if (CONSUME_DURABILITY) stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
    }

    /** 从背包里扣一支箭，成功返回 true */
    private static boolean consumeArrow(ServerPlayerEntity player) {
        if (!CONSUME_AMMO) return true;
        if (player.getAbilities().creativeMode) return true;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.ARROW) || stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.TIPPED_ARROW)) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }
}
