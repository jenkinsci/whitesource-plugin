package org.whitesource.jenkins.extractor.maven;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.jenkins.WssUtils;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation for collecting open source info from embedded maven projects.
 *
 * @author Edo.Shor
 */
public class MavenOssInfoExtractor extends BaseOssInfoExtractor {

    /* --- Members--- */

    private final MavenModuleSetBuild mavenModuleSetBuild;

    private final String mavenProjectToken;

    private final Map<String, String> moduleTokens;

    private final boolean ignorePomModules;

    /* --- Constructors--- */

    public MavenOssInfoExtractor(String includes,
                                 String excludes,
                                 MavenModuleSetBuild mavenModuleSetBuild,
                                 BuildListener listener,
                                 String mavenProjectToken,
                                 String moduleTokens,
                                 boolean ignorePomModules) {
        super(includes, excludes, mavenModuleSetBuild, listener);

        this.mavenModuleSetBuild = mavenModuleSetBuild;
        this.mavenProjectToken = mavenProjectToken;
        this.ignorePomModules = ignorePomModules;
        this.moduleTokens = WssUtils.splitParametersMap(moduleTokens);
    }

    /* --- Concrete implementation methods --- */

    @Override
    public Collection<AgentProjectInfo> extract() throws InterruptedException, IOException {
        Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();

        Map<MavenModule, MavenBuild> moduleLastBuilds = mavenModuleSetBuild.getModuleLastBuilds();
        for (Map.Entry<MavenModule, MavenBuild> entry : moduleLastBuilds.entrySet()) {
            MavenBuild moduleBuild = entry.getValue();

            MavenArtifactRecord action = moduleBuild.getAction(MavenArtifactRecord.class);
            if (shouldProcess(action)) {
                listener.getLogger().println("Processing " + action.pomArtifact.canonicalName);
                AgentProjectInfo projectInfo = new AgentProjectInfo();

                projectInfo.setCoordinates(new Coordinates(action.mainArtifact.groupId,
                        action.mainArtifact.artifactId,
                        action.mainArtifact.version));
                projectInfo.setParentCoordinates(new Coordinates(action.pomArtifact.groupId,
                        action.pomArtifact.artifactId,
                        action.pomArtifact.version));


                if (moduleLastBuilds.size() == 1) {
                    projectInfo.setProjectToken(mavenProjectToken);
                } else {
                    projectInfo.setProjectToken(moduleTokens.get(action.mainArtifact.artifactId));
                }

                // dependencies
                Collection<DependencyInfo> dependencyInfos = projectInfo.getDependencies();
                MavenDependenciesRecord dependenciesAction = moduleBuild.getAction(MavenDependenciesRecord.class);
                if (dependenciesAction == null) {
                    listener.getLogger().println("No dependencies found.");
                } else {
                    dependencyInfos.addAll(dependenciesAction.getDependencies());
                    listener.getLogger().println("Found " + dependenciesAction.getDependencies().size() + " dependencies (transitive included)");
                }
                projectInfos.add(projectInfo);
            } else {
                listener.getLogger().println("Skipping module: " + moduleBuild.getProject().getDisplayName());
            }
        }

        return projectInfos;
    }

    /* --- Private methods --- */

    private boolean shouldProcess(MavenArtifactRecord action) {
        if (action == null) {
            return false;
        }

        boolean process = true;

        String artifactId = action.mainArtifact.artifactId;
        String type = action.mainArtifact.type;
        if (ignorePomModules && "pom".equals(type)) { // always true when maven is not producing artifacts due to goal < package.
            process = false;
        } else if (!excludes.isEmpty() && matchAny(artifactId, excludes)) {
            process = false;
        } else if (!includes.isEmpty() && matchAny(artifactId, includes)) {
            process = true;
        }

        return process;
    }

    private boolean matchAny(String value, List<String> patterns) {
        boolean match = false;

        for (String pattern : patterns) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            if (value.matches(regex)) {
                match = true;
                break;
            }
        }

        return match;
    }
}
