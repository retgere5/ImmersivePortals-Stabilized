package qouteall.imm_ptl.core.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the Sodium 0.8 fail-soft version gate.
 * <p>
 * Context: Sodium 0.8 removed internals (e.g. {@code OcclusionCuller$Visitor}) that the
 * Sodium/IrisSodium compat mixins in imm_ptl_compat.mixins.json target. Applying those mixins
 * against Sodium 0.8 fails at mixin APPLY time with a ClassMetadataNotFoundException, hard
 * crashing world creation (see docs/audit/faz0-evidence.md Kume A). Before this fix,
 * {@code IPCompatMixinPlugin.shouldApplyMixin} only gated on mod presence, never version.
 * <p>
 * Contract under test: {@code isSodiumVersionSupported} must return true only for the 0.6.x
 * generation (major.minor == 0.6), regardless of which wrapper shape the raw version string
 * comes in as -- and must fail closed (false) for anything else, including null/unparseable
 * input, rather than throwing.
 */
public class IPCompatMixinPluginTest {

    @Test
    public void modrinthArtifactCoordinateShapeIsSupported() {
        // The Modrinth artifact coordinate shape used in build.gradle's dependency notation
        // ("maven.modrinth:sodium:mc1.21.1-0.6.13-neoforge") -- Minecraft-version prefix, then
        // the real mod version, then a loader suffix.
        assertTrue(IPCompatMixinPlugin.isSodiumVersionSupported("mc1.21.1-0.6.13-neoforge"));
    }

    @Test
    public void modrinthArtifactCoordinateShapeWithoutLoaderSuffixIsSupported() {
        assertTrue(IPCompatMixinPlugin.isSodiumVersionSupported("mc1.21.1-0.6.13"));
    }

    @Test
    public void realJarMetadataShapeIsSupported() {
        // The actual "version" field read from the dev Sodium 0.6.13 jar's
        // META-INF/neoforge.mods.toml (verified by extracting the jar from the Gradle cache):
        // semver with a build-metadata suffix after '+'. A bare `new DefaultArtifactVersion(raw)`
        // fails to parse this (the '+' is not a recognized separator) and yields major=0/minor=0,
        // which would be misread as unsupported without stripping the suffix first.
        assertTrue(IPCompatMixinPlugin.isSodiumVersionSupported("0.6.13+mc1.21.1"));
    }

    @Test
    public void plainSemverIsSupported() {
        assertTrue(IPCompatMixinPlugin.isSodiumVersionSupported("0.6.13"));
    }

    @Test
    public void minorZeroPatchIsSupported() {
        assertTrue(IPCompatMixinPlugin.isSodiumVersionSupported("0.6.0"));
    }

    @Test
    public void sodium08PlainSemverIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("0.8.12"));
    }

    @Test
    public void sodium08PreReleaseIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("0.8.13-beta.2"));
    }

    @Test
    public void sodium08ModrinthArtifactCoordinateShapeIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("mc1.21.1-0.8.12-neoforge"));
    }

    @Test
    public void sodium08RealJarMetadataShapeIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("0.8.12+mc1.21.1"));
    }

    @Test
    public void nullIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported(null));
    }

    @Test
    public void garbageIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("garbage"));
    }

    @Test
    public void emptyStringIsUnsupported() {
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported(""));
    }

    @Test
    public void minorSixtyIsNotConfusedWithMinorSix() {
        // A naive string-prefix check ("starts with 0.6") would wrongly accept 0.60.x. The real
        // ArtifactVersion-based numeric comparison must not make that mistake.
        assertFalse(IPCompatMixinPlugin.isSodiumVersionSupported("0.60.0"));
    }
}
