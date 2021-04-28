/**
 * (C) Copyright IBM Corporation 2021.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.openliberty.tools.maven.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.pluginsupport.util.ArtifactItem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyResolutionException;

import io.openliberty.tools.common.plugins.config.ServerConfigDropinXmlDocument;
import io.openliberty.tools.common.plugins.util.InstallFeatureUtil;
import io.openliberty.tools.common.plugins.util.InstallFeatureUtil.ProductProperties;
import io.openliberty.tools.common.plugins.util.PluginExecutionException;
import io.openliberty.tools.common.plugins.util.PluginScenarioException;
import io.openliberty.tools.maven.BasicSupport;
import io.openliberty.tools.maven.InstallFeatureSupport;

/**
 * This mojo generates the features required in the featureManager element in server.xml.
 * It examines the dependencies declared in the pom.xml and the features already declared
 * in the featureManager elements in the XML configuration files. Then it generates any
 * missing feature names and stores them in a new featureManager element in a new XML file.
 */
@Mojo(name = "generate-features")
public class GenerateFeaturesMojo extends InstallFeatureSupport {

    protected static final String PLUGIN_ADDED_FEATURES_FILE = "configDropins/overrides/liberty-plugin-added-features.xml";
    protected static final String HEADER = "# Generated by liberty-maven-plugin";

    @Parameter(property = "filterDependency", defaultValue = "false")
    private boolean filterDependency;

    @Parameter(property = "includes")
    private String includes;

    @Parameter(property = "openLibertyRepo")
    private String openLibertyRepo;

    /*
     * (non-Javadoc)
     * @see org.codehaus.mojo.pluginsupport.MojoSupport#doExecute()
     */
    @Override
    protected void doExecute() throws Exception {
        if(!initialize()) {
            return;
        }
        if (filterDependency) {
            if (openLibertyRepo == null) {
                openLibertyRepo = "../open-liberty";
            }
            File openLibertyRepoDir = new File(openLibertyRepo);
    
            if (!openLibertyRepoDir.exists()) {
                try {
                    throw new MojoExecutionException("open-liberty git repository must exist at " + openLibertyRepoDir.getCanonicalPath() + ", or use -DopenLibertyRepo to specify custom location");
                } catch (IOException e) {
                    throw new MojoExecutionException("open-liberty git repository must exist at " + openLibertyRepoDir.getAbsolutePath() + ", or use -DopenLibertyRepo to specify custom location");
                }
            }

            Set<String> publicFeatures = getPublicFeatures();
            if (includes == null) {
                Set<ArtifactItem> featureDefinedMavenArtifacts = getFeatureDefinedMavenArtifacts(openLibertyRepoDir);
                for (ArtifactItem artifact : featureDefinedMavenArtifacts) {
                    filterDependency(getFilter(artifact), publicFeatures);
                }
            } else {
                filterDependency(includes, publicFeatures);
            }
        } else {
            generateFeatures();
        }
    }

