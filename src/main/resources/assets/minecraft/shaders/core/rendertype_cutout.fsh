#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    if (color.a < 0.99) {

        vec2 pixelSize = 1.0 / vec2(textureSize(Sampler0, 0));
        vec2 pixelCoord = texCoord0 / pixelSize;
        vec2 snappedCoord = floor(pixelCoord);

        float offset = FogEnd * 0.01;
        float offset2 = FogStart * 0.01;

        float diagonal = mod(snappedCoord.x * offset + snappedCoord.y * offset, 16.0);

        float size = clamp(color.a, 0.5, 0.8);

        //float var = exp(-abs(diagonal - 16.0));
        float var = 0;
        float line = 1.0 - step(size * 10, abs(diagonal - 16.0));

        vec4 highlight = vec4(0.2 + var, 0.0, 0.2 + var, 0.0) * line * vertexColor * ColorModulator;
        vec4 modifiedColor = vec4(color.rgba + highlight);
        color = modifiedColor;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}