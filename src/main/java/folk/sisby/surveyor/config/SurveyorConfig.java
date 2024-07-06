package folk.sisby.surveyor.config;

import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.IntegerRange;

public class SurveyorConfig extends WrappedConfig {
    @Comment("Zeroes out waypoint owner UUIDs to use the new 'host uuid' system, which mirrors vanilla behaviour")
    @Comment("Only occurs when loading into a singleplayer world - Will be removed in the next version")
    public boolean migrateSingleplayerLandmarksFrom05to06 = true;

    @Comment("Terrain system - records layers of blocks and biomes for maps to render")
    @Comment("DISABLED prevents loading, FROZEN loads but prevents updates, DYNAMIC loads with addons or on servers, ENABLED always loads")
    public SystemMode terrain = SystemMode.DYNAMIC;

    @Comment("Structure system - records structure identifiers and piece data for specialized maps and utilities to render")
    @Comment("DISABLED prevents loading, FROZEN loads but prevents updates, DYNAMIC loads with addons or on servers, ENABLED always loads")
    public SystemMode structures = SystemMode.DYNAMIC;

    @Comment("Landmark system - a generic record of both player-owned waypoints and server-owned POIs, accessible via API")
    @Comment("DISABLED prevents loading, FROZEN loads but prevents updates, DYNAMIC loads with addons or on servers, ENABLED always loads")
    public SystemMode landmarks = SystemMode.DYNAMIC;

    @Comment("Whether to automatically add/remove nether portal landmarks")
    public boolean netherPortalLandmarks = true;

    @Comment("Whether to automatically add player death waypoints")
    public boolean playerDeathLandmarks = true;

    @Comment("Displays the following logs and messages:")
    @Comment("[Action Bar] Structure Discovery")
    public boolean debugMode = false;

    public Networking networking = new Networking();
    public static final class Networking implements Section {
        @Comment("[Server] Whether to place every player in a single share group")
        @Comment("Disables /surveyor share and /surveyor unshare")
        public boolean globalSharing = false;

        @Comment("How much terrain data to send to clients")
        @Comment("SERVER sends server-known data, GROUP sends group-known data, SOLO sends player-known data, NONE sends no data")
        public NetworkMode terrain = NetworkMode.GROUP;

        @Comment("How much structure data to send to clients")
        @Comment("SERVER sends server-known data, GROUP sends group-known data, SOLO sends player-known data, NONE sends no data")
        @Comment("When NONE, clients will never see structures")
        public NetworkMode structures = NetworkMode.GROUP;

        @Comment("Which landmarks to sync between client and server")
        @Comment("SERVER sync server-known landmarks, GROUP sends group-known landmarks, SOLO sends player-known landmarks, NONE sends no landmarks")
        public NetworkMode landmarks = NetworkMode.GROUP;

        @Comment("Which waypoints (player-created landmarks) to sync between client and server")
        @Comment("When SERVER, players can see (but not edit) all waypoints, including potentially offensive names")
        @Comment("When GROUP, players can see (but not edit) waypoints created by players in their share group")
        @Comment("When SOLO, player-created waypoints will be stored on the server as a backup")
        @Comment("When NONE, waypoint data will never be synced (e.g. for privacy)")
        public NetworkMode waypoints = NetworkMode.SOLO;

        @Comment("[Server] How much player position data to send to clients")
        @Comment("SERVER sends all players positions, GROUP sends just group players, SOLO sends nothing, NONE sends nothing")
        public NetworkMode positions = NetworkMode.GROUP;

        @Comment("[Server] Ticks per position update - lower is more frequent")
        @IntegerRange(min = 1, max = 200)
        public int positionTicks = 1;
    }
}
