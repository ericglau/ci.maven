package io.openliberty.tools.maven.server.types;

import java.util.Set;

public class FeatureLookupTable {
    public Set<FeatureLookupEntry> entries;

    public FeatureLookupTable() {
        // A default constructor is required
        // If no default constructor is present, the class must be annotated with @JsonbCreator
    }

    public FeatureLookupTable(Set<FeatureLookupEntry> entries) {
        this.entries = entries;
    }
}
