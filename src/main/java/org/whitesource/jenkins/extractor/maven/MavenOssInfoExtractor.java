package org.whitesource.jenkins.extractor.maven;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.jenkins.WssUtils;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

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
        PrintStream logger = listener.getLogger();
        Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();

        Map<MavenModule, MavenBuild> moduleLastBuilds = mavenModuleSetBuild.getModuleLastBuilds();
        for (Map.Entry<MavenModule, MavenBuild> entry : moduleLastBuilds.entrySet()) {
            MavenBuild moduleBuild = entry.getValue();

            MavenArtifactRecord action = moduleBuild.getAction(MavenArtifactRecord.class);
            if (shouldProcess(action)) {
                logger.println("Processing " + action.pomArtifact.canonicalName);
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
                    logger.println("No dependencies found.");
                } else {
                    Set<DependencyInfo> dependencies = dependenciesAction.getDependencies();
                    dependencyInfos.addAll(dependencies);
                    logger.println("Found " + dependencies.size() + " dependencies (transitive included)");
                }
                projectInfos.add(projectInfo);
            } else {
                logger.println("Skipping module: " + moduleBuild.getProject().getDisplayName());
            }
        }

        return projectInfos;
    }

    public String getTopMostProjectName() {
        MavenModule rootModule = mavenModuleSetBuild.getParent().getRootModule();
        String name = rootModule.getDisplayName();

        return StringUtils.isBlank(name) ? rootModule.getModuleName().artifactId : name;
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

        Iterator<String> it = patterns.iterator();
        while (it.hasNext() && !match) {
            String regex = it.next().replace(".", "\\.").replace("*", ".*");
            match = value.matches(regex);
        }

        return match;
    }
}
