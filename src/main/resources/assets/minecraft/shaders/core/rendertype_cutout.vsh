#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec4 rawColor;      // Original vertex color (without lightmap), used for crystal detection
out vec2 texCoord0;
out vec3 worldPos;      // World-space position for continuous shine across stacked blocks

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    rawColor = Color;   // Preserve the vertex color without lightmap multiplication
    worldPos = pos;     // Pass world position for cross-block continuous patterns

    texCoord0 = UV0;
}
