package org.whitesource.jenkins.extractor.generic;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Concrete implementation for collecting open source info from FreeStyle projects.
 *
 * @author Edo.Shor
 */
public class GenericOssInfoExtractor extends BaseOssInfoExtractor {

    private static final List<String> DEFAULT_SCAN_EXTENSIONS = new ArrayList<String>();
    static {
        DEFAULT_SCAN_EXTENSIONS.addAll(
                Arrays.asList("jar", "war", "ear", "par", "rar",
                        "dll", "exe", "ko", "so", "msi",
                        "zip", "tar", "tar.gz",
                        "swc", "swf"));
    }

    /* --- Members --- */

    private final String projectToken;
    private final FilePath workspace;

    /* --- Constructors --- */

    public GenericOssInfoExtractor(String includes,
                                   String excludes,
                                   Run<?, ?> run,
                                   TaskListener listener,
                                   String projectToken, FilePath workspace) {
        super(includes, excludes, run, listener);
        this.projectToken = projectToken;
        this.workspace = workspace;
    }

    /* --- Concrete implementation methods --- */

    @Override
    public Collection<AgentProjectInfo> extract() throws InterruptedException, IOException{
        Collection<AgentProjectInfo> projectInfos = new ArrayList<>();

        if (CollectionUtils.isEmpty(includes)) {
            for (String extension : DEFAULT_SCAN_EXTENSIONS) {
                includes.add("**/*." + extension);
            }
        }

        LibFolderScanner libScanner = new LibFolderScanner(includes, excludes, listener);
        AgentProjectInfo projectInfo = new AgentProjectInfo();
        if (StringUtils.isBlank(projectToken)) {
            projectInfo.setCoordinates(new Coordinates(null, run.getParent().getName(), "build #" + run.getNumber()));
        } else {
            projectInfo.setProjectToken(projectToken);
        }

        if (workspace == null) {
            throw new RuntimeException("Failed to acquire the Build's workspace");
        }
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        if (dependencies == null) {
            dependencies = new ArrayList<>();
            projectInfo.setDependencies((List<DependencyInfo>) dependencies);
        }
        dependencies.addAll(workspace.act(libScanner));
        projectInfos.add(projectInfo);

        return projectInfos;
    }
}
