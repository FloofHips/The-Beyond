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
in float timeValue;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    // Standard cutout alpha discard
    if (color.a < 0.1) {
        discard;
    }

    // === Crystal shine effect (applies to ALL pixels in this RenderType) ===
    // This dedicated shader only runs on crystals, no detection needed.
    vec2 pixelSize = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 pixelCoord = texCoord0 / pixelSize;
    vec2 snappedCoord = floor(pixelCoord);

    float offset = FogEnd * 0.01;

    float diagonal = mod(snappedCoord.x * offset + snappedCoord.y * offset, 16.0);

    // Use pixel luminance to modulate shine line width
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float size = clamp(luminance, 0.3, 0.8);

    float line = 1.0 - step(size * 10.0, abs(diagonal - 16.0));

    // Purple/magenta highlight modulated by luminance
    float intensity = 0.15 + 0.1 * luminance;
    vec4 highlight = vec4(intensity, 0.0, intensity, 0.0) * line * vertexColor * ColorModulator;
    color = vec4(color.rgb + highlight.rgb, color.a);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
