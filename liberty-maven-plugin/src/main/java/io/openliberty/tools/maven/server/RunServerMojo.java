/**
 * (C) Copyright IBM Corporation 2014, 2020.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.maven.server;

import java.util.List;

import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.openliberty.tools.ant.ServerTask;
import io.openliberty.tools.maven.utils.ExecuteMojoUtil;

/**
 * Start a liberty server
 */
@Mojo(name = "run", requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RunServerMojo extends PluginConfigSupport {

    /**
     * Clean all cached information on server start up.
     */
    @Parameter(property = "clean", defaultValue = "false")
    protected boolean clean;
    
    /**
     * Run the server in embedded mode
     */
    @Parameter(property = "embedded", defaultValue = "false")
    private boolean embedded;

    @Override
    protected void doExecute() throws Exception {
        if (skip) {
            getLog().info("\nSkipping run goal.\n");
            return;
        }
        String projectPackaging = project.getPackaging();

        // If there are downstream projects (e.g. other modules depend on this module in the Maven Reactor build order),
        // then skip running Liberty on this module but only run compile.
        ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        if (graph != null) {
            List<MavenProject> downstreamProjects = graph.getDownstreamProjects(project, true);
            if (!downstreamProjects.isEmpty()) {
                log.debug("Downstream projects: " + downstreamProjects);
                if (projectPackaging.equals("ear")) {
                    runMojo("org.apache.maven.plugins", "maven-ear-plugin", "generate-application-xml");
                    runMojo("org.apache.maven.plugins", "maven-resources-plugin", "resources");
                } else {
                    runMojo("org.apache.maven.plugins", "maven-resources-plugin", "resources");
                    runMojo("org.apache.maven.plugins", "maven-compiler-plugin", "compile");
                }
                //return;
            }
        }

        //if (!looseApplication) {
            // List<MavenProject> upstreamProjects = graph.getUpstreamProjects(project, true);
            // if (!upstreamProjects.isEmpty()) {
            //     log.info("Upstream projects: " + upstreamProjects);
            //     for (MavenProject upstreamProject : upstreamProjects) {
            //         String upstreamPackaging = upstreamProject.getPackaging();
            //         log.info("Upstream packaging: " + upstreamPackaging);
            //         switch (upstreamPackaging) {
            //             case "war":
            //                 runMojo("org.apache.maven.plugins", "maven-war-plugin", "war", upstreamProject);
            //                 break;
            //             case "ear":
            //                 runMojo("org.apache.maven.plugins", "maven-ear-plugin", "ear", upstreamProject);
            //                 break;
            //             case "ejb":
            //                 runMojo("org.apache.maven.plugins", "maven-ejb-plugin", "ejb", upstreamProject);
            //                 break;
            //         }
            //         log.info("Done upstream");
            //     }
            // }

            switch (projectPackaging) {
                case "war":
                    runMojo("org.apache.maven.plugins", "maven-war-plugin", "war");
                    break;
                case "ear":
                    runMojo("org.apache.maven.plugins", "maven-ear-plugin", "ear");
                    break;
                case "ejb":
                    runMojo("org.apache.maven.plugins", "maven-ejb-plugin", "ejb");
                    break;
            }
        //}

        if(true)return;
        
        runLibertyMojoCreate();
        runLibertyMojoInstallFeature(null, null);
        runLibertyMojoDeploy(false);

        ServerTask serverTask = initializeJava();
        copyConfigFiles();
        serverTask.setUseEmbeddedServer(embedded);
        serverTask.setClean(clean);
        serverTask.setOperation("run");       
        serverTask.execute();
    }

}
