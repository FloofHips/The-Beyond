package com.thebeyond.common.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.util.livingblock.movement.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.nio.ByteBuffer;

public class BeyondEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, TheBeyond.MODID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<MovementData>> MOVEMENT_DATA =
            ENTITY_DATA_SERIALIZERS.register("movement_data", () -> new EntityDataSerializer<MovementData>() {

                public void write(FriendlyByteBuf buf, MovementData value) {
                    if (value instanceof BouncingMovement.Data data) {
                        buf.writeByte(1);
                        data.write(buf);
                    } else if (value instanceof RollingMovement.Data data) {
                        buf.writeByte(2);
                        data.write(buf);
                    } else if (value instanceof FloatingMovement.Data data) {
                        buf.writeByte(3);
                        data.write(buf);
                    } else {
                        buf.writeByte(0);
                    }
                }

                public MovementData read(FriendlyByteBuf buf) {
                    byte type = buf.readByte();
                    return switch (type) {
                        case 1 -> BouncingMovement.Data.read(buf);
                        case 2 -> RollingMovement.Data.read(buf);
                        case 3 -> FloatingMovement.Data.read(buf);
                        default -> MovementData.EMPTY;
                    };
                }

                @Override
                public MovementData copy(MovementData value) {
                    return value;
                }

                @Override
                public StreamCodec<? super FriendlyByteBuf, MovementData> codec() {
                    return new StreamCodec<FriendlyByteBuf, MovementData>() {
                        @Override
                        public MovementData decode(FriendlyByteBuf buf) {
                            return read(buf);
                        }

                        @Override
                        public void encode(FriendlyByteBuf buf, MovementData value) {
                            write(buf, value);
                        }
                    };
                }
            });

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Target>> TARGET =
            ENTITY_DATA_SERIALIZERS.register("target", () -> new EntityDataSerializer<Target>() {

                public void write(FriendlyByteBuf buf, Target value) {
                    if (value instanceof Target.PositionTarget pos) {
                        buf.writeByte(1);
                        pos.write(buf);
                    } else if (value instanceof Target.EntityTarget entity) {
                        buf.writeByte(2);
                        entity.write(buf);
                    } else {
                        buf.writeByte(0);
                    }
                }

                public Target read(FriendlyByteBuf buf) {
                    byte type = buf.readByte();
                    return switch (type) {
                        case 1 -> Target.PositionTarget.read(buf);
                        case 2 -> Target.EntityTarget.read(buf);
                        default -> Target.NONE;
                    };
                }

                @Override
                public Target copy(Target value) {
                    return value;
                }

                @Override
                public StreamCodec<? super FriendlyByteBuf, Target> codec() {
                    return new StreamCodec<FriendlyByteBuf, Target>() {
                        @Override
                        public Target decode(FriendlyByteBuf buf) {
                            return read(buf);
                        }

                        @Override
                        public void encode(FriendlyByteBuf buf, Target value) {
                            write(buf, value);
                        }
                    };
                }
            });



    public record TrinketGrowth(byte[] growths) {}
    private static final int MAX_GROWTH_SIZE = 16 * 16;
    private static final Codec<byte[]> GROWTH_BYTES = Codec.BYTE_BUFFER.xmap(
            bb -> {
                byte[] a = new byte[bb.remaining()];
                bb.get(a);
                return a;
            },
            ByteBuffer::wrap);

    public static final Codec<TrinketGrowth> TRINKET_GROWTH_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    GROWTH_BYTES.fieldOf("growths").forGetter(TrinketGrowth::growths)
            ).apply(instance, TrinketGrowth::new));

    public static final StreamCodec<ByteBuf, TrinketGrowth> TRINKET_GROWTH_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.byteArray(MAX_GROWTH_SIZE), TrinketGrowth::growths,
                    TrinketGrowth::new);


    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<TrinketGrowth>> TRINKET_GROWTH =
            ENTITY_DATA_SERIALIZERS.register("trinket_growth", () -> EntityDataSerializer.forValueType(TRINKET_GROWTH_STREAM_CODEC));

}