package qouteall.imm_ptl.core.compat;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPCompatMixinPlugin implements IMixinConfigPlugin {
    // Self-contained logger (same name/output as qouteall.q_misc_util.Helper.LOGGER) rather than
    // depending on Helper directly -- this class is a mixin config plugin, loaded and invoked very
    // early in the game bootstrap, so it deliberately keeps its own dependency footprint minimal.
    private static final Logger LOGGER = LogManager.getLogger("iPortal");

    // Sodium/Embeddium generation the Sodium & IrisSodium compat mixins in
    // imm_ptl_compat.mixins.json were written against and are known to work with.
    private static final int SUPPORTED_SODIUM_MAJOR = 0;
    private static final int SUPPORTED_SODIUM_MINOR = 6;

    // Strips a Modrinth-style artifact coordinate's leading Minecraft-version marker, e.g. the
    // "mc1.21.1-" in "mc1.21.1-0.6.13-neoforge".
    private static final Pattern MC_COORDINATE_PREFIX =
        Pattern.compile("^mc\\d+(?:\\.\\d+)*-", Pattern.CASE_INSENSITIVE);

    // Ensures the "unsupported Sodium" warning is logged once total for the whole boot, even
    // though shouldApplyMixin below is invoked once per mixin in the config (many Sodium-named
    // mixins would otherwise each trigger their own log line).
    private static volatile boolean unsupportedSodiumWarningLogged = false;

    /**
     * Pure, unit-testable version gate. SUPPORTED means major.minor == 0.6 (see
     * SUPPORTED_SODIUM_MAJOR/MINOR above); anything else (including unparseable/garbage/null
     * input) is treated as unsupported so callers fail soft instead of crashing.
     * <p>
     * Handles the version-string shapes actually observed at runtime/build time for these mods,
     * both of which wrap the real X.Y.Z Sodium version differently and would be misread by a bare
     * {@code new DefaultArtifactVersion(raw)} or a plain string-prefix check:
     * <ul>
     *   <li>the mod's own metadata version, e.g. {@code "0.6.13+mc1.21.1"} (read from the dev
     *   Sodium 0.6.13 jar's neoforge.mods.toml) -- semver build-metadata suffix after '+';
     *   {@code new DefaultArtifactVersion("0.6.13+mc1.21.1")} fails to parse the numeric part at
     *   all (the '+' is not a recognized separator) and yields major=0/minor=0, which would be
     *   wrongly read as unsupported without stripping the suffix first.</li>
     *   <li>the Modrinth artifact coordinate shape, e.g. {@code "mc1.21.1-0.6.13-neoforge"} --
     *   Minecraft-version prefix before the real mod version.</li>
     * </ul>
     */
    public static boolean isSodiumVersionSupported(@Nullable String rawVersion) {
        if (rawVersion == null) {
            return false;
        }

        String s = rawVersion.trim();
        if (s.isEmpty()) {
            return false;
        }

        int plusIndex = s.indexOf('+');
        if (plusIndex >= 0) {
            s = s.substring(0, plusIndex);
        }

        Matcher mcPrefixMatcher = MC_COORDINATE_PREFIX.matcher(s);
        if (mcPrefixMatcher.find()) {
            s = s.substring(mcPrefixMatcher.end());
        }

        if (s.isEmpty()) {
            return false;
        }

        try {
            ArtifactVersion version = new DefaultArtifactVersion(s);
            return version.getMajorVersion() == SUPPORTED_SODIUM_MAJOR
                && version.getMinorVersion() == SUPPORTED_SODIUM_MINOR;
        }
        catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static String readModVersionString(LoadingModList modList, String modId) {
        ModFileInfo modFileInfo = modList.getModFileById(modId);
        if (modFileInfo == null) {
            return null;
        }

        return modFileInfo.getMods().stream()
            .filter(info -> modId.equals(info.getModId()))
            .findFirst()
            .map(info -> info.getVersion().toString())
            .orElse(null);
    }

    @Nullable
    private static String findLoadedSodiumOrEmbeddiumRawVersion(LoadingModList modList) {
        String embeddiumVersion = readModVersionString(modList, "embeddium");
        if (embeddiumVersion != null) {
            return embeddiumVersion;
        }
        return readModVersionString(modList, "sodium");
    }

    private static void warnUnsupportedSodiumOnce(String rawVersion) {
        if (unsupportedSodiumWarningLogged) {
            return;
        }
        unsupportedSodiumWarningLogged = true;

        LOGGER.warn(
            "Immersive Portals: unsupported Sodium {}; portal-Sodium integration disabled, vanilla portal rendering in use",
            rawVersion
        );
    }

    /**
     * True when a Sodium/Embeddium mod file is present but its version is not the supported 0.6.x
     * generation. Presence-only detection (no version check) is left to the pre-existing
     * substring branches below, unchanged.
     */
    private static boolean isSodiumPresentButUnsupported(LoadingModList modList) {
        String rawVersion = findLoadedSodiumOrEmbeddiumRawVersion(modList);
        if (rawVersion == null) {
            return false;
        }

        if (isSodiumVersionSupported(rawVersion)) {
            return false;
        }

        warnUnsupportedSodiumOnce(rawVersion);
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {


        LoadingModList modList = LoadingModList.get();

        // Sodium 0.8+ removed internals (e.g. OcclusionCuller$Visitor) that the Sodium/IrisSodium
        // compat mixins below target; applying those mixins against an unsupported Sodium version
        // fails at mixin APPLY time with ClassMetadataNotFoundException, hard-crashing world
        // creation. This pre-gate sits IN FRONT of the substring checks below and returns false
        // early for any Sodium-related mixin (this also covers "IrisSodium" mixins, since that
        // name already contains "Sodium") when the detected version is unsupported -- vanilla
        // portal rendering is used instead of crashing. The substring check chain below is
        // otherwise untouched: its order is load-bearing (IrisSodium before Iris before Sodium)
        // and this pre-gate does not reorder it.
        if (mixinClassName.contains("Sodium")) {
            if (isSodiumPresentButUnsupported(modList)) {
                return false;
            }
        }

        if (mixinClassName.contains("IrisSodium")) {
            boolean sodiumLoaded = modList.getModFileById("embeddium") != null || modList.getModFileById("sodium") != null;
            boolean irisLoaded = modList.getModFileById("iris") != null;
            return sodiumLoaded && irisLoaded;
        }
        
        if (mixinClassName.contains("Iris")) {
            boolean irisLoaded = modList.getModFileById("iris") != null;
            return irisLoaded;
        }
        
        if (mixinClassName.contains("Sodium")) {
            boolean sodiumLoaded = modList.getModFileById("embeddium") != null || modList.getModFileById("sodium") != null;
            return sodiumLoaded;
        }
        
        if (mixinClassName.contains("Flywheel")) {
            boolean flywheelLoaded =  modList.getModFileById("flywheel") != null;
            return flywheelLoaded;
        }
        
        if (mixinClassName.contains("CardinalComp")) {
            boolean cardinalCompLoaded = modList.getModFileById("cardinal-components-base") != null;
            return cardinalCompLoaded;
        }
        
        return false;
    }
    
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    
    }
    
    @Override
    public List<String> getMixins() {
        return null;
    }
    
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    
    }
    
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    
    }
}
