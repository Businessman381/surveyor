package folk.sisby.surveyor.terrain;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DimensionSupport {
    public static Map<RegistryKey<World>, List<Integer>> cache = new HashMap<>();

    private static List<Integer> getSummaryLayersInternal(World world) {
        List<Integer> layers = new ArrayList<>();
        DimensionType dimension = world.getDimension();
        layers.add(dimension.minY() + dimension.height() - 1); // Layer at Max Y
        if (dimension.logicalHeight() != dimension.height()) layers.add(dimension.minY() + dimension.logicalHeight() - 2); // Layer below Playable Limit
        if (dimension.hasSkyLight()) layers.add(world.getSeaLevel() - 2); // Layer below sea level (assume caves underneath)
        if (dimension.minY() + dimension.height() > 256) layers.add(256); // Layer At Y=256 (assume special layer change)
        if (dimension.minY() < 0) layers.add(0); // Layer At Y=0 (assume special layer change)
        if (world.getDimensionKey() == DimensionTypes.THE_NETHER) {
            layers.add(70); // Mid outcrops
            layers.add(40); // Lava Shores
        }
        layers.add(dimension.minY()); // End Layers at Min Y
        return Collections.unmodifiableList(layers);
    }

    public static List<Integer> getSummaryLayers(World world) {
        return cache.computeIfAbsent(world.getRegistryKey(), k -> getSummaryLayersInternal(world));
    }
}
