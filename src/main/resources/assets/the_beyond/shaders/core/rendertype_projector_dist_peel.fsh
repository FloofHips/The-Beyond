#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;   // first depth layer this peel reads past
uniform float MaxThrow;

in float coneDist;
in vec2 texCoord0;

out vec4 fragColor;

// Second depth layer: nearest surface beyond the first; LEQUAL keeps the nearest survivor.
void main() {
    if (texture(Sampler0, texCoord0).a < 0.5) {
        discard;
    }
    float n = clamp(coneDist / MaxThrow, 0.0, 1.0);
    float first = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    if (n <= first + 0.25 / MaxThrow) {
        discard;
    }
    fragColor = vec4(n, 0.0, n, 1.0);
}
