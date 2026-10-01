package com.holyknight.spell;

import com.holyknight.HolyKnight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public class HolyRayVisualEntity extends Entity implements IEntityAdditionalSpawnData {
    public static final int LIFETIME = 15;

    public float distance;

    public HolyRayVisualEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public HolyRayVisualEntity(Level level, Vec3 start, Vec3 end, LivingEntity owner) {
        super(HolyKnight.HOLY_RAY_VISUAL.get(), level);
        this.setPos(start.subtract(0, 0.75F, 0));
        this.distance = (float) start.distanceTo(end);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    public void tick() {
        if (++tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeInt((int) (distance * 10));
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.distance = buffer.readInt() / 10F;
    }
}
