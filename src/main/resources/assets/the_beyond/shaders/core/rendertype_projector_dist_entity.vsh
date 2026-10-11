#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float coneDist;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // RADIAL distance from the lens; Position is the entity's CPU-baked camera-relative world pos, so the lens sits at
    // view-space origin like the block dist shader. METRIC PIN: must equal the block dist vsh and the decal. Never axial.
    coneDist = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    texCoord0 = UV0;
}
