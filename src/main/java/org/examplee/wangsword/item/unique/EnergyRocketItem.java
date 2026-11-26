package org.examplee.wangsword.item.unique;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * 能源火箭。
 *
 * <p>滑翔时右键：生成一个真正的烟花火箭实体（飞行时间 3），推进完全交给原版，
 * 所以速度和原版顶级烟花一致。
 *
 * <p>额外给自己的火箭加紫色能量拖尾 + 信标音效；用实体 UUID 白名单实现，
 * 不影响原版烟花。
 */
public class EnergyRocketItem extends Item {

    /** 火箭耐久 */
    public static final int DURABILITY = 6666;
    /** 每次使用消耗的耐久 */
    private static final int DURABILITY_COST = 1;
    /** 烟花飞行时间（3 = 最顶级） */
    private static final byte FLIGHT = 3;

    /** 自己生成的火箭实体（UUID 白名单） */
    private static final Set<UUID> ACTIVE_ROCKETS = new HashSet<>();

    public EnergyRocketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }

        if (user.isFallFlying()) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            NbtCompound nbt = rocket.getOrCreateNbt();
            NbtCompound fireworks = new NbtCompound();
            fireworks.putByte("Flight", FLIGHT);
            nbt.put("Fireworks", fireworks);
            nbt.putBoolean("ws_energy", true);

            FireworkRocketEntity entity = new FireworkRocketEntity(world, rocket, user);
            world.spawnEntity(entity);
            ACTIVE_ROCKETS.add(entity.getUuid());

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            Vec3d look = user.getRotationVector();
            Vec3d v = user.getVelocity();
            user.setVelocity(v.x + look.x * 0.9, Math.max(v.y, 0.0) + 1.1, v.z + look.z * 0.9);
            user.velocityModified = true;
            user.fallDistance = 0.0F;
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        int cost = Math.min(DURABILITY_COST, stack.getMaxDamage() - stack.getDamage());
        if (cost > 0) {
            stack.damage(cost, user, (p) -> p.sendToolBreakStatus(hand));
        }

        return TypedActionResult.success(stack, false);
    }

    /** 每 tick 给自己的火箭加紫色拖尾和信标音效（服务端调用） */
    public static void tickTrail(MinecraftServer server) {
        if (ACTIVE_ROCKETS.isEmpty()) return;

        Iterator<UUID> it = ACTIVE_ROCKETS.iterator();
        while (it.hasNext()) {
            UUID id = it.next();

            Entity entity = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity found = w.getEntity(id);
                if (found != null) { entity = found; break; }
            }

            if (entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }

            ServerWorld world = (ServerWorld) entity.getWorld();
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            // 紫色能量拖尾
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 4, 0.06, 0.06, 0.06, 0.01);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.08, 0.08, 0.08, 0.02);

            // 信标音效：每 8 tick 一次
            if (entity.age % 8 == 0) {
                world.playSound(null, x, y, z,
                        SoundEvents.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.PLAYERS, 0.7F, 1.5F);
            }
        }
    }

    /** 用能源水晶修理 */
    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.getItem() == org.examplee.wangsword.registry.ItemsRegistry.WS_ENERGY_CRYSTAL;
    }
}