package zomboidchunkreset.core.terrain;

import java.util.Map;

/**
 * Decides the {@link TileClass} of a tile from its tilesheet name and its properties, as read from
 * the game's {@code .tiles} files. Used by the tool that builds the tile table.
 */
public final class TileClassifier {

    private TileClassifier() {
    }

    /**
     * @param sheetName name of the tilesheet (for example {@code blends_natural_01})
     * @param props     properties of the tile
     * @param treeSheet whether the tilesheet is a tree sheet (see {@link #isTreeSheet})
     */
    public static TileClass classify(String sheetName, Map<String, String> props, boolean treeSheet) {
        String material = props.getOrDefault("FloorMaterial", "");
        if (props.containsKey("water") || material.equals("Water")) {
            return TileClass.WATER;
        }
        if (material.startsWith("Road")) {
            return TileClass.ROAD;
        }
        switch (material) {
            case "Sand":
                return TileClass.SAND;
            case "Dirt", "Dirt_Grass", "Gravel", "Clay":
                return TileClass.DIRT;
            case "Grass_Light":
                return TileClass.GRASS_LIGHT;
            case "Grass_Medium":
                return TileClass.GRASS_MEDIUM;
            case "Grass_Dark":
                return TileClass.GRASS_DARK;
            default:
                break;
        }
        if (sheetName.startsWith("roofs_") || props.containsKey("RoofGroup") || props.containsKey("WestRoofB")
                || props.containsKey("WestRoofM") || props.containsKey("WestRoofT")) {
            return TileClass.ROOF;
        }
        if (sheetName.startsWith("ceilings")) {
            return TileClass.CEILING;
        }
        if (treeSheet || props.containsKey("tree")) {
            return TileClass.TREE;
        }
        if (isWall(props)) {
            return TileClass.WALL;
        }
        if (sheetName.startsWith("floors_exterior_natural")) {
            return TileClass.DIRT;
        }
        if (sheetName.startsWith("floors_exterior") || sheetName.startsWith("blends_street")) {
            return TileClass.PAVEMENT;
        }
        boolean solid = props.containsKey("solidfloor");
        if (sheetName.startsWith("floors_interior") || sheetName.startsWith("floor_")
                || (solid && props.containsKey("attachedFloor"))) {
            return TileClass.FLOOR_INTERIOR;
        }
        if (solid) {
            return TileClass.FLOOR_OTHER;
        }
        if (props.containsKey("vegitation") || props.containsKey("Bush") || sheetName.startsWith("f_bushes")
                || sheetName.startsWith("e_newgrass_")) {
            return TileClass.VEGETATION;
        }
        return TileClass.OTHER;
    }

    /**
     * Whether a whole tilesheet holds trees: a sheet of the erosion or jumbo tree files where at least
     * one tile has the {@code tree} property, or a jumbo tree sheet; stumps are not trees.
     */
    public static boolean isTreeSheet(String sheetName, boolean anyTileHasTreeProperty, boolean treeFile) {
        if (sheetName.contains("stumps")) {
            return false;
        }
        return treeFile && (anyTileHasTreeProperty || sheetName.contains("JUMBO"));
    }

    private static boolean isWall(Map<String, String> props) {
        for (String key : new String[] {"wall", "WallN", "WallW", "WallNW", "WallSE", "WallNTrans", "WallWTrans",
                "WallNWTrans", "DoorWallN", "DoorWallW", "WindowN", "WindowW", "windowN", "windowW", "doorN", "doorW",
                "doorFrN", "doorFrW", "HoppableN", "HoppableW", "TallHoppableN", "TallHoppableW"}) {
            if (props.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
