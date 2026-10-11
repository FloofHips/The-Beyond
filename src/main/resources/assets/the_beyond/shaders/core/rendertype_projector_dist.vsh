#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float coneDist;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // RADIAL distance from the lens (ModelViewMat = projector VIEW, Position camera-relative). METRIC PIN: this length()
    // MUST match the decal's length(worldRel - ProjectorEye). Never axial pc.z/pc.w.
    coneDist = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    texCoord0 = UV0;
}
