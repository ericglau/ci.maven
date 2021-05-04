package io.openliberty.tools.maven.server.types;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureLookupEntry {

    // Public fields are included in the JSON data by default
    public Set<String> javaPackageNames;
    public String mavenDependency;
    public String featureName;
    public Map<String, Integer> occurrences;
    public List<String> conflicts;

    public FeatureLookupEntry() {
        // A default constructor is required
        // If no default constructor is present, the class must be annotated with @JsonbCreator
    }

    public FeatureLookupEntry(Set<String> javaPackageNames, String mavenDependency, String featureName,
            Map<String, Integer> occurrences, List<String> conflicts) {
        this.javaPackageNames = javaPackageNames;
        this.mavenDependency = mavenDependency;
        this.featureName = featureName;
        this.occurrences = occurrences;
        this.conflicts = conflicts;
    }

}
