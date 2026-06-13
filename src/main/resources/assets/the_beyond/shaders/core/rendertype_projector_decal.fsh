#version 150

uniform sampler2D Sampler0;       // slot image
uniform sampler2D Sampler1;       // scene depth copy
uniform sampler2D Sampler2;       // depth map (RGBA16F): R = radial dist, G = entity bit, B = blocks-only dist; GL_LINEAR
uniform sampler2D Sampler3;       // peel: nearest block surface beyond the first
uniform mat4 InverseViewProj;     // (ProjMat*ModelViewMat)^-1: NDC + depth -> camera-relative world pos
uniform mat4 ProjectorViewProj;   // projector PROJ*VIEW (camera-relative)
uniform vec3 ProjectorEye;        // lens, camera-relative; radial metric origin (matches the dist shader)
uniform vec2 ScreenSize;
uniform vec4 ConeParams;          // x coneK, y maxThrow, z z-fight floor, w texel (1/depthRes)
uniform vec4 ImageRegion;         // u0,v0,u1,v1: slot sub-rect
uniform vec4 Flags;               // x flipV, y shadowStrength, z opacity, w near-camera cutoff in blocks (0 = off)
uniform float DebugMode;          // F3: false-color diagnostics

out vec4 fragColor;

vec3 reconstructRel(vec2 uvN, float dN) {
    vec4 ndcN = vec4(uvN * 2.0 - 1.0, dN * 2.0 - 1.0, 1.0);
    vec4 wpN = InverseViewProj * ndcN;
    return wpN.xyz / wpN.w;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float d = texture(Sampler1, uv).r;
    if (d >= 1.0) {
        discard;                                  // sky
    }

    vec4 ndc = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 wp = InverseViewProj * ndc;
    vec3 worldRel = wp.xyz / wp.w;

    // Near-camera cutoff (shaderpack path): Iris z-scales the 1st-person hand to ~0 from the camera; discard so the beam
    // never paints the held item. 0 = off.
    if (Flags.w > 0.0 && length(worldRel) < Flags.w) {
        discard;
    }

    float maxThrow = ConeParams.y;
    float coneK = ConeParams.x;

    // Receiver normal from neighbor depth taps (closer-in-depth side per axis): the 2x2 derivative quad blends two planes
    // into garbage at an arris. Pre-projection so the receiver can be normal-offset.
    vec2 px = 1.0 / ScreenSize;
    float dL = texture(Sampler1, uv - vec2(px.x, 0.0)).r;
    float dR = texture(Sampler1, uv + vec2(px.x, 0.0)).r;
    float dD = texture(Sampler1, uv - vec2(0.0, px.y)).r;
    float dU = texture(Sampler1, uv + vec2(0.0, px.y)).r;
    bool pickL = dL < 1.0 && (dR >= 1.0 || abs(dL - d) <= abs(dR - d));
    vec3 tX = pickL ? worldRel - reconstructRel(uv - vec2(px.x, 0.0), dL)
            : (dR < 1.0 ? reconstructRel(uv + vec2(px.x, 0.0), dR) - worldRel : vec3(0.0));
    bool pickD = dD < 1.0 && (dU >= 1.0 || abs(dD - d) <= abs(dU - d));
    vec3 tY = pickD ? worldRel - reconstructRel(uv - vec2(0.0, px.y), dD)
            : (dU < 1.0 ? reconstructRel(uv + vec2(0.0, px.y), dU) - worldRel : vec3(0.0));
    vec3 nrmRaw = cross(tX, tY);
    vec3 nrmQuad = cross(dFdx(worldRel), dFdy(worldRel)); // uniform-flow derivatives (a ternary may skip them: UB)
    // Degeneracy test relative to tangent scale; an absolute epsilon trips on a wall hugged at sub-block distance.
    float nrmLen2 = dot(nrmRaw, nrmRaw);
    vec3 nrm = nrmLen2 > 1e-12 * dot(tX, tX) * dot(tY, tY)
            ? nrmRaw * inversesqrt(nrmLen2)
            : normalize(nrmQuad);
    vec3 toCam = normalize(-worldRel);
    if (dot(nrm, toCam) < 0.0) {
        nrm = -nrm;
    }
    vec3 toLens = normalize(ProjectorEye - worldRel);
    float ndlRaw = dot(nrm, toLens);              // <=0 => lens behind this face

    // UN-OFFSET radial distance: the leak gate must measure the TRUE receiver to keep its 4-texel margin.
    float coneDist0 = length(worldRel - ProjectorEye);
    float scrEdge = fwidth(coneDist0);            // depth-edge magnitude: huge across an entity's camera-depth silhouette
    float throwN0   = coneDist0 / maxThrow;
    float texelWorld0 = coneDist0 * (2.0 * coneK) * ConeParams.w;

    const float BASE_K          = 3.0;             // base bias in texels: absorbs the 3-texel PCF kernel span
    const float MAX_LEAK_TEX    = 4.0;             // radial leak-gate width in texels
    const float NORMAL_OFFSET_K = 2.0;             // normal-offset baseline in texels
    const float RPDB_CLAMP_TEX  = 3.0;             // cap on the per-tap RPDB compare shift (texelN)
    const float BAND_K          = 0.25;            // soft-band as a fraction of texelN

    vec3 rcv = worldRel + nrm * (texelWorld0 * NORMAL_OFFSET_K * clamp(1.0 - abs(ndlRaw), 0.0, 1.0));

    vec4 pc = ProjectorViewProj * vec4(rcv, 1.0);
    if (pc.w <= 0.0) {
        discard;                                  // behind the lens
    }
    vec2 puv = pc.xy / pc.w * 0.5 + 0.5;          // depth-map address space, y up

    // IMAGE space projects the UN-OFFSET worldRel so the picture never crawls under the offset.
    vec4 pcImg = ProjectorViewProj * vec4(worldRel, 1.0);
    if (pcImg.w <= 0.0) {
        discard;
    }
    vec2 puvImg = pcImg.xy / pcImg.w * 0.5 + 0.5;
    vec2 imgUv = vec2(puvImg.x, 1.0 - puvImg.y);
    if (imgUv.x < ImageRegion.x || imgUv.x > ImageRegion.z || imgUv.y < ImageRegion.y || imgUv.y > ImageRegion.w) {
        discard;
    }

    // RADIAL distance to the OFFSET receiver -- METRIC PIN: matches the dist shader's length().
    float coneDist = length(rcv - ProjectorEye);
    float throwN = coneDist / maxThrow;

    float texel = ConeParams.w;                    // PCF tap spacing = one texel
    float texelWorld = coneDist * (2.0 * coneK) * ConeParams.w;
    float texelN = texelWorld / maxThrow;

    // Receiver-plane depth bias (Isidoro GDC'06): per-tap dot(tapPuvOffset, rpdb) makes the 9 compares follow the tilted
    // receiver, collapsing the grazing comb. Differentiate the CONTINUOUS puv/throwN, never a tap.
    vec3 dX = vec3(dFdx(puv), dFdx(throwN));
    vec3 dY = vec3(dFdy(puv), dFdy(throwN));
    float rpdbDet = dX.x * dY.y - dX.y * dY.x;
    bool rpdbValid = abs(rpdbDet) > 1e-12;
    vec2 rpdb = vec2(0.0);
    if (rpdbValid) {
        rpdb = vec2(dY.y * dX.z - dX.y * dY.z,
                    dX.x * dY.z - dY.x * dX.z) / rpdbDet;
    }
    float planeSlopeN = rpdbValid ? min(length(rpdb) * texel, 12.0 * texelN) : 0.0;

    // Screen-trusted slope for the census: garbage only where the screen quad spans a silhouette, not where the map kernel
    // spread. Capped high -- it feeds MATCH tests, not inequality slack.
    float scrSilT = smoothstep(16.0, 64.0, scrEdge / max(texelWorld0, 1e-6));
    float rpdbCapG = (64.0 * texelN) / (2.0 * texel);
    vec2 rpdbG = clamp(rpdb, vec2(-rpdbCapG), vec2(rpdbCapG)) * (1.0 - scrSilT);
    float planeStepG = length(rpdbG) * texel;

    // 3x3 gate, UNFILTERED via texelFetch: the gate must never see a hardware-blended distance, which pulls minStored
    // toward the clear sentinel across the rim and re-opens the wall leak. GL_LINEAR is only for the coverage taps below.
    ivec2 dmSize = textureSize(Sampler2, 0);
    float stored[9];
    float bbit[9];                  // per-tap entity bit (G), clear-masked
    float minStored = 1.0;
    float storedCB = 1.0;           // CENTER blocks-only B: block surface along this ray, surviving under an entity
    float storedFar = 1.0039;       // CENTER peel: second block surface (sentinel = none)
    float minStoredEnt = 1.0;
    float maxStored = 0.0;
    // Plane-confirm census: non-entity taps matching this receiver's plane. At grazing a texel row straddles a hole rim,
    // so only a same-plane match one tap over clears the leak; the center gate can't reach.
    float confirmTol = MAX_LEAK_TEX * texelN + 0.5 * planeStepG + ConeParams.z;
    float confirmFrac = 0.0;
    int si = 0;
    for (int oy = -1; oy <= 1; oy++) {
        for (int ox = -1; ox <= 1; ox++) {
            vec2 t = clamp(puv + vec2(float(ox), float(oy)) * texel, 0.0, 1.0);
            ivec2 itc = clamp(ivec2(t * vec2(dmSize)), ivec2(0), dmSize - ivec2(1));
            vec3 tap = texelFetch(Sampler2, itc, 0).rgb;
            float s = tap.r;
            stored[si] = s;
            bbit[si] = (s <= 1.0) ? tap.g : 0.0; // clear R = 1.0039 is the only s>1.0, so s<=1.0 drops cleared rim texels
            si++;
            // Census excludes entity taps: their B may sit on the plane while the entity still occludes.
            if (s <= 1.0 && tap.g < 0.5) {
                float shiftG = dot(vec2(float(ox), float(oy)) * texel, rpdbG);
                if (abs(throwN0 + shiftG - s) < confirmTol + 0.5 * abs(shiftG)) {
                    confirmFrac += 1.0;
                }
            }
            minStored = min(minStored, s);
            if (s <= 1.0 && tap.g > 0.5) {
                minStoredEnt = min(minStoredEnt, s);
            }
            if (ox == 0 && oy == 0) {
                storedCB = tap.b;
                storedFar = texelFetch(Sampler3, itc, 0).r;
            }
            maxStored = max(maxStored, s);
        }
    }
    confirmFrac /= 9.0;
    float rescueT = smoothstep(0.0, 0.34, confirmFrac);
    float silT = smoothstep(1.5, 4.5, (maxStored - minStored) / texelN); // kernel depth spread: flat..silhouette ramp
    if (!rpdbValid) {
        rpdb = vec2(0.0);
    }
    rpdb *= 1.0 - silT;
    // Bound EACH rpdb component to half the cap, so the worst-case diagonal sum stays under the 4-texel leak gate.
    float rpdbCap = (RPDB_CLAMP_TEX * texelN) / (2.0 * texel);
    rpdb = clamp(rpdb, vec2(-rpdbCap), vec2(rpdbCap));
    // Entity-center: silT zeroes rpdb across the limb, so use the screen-trusted slope there instead.
    if (bbit[4] > 0.5) {
        rpdb = clamp(rpdbG, vec2(-rpdbCap), vec2(rpdbCap));
    }
    float sbias = texelN * BASE_K + ConeParams.z;

    // Leak gate uses the UN-OFFSET throwN0, not throwN. Inside FADE_BLOCKS past the texel margin the image fades; beyond,
    // hard discard. A wall's back face is ~1.0 >> FADE_BLOCKS, so only thin geometry sneaks a sliver.
    const float FADE_BLOCKS = 0.25;
    float gateN = MAX_LEAK_TEX * texelN;
    float fadeN = (FADE_BLOCKS / maxThrow) * clamp(abs(ndlRaw), 0.1, 1.0); // radial; |ndl| keeps along-surface fade constant
    // The gate asks the CENTER TAP alone: a kernel-min gate fires early at every nearer silhouette, erasing thin-occluder
    // shadows. Only the center separates lit-self from a true shadow. PCF stays for edges, never the gate.
    float storedC = stored[4];
    float leakN = throwN0 - storedC;
    // First block beyond the occluder: peel (storedFar) for a block center, storedCB for an ENTITY center (peel is blocks-only).
    float blockBeyond = (bbit[4] > 0.5) ? storedCB : storedFar;
    bool beyondLeak = leakN > gateN + fadeN + 2.0 * planeSlopeN && confirmFrac < 0.06;
    float leakFade = 1.0 - smoothstep(gateN + 2.0 * planeSlopeN, gateN + 2.0 * planeSlopeN + fadeN, leakN);
    // Admit a beyond-leak pixel to the shadow path only if a kernel tap is an ENTITY occluder ahead of it.
    bool anyEntityTap = false;
    for (int k = 0; k < 9; k++) {
        anyEntityTap = anyEntityTap || (bbit[k] > 0.5 && stored[k] + sbias < throwN0);
    }
    bool entityGate = Flags.y > 0.0 && anyEntityTap && throwN0 < 1.0;
    // Blocks cast the same dark layer entities do; it fades out by 2x SHADOW_REACH so a blocked beam doesn't smear a cone
    // across far terrain seen around the wall.
    const float SHADOW_REACH = 4.0;
    bool blockShadowCandidate = Flags.y > 0.0 && throwN0 < 1.0 && leakN < (SHADOW_REACH * 2.0) / maxThrow;
    if (beyondLeak && !entityGate && !blockShadowCandidate && DebugMode < 0.5) {
        discard;
    }

    // Band: tight on flats (RPDB tracks the plane), wide at silhouettes (RPDB dropped) so a thin moving edge stops
    // shimmering. Coverage only, never the leak gate.
    float discN = 0.5 / maxThrow;
    float entBiasN = 0.05 / maxThrow;
    float band = max(mix(BAND_K, 2.0, silT) * texelN, ConeParams.z);
    // Penumbra: a receiver far behind an ENTITY samples a wider kernel, blurring the shadow edge; lit pixels keep the tight kernel.
    float entAnyG = 0.0;
    for (int k = 0; k < 9; k++) {
        entAnyG = max(entAnyG, bbit[k]);
    }
    float behindN = max(throwN0 - minStored, 0.0);
    float tapScale = 1.0 + 7.0 * entAnyG * smoothstep(0.0, 0.06, behindN);
    float lit = 0.0;
    float minLit = 1.0;             // kernel min/max of coverage -> type-blind silhouette spread for the contour
    float maxLit = 0.0;
    float entityCov = 0.0;
    float entNear = 0.0;            // averaged entity presence -- gates the screen-edge feather
    for (int oy = -1; oy <= 1; oy++) {
        for (int ox = -1; ox <= 1; ox++) {
            vec2 t = clamp(puv + vec2(float(ox), float(oy)) * texel * tapScale, 0.0, 1.0);
            // Coverage pass: hardware-FILTERED (GL_LINEAR), so a moving silhouette stops snapping. Softens coverage only;
            // the gate ran on the unfiltered values.
            vec2 lin = texture(Sampler2, t).rg;
            float storedLin = lin.r;
            float bbitLin = (storedLin <= 1.0) ? lin.g : 0.0;
            float throwTap = throwN + dot(vec2(float(ox), float(oy)) * texel * tapScale, rpdb);
            // Outlier clamp: once the gate confirmed this receiver lit, taps from another surface must not drag lit into a
            // torn seam. Safe because the gate is center-based.
            float storedUse = (!beyondLeak && abs(storedLin - storedC) > discN) ? storedC : storedLin;
            float passLit = smoothstep(throwTap - band, throwTap + band,
                    storedUse + sbias + bbitLin * (entBiasN + 2.0 * planeSlopeN));
            lit += passLit;
            minLit = min(minLit, passLit);
            maxLit = max(maxLit, passLit);
            entityCov += bbitLin * (1.0 - passLit);
            entNear += bbitLin;
        }
    }
    lit /= 9.0;
    entityCov /= 9.0;
    entNear /= 9.0;
    if (beyondLeak) {
        lit = 0.0;
    }
    // Rim-tear rescue: the census placed this surface in the map but the PCF taps went dark against the nearer
    // discontinuity. GUARD on center-is-receiver, else a fence rail's own shadow self-erases.
    bool centerIsReceiver = leakN <= gateN + fadeN + 2.0 * planeSlopeN;
    lit = max(lit, centerIsReceiver ? rescueT : 0.0);
    // F3 mode 1: R = storedC (occluder imprints darker; solid ~1 = empty map), G = PCF lit, B = leakN.
    if (DebugMode > 1.5 && DebugMode < 2.5) {
        // mode 2 (+sneak): R = screen depth jump, G = entity presence, B = PCF lit.
        fragColor = vec4(clamp(scrEdge * 4.0, 0.0, 1.0), entNear, smoothstep(0.15, 0.85, lit), 0.85);
        return;
    }
    if (DebugMode > 0.5 && DebugMode < 1.5) {
        fragColor = vec4(clamp(storedC, 0.0, 1.0), smoothstep(0.15, 0.85, lit), clamp(leakN * 16.0, 0.0, 1.0), 0.85);
        return;
    }

    // A pixel the compare confirms lit must not be dimmed by the edge fade (thin blocks sit fractions of a block behind
    // their own silhouette edges).
    leakFade = max(leakFade, smoothstep(0.15, 0.85, lit));

    float imageLit = smoothstep(0.15, 0.85, lit); // saturate toward 1 where unoccluded; soft low end avoids a black rim
    // Angle fade, strict for image and shadow: no rescue, or it re-lights the crease lines it fires on.
    float ndlFade = smoothstep(0.0, 0.15, ndlRaw);
    imageLit *= ndlFade;

    // Cone-edge vignette, widened by fwidth so it never collapses at grazing.
    const float RIM_WIDTH = 0.06;
    float edge = min(min(imgUv.x, 1.0 - imgUv.x), min(imgUv.y, 1.0 - imgUv.y));
    float rimW = max(RIM_WIDTH, 1.5 * fwidth(edge));
    float rim = smoothstep(0.0, rimW, edge);

    // F3 mode 3 (+sprint): R = angle fade, G = leak fade, B = peel (1.0 where the UNGUARDED second-layer kill fires). The
    // live kill also needs surfHug<0.05, so a lit B over a merge is expected.
    if (DebugMode > 2.5) {
        bool slbDbg = blockBeyond <= 1.0 && blockBeyond > storedC + 0.5 / maxThrow
                && throwN0 > blockBeyond + 0.5 / maxThrow && confirmFrac < 0.06;
        fragColor = vec4(ndlFade, leakFade, slbDbg ? 1.0 : (storedFar <= 1.0 ? 0.5 * storedFar : 0.0), 0.85);
        return;
    }

    if (imageLit > 0.0) {
        float texU = (imgUv.x - ImageRegion.x) / (ImageRegion.z - ImageRegion.x);
        float texV = (imgUv.y - ImageRegion.y) / (ImageRegion.w - ImageRegion.y);
        if (Flags.x > 0.5) {
            texV = 1.0 - texV;
        }
        vec4 img = texture(Sampler0, vec2(texU, texV));
        // Screen-edge feather, ENTITY-gated: across an entity's silhouette the reconstruction and UV jump per pixel
        // (crawling teeth). Covers the one-texel annulus outside the limb; flat surfaces untouched.
        float scrEdgeRel = scrEdge / max(coneDist0, 1.0);
        float entEdgeFade = mix(1.0, 1.0 - smoothstep(0.05, 0.15, scrEdgeRel),
                max(smoothstep(0.05, 0.3, entNear), entAnyG));
        // Ungated fallback, PARTIAL (0.7 cap): block and limb jumps overlap, so a full kill would outline every block edge.
        entEdgeFade = min(entEdgeFade, 1.0 - 0.7 * smoothstep(0.3, 0.8, scrEdgeRel));
        // Cone-base falloff (image-only): the shadow self-bounds, and a base receiver may still take a mid-cone shadow.
        float coneBaseFade = 1.0 - smoothstep(12.5 / maxThrow, 1.0, throwN0);
        float alpha = img.a * Flags.z * 0.8 * imageLit * rim * leakFade * entEdgeFade * coneBaseFade;
        if (alpha < 0.002) {
            discard;
        }
        fragColor = vec4(img.rgb, alpha);
    } else if (Flags.y > 0.0 && throwN0 < 1.0) {
        // ONE shadow layer for every occluder, so overlapping shadows merge. The only exclusion is an ENTITY BODY by the
        // center tap: the receiver IS the body when leakN < storedCB-throwN0 (open-air body has storedCB at the sentinel).
        bool receiverIsEntity = bbit[4] > 0.5 && leakN < 0.8 / maxThrow && (storedCB - throwN0) > leakN;
        // The PCF term (1-lit) IS type-blind coverage; entity coverage survives only on a self-occluded receiver.
        bool selfBlock = bbit[4] < 0.5 && leakN <= 0.25 / maxThrow;
        float occCov = selfBlock ? smoothstep(0.0, 1.0, entityCov) : 1.0;
        // Light-hug: the projector ADDS light, so a shadow only reads next to where light lands. Two tap rings find
        // landings (a tap clearing the center occluder onto the receiver depth): a fence shadow hugs its escaped beam ~a
        // block, a full-cone wall paints nothing. Blocks only.
        const float FILL_RADIUS = 160.0;               // outer ring radius in texels
        const float OPEN_MARGIN = 0.5;                 // blocks a ring tap must see past the occluder to count as open
        const float LAND_NEAR = 1.5;                   // blocks behind the receiver depth where escaped light may land
        const float LAND_FRONT = 0.5;                  // blocks in front: tight, so an interposed wall stays a blocker
        float openThresh = storedC + OPEN_MARGIN / maxThrow;
        // Escape past the BLOCK occluder via the B channel, which survives under an entity (else the hug collapses on a body center).
        float openThreshB = storedCB + OPEN_MARGIN / maxThrow;
        float lightHug = 0.0;
        // surfHug = lightHug without the entity clause: only landed light + the receiver's own block plane. The through-
        // wall kill asks "does light land here?"; a scattered body is an occluder, not light.
        float surfHug = 0.0;
        float entRing = 0.0;        // entity sighted at ring scale (the 3x3 can't see past a wide cutout)
        float minEntRing = 1.0;
        for (int k = 0; k < 16; k++) {
            float ang = float(k & 7) * 0.785398163;    // 8 directions, 45 deg apart
            float rad = (k < 8) ? FILL_RADIUS : FILL_RADIUS * 0.5; // inner ring too: outer taps overshoot at grazing
            vec2 rt = clamp(puv + vec2(cos(ang), sin(ang)) * (rad * texel), 0.0, 1.0);
            ivec2 ri = clamp(ivec2(rt * vec2(dmSize)), ivec2(0), dmSize - ivec2(1));
            vec3 rsg = texelFetch(Sampler2, ri, 0).rgb;
            // A landing is escaped light at/behind the receiver depth; a surface well in front is an interposed wall, so
            // the block window is one-sided (behind, not front).
            bool lands = (rsg.y >= 0.5 && rsg.x < throwN0 + LAND_NEAR / maxThrow)
                    || (rsg.x > throwN0 - LAND_FRONT / maxThrow && rsg.x < throwN0 + LAND_NEAR / maxThrow);
            if (rsg.y >= 0.5 && rsg.x <= 1.0 && rsg.x > openThresh && rsg.x < throwN0 + LAND_NEAR / maxThrow) {
                entRing = 1.0;
                minEntRing = min(minEntRing, rsg.x);
            }
            // Block landing via B: counted even under an entity, so a fence's shadow merges into a body's instead of a cutout.
            bool landsB = rsg.z > openThreshB && rsg.z <= 1.0
                    && rsg.z > throwN0 - LAND_FRONT / maxThrow && rsg.z < throwN0 + LAND_NEAR / maxThrow;
            lightHug += ((rsg.x > openThresh && rsg.x <= 1.0 && lands) || landsB) ? ((k < 8) ? 0.175 : 0.5) : 0.0;
            // Guard-only hug: the R landing needs a non-entity tap, so a lateral body isn't counted as landed light.
            surfHug += ((rsg.y < 0.5 && rsg.x > openThresh && rsg.x <= 1.0
                        && rsg.x > throwN0 - LAND_FRONT / maxThrow && rsg.x < throwN0 + LAND_NEAR / maxThrow)
                        || landsB) ? ((k < 8) ? 0.175 : 0.5) : 0.0;
        }
        lightHug = min(lightHug, 1.0);
        // Entity center can't bypass the ring when a block fully occludes the receiver; a first-block receiver skips it.
        bool fillGated = (throwN0 - storedCB) > 0.25 / maxThrow;
        float fillKeep = fillGated ? lightHug : 1.0;
        // Short reach (gone by 2x SHADOW_REACH). Same-source shadows equalize so a far block before a close body can't
        // punch a lighter cutout through its shadow.
        float reachLeak = (entAnyG > 0.5 || entRing > 0.5)
                ? min(leakN, throwN0 - min(minStoredEnt, minEntRing)) : leakN;
        float reach = 1.0 - smoothstep(SHADOW_REACH / maxThrow, (SHADOW_REACH * 2.0) / maxThrow, reachLeak);
        // Cosmetic contour on the merged outline: the type-blind coverage spread (maxLit-minLit) peaks only at the union
        // outline. CONTOUR_GAIN=1.0 off, <1.0 = bright rim.
        const float CONTOUR_GAIN = 1.35;
        float entEdgeBump = smoothstep(0.15, 0.6, maxLit - minLit);
        // An interposed block killed the beam, so the stripe ends ON the wall. GUARDS: confirmFrac (R census, blind to a
        // front entity) AND surfHug (B-landings under the body) keep the merge while killing the through-wall stripe.
        bool secondLayerBlocks = blockBeyond <= 1.0 && blockBeyond > storedC + 0.5 / maxThrow
                && throwN0 > blockBeyond + 0.5 / maxThrow && confirmFrac < 0.06 && surfHug < 0.05;
        float shadowAlpha = (receiverIsEntity || secondLayerBlocks) ? 0.0
                : Flags.y * (1.0 - lit) * occCov * rim * ndlFade * reach * fillKeep
                  * mix(1.0, CONTOUR_GAIN, entEdgeBump);
        if (shadowAlpha < 0.002) {
            discard;
        }
        fragColor = vec4(0.0, 0.0, 0.0, shadowAlpha);
    } else {
        discard;
    }
}