    private void recursiveAddFeaturesFiles(File dir, List<File> appendedResults) {
        // add all features files that are directly in this directory 
        File[] featureFilesArray = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".feature");
            }
        });
        Collections.addAll(appendedResults, featureFilesArray);

        // then recursively call the same method on all of its children dirs
        File[] subFiles = dir.listFiles();
        if (subFiles == null) {
            return;
        }
        for (File subFile : subFiles) {
            if (subFile.isDirectory()) {
                recursiveAddFeaturesFiles(subFile, appendedResults);
            }
        }
    }

    private Set<ArtifactItem> getFeatureDefinedMavenArtifacts(File openLibertyRepoDir) throws Exception {
        // get list of all mavenCoordinate items from .feature files in OL repo
        File featureVisibilityDir = new File(openLibertyRepoDir, "dev/com.ibm.websphere.appserver.features/visibility");
        if (!featureVisibilityDir.exists()) {
            throw new MojoExecutionException(featureVisibilityDir.getAbsolutePath() + " does not exist. Ensure open-liberty git repository is cloned to " + openLibertyRepoDir.getAbsolutePath());
        }

        List<File> allFeatureFiles = new ArrayList<File>();
        recursiveAddFeaturesFiles(featureVisibilityDir, allFeatureFiles);

        log.info("All features size " + allFeatureFiles.size());

        // unique set of artifact items (to avoid duplicates)
        Set<ArtifactItem> allArtifactItems = new HashSet<ArtifactItem>();

        for (File featureFile : allFeatureFiles) {
            log.info(featureFile.getAbsolutePath());

            try {
                addArtifactsFromFeatureFile(featureFile, allArtifactItems);
            } catch (IOException e) {
                log.error("Could not read file " + featureFile, e);
            }
        }
        return allArtifactItems;
    }
    
    private void addArtifactsFromFeatureFile(File featureFile, Set<ArtifactItem> allArtifactItems) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(featureFile));
        StringBuilder sb = new StringBuilder();
        while (reader.ready()) {
            sb.append(reader.readLine());
        }
        reader.close();
        String content = sb.toString();
        
        final String KEYWORD = "mavenCoordinates";
        Pattern p = Pattern.compile(KEYWORD + "=\"[^\"]*\"");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String match = m.group();
            // get the part within quotes
            String coordinates = match.substring(KEYWORD.length() + 2, match.length() - 1);
            log.info("File " + featureFile + " has mavenCoordinates " + coordinates);

            String[] tokens = coordinates.split(":");
            if (tokens.length != 3) {
                throw new MojoExecutionException("The string " + coordinates
                        + " is not a valid Maven coordinates string. Expected format is groupId:artifactId:version");
            }
            ArtifactItem item = new ArtifactItem();
            item.setGroupId(tokens[0]);
            item.setArtifactId((tokens[1]));
            item.setVersion(tokens[2]);
            allArtifactItems.add(item);
        }
    }

    private String getFilter(ArtifactItem artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + "::" + artifact.getVersion();
    }

    private void generateFeatures() throws PluginExecutionException {
        log.warn("warn");
        List<ProductProperties> propertiesList = InstallFeatureUtil.loadProperties(installDirectory);
        String openLibertyVersion = InstallFeatureUtil.getOpenLibertyVersion(propertiesList);
        log.warn("version:"+openLibertyVersion);

        InstallFeatureMojoUtil util;
        try {
            util = new InstallFeatureMojoUtil(new HashSet<String>(), propertiesList, openLibertyVersion, null);
        } catch (PluginScenarioException e) {
            log.debug("Exception creating the server utility object", e);
            log.error("Error attempting to generate server feature list.");
            return;
        }

        Set<String> visibleServerFeatures = util.getAllServerFeatures();
        log.warn("feature count="+visibleServerFeatures.size());

        Set<String> libertyFeatureDependencies = getFeaturesFromDependencies(project);
        log.warn("maven dependencies that are liberty features:"+libertyFeatureDependencies);

        // Remove project dependency features which are hidden.
        Set<String> visibleLibertyProjectDependencies = new HashSet<String>(libertyFeatureDependencies);
        visibleLibertyProjectDependencies.retainAll(visibleServerFeatures);
        log.warn("maven dependencies that are VALID liberty features:"+visibleLibertyProjectDependencies);

        File newServerXml = new File(serverDirectory, PLUGIN_ADDED_FEATURES_FILE);
        log.warn("New server xml file:"+newServerXml+". Now to delete this file if it exists.");
        newServerXml.delete(); // about to regenerate this file. Must be removed before getLibertyDirectoryPropertyFiles

        Map<String, File> libertyDirPropertyFiles;
        try {
            libertyDirPropertyFiles = BasicSupport.getLibertyDirectoryPropertyFiles(installDirectory, userDirectory, serverDirectory);
        } catch (IOException e) {
            // TODO restore the xml file just deleted above
            log.debug("Exception reading the server property files", e);
            log.error("Error attempting to generate server feature list. Ensure you can read the property files in the server installation directory.");
            return;
        }
        Set<String> existingFeatures = util.getServerFeatures(serverDirectory, libertyDirPropertyFiles);
        log.warn("Features in server.xml:"+existingFeatures);

        Set<String> missingLibertyFeatures = getMissingLibertyFeatures(visibleLibertyProjectDependencies,
				existingFeatures);
        log.warn("maven dependencies that are VALID liberty features but are missing from server.xml:"+missingLibertyFeatures);

        // Create specialized server.xml
        try {
            ServerConfigDropinXmlDocument configDocument = ServerConfigDropinXmlDocument.newInstance();
            configDocument.createComment(HEADER);
            for (String missing : missingLibertyFeatures) {
                log.warn("adding missing feature:"+missing);
                configDocument.createFeature(missing);
            }
            configDocument.writeXMLDocument(newServerXml);
            log.warn("Created file "+newServerXml);
        } catch(ParserConfigurationException | TransformerException | IOException e) {
            log.debug("Exception creating the server features file", e);
            log.error("Error attempting to create the server feature file. Ensure you can write to the server installation directory.");
            return;
        }
    }

    /**
     * Comb through the list of Maven project dependencies and find the ones which are 
     * Liberty features.
     * @param project  Current Maven project
     * @return List of names of dependencies
     */
    private Set<String> getFeaturesFromDependencies(MavenProject project) {
        Set<String> libertyFeatureDependencies = new HashSet<String>();
        List<Dependency> allProjectDependencies = project.getDependencies();
        for (Dependency d : allProjectDependencies) {
            String featureName = getFeatureName(d);
            if (featureName != null) {
                libertyFeatureDependencies.add(featureName);
            }
        }
        return libertyFeatureDependencies;
    }

    /**
     * From all the candidate project dependencies remove the ones already in server.xml
     * to make the list of the ones that are missing from server.xml.
     * @param visibleLibertyProjectDependencies
     * @param existingFeatures
     * @return
     */
	private Set<String> getMissingLibertyFeatures(Set<String> visibleLibertyProjectDependencies,
			Set<String> existingFeatures) {
		Set<String> missingLibertyFeatures = new HashSet<String>(visibleLibertyProjectDependencies);
        if (existingFeatures != null) {
            for (String s : visibleLibertyProjectDependencies) {
                // existingFeatures are all lower case
                if (existingFeatures.contains(s.toLowerCase())) {
                    missingLibertyFeatures.remove(s);
                }
            }
        }
		return missingLibertyFeatures;
	}

	/**
	 * Determine if a dependency is a Liberty feature or not
	 * @param mavenDependency  a Maven project dependency 
	 * @return the Liberty feature name if the input is a Liberty feature otherwise return null.
	 */
    private String getFeatureName(Dependency mavenDependency) {
        if (mavenDependency.getGroupId().equals("io.openliberty.features")) {
            return mavenDependency.getArtifactId();
        }
        return null;
    }

    private void filterDependency(String includesPattern, Set<String> publicFeatures) throws DependencyResolutionException, MojoExecutionException {
        log.debug("<<<<<<<<<<<< Finding Dependency Paths >>>>>>>>>>>");
        DependencyManagement dm = project.getDependencyManagement();
        // null check for dm
        if (dm == null) {
            log.debug("DependencyManagement is null");
            return;
        }
        List<Dependency> dependencies = dm.getDependencies();
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for (Dependency dep : dependencies) {
            ArtifactItem item = new ArtifactItem();
            item.setGroupId(dep.getGroupId());
            item.setArtifactId(dep.getArtifactId());
            // force the collection to get only the pom, not the actual artifact type
            item.setType("pom");
            item.setVersion(dep.getVersion());

            artifacts.add(getArtifact(item));
        }

        List<List<org.eclipse.aether.graph.DependencyNode>> allPaths = new ArrayList<List<org.eclipse.aether.graph.DependencyNode>>();

        for (Artifact artifact : artifacts) {
            org.eclipse.aether.artifact.Artifact aetherArtifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getType(), artifact.getVersion());
            org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(aetherArtifact, null, true);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.setRepositories(repositories);
            
            CollectResult collectResult;
            try {
                // builds the dependency graph without downloading actual artifact files
                collectResult = repositorySystem.collectDependencies(repoSession, collectRequest);

                org.eclipse.aether.graph.DependencyNode rootNode = collectResult.getRoot();
                org.eclipse.aether.graph.DependencyFilter depFilter = new org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter(
                        includesPattern);
                org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor filteringVisitor = new org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor(
                        depFilter);
                rootNode.accept(filteringVisitor);
                List<List<org.eclipse.aether.graph.DependencyNode>> nodeList = filteringVisitor.getPaths();
                if (nodeList == null || nodeList.isEmpty()) {
                    log.debug("No Paths");
                } else {
                    for (List<org.eclipse.aether.graph.DependencyNode> pathList : nodeList) {
                        allPaths.add(pathList);
                        log.debug("Path added");
                    }
                }
            } catch (DependencyCollectionException e) {
                log.error("Could not collect dependencies", e);
            }
        }

        Map<String, Integer> publicFeatureOccurrences = new HashMap<String, Integer>();

        int i = 0;
        for (List<org.eclipse.aether.graph.DependencyNode> pathList : allPaths) {
            log.debug("----------------------------------------------------------");
            log.debug("<<< Path " + ++i + " >>>");
            // for each node within the path list, go up the dependencies until a public feature is found
            String publicFeature = null;
            for (int j = pathList.size() - 1; j>=0; j--) {
                org.eclipse.aether.graph.DependencyNode node = pathList.get(j);
                log.debug(node.getArtifact().toString());
                if (isPublicFeature(node, publicFeatures)) {
                    if (publicFeature == null) {
                        log.debug("- Found public feature!");
                        publicFeature = node.getArtifact().getArtifactId();
                        Integer previousOccurrences = publicFeatureOccurrences.get(publicFeature);
                        if (previousOccurrences == null) {
                            previousOccurrences = 0;
                        }
                        publicFeatureOccurrences.put(publicFeature, previousOccurrences + 1);
                    } else {
                        log.debug("- Ignoring parent");
                        // TODO keep track of this for reporting purposes
                    }
                }
            }
            log.debug("----------------------------------------------------------");
        }

        String mostCommonPublicFeature = null;
        int mostFeatureOccurrences = 0;
        //log.info("===== Keyset size " + publicFeatureOccurrences.keySet().size()); 
        for (String publicFeature : publicFeatureOccurrences.keySet()) {
            //log.info("===== Looking at feature " + publicFeature); 
            int occurrences = publicFeatureOccurrences.get(publicFeature);
            if (occurrences > mostFeatureOccurrences) {
                mostCommonPublicFeature = publicFeature;
                mostFeatureOccurrences = occurrences;
                log.debug("Feature " + mostCommonPublicFeature + " has " + mostFeatureOccurrences + " occurrences");
            }
        }
        if (publicFeatureOccurrences.size() > 1) {
            log.info("Dependency [" + includesPattern + "] -> Feature [" + mostCommonPublicFeature + "].  Occurrences: " + publicFeatureOccurrences);
            findConflicts(mostFeatureOccurrences, publicFeatureOccurrences);
        } else {
            log.info("Dependency [" + includesPattern + "] -> Feature [" + mostCommonPublicFeature + "]");
        }
    }

    private void findConflicts(int mostFeatureOccurrences, Map<String, Integer> publicFeatureOccurrences) {
        List<String> potentialConflicts = new ArrayList<String>();
        for (Map.Entry<String,Integer> entry : publicFeatureOccurrences.entrySet()) {
            if (entry.getValue() == mostFeatureOccurrences) {
                potentialConflicts.add(entry.getKey());
            }
        }
        if (potentialConflicts.size() > 1) {
            log.info("===== CONFLICTS: " + potentialConflicts);
        }
    }

    // get set of public features artifactIds
    private Set<String> getPublicFeatures() {
        File featuresVisibilityDir = new File(openLibertyRepo, "dev/com.ibm.websphere.appserver.features/visibility");
        // get public folder
        File publicFolder = new File(featuresVisibilityDir, "public");
        File[] publicFeatures = publicFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });
        Set<String> result = new HashSet<String>();
        for (File f : publicFeatures) {
            // get folder names, populate list
            // TODO only add real features based on .feature file
            result.add(f.getName());
        }
        log.debug("Public features: " + result);
        return result;
    }

    private boolean isPublicFeature(DependencyNode node, Set<String> publicFeatures) {
        return publicFeatures.contains(node.getArtifact().getArtifactId());
    }

    

}