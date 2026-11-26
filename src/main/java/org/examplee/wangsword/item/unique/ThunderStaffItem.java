package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.ThunderBeamEntity;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 雷电法杖：远程雷电武器，右键开启持续 5 秒的紫电风暴，X 释放雷锁囚笼。
 *
 * <p>视觉走独立的 {@link ThunderBeamEntity}（紫雷贴图），判定方式和冰霜法杖一样
 * 是每道闪电开火瞬间一条射线。
 */
public class ThunderStaffItem extends Item implements org.examplee.wangsword.item.ISkillWeapon {

    public static final float RANGE = 48.0F;
    public static final float BASE_DAMAGE = 22.0F;
    public static final float SPLASH_DAMAGE = 6.0F;
    public static final double SPLASH_RADIUS = 2.5D;
    public static final int COOLDOWN = 160;
    public static final String SKILL_BEAM = "thunder_staff_beam";

    public static final int BURST_COUNT = 3;
    public static final int DURATION_TICKS = 100;
    public static final int FIRE_INTERVAL = 20;

    // ------------------------------------------------------------ 被动：雷能蓄涌
    public static final int MAX_MARKS = 5;
    public static final int MARK_DURATION_TICKS = 80;
    public static final float MARK_DAMAGE_BONUS = 0.12F;
    public static final int CHAIN_COUNT = 3;
    public static final float CHAIN_DAMAGE = 16.0F;
    public static final double CHAIN_RADIUS = 6.0D;

    // ------------------------------------------------------------ X：雷锁囚笼
    public static final String SKILL_CAGE = "thunder_staff_cage";
    public static final int ULT_COOLDOWN = 240;
    public static final float CAGE_RANGE = 32.0F;
    public static final int CAGE_LINK_COUNT = 3;
    public static final double CAGE_RADIUS = 8.0D;
    /** 锁链持续 4 秒 */
    public static final int CAGE_DURATION_TICKS = 80;
    /** 起手对每个被锁目标的雷击 */
    public static final float CAGE_IMPACT_DAMAGE = 16.0F;
    public static final float CAGE_HIT_DAMAGE = 16.0F;
    public static final int CAGE_HIT_COOLDOWN = 4;
    public static final float CAGE_BURST_DAMAGE = 20.0F;
    public static final double CAGE_BURST_RADIUS = 2.5D;

    /** 客户端左键放电请求 */
    public static final Identifier CAGE_HIT_PACKET =
            new Identifier("wangsword", "thunder_staff_cage_hit");

    private static final Map<UUID, Storm> STORMS = new HashMap<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static final Map<UUID, Cage> CAGES = new HashMap<>();

    private static final class Storm {
        final long endAt;
        long nextFire;

        Storm(long now) {
            this.endAt = now + DURATION_TICKS;
            this.nextFire = now + FIRE_INTERVAL;
        }
    }

    private static final class Mark {
        final LivingEntity target;
        int stacks;
        long endAt;

        Mark(LivingEntity target) {
            this.target = target;
        }
    }

    private static final class Cage {
        final List<LivingEntity> targets;
        final long endAt;
        long nextHit;
        /** 每条链一束常驻紫雷，跟着目标移动 */
        final List<ThunderBeamEntity> links = new ArrayList<>();

        Cage(List<LivingEntity> targets, long now) {
            this.targets = targets;
            this.endAt = now + CAGE_DURATION_TICKS;
            this.nextHit = now;
        }
    }

