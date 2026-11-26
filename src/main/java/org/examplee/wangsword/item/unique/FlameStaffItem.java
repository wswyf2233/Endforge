package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.FlameBoltEntity;
import org.examplee.wangsword.entity.FlamePatchEntity;
import org.examplee.wangsword.entity.FlamePillarEntity;
import org.examplee.wangsword.registry.EntityRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 火焰法杖：右键烈焰弹，X 熔火领域，被动燎原。
 *
 * <p>右键的火球继承原版小火球，命中后炸出范围火焰并留一片火地；
 * X 在准心落点展开持续领域，边缘立一圈火柱；被动负责让烧死的目标炸火星连锁。
 */
public class FlameStaffItem extends Item implements org.examplee.wangsword.item.ISkillWeapon {

    // ------------------------------------------------------------ 右键：烈焰弹
    public static final float BOLT_DAMAGE = 18.0F;
    /** 火球每 tick 飞行格数 */
    public static final float BOLT_SPEED = 0.9F;
    /** 0.8 秒 */
    public static final int BOLT_COOLDOWN = 16;
    public static final double EXPLOSION_RADIUS = 3.0D;
    public static final int BURN_SECONDS = 5;
    public static final String SKILL_BOLT = "flame_staff_bolt";

    /** 熔岩蝠晶充能的强化弹：伤害 ×1.5、爆炸半径 +1、点燃 8 秒 */
    public static final float EMPOWERED_DAMAGE_MULT = 1.5F;
    public static final double EMPOWERED_RADIUS_BONUS = 1.0D;
    public static final int EMPOWERED_BURN_SECONDS = 8;
    /** 每发强化弹扣多少点蝠晶耐久 */
    public static final int EMPOWERED_CRYSTAL_COST = 1;

    /** 命中后留下的小片火地 */
    public static final int PATCH_DURATION = 60;
    public static final int PATCH_PULSE = 10;
    public static final float PATCH_DAMAGE = 4.0F;
    public static final float PATCH_RADIUS = 2.5F;

    // ------------------------------------------------------------ X：熔火领域
    public static final String SKILL_FIELD = "flame_staff_field";
    /** 25 秒 */
    public static final int ULT_COOLDOWN = 500;
    public static final float FIELD_RANGE = 32.0F;
    public static final float FIELD_RADIUS = 6.0F;
    public static final float FIELD_DAMAGE = 12.0F;
    /** 6 秒 */
    public static final int FIELD_DURATION = 120;
    /** 每秒结算一次 */
    public static final int FIELD_PULSE = 20;
    /** 边缘火柱数量 */
    public static final int FIELD_PILLARS = 6;

    // ------------------------------------------------------------ 被动：燎原
    public static final float WILDFIRE_DAMAGE = 10.0F;
    public static final double WILDFIRE_RADIUS = 3.0D;
    /** 被本法杖点燃过的目标，死亡时才能炸火星 */
    private static final Set<UUID> BURNING = new HashSet<>();

