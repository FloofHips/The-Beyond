#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;   // the first layer as the block pass left it, before any block entity
uniform float MaxThrow;

in float coneDist;
in vec2 texCoord0;

out vec4 fragColor;

// MIN keeps the nearest peel candidate: the first block surface when in front of it, the block entity itself when past it.
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    float n = clamp(coneDist / MaxThrow, 0.0, 1.0);
    float first = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    float second;
    if (n < first - 0.25 / MaxThrow) {
        second = first;
    } else if (n > first + 0.25 / MaxThrow) {
        second = n;
    } else {
        discard;
    }
    fragColor = vec4(second, 0.0, second, 1.0);
}
