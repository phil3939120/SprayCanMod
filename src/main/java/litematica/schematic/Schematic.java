package litematica.schematic;

import java.util.Optional;
import com.google.common.collect.ImmutableMap;

import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.position.Vec3i;

public interface Schematic
{
    /**
     * @return the metadata object for this schematic
     */
    SchematicMetadata getMetadata();

    /**
     * @return the type (format) of this schematic
     */
    SchematicType getType();

    /**
     * @return the enclosing size of all the (sub-)regions in this schematic
     */
    Vec3i getEnclosingSize();

    /**
     * @return a map of all the (sub-)regions in this schematic
     */
    ImmutableMap<String, SchematicRegion> getRegions();

    /**
     * Read the contents of the schematic from the provided data.
     * If the schematic contained anything before the call, it will be cleared/removed.
     * @param dataIn the data to read the schematic contents from
     * @return true if the read succeeded without errors, false if there was an error
     */
    boolean read(DataView dataIn);

    /**
     * Write the schematic out to compound data. If an error occurs, returns an empty Optional.
     * @return an optional of the compound data representing the schematic
     */
    Optional<CompoundData> write();

    /**
     * @return the name of the root tag in the schematic file
     */
    default String getRootTagName()
    {
        return "";
    }
}