    public FlameStaffItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_BOLT)) {
            return TypedActionResult.fail(stack);
        }

        Vec3d dir = user.getRotationVector().normalize();
        boolean empowered = consumeCrystalCharge(player);
        world.spawnEntity(FlameBoltEntity.create(world, user, dir, empowered));
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS,
                empowered ? 1.0F : 0.9F, empowered ? 0.85F : 1.15F);
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_BOLT, BOLT_COOLDOWN);
        user.swingHand(hand, true);
        return TypedActionResult.success(stack);
    }

    /** 对着生物右键走 useOnEntity，不补这条就变成"瞄准谁就打不出谁" */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player
                && org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_BOLT)) {
            Vec3d dir = user.getRotationVector().normalize();
            boolean empowered = consumeCrystalCharge(player);
            player.getWorld().spawnEntity(FlameBoltEntity.create(player.getWorld(), user, dir, empowered));
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS,
                    empowered ? 1.0F : 0.9F, empowered ? 0.85F : 1.15F);
            org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_BOLT, BOLT_COOLDOWN);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /**
     * 找一块还有耐久的熔岩蝠晶，扣 {@value #EMPOWERED_CRYSTAL_COST} 点耐久。
     *
     * <p>不是一发吞一个晶体：一块蝠晶 64 点耐久，打完才碎。创造模式直接算作
     * 充能成功、不扣耐久，省得测试时背包里揣一堆。
     *
     * @return 这一发是不是强化弹
     */
    private static boolean consumeCrystalCharge(ServerPlayerEntity player) {
        if (player.isCreative()) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack crystal = inventory.getStack(i);
            if (!crystal.isOf(
                    org.examplee.wangsword.registry.ItemsRegistry.WS_CALDERA_CRYSTAL)) {
                continue;
            }
            int next = crystal.getDamage() + EMPOWERED_CRYSTAL_COST;
            if (next >= crystal.getMaxDamage()) {
                crystal.decrement(1);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.7F, 1.25F);
            } else {
                crystal.setDamage(next);
            }
            return true;
        }
        return false;
    }

    /** 火球命中：范围火焰伤害 + 点燃 + 留一片火地 */
    public static void explode(ServerWorld world, Entity owner, Vec3d pos) {
        explode(world, owner, pos, false);
    }

    /**
     * @param empowered 是否为熔岩蝠晶强化的弹体：伤害、爆炸半径、燃烧时间都上一档
     */
    public static void explode(ServerWorld world, Entity owner, Vec3d pos, boolean empowered) {
        double radius = empowered ? EXPLOSION_RADIUS + EMPOWERED_RADIUS_BONUS : EXPLOSION_RADIUS;
        float damage = empowered ? BOLT_DAMAGE * EMPOWERED_DAMAGE_MULT : BOLT_DAMAGE;
        int burn = empowered ? EMPOWERED_BURN_SECONDS : BURN_SECONDS;

        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.3D, pos.z, 24, 1.0D, 0.6D, 1.0D, 0.06D);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3D, pos.z, 12, 0.9D, 0.4D, 0.9D, 0.25D);
        if (empowered) {
            // 强化弹：白热芯 + 一圈上冲的魂火，和普通弹一眼能分出来
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 0.4D, pos.z, 26, 1.3D, 0.9D, 1.3D, 0.05D);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.6D, pos.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                empowered ? 1.3F : 1.1F, empowered ? 1.0F : 1.25F);

        var source = owner instanceof PlayerEntity player
                ? world.getDamageSources().playerAttack(player)
                : world.getDamageSources().magic();

        for (Entity entity : world.getOtherEntities(owner, new Box(pos, pos).expand(radius))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            living.damage(source, damage);
            living.setOnFireFor(burn);
            markBurning(living);
        }

        // 火地是一张贴地圆盘：命中点在生物身上时往下找地面，否则焦土会浮在半空
        Vec3d ground = pos;
        BlockHitResult down = world.raycast(new RaycastContext(
                pos.add(0.0D, 0.5D, 0.0D), pos.add(0.0D, -4.0D, 0.0D),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
        if (down.getType() != HitResult.Type.MISS) {
            ground = down.getPos();
        }

        FlamePatchEntity patch = new FlamePatchEntity(EntityRegistry.FLAME_PATCH, world);
        patch.configure(ground,
                empowered ? PATCH_RADIUS + 0.7F : PATCH_RADIUS,
                empowered ? PATCH_DAMAGE + 2.0F : PATCH_DAMAGE,
                PATCH_PULSE, PATCH_DURATION, true);
        world.spawnEntity(patch);
    }

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof FlameStaffItem)) {
            return;
        }
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_FIELD)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVector().normalize();
        Vec3d reach = eye.add(dir.multiply(FIELD_RANGE));

        BlockHitResult hit = world.raycast(new RaycastContext(
                eye, reach, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d base = hit.getType() == HitResult.Type.MISS
                ? new Vec3d(eye.x + dir.x * 16.0D, player.getY(), eye.z + dir.z * 16.0D)
                : hit.getPos().add(0.0D, 0.05D, 0.0D);

        FlamePatchEntity field = new FlamePatchEntity(EntityRegistry.FLAME_PATCH, world);
        field.configure(base, FIELD_RADIUS, FIELD_DAMAGE, FIELD_PULSE, FIELD_DURATION, true);
        field.setStyle(1);
        world.spawnEntity(field);

        // 边缘一圈火柱，把领域范围立起来
        for (int i = 0; i < FIELD_PILLARS; i++) {
            double angle = Math.PI * 2.0D * i / FIELD_PILLARS;
            FlamePillarEntity pillar = new FlamePillarEntity(EntityRegistry.FLAME_PILLAR, world);
            pillar.setPosition(
                    base.x + Math.cos(angle) * FIELD_RADIUS,
                    base.y,
                    base.z + Math.sin(angle) * FIELD_RADIUS);
            world.spawnEntity(pillar);
        }

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 0.5D, base.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.playSound(null, base.x, base.y, base.z,
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.6F, 0.65F);
        player.sendMessage(Text.translatable("message.wangsword.flame_staff.field"), true);
        player.swingHand(Hand.MAIN_HAND, true);
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_FIELD, ULT_COOLDOWN);
    }

    // ------------------------------------------------------------ 被动：燎原

    private static void markBurning(LivingEntity target) {
        BURNING.add(target.getUuid());
    }

    /**
     * 被本法杖点着的目标死了：炸一圈火星，点燃并伤害周围敌人。
     * 火星点到的新目标也会入表，所以能继续连锁，直到周围没人。
     */
    public static void onBurnDeath(LivingEntity dead) {
        if (!BURNING.remove(dead.getUuid())) {
            return;
        }
        if (!(dead.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d at = dead.getPos();
        world.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 0.5D, at.z,
                18, 1.2D, 0.6D, 1.2D, 0.05D);
        world.spawnParticles(ParticleTypes.LAVA, at.x, at.y + 0.5D, at.z,
                6, 0.8D, 0.4D, 0.8D, 0.2D);

        var source = world.getDamageSources().magic();
        for (Entity entity : world.getOtherEntities(dead, new Box(at, at).expand(WILDFIRE_RADIUS))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()
                    || living.isSpectator() || !living.canHit()) {
                continue;
            }
            living.damage(source, WILDFIRE_DAMAGE);
            living.setOnFireFor(4);
            markBurning(living);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.bolt.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.bolt.desc",
                (int) BOLT_DAMAGE, (int) EXPLOSION_RADIUS, BURN_SECONDS));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.field.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.field.desc",
                (int) FIELD_RADIUS,
                String.format(Locale.ROOT, "%.0f", FIELD_DURATION / 20.0F),
                (int) FIELD_DAMAGE,
                String.format(Locale.ROOT, "%.0f", ULT_COOLDOWN / 20.0F)));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.passive.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_staff.passive.desc",
                (int) WILDFIRE_DAMAGE, (int) WILDFIRE_RADIUS));
    }
}
