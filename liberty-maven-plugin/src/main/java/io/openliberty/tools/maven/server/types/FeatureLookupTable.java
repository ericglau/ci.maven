package io.openliberty.tools.maven.server.types;

import java.util.List;

public class FeatureLookupTable {
    public List<FeatureLookupEntry> entries;

    public FeatureLookupTable() {
        // A default constructor is required
        // If no default constructor is present, the class must be annotated with @JsonbCreator
    }

    public FeatureLookupTable(List<FeatureLookupEntry> entries) {
        this.entries = entries;
    }
}
