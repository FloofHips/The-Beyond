#version 150

uniform sampler2D Sampler0;   // block atlas -- read ONLY for the cutout alpha test
uniform float MaxThrow;

in float coneDist;
in vec2 texCoord0;

out vec4 fragColor;

// RGBA16F target: R = radial distance n in [0,1] (hardware-filterable), G = 0 (block bit), B = the same n -- the entity
// pass masks B off, so B keeps the nearest BLOCK under entity fragments. The >1.0 clear sentinels mark untouched texels.
void main() {
    // Cutout: models trim their shape with texture transparency over larger helper quads (a torch's side faces span the
    // full block); without it those quads stamp a full-block occluder and the shadow dwarfs the model.
    if (texture(Sampler0, texCoord0).a < 0.5) {
        discard;
    }
    float n = clamp(coneDist / MaxThrow, 0.0, 1.0);
    fragColor = vec4(n, 0.0, n, 1.0);
}
