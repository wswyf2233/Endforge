package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.examplee.wangsword.effect.FrozenEffect;
import org.examplee.wangsword.entity.FrostBeamEntity;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.List;
import java.util.Locale;

/**
 * 冰霜法杖：纯远程武器，右键射出一道冰霜光束。
 *
 * <p>判定方式照远古守卫的激光：右键那一刻做一次射线，取沿途最近的生物，
 * 伤害与冰封当场结算，然后生成一条 {@link FrostBeamEntity} 做视觉表现。
 * 因此它**没有飞行时间、不会提前量、也不会被弹开**——准心对上了就必中，
 * 光束长度按实际落点同步，所以不会穿墙。
 *
 * <p>近战几乎没用（不继承 {@code SwordItem}），它的定位就是"站远处点名"。
 */
public class FrostStaffItem extends Item implements org.examplee.wangsword.item.ISkillWeapon {

    /** 最大射程（格）。原版箭在正常搭弓下的有效射程约 25 格，这里留出接近两倍 */
    public static final float RANGE = 48.0F;
    /** 单发基础伤害：魔法伤害，无视护甲 */
    public static final float BASE_DAMAGE = 10.0F;
    /** 命中附带的冰封秒数 */
    public static final int FREEZE_SECONDS = 3;
    /** 每发间隔（tick）：1.5 秒 */
    public static final int COOLDOWN = 30;
    /** 技能 ID（冷却按技能记；1.5 秒低于图标门槛，所以只走冷却不画格子） */
    public static final String SKILL_BEAM = "frost_staff_beam";

    // ---------------------------------------------------------------- X：绝对零度
    public static final String SKILL_ULT = "frost_staff_ult";
    /** 12 秒 */
    public static final int ULT_COOLDOWN = 240;
    public static final float ULT_RANGE = 24.0F;
    /** 直筒半径 */
    public static final float ULT_RADIUS = 4.0F;
    public static final float ULT_DAMAGE = 35.0F;
    public static final int ULT_FREEZE_SECONDS = 4;
    /** 已被冰封的目标额外吃这么多伤害 */
    private static final float ULT_FROZEN_BONUS = 0.3F;

    /** 被动「霜甲」内置冷却（毫秒） */
    private static final long GUARD_COOLDOWN_MS = 5000L;
    private static final java.util.Map<java.util.UUID, Long> GUARD_AT = new java.util.HashMap<>();
    /** 光束视觉起点：从"法杖球"的位置出发，不是从眼睛出发 */
    private static final double MUZZLE_FORWARD = 0.9D;
    private static final double MUZZLE_RIGHT = 0.30D;
    private static final double MUZZLE_DOWN = 0.22D;

