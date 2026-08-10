package qouteall.imm_ptl.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

/**
 * First automated in-game safety net for this fork: spawns a {@link Portal} entity
 * server-side with a fully-specified transform and asserts it becomes valid.
 * <p>
 * Uses a structure-less (empty, no blocks) 5x5x5 template since NeoForge 21.1.152 has no
 * {@code @EmptyTemplate}-style annotation to skip templates entirely
 * (see {@code src/main/resources/data/immersive_portals_core/structure/empty_5x5.nbt}).
 * {@code @PrefixGameTestTemplate(false)} disables the default class-name-prefixing of the
 * template lookup, so the structure resolves to {@code immersive_portals_core:empty_5x5}
 * instead of {@code immersive_portals_core:portalsmokegametest.empty_5x5}.
 */
@GameTestHolder("immersive_portals_core")
public class PortalSmokeGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void portalEntitySpawns(GameTestHelper helper) {
        Portal portal = Portal.ENTITY_TYPE.create(helper.getLevel());
        helper.assertTrue(portal != null, "Portal entity could not be created");

        portal.setOriginPos(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)));
        portal.setDestinationDimension(helper.getLevel().dimension());
        portal.setDestination(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)).add(0, 0, 8));
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2, 2);

        helper.getLevel().addFreshEntity(portal);

        helper.succeedWhen(() -> helper.assertTrue(portal.isPortalValid(), "Portal invalid after spawn"));
    }

    /**
     * Faz1 T7 regression coverage: the server-side half of the {@code disableTeleportation}
     * kill switch.
     * <p>
     * Drives {@link ServerTeleportationManager#startTeleportingRegularEntity} directly --
     * the real server-side entry point that {@code ServerPortalTickEvent} and the
     * global-portal tick loop both call to move a non-player entity through a portal --
     * instead of relying on physics-driven motion detection
     * ({@code ServerTeleportationManager#shouldEntityTeleport}), which is unreliable to
     * trigger deterministically within a gametest's structure-local tick budget. Asserting
     * through the real entry point matters more than simulating motion.
     * <p>
     * The armor stand is positioned straddling the portal plane (half a block behind, half
     * a block ahead, along the portal normal) immediately before the call, via
     * {@link #spawnStraddlingArmorStand}, so its last-tick/this-tick position delta looks
     * like a genuine crossing to {@code teleportRegularEntity}'s eye-position ray trace
     * (avoiding a degenerate zero-length delta, which would otherwise NaN out of
     * {@code Vec3#normalize}).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void teleportEntityAcrossPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 originPos = helper.absoluteVec(new Vec3(2.5, 2.5, 2.5));

        Portal portal = createStraightPortal(helper, originPos);
        ArmorStand entity = spawnStraddlingArmorStand(helper, originPos);

        ServerTeleportationManager.of(level.getServer())
            .startTeleportingRegularEntity(portal, entity);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                entity.position().z > originPos.z + 4,
                "Entity should have teleported to the destination side of the portal (expected z > "
                    + (originPos.z + 4) + "), but is at z=" + entity.position().z
            );
            helper.succeed();
        });
    }

    /**
     * Same scenario as {@link #teleportEntityAcrossPortal}, but with
     * {@code IPGlobal.disableTeleportation} forced on for the duration of the
     * {@code startTeleportingRegularEntity} call.
     * <p>
     * Before the Faz1 T7 gate, this call would still schedule and complete the teleport --
     * {@code disableTeleportation} only gated {@code ClientTeleportationManager}. After the
     * gate, {@code startTeleportingRegularEntity} returns immediately without scheduling
     * anything, so the entity never moves. The flag is restored in a {@code finally} block
     * immediately after the call -- it is never left set across a tick boundary, so it
     * cannot interfere with any other gametest that happens to run concurrently.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void teleportEntityAcrossPortalBlockedWhenDisabled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 originPos = helper.absoluteVec(new Vec3(2.5, 2.5, 2.5));

        Portal portal = createStraightPortal(helper, originPos);
        ArmorStand entity = spawnStraddlingArmorStand(helper, originPos);

        boolean previousDisableTeleportation = IPGlobal.disableTeleportation;
        IPGlobal.disableTeleportation = true;
        try {
            ServerTeleportationManager.of(level.getServer())
                .startTeleportingRegularEntity(portal, entity);
        }
        finally {
            IPGlobal.disableTeleportation = previousDisableTeleportation;
        }

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                Math.abs(entity.position().z - originPos.z) < 2,
                "Entity teleported across the portal despite disableTeleportation=true "
                    + "(expected to stay near z=" + originPos.z + ", but is at z="
                    + entity.position().z + ")"
            );
            helper.succeed();
        });
    }

    /**
     * A 3x3 portal facing +Z (axisW=+X, axisH=+Y, so normal = axisW x axisH = +Z), with its
     * destination 8 blocks further along its own normal in the same dimension. Sized 3x3
     * (instead of the 2x2 used by {@link #portalEntitySpawns}) so an armor stand's eye
     * height fits inside the portal bounds, guaranteeing the ray trace in
     * {@code teleportRegularEntity} lands a clean hit instead of falling back to its
     * last-tick-position fallback path.
     */
    private static Portal createStraightPortal(GameTestHelper helper, Vec3 originPos) {
        ServerLevel level = helper.getLevel();
        Portal portal = Portal.ENTITY_TYPE.create(level);
        helper.assertTrue(portal != null, "Portal entity could not be created");

        portal.setOriginPos(originPos);
        portal.setDestinationDimension(level.dimension());
        portal.setDestination(originPos.add(0, 0, 8));
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3, 3);

        level.addFreshEntity(portal);
        return portal;
    }

    /**
     * Spawns an armor stand at {@code originPos} and immediately overrides its
     * current/last-tick position so it straddles the portal plane: half a block behind,
     * half a block ahead, along the portal normal (+Z, matching
     * {@link #createStraightPortal}). This is done synchronously, in the same tick the
     * entity is spawned in (before it has ticked even once), so nothing overwrites it
     * before {@code startTeleportingRegularEntity} reads it.
     */
    private static ArmorStand spawnStraddlingArmorStand(GameTestHelper helper, Vec3 originPos) {
        ArmorStand entity = helper.spawn(EntityType.ARMOR_STAND, new Vec3(2.5, 2.5, 2.5));
        McHelper.setPosAndLastTickPos(
            entity,
            originPos.add(0, 0, 0.5),
            originPos.add(0, 0, -0.5)
        );
        return entity;
    }
}
