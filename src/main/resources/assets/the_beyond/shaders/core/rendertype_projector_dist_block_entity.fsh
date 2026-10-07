#version 150

uniform sampler2D Sampler0;   // the renderer's own texture, read ONLY for the cutout alpha test
uniform float MaxThrow;

in float coneDist;
in vec2 texCoord0;

out vec4 fragColor;

// A block entity is a block to the decal: G = 0 and B = n like the block dist shader, so its shadow follows the block rules.
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    float n = clamp(coneDist / MaxThrow, 0.0, 1.0);
    fragColor = vec4(n, 0.0, n, 1.0);
}
