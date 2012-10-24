package org.whitesource.jenkins.extractor.generic;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Concrete implementation for collecting open source info from FreeStyle projects.
 *
 * @author Edo.Shor
 */
public class GenericOssInfoExtractor extends BaseOssInfoExtractor {

    /* --- Members --- */

    private final String projectToken;

    /* --- Constructors --- */

    public GenericOssInfoExtractor(String includes,
                                   String excludes,
                                   AbstractBuild build,
                                   BuildListener listener,
                                   String projectToken) {
        super(includes, excludes, build, listener);
        this.projectToken = projectToken;
    }

    /* --- Concrete implementation methods --- */

    @Override
    public Collection<AgentProjectInfo> extract() throws InterruptedException, IOException{
        Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();

        if (CollectionUtils.isEmpty(includes)) {
            listener.error("No include patterns defined. Skipping update.");
        } else {
            LibFolderScanner libScanner = new LibFolderScanner(includes, excludes, listener);
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            if (StringUtils.isBlank(projectToken)) {
                projectInfo.setCoordinates(new Coordinates(null, build.getProject().getName(), "build #" + build.getNumber()));
            } else {
                projectInfo.setProjectToken(projectToken);
            }

            projectInfo.getDependencies().addAll(build.getWorkspace().act(libScanner));
            projectInfos.add(projectInfo);
        }

        return projectInfos;
    }
}
