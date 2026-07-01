#version 150

uniform sampler2D Sampler0;   // entity albedo atlas -- read ONLY for the cutout alpha test
uniform float MaxThrow;

in float coneDist;
in vec2 texCoord0;

out vec4 fragColor;

// R = radial distance n (same metric as the block dist shader), G = 1.0 = ENTITY-occluder bit (block dist writes G=0.0)
// so the decal's shadow branch fires only for entity occluders. The >1.0 clear sentinel marks untouched texels.
void main() {
    // Cutout so hat/hair/cape transparent texels do not stamp a solid silhouette block. 0.1 matches entity_depth.fsh.
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    float n = clamp(coneDist / MaxThrow, 0.0, 1.0);
    fragColor = vec4(n, 1.0, 0.0, 1.0);   // G=1.0 -> "this texel's nearest occluder is an ENTITY"
}
