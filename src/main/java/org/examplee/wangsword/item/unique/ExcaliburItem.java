package org.examplee.wangsword.item.unique;

import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.item.ISkillWeapon;

import java.util.List;

/**
 * 誓约胜利之剑（Excalibur）
 *
 * <p>右键【风王结界】：向前方扇形卷起一阵风，造成伤害并把目标击飞
 * <p>X【誓约胜利之剑】：按住蓄力，松手放出贯穿型的光炮
 */
public class ExcaliburItem extends SwordItem implements ISkillWeapon {

    /** X 键蓄力所需 tick.95 秒（音效仍从头播放，比音效结尾早 1 秒发射） */
    public static final int CHARGE_TICKS = 59;

    /** 客户端蓄力状态，仅用于切换模型（风王结界 解放剑身*/
    public static boolean CHARGING = false;

    /** 客户端蓄力累tick（用于举剑动作的缓动*/
    public static int CHARGE_ELAPSED = 0;
    /** 发射后的下劈动画剩余 tick（客户端*/
    public static int FIRE_ANIM = 0;
    /** 下劈动画总时长（tick*/
    public static final int FIRE_ANIM_TICKS = 10;

    /** 光炮是否破坏地形（当前关闭；末端爆炸同样不破坏地形） */
    private static final boolean BREAK_TERRAIN = false;

    /** 风王结界：射程（正前方一条直筒，不再是扇形*/
    private static final double WIND_RANGE = 32.0D;
    /** 风王结界：风筒半径*/
    private static final double WIND_RADIUS = 5D;
    /** 风王结界：纯视觉往前推的格数（伤害判定不跟着动） */
    private static final double WIND_VISUAL_OFFSET = 3.0D;
    /** 风王结界：单次伤*/
    private static final float WIND_DAMAGE = 24.0F;
    /** 按住右键时的脉冲间隔（tick*/
    private static final int PULSE_TICKS = 5;
    /** 按住期间每次脉冲的伤*/
    private static final float PULSE_DAMAGE = 20F;