    public ThunderStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (user instanceof ServerPlayerEntity sp
                && !beginStorm((ServerWorld) world, sp, hand)) {
            return TypedActionResult.fail(stack);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player
                && org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_BEAM)) {
            beginStorm((ServerWorld) player.getWorld(), player, hand);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private boolean beginStorm(ServerWorld world, ServerPlayerEntity player, Hand hand) {
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_BEAM)) {
            return false;
        }
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_BEAM, COOLDOWN);
        long now = world.getTime();
        STORMS.put(player.getUuid(), new Storm(now));
        fireVolley(world, player, hand, now);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient() || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        long now = world.getTime();
        ServerWorld serverWorld = (ServerWorld) world;

        MARKS.entrySet().removeIf(e ->
                e.getValue().endAt <= now || !e.getValue().target.isAlive());

        Storm storm = STORMS.get(player.getUuid());
        if (storm != null) {
            if (player.isDead() || player.isSpectator() || now >= storm.endAt) {
                STORMS.remove(player.getUuid());
            } else if (now >= storm.nextFire) {
                fireVolley(serverWorld, player, Hand.MAIN_HAND, now);
                storm.nextFire = now + FIRE_INTERVAL;
            }
        }

        Cage cage = CAGES.get(player.getUuid());
        if (cage != null) {
            cage.targets.removeIf(t -> !t.isAlive() || t.isSpectator());
            if (player.isDead() || player.isSpectator()
                    || now >= cage.endAt || cage.targets.isEmpty()) {
                CAGES.remove(player.getUuid());
                breakCage(serverWorld, player, cage);
            } else {
                updateLinks(serverWorld, cage);
            }
        }
    }

    // ---------------------------------------------------------------- 被动

    private void fireVolley(ServerWorld world, ServerPlayerEntity player, Hand hand, long now) {
        // 每轮放电最多触发一次满层爆发：取离玩家最近的满层目标，爆完清零重新叠
        LivingEntity overflow = null;
        double best = Double.MAX_VALUE;
        for (Mark mark : MARKS.values()) {
            if (mark.stacks >= MAX_MARKS && mark.endAt > now && mark.target.isAlive()) {
                double d = mark.target.squaredDistanceTo(player);
                if (d < best) {
                    best = d;
                    overflow = mark.target;
                }
            }
        }
        if (overflow != null) {
            burstChain(world, player, overflow);
            MARKS.remove(overflow.getUuid());
        }

        Set<UUID> hit = new HashSet<>();
        Set<LivingEntity> marked = new HashSet<>();
        for (int i = 0; i < BURST_COUNT; i++) {
            fireBolt(world, player, i, hit, marked);
        }
        for (LivingEntity target : marked) {
            addMark(target, now);
        }
        player.swingHand(hand, true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0F, 1.35F);
    }

    private void addMark(LivingEntity target, long now) {
        Mark mark = MARKS.computeIfAbsent(target.getUuid(), k -> new Mark(target));
        mark.stacks = Math.min(MAX_MARKS, mark.stacks + 1);
        // 满层是「蓄满待发」，锁住等下一次放电触发爆发，不再按 4 秒自然过期
        mark.endAt = mark.stacks >= MAX_MARKS ? Long.MAX_VALUE : now + MARK_DURATION_TICKS;
    }

    private void burstChain(ServerWorld world, ServerPlayerEntity player, LivingEntity source) {
        Vec3d at = source.getBoundingBox().getCenter();
        List<LivingEntity> near = new ArrayList<>();
        for (Entity e : world.getOtherEntities(player, new Box(at, at).expand(CHAIN_RADIUS))) {
            if (e instanceof LivingEntity living && e != source
                    && living.isAlive() && !living.isSpectator() && living.canHit()) {
                near.add(living);
            }
        }
        near.sort(Comparator.comparingDouble(o -> o.squaredDistanceTo(at)));

        float[] damage = {CHAIN_DAMAGE, CHAIN_DAMAGE * 0.75F, CHAIN_DAMAGE * 0.5F};
        for (int i = 0; i < Math.min(CHAIN_COUNT, near.size()); i++) {
            LivingEntity target = near.get(i);
            rawDamage(world, player, target, damage[i]);
            Vec3d p = target.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 8, 0.20D, 0.20D, 0.20D, 0.02D);
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                at.x, at.y, at.z, 16, 0.40D, 0.40D, 0.40D, 0.03D);
    }

    /** 只算雷痕加成后打伤害，不做囚笼分担 */
    private static void rawDamage(ServerWorld world, ServerPlayerEntity player,
                                  LivingEntity target, float base) {
        Mark mark = MARKS.get(target.getUuid());
        float stacks = mark != null && mark.endAt > world.getTime() ? mark.stacks : 0;
        target.damage(world.getDamageSources().lightningBolt(),
                base * (1.0F + MARK_DAMAGE_BONUS * stacks));
    }

    private static void applyDamage(ServerWorld world, ServerPlayerEntity player,
                                    LivingEntity target, float base) {
        Mark mark = MARKS.get(target.getUuid());
        float stacks = mark != null && mark.endAt > world.getTime() ? mark.stacks : 0;
        float damage = base * (1.0F + MARK_DAMAGE_BONUS * stacks);
        target.damage(world.getDamageSources().lightningBolt(), damage);

        Cage cage = CAGES.get(player.getUuid());
        if (cage == null || cage.endAt <= world.getTime() || !cage.targets.contains(target)) {
            return;
        }
        for (LivingEntity other : cage.targets) {
            if (other != target && other.isAlive()) {
                other.damage(world.getDamageSources().lightningBolt(), damage * 0.5F);
            }
        }
    }

    // ---------------------------------------------------------------- 右键风暴

    private void fireBolt(ServerWorld world, ServerPlayerEntity player, int index,
                          Set<UUID> hit, Set<LivingEntity> marked) {
        Vec3d base = player.getRotationVector().normalize();
        Vec3d worldUp = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = base.crossProduct(worldUp);
        if (right.lengthSquared() < 1.0E-4D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(base).normalize();

        float spread = (index - (BURST_COUNT - 1) / 2.0F) * 0.14F;
        float jitter = (float) ((Math.random() - 0.5D) * 0.06D);
        Vec3d dir = base.add(right.multiply(spread)).add(up.multiply(jitter)).normalize();

        Vec3d start = player.getEyePos();
        Vec3d end = start.add(dir.multiply(RANGE));

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        LivingEntity target = findTarget(world, player, start, end);
        Vec3d beamEnd = end;
        if (target != null) {
            beamEnd = target.getBoundingBox().getCenter();
            marked.add(target);
            if (hit.add(target.getUuid())) {
                applyDamage(world, player, target, BASE_DAMAGE);
                splash(world, player, target);
            }
        }

        spawnBeam(world, player, start, beamEnd, dir);
    }

    private void splash(ServerWorld world, ServerPlayerEntity user, LivingEntity hit) {
        for (Entity entity : world.getOtherEntities(user,
                hit.getBoundingBox().expand(SPLASH_RADIUS))) {
            if (entity == hit || !(entity instanceof LivingEntity living)
                    || !living.isAlive() || living.isSpectator() || !living.canHit()) {
                continue;
            }
            rawDamage(world, user, living, SPLASH_DAMAGE);
        }
    }

    private void spawnBeam(ServerWorld world, PlayerEntity user, Vec3d eye, Vec3d end, Vec3d forward) {
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);

        Vec3d right = forward.crossProduct(up);
        if (right.lengthSquared() < 1.0E-4D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }

        Vec3d origin = eye.add(forward.multiply(0.9D))
                .add(right.multiply(0.30D))
                .add(up.multiply(-0.22D));

        Vec3d toEnd = end.subtract(origin);
        double length = toEnd.length();
        if (length < 0.3D) {
            return;
        }
        Vec3d unit = toEnd.multiply(1.0D / length);

        float yaw = (float) Math.toDegrees(Math.atan2(-unit.x, unit.z));
        float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(unit.y, -1.0D, 1.0D)));

        ThunderBeamEntity beam = new ThunderBeamEntity(EntityRegistry.THUNDER_BEAM, world);
        beam.setPosition(origin.x, origin.y, origin.z);
        beam.setYaw(yaw);
        beam.setPitch(pitch);
        beam.setLength((float) length);
        world.spawnEntity(beam);
    }

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

            double hitDistance = Math.max(0.0D, along - radius);
            if (hitDistance < bestDistance) {
                bestDistance = hitDistance;
                best = living;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- X：雷锁囚笼

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof ThunderStaffItem)) {
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_CAGE)) {
            return;
        }

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVector().normalize();
        Vec3d end = start.add(dir.multiply(CAGE_RANGE));
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        LivingEntity first = findTarget(world, player, start, end);
        if (first == null) {
            return;
        }

        Vec3d at = first.getBoundingBox().getCenter();
        List<LivingEntity> near = new ArrayList<>();
        for (Entity e : world.getOtherEntities(player, new Box(at, at).expand(CAGE_RADIUS))) {
            if (e instanceof LivingEntity living && e != first
                    && living.isAlive() && !living.isSpectator() && living.canHit()) {
                near.add(living);
            }
        }
        near.sort(Comparator.comparingDouble(o -> o.squaredDistanceTo(at)));

        List<LivingEntity> targets = new ArrayList<>();
        targets.add(first);
        for (int i = 0; i < Math.min(CAGE_LINK_COUNT, near.size()); i++) {
            targets.add(near.get(i));
        }

        long now = world.getTime();
        Cage cage = new Cage(targets, now);
        CAGES.put(player.getUuid(), cage);
        for (LivingEntity target : targets) {
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, CAGE_DURATION_TICKS, 2, false, true, true));
            rawDamage(world, player, target, CAGE_IMPACT_DAMAGE);
            strike(world, target);
        }
        // 先把链画出来，别等下一次 inventoryTick
        updateLinks(world, cage);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.4F, 0.7F);
        player.swingHand(Hand.MAIN_HAND, true);
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_CAGE, ULT_COOLDOWN);
    }

    /** 服务端收到左键放电请求 */
    public static void onCageHit(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof ThunderStaffItem)) {
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        Cage cage = CAGES.get(player.getUuid());
        if (cage == null) {
            return;
        }
        long now = world.getTime();
        if (now >= cage.endAt || now < cage.nextHit) {
            return;
        }
        cage.nextHit = now + CAGE_HIT_COOLDOWN;

        for (LivingEntity target : cage.targets) {
            if (!target.isAlive()) {
                continue;
            }
            rawDamage(world, player, target, CAGE_HIT_DAMAGE);
            strike(world, target);
        }
        player.swingHand(Hand.MAIN_HAND, true);
    }

    /** 从目标头顶劈下一道纯视觉紫雷（竖直光束，不发声、不造成额外伤害） */
    private static void strike(ServerWorld world, LivingEntity target) {
        Vec3d center = target.getBoundingBox().getCenter();
        Vec3d top = center.add(0.0D, 3.2D, 0.0D);
        ThunderBeamEntity beam = new ThunderBeamEntity(EntityRegistry.THUNDER_BEAM, world);
        beam.setPosition(top.x, top.y, top.z);
        beam.setYaw(0.0F);
        beam.setPitch(90.0F);
        beam.setLength(3.2F);
        beam.setMaxLife(12);
        world.spawnEntity(beam);
    }

    /**
     * 每条链挂一束常驻紫雷，每 tick 把起点/朝向/长度刷到两个目标之间。
     * 目标在动也不会断，比每 tick 撒粒子明显得多。
     */
    private static void updateLinks(ServerWorld world, Cage cage) {
        int need = cage.targets.size() - 1;
        if (need <= 0) {
            return;
        }
        while (cage.links.size() < need) {
            ThunderBeamEntity beam = new ThunderBeamEntity(EntityRegistry.THUNDER_BEAM, world);
            beam.setMaxLife(CAGE_DURATION_TICKS + 6);
            world.spawnEntity(beam);
            cage.links.add(beam);
        }
        while (cage.links.size() > need) {
            cage.links.remove(cage.links.size() - 1).discard();
        }

        Vec3d anchor = cage.targets.get(0).getBoundingBox().getCenter();
        for (int i = 1; i < cage.targets.size(); i++) {
            ThunderBeamEntity beam = cage.links.get(i - 1);
            Vec3d to = cage.targets.get(i).getBoundingBox().getCenter();
            Vec3d delta = to.subtract(anchor);
            double len = delta.length();
            if (len < 0.2D) {
                continue;
            }
            Vec3d unit = delta.multiply(1.0D / len);
            float yaw = (float) Math.toDegrees(Math.atan2(-unit.x, unit.z));
            float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(unit.y, -1.0D, 1.0D)));

            beam.setPosition(anchor.x, anchor.y, anchor.z);
            beam.setYaw(yaw);
            beam.setPitch(pitch);
            beam.setLength((float) len);
        }
    }

    private static void breakCage(ServerWorld world, ServerPlayerEntity player, Cage cage) {
        for (ThunderBeamEntity beam : cage.links) {
            beam.discard();
        }
        for (LivingEntity target : cage.targets) {
            Vec3d at = target.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    at.x, at.y, at.z, 16, 0.35D, 0.35D, 0.35D, 0.05D);
            for (Entity e : world.getOtherEntities(player,
                    new Box(at, at).expand(CAGE_BURST_RADIUS))) {
                if (e instanceof LivingEntity living
                        && living.isAlive() && !living.isSpectator() && living.canHit()) {
                    rawDamage(world, player, living, CAGE_BURST_DAMAGE);
                }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.stats",
                (int) BASE_DAMAGE, (int) RANGE));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.beam.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.beam.desc",
                (int) SPLASH_DAMAGE));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.passive.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.passive.desc",
                MAX_MARKS, MARK_DURATION_TICKS / 20, (int) (MARK_DAMAGE_BONUS * 100)));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.cage.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.cage.desc",
                CAGE_LINK_COUNT,
                String.format(Locale.ROOT, "%.1f", CAGE_DURATION_TICKS / 20.0F),
                String.format(Locale.ROOT, "%.1f", ULT_COOLDOWN / 20.0F)));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_staff.hint",
                String.format(Locale.ROOT, "%.1f", COOLDOWN / 20.0F)));
    }
}
