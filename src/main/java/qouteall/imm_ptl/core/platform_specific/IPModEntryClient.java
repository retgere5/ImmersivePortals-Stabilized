package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.IPModMainClient;
import qouteall.imm_ptl.core.compat.IPCompatMixinPlugin;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.BreakableMirror;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.render.LoadingIndicatorRenderer;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.Arrays;
import java.util.Optional;

public class IPModEntryClient {
    


    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void initPortalRenderers(EntityRenderersEvent.RegisterRenderers event) {
        
        Arrays.stream(new EntityType<?>[]{
            Portal.ENTITY_TYPE,
            NetherPortalEntity.ENTITY_TYPE,
            EndPortalEntity.ENTITY_TYPE,
            Mirror.ENTITY_TYPE,
            BreakableMirror.ENTITY_TYPE,
            GlobalTrackedPortal.ENTITY_TYPE,
            WorldWrappingPortal.ENTITY_TYPE,
            VerticalConnectingPortal.ENTITY_TYPE,
            GeneralBreakablePortal.ENTITY_TYPE
        }).forEach(
            entityType -> event.registerEntityRenderer(
                entityType,
                (EntityRendererProvider) PortalEntityRenderer::new
            )
        );
        
        event.registerEntityRenderer(
            LoadingIndicatorEntity.entityType,
            LoadingIndicatorRenderer::new
        );
        
    }

    public void onInitializeClient(IEventBus modEventBus) {
        IPModMainClient.init();

        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, IPModEntryClient::initPortalRenderers);
        
        boolean isSodiumPresent =
            ModList.get().isLoaded("embeddium") || ModList.get().isLoaded("sodium");
        if (isSodiumPresent) {
            Helper.log("Sodium is present");

            String sodiumRawVersion = getLoadedSodiumOrEmbeddiumVersionString();

            if (IPCompatMixinPlugin.isSodiumVersionSupported(sodiumRawVersion)) {
                SodiumInterface.invoker = new SodiumInterface.OnSodiumPresent();
            }
            else {
                // Unsupported Sodium (e.g. 0.8.x): leave SodiumInterface.invoker at its default
                // no-op, so rendering falls back to the vanilla portal path instead of hitting the
                // ClassMetadataNotFoundException crash the Sodium compat mixins would cause (see
                // docs/audit/faz0-evidence.md Kume A). IPCompatMixinPlugin.shouldApplyMixin already
                // logs the single "unsupported Sodium" warning for this case (it runs earlier, during
                // mixin application) -- intentionally not repeating it here to keep it to one line.
                Helper.log("Sodium " + sodiumRawVersion + " is unsupported; using vanilla portal rendering");
            }

            // Sodium compat is pretty ok now. No warning needed.
//            IPGlobal.clientTaskList.addTask(MyTaskList.oneShotTask(() -> {
//                if (IPGlobal.enableWarning) {
//                    CHelper.printChat(
//                        Component.translatable("imm_ptl.sodium_warning")
//                            .append(IPMcHelper.getDisableWarningText())
//                    );
//                }
//            }));
        }
        else {
            Helper.log("Sodium is not present");
        }
        
        if (ModList.get().isLoaded("iris")) {
            Helper.log("Iris is present");
            IrisInterface.invoker = new IrisInterface.OnIrisPresent();
            ExperimentalIrisPortalRenderer.init();
            
            IPGlobal.CLIENT_TASK_LIST.addTask(MyTaskList.oneShotTask(() -> {
                if (IPConfig.getConfig().shouldDisplayWarning("iris")) {
                    CHelper.printChat(
                        Component.translatable("imm_ptl.iris_warning")
                            .append(IPMcHelper.getDisableWarningText("iris"))
                    );
                }
            }));
        }
        else {
            Helper.log("Iris is not present");
        }
        
        IPModInfoChecking.initClient();
    }

    /**
     * Reads the raw version string of whichever of embeddium/sodium is loaded (embeddium checked
     * first, matching the presence-check order above), for the version gate in
     * {@link IPCompatMixinPlugin#isSodiumVersionSupported(String)}. Unlike the mixin plugin (which
     * runs too early for {@link ModList} to be usable and has to go through
     * {@code LoadingModList}/{@code ModFileInfo} instead), this runs during mod client init, where
     * the regular {@link ModList} API is already available.
     */
    @Nullable
    private static String getLoadedSodiumOrEmbeddiumVersionString() {
        Optional<? extends ModContainer> embeddium = ModList.get().getModContainerById("embeddium");
        if (embeddium.isPresent()) {
            return embeddium.get().getModInfo().getVersion().toString();
        }

        Optional<? extends ModContainer> sodium = ModList.get().getModContainerById("sodium");
        return sodium.map(c -> c.getModInfo().getVersion().toString()).orElse(null);
    }

}