    public ExcaliburItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    // ---------------------------------------------------------------- 右键：风王结

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        // 按住右键持续释放：进使用状态，松开onStoppedUsing 收尾
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        // 允许一直按着不放
        return 72000;
    }

    /** 按住右键期间每个 tick 调用：起步一大团，之后持续脉*/
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return;
        }
        int elapsed = this.getMaxUseTime(stack) - remainingUseTicks;
        if (elapsed <= 1) {
            castWindBarrier(player);
        } else if (elapsed % PULSE_TICKS == 0) {
            windPulse(player);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()) {
            return;
        }
        // 松手：风停，补一声收
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.7F, 1.4F);
    }

    /** 按住期间的持续风压：轻伤+ 击退 + 风环 */
    private void windPulse(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F).normalize();

        Box area = player.getBoundingBox().expand(WIND_RANGE + 1.0D);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || target == player) {
                continue;
            }
            Vec3d offset = target.getPos()
                    .add(0.0D, target.getHeight() * 0.5D, 0.0D)
                    .subtract(eye);
            if (!inWindCylinder(offset, dir, WIND_RADIUS * 0.8D)) {
                continue;
            }
            target.damage(world.getDamageSources().playerAttack(player), PULSE_DAMAGE);
            Vec3d push = offset.normalize().multiply(0.9D).add(0.0D, 0.30D, 0.0D);
            target.setVelocity(push.x, push.y, push.z);
            target.velocityModified = true;
        }

        // 前方两圈小风
        Vec3d right = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d planeUp = right.crossProduct(dir).normalize();
        for (double step = 1.5D; step <= WIND_RANGE; step += 2.5D) {
            Vec3d center = eye.add(dir.multiply(step + WIND_VISUAL_OFFSET));
            double ringRadius = 0.4D + step * 0.35D;
            for (int i = 0; i < 16; i++) {
                double ang = Math.PI * 2.0D * i / 16.0D;
                Vec3d point = center
                        .add(right.multiply(Math.cos(ang) * ringRadius))
                        .add(planeUp.multiply(Math.sin(ang) * ringRadius));
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (i % 2 == 0) {
                    world.spawnParticles(ParticleTypes.SNOWFLAKE, point.x, point.y, point.z, 1,
                            0.02D, 0.02D, 0.02D, 0.0D);
                }
            }
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    center.x, center.y, center.z, 3, 0.4D, 0.4D, 0.4D, 0.0D);
        }

        // 每次脉冲都续一段风筒：和起手一样的圆柱气流，只是短一些
        Vec3d pulseAt = eye.add(dir.multiply(1.2D));
        world.spawnParticles(ParticleTypes.SONIC_BOOM,
                pulseAt.x, pulseAt.y, pulseAt.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                pulseAt.x, pulseAt.y, pulseAt.z, 24, 1.4D, 1.0D, 1.4D, 0.2D);

        Vec3d pulseRight = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (pulseRight.lengthSquared() < 1.0E-6D) {
            pulseRight = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            pulseRight = pulseRight.normalize();
        }
        Vec3d pulseUp = pulseRight.crossProduct(dir).normalize();
        for (double step = 0.8D; step <= WIND_RANGE; step += 1.1D) {
            Vec3d center = eye.add(dir.multiply(step + WIND_VISUAL_OFFSET));
            double ringRadius = 0.6D + step * 0.08D;
            double spin = step * 1.7D;
            for (int i = 0; i < 14; i++) {
                double ang = Math.PI * 2.0D * i / 14.0D + spin;
                Vec3d point = center
                        .add(pulseRight.multiply(Math.cos(ang) * ringRadius))
                        .add(pulseUp.multiply(Math.sin(ang) * ringRadius));
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        // 长按期间每 5 tick 重生一根风筒，旧的 10 tick 后消失，这样风筒始终跟着准星
        spawnWindJet(world, eye, dir);
    }

    /** 生成一根沿视线的蓝色风筒（圆柱网格），横放在眼前 */
    private static void spawnWindJet(ServerWorld world, Vec3d eye, Vec3d dir) {
        org.examplee.wangsword.entity.GoldenBurstEntity jet =
                new org.examplee.wangsword.entity.GoldenBurstEntity(
                        org.examplee.wangsword.registry.EntityRegistry.WIND_BURST, world);
        jet.setPosition(eye.x + dir.x * 4.4D, eye.y + dir.y * 4.4D, eye.z + dir.z * 4.4D);
        jet.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        jet.setPitch((float) -Math.toDegrees(Math.asin(dir.y)));
        world.spawnEntity(jet);
    }

    private void castWindBarrier(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F).normalize();

        Box area = player.getBoundingBox().expand(WIND_RANGE + 1.0D);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || target == player) {
                continue;
            }
            Vec3d offset = target.getPos()
                    .add(0.0D, target.getHeight() * 0.5D, 0.0D)
                    .subtract(eye);
            if (!inWindCylinder(offset, dir, WIND_RADIUS)) {
                continue;
            }
            target.damage(world.getDamageSources().playerAttack(player), WIND_DAMAGE);
            Vec3d push = offset.normalize().multiply(1.7D).add(0.0D, 0.55D, 0.0D);
            target.setVelocity(push.x, push.y, push.z);
            target.velocityModified = true;
        }

        // 脚下盘旋上升的气
        for (int i = 0; i < 48; i++) {
            double t = i / 48.0D;
            double ang = t * Math.PI * 4.0D;
            double radius = 0.5D + t * 1.7D;
            double px = player.getX() + Math.cos(ang) * radius;
            double pz = player.getZ() + Math.sin(ang) * radius;
            double py = player.getY() + t * 1.9D;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 1, 0.0D, 0.02D, 0.0D, 0.0D);
            if (i % 3 == 0) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0.04D, 0.02D, 0.04D, 0.01D);
            }
        }

        // 【风筒】沿正前方铺一条圆柱形气流：一圈一圈往前推，看起来像一门风炮
        Vec3d right = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d planeUp = right.crossProduct(dir).normalize();
        for (double step = 0.8D; step <= WIND_RANGE; step += 0.7D) {
            Vec3d center = eye.add(dir.multiply(step + WIND_VISUAL_OFFSET));
            // 前端略微张开，形成一个从枪口喷出去的锥筒
            double ringRadius = 0.65D + step * 0.09D;
            double spin = step * 1.7D;
            for (int i = 0; i < 22; i++) {
                double ang = Math.PI * 2.0D * i / 22.0D + spin;
                Vec3d point = center
                        .add(right.multiply(Math.cos(ang) * ringRadius))
                        .add(planeUp.multiply(Math.sin(ang) * ringRadius));
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (i % 2 == 0) {
                    world.spawnParticles(ParticleTypes.SNOWFLAKE,
                            point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.02D);
                }
                if (i % 6 == 0) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.05D);
                }
            }
            // 筒壁上的环
            if (Math.round(step * 10.0D) % 25 == 0) {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        center.x, center.y, center.z, 2, ringRadius * 0.8D, ringRadius * 0.8D, ringRadius * 0.8D, 0.0D);
            }
        }

        // 膛口：出手瞬间的蓝色冲击环
        Vec3d burstAt = eye.add(dir.multiply(1.2D));
        world.spawnParticles(ParticleTypes.SONIC_BOOM,
                burstAt.x, burstAt.y, burstAt.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                burstAt.x, burstAt.y, burstAt.z, 6, 0.7D, 0.7D, 0.7D, 0.0D);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                burstAt.x, burstAt.y, burstAt.z, 40, 1.6D, 1.2D, 1.6D, 0.25D);

        // 前端炸开的位置：音爆 + 四散的碎风
        Vec3d front = eye.add(dir.multiply(WIND_RANGE * 0.85D));
        world.spawnParticles(ParticleTypes.SONIC_BOOM,
                front.x, front.y, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                front.x, front.y, front.z, 30, 1.8D, 1.8D, 1.8D, 0.3D);

        // 风筒实体：复制大招那根圆柱，换成蓝色贴图并沿视线横放
        spawnWindJet(world, eye, dir);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.EXCALIBUR_WIND,
                SoundCategory.PLAYERS, 1.3F, 1.0F);
    }

    /**
     * 目标是否落在“从眼睛出发、沿 dir 的一条直筒”里。
     *
     * @param offset 目标相对眼睛的向量
     * @param dir    视线方向（单位向量）
     * @param radius 筒半径
     */
    private static boolean inWindCylinder(Vec3d offset, Vec3d dir, double radius) {
        double along = offset.dotProduct(dir);
        if (along < 0.1D || along > WIND_RANGE) {
            return false;
        }
        Vec3d perpendicular = offset.subtract(dir.multiply(along));
        return perpendicular.lengthSquared() <= radius * radius;
    }

    /** Y 轴旋转一个方向向量（角度制） */
    private static Vec3d rotateAroundY(Vec3d vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3d(
                vector.x * cos - vector.z * sin,
                vector.y,
                vector.x * sin + vector.z * cos);
    }

    // ---------------------------------------------------------------- X：誓约胜利之

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        // 只有满蓄力才发射，没蓄满什么都不发
        if (!charged) {
            return;
        }
        castExcalibur(player, true);
    }

    private void castExcalibur(ServerPlayerEntity player, boolean charged) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F).normalize();

        // 音效放在最前面：即使后面的破块/实体/粒子出问题，也一定能听到
        // （誓约胜利之剑的音效在客户端蓄力开始时就播了，这里只补爆炸层）
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                charged ? 1.0F : 0.5F, 0.7F);
        org.examplee.wangsword.Wangsword.LOGGER.info("[Excalibur] cast charged={} pos={}",
                charged, player.getBlockPos());

        double range = charged ? 64.0D : 26.0D;
        // 水平半径（光束粗细）与垂直半径（上下高度）分开：高度给得比粗细大得
        double radiusH = charged ? 5.0D : 2.6D;
        double radiusV = charged ? 10.0D : 4.5D;
        float damage = charged ? 150.0F : 45.0F;
        Vec3d end = eye.add(dir.multiply(range));

        // 地形破坏开关：暂时关闭，改由剑气末端的爆炸表现（要恢复就改true
        if (BREAK_TERRAIN) {
            // 破坏光束沿途的方块：水平半H、垂直半V，形成一个高墙状切口，且不掉落物
            int blockRadiusH = charged ? 3 : 2;
            int blockRadiusV = charged ? 10 : 4;
            // 从身BEAM_START 格才开始破坏，避免把自己脚脚下的地挖掉
            double beamStart = charged ? 6.0D : 4.0D;
            for (double step = beamStart; step <= range; step += 1.0D) {
                BlockPos center = BlockPos.ofFloored(eye.add(dir.multiply(step)));
                for (BlockPos pos : BlockPos.iterate(
                        center.add(-blockRadiusH, -blockRadiusV, -blockRadiusH),
                        center.add(blockRadiusH, blockRadiusV, blockRadiusH))) {
                    int dx = pos.getX() - center.getX();
                    int dz = pos.getZ() - center.getZ();
                    if (dx * dx + dz * dz > blockRadiusH * blockRadiusH) {
                        continue;
                    }
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getHardness(world, pos) < 0) {
                        continue;
                    }
                    if (state.getBlock().getBlastResistance() > 100.0F) {
                        continue;
                    }
                    world.breakBlock(pos, false, player);
                }
            }
        }

        // 视觉：和王者之Shift+右键同一套剑气，换成金色并放大飞出去
        Vec3d up = dir.crossProduct(dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D)));
        if (up.lengthSquared() < 1.0E-6D) {
            up = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            up = up.normalize();
        }
        org.examplee.wangsword.entity.ArcSlashEntity slash = new org.examplee.wangsword.entity.ArcSlashEntity(
                org.examplee.wangsword.registry.EntityRegistry.ARC_SLASH_GOLD, player, world, 0.0F, dir, up);
        slash.setGolden(true);
        slash.setBurstOnEnd(true);
        slash.setBreakBlocks(false);
        slash.setMoveSpeed(1.6D);
        slash.setMaxLife(charged ? 40 : 16);
        slash.setScaleMult(charged ? 3.5F : 2.0F);
        slash.setPosition(eye.add(dir.multiply(0.3D)));
        world.spawnEntity(slash);

        Box area = new Box(eye, end).expand(Math.max(radiusH, radiusV) + 2.0D);
        Vec3d hitPoint = null;
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || target == player) {
                continue;
            }
            Vec3d center = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            double along = center.subtract(eye).dotProduct(dir);
            if (along < -1.0D || along > range) {
                continue;
            }
            Vec3d closest = eye.add(dir.multiply(Math.max(0.0D, along)));
            // 水平按光束粗细判定，垂直单独放宽（能打到光束上下的目标）
            double dx = center.x - closest.x;
            double dz = center.z - closest.z;
            double dy = center.y - closest.y;
            if (Math.sqrt(dx * dx + dz * dz) > radiusH + target.getWidth() * 0.5D) {
                continue;
            }
            if (Math.abs(dy) > radiusV + target.getHeight() * 0.5D) {
                continue;
            }
            target.damage(world.getDamageSources().playerAttack(player), damage);
            if (hitPoint == null) {
                hitPoint = center;
            }
        }

        // 命中即爆：打中目标时立刻在其身上炸出金色光柱
        if (hitPoint != null) {
            org.examplee.wangsword.entity.ArcSlashEntity.goldenExplosion(world, hitPoint, player);
        }

        // 光束
        for (double step = 0.0D; step <= range; step += 1.0D) {
            Vec3d point = eye.add(dir.multiply(step));
            world.spawnParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 3,
                    radiusH * 0.35D, radiusV * 0.35D, radiusH * 0.35D, 0.02D);
            if (charged) {
                world.spawnParticles(ParticleTypes.FIREWORK,
                        point.x, point.y, point.z, 3,
                        radiusH * 0.5D, radiusV * 0.5D, radiusH * 0.5D, 0.05D);
            }
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z, 1, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 说明

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.title").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.stats").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.wind").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.wind_detail").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.ult").formatted(Formatting.YELLOW));
        tooltip.add(Text.translatable("tooltip.wangsword.excalibur.ult_detail").formatted(Formatting.GRAY));
    }
}
