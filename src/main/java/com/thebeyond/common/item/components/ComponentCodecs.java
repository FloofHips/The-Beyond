package com.thebeyond.common.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;

public class ComponentCodecs {
    public static final Codec<Components.DynamicColorComponent> DYNAMIC_COLOR_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("red").forGetter(Components.DynamicColorComponent::red),
                    Codec.FLOAT.fieldOf("green").forGetter(Components.DynamicColorComponent::green),
                    Codec.FLOAT.fieldOf("blue").forGetter(Components.DynamicColorComponent::blue),
                    Codec.FLOAT.fieldOf("alpha").forGetter(Components.DynamicColorComponent::alpha),
                    Codec.FLOAT.fieldOf("roffset").forGetter(Components.DynamicColorComponent::roffset),
                    Codec.FLOAT.fieldOf("goffset").forGetter(Components.DynamicColorComponent::goffset),
                    Codec.FLOAT.fieldOf("boffset").forGetter(Components.DynamicColorComponent::boffset),
                    Codec.FLOAT.fieldOf("aoffset").forGetter(Components.DynamicColorComponent::aoffset),
                    Codec.INT.fieldOf("brightness").forGetter(Components.DynamicColorComponent::brightness)
            ).apply(instance, Components.DynamicColorComponent::new));

    public static final StreamCodec<ByteBuf, Components.DynamicColorComponent> DYNAMIC_COLOR_STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public Components.DynamicColorComponent decode(ByteBuf buf) {
                    return new Components.DynamicColorComponent(
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readInt()
                    );
                }

                @Override
                public void encode(ByteBuf buf, Components.DynamicColorComponent component) {
                    buf.writeFloat(component.red());
                    buf.writeFloat(component.green());
                    buf.writeFloat(component.blue());
                    buf.writeFloat(component.alpha());
                    buf.writeFloat(component.roffset());
                    buf.writeFloat(component.goffset());
                    buf.writeFloat(component.boffset());
                    buf.writeFloat(component.aoffset());
                    buf.writeInt(component.brightness());
                }
            };
}