    public FrostStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            // 判定全在服务端做，客户端只负责别把物品"用出去"
            return TypedActionResult.success(stack);
        }

        // 冷却没走完：直接吞掉这一下，不进判定
        if (user instanceof ServerPlayerEntity sp
                && !org.examplee.wangsword.util.SkillCooldowns.isReady(sp, SKILL_BEAM)) {
            return TypedActionResult.fail(stack);
        }

        fireBeam((ServerWorld) world, user, hand);
        return TypedActionResult.success(stack);
    }

    /**
     * 对着生物右键时，原版走 {@code interactMob → useOnEntity}，不会调用 {@link #use}。
     *
     * <p>法杖本来就是拿来点怪物的，少了这条分支等于「瞄准谁就打不到谁」——
     * 只有对着空气或普通方块右键才能开火。
     *
     * <p>村民、商人的 {@code interactMob} 返回 SUCCESS（要走交易界面），
     * 所以不会走到这里，右键交易不受影响。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player
                && org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_BEAM)) {
            fireBeam((ServerWorld) player.getWorld(), player, hand);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** 开火的真正实现，两个右键分支共用 */
    private void fireBeam(ServerWorld serverWorld, PlayerEntity user, Hand hand) {
        Vec3d start = user.getEyePos();
        Vec3d direction = user.getRotationVector().normalize();
        Vec3d end = start.add(direction.multiply(RANGE));

        // 先被方块挡住：光束打到墙就停，不能穿墙打后面的怪
        BlockHitResult blockHit = serverWorld.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, user));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        LivingEntity target = findTarget(serverWorld, user, start, end);
        Vec3d beamEnd = end;

        if (target != null) {
            beamEnd = target.getBoundingBox().getCenter();
            target.damage(serverWorld.getDamageSources().magic(), BASE_DAMAGE);
            // 冰封：Boss 级在这里自动降级为霜缚（只减速），末影龙/凋灵直接免疫，
            // 分级与排除的判断都在 freeze 内部
            FrozenEffect.freeze(target, FREEZE_SECONDS);

            serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.9F, 1.8F);
        }

        spawnBeam(serverWorld, user, start, beamEnd);

        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.2F, 1.45F);

        user.swingHand(hand, true);
        if (user instanceof ServerPlayerEntity sp) {
            org.examplee.wangsword.util.SkillCooldowns.start(sp, SKILL_BEAM, COOLDOWN);
        }
    }

    /**
     * 生成视觉光束，起点放在右手握杖的位置，方向指向实际落点。
     *
     * <p>为什么不做成"沿视线从眼睛射出"：光柱是圆柱，第一人称的镜头又正好在轴线上，
     * 那样看到的其实是圆柱的<b>内壁</b>——一圈亮环套在屏幕正中，准心直接被糊住，
     * 而且光团就贴脸。往前推几格只能缓解，环还在。
     *
     * <p>所以起点取"眼睛 + 前 0.9 + 右 0.3 + 下 0.22"，也就是法杖球大概的位置，
     * 再让光柱<b>斜着指向落点</b>。第一人称看到的是从右下角射向准心的一条细光，
     * 中心区域空着；第三人称也不会觉得光是从胸口冒出来的。
     *
     * <p>判定完全不受影响：伤害射线仍然从眼睛出发算满 {@link #RANGE} 格。
     */
    private void spawnBeam(ServerWorld world, PlayerEntity user, Vec3d eye, Vec3d end) {
        spawnBeam(world, user, eye, end, 1.0F);
    }

    private void spawnBeam(ServerWorld world, PlayerEntity user, Vec3d eye, Vec3d end, float thickness) {
        Vec3d forward = user.getRotationVector().normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);

        // 抬头/低头到正上正下时 right 会退化成零向量，这时不给横向偏移
        Vec3d right = forward.crossProduct(up);
        if (right.lengthSquared() < 1.0E-4D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }

        Vec3d origin = eye.add(forward.multiply(MUZZLE_FORWARD))
                .add(right.multiply(MUZZLE_RIGHT))
                .add(up.multiply(-MUZZLE_DOWN));

        Vec3d toEnd = end.subtract(origin);
        double length = toEnd.length();
        if (length < 0.3D) {
            // 贴脸开火，光束短到看不清，干脆不画
            return;
        }
        Vec3d unit = toEnd.multiply(1.0D / length);

        // 由方向反解 yaw/pitch：渲染器靠这两个角度把 +Y 轴转到光束走向
        float yaw = (float) Math.toDegrees(Math.atan2(-unit.x, unit.z));
        float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(unit.y, -1.0D, 1.0D)));

        FrostBeamEntity beam = new FrostBeamEntity(EntityRegistry.FROST_BEAM, world);
        beam.setPosition(origin.x, origin.y, origin.z);
        beam.setYaw(yaw);
        beam.setPitch(pitch);
        beam.setLength((float) length);
        beam.setThickness(thickness);
        world.spawnEntity(beam);
    }

    /**
     * 沿射线找最近的活物。
     *
     * <p>没用 {@code ProjectileUtil.raycast}，是因为这里需要"擦边也算命中"的宽松判定：
     * 把实体当竖直圆柱，只要射线到中心的垂距小于 {@code 0.55 + 半宽} 就算打中。
     * 比纯 AABB 求交更宽容，远程点小怪时不会因为差一个像素就空放。
     */
    private static LivingEntity findTarget(World world, PlayerEntity user, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.1D) {
            return null;
        }
        Vec3d dir = delta.multiply(1.0D / length);

        Box search = new Box(start, end).expand(2.5D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : world.getOtherEntities(user, search)) {
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || living.isSpectator()
                    || !living.canHit()) {
                continue;
            }

            Vec3d toCenter = living.getBoundingBox().getCenter().subtract(start);
            double along = toCenter.dotProduct(dir);
            if (along < 0.0D || along > length) {
                continue;
            }

            double radius = 0.55D + living.getWidth() * 0.5D;
            double perpendicular = toCenter.subtract(dir.multiply(along)).length();
            if (perpendicular > radius) {
                continue;
            }

            // 用"擦到边缘的那个点"排序，避免远处的胖子抢掉近处的瘦子
            double hitDistance = Math.max(0.0D, along - radius);
            if (hitDistance < bestDistance) {
                bestDistance = hitDistance;
                best = living;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- X：绝对零度

    /**
     * X 键：前方 24 格、半径 4 的直筒内所有目标吃 35 点冰伤并冰封 4 秒，
     * 已经被冰封的目标额外吃 30%。视觉上就是右键那束光柱加粗三倍。
     */
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_ULT)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVector().normalize();
        Vec3d end = eye.add(dir.multiply(ULT_RANGE));

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getPos();
        }
        double reach = eye.distanceTo(end);

        for (Entity entity : world.getOtherEntities(player,
                new Box(eye, end).expand(ULT_RADIUS + 1.0D))) {
            if (!(entity instanceof LivingEntity target)
                    || target == player
                    || !target.isAlive()
                    || target.isSpectator()) {
                continue;
            }

            Vec3d center = target.getBoundingBox().getCenter();
            Vec3d offset = center.subtract(eye);
            double along = offset.dotProduct(dir);
            if (along < 0.0D || along > reach) {
                continue;
            }

            Vec3d perpendicular = offset.subtract(dir.multiply(along));
            double horizontal = Math.sqrt(perpendicular.x * perpendicular.x
                    + perpendicular.z * perpendicular.z);
            if (horizontal > ULT_RADIUS + target.getWidth() * 0.5D) {
                continue;
            }
            if (Math.abs(perpendicular.y) > ULT_RADIUS + target.getHeight() * 0.5D) {
                continue;
            }

            boolean frozen = target.hasStatusEffect(org.examplee.wangsword.registry.EffectRegistry.FROZEN)
                    || target.hasStatusEffect(org.examplee.wangsword.registry.EffectRegistry.FROSTBOUND);
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().magic(),
                    ULT_DAMAGE * (frozen ? 1.0F + ULT_FROZEN_BONUS : 1.0F));
            FrozenEffect.freeze(target, ULT_FREEZE_SECONDS);
        }

        spawnBeam(world, player, eye, end, 3.2F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.6F, 0.65F);
        player.swingHand(Hand.MAIN_HAND, true);
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_ULT, ULT_COOLDOWN);
    }

    /**
     * 被动「霜甲」：持杖挨近战，冰封攻击者 1 秒并给自己 2 秒抗性 I，内置冷却 5 秒。
     *
     * @return 本次是否触发
     */
    public static boolean tryFrostGuard(ServerPlayerEntity player, LivingEntity attacker) {
        long now = System.currentTimeMillis();
        Long last = GUARD_AT.get(player.getUuid());
        if (last != null && now - last < GUARD_COOLDOWN_MS) {
            return false;
        }
        GUARD_AT.put(player.getUuid(), now);

        FrozenEffect.freezeTicks(attacker, 20);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.RESISTANCE,
                40, 0, false, true, true));
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.frost_staff.stats",
                (int) BASE_DAMAGE, (int) RANGE));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.frost_staff.beam.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_staff.beam.desc", FREEZE_SECONDS));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_staff.hint",
                String.format(Locale.ROOT, "%.1f", COOLDOWN / 20.0F)));
    }
}
