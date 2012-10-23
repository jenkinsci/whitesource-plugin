package org.whitesource.jenkins.extractor.generic;

import hudson.FilePath;
import hudson.model.BuildListener;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Concrete implementation for collecting open source info from FreeStyle projects.
 *
 * @author Edo.Shor
 */
public class GenericOssInfoExtractor extends BaseOssInfoExtractor {

    /* --- Members --- */

    private final FilePath rootFolder;

    private final String projectToken;

    /* --- Constructors --- */

    /**
     * Constructor
     *
     * @param includes
     * @param excludes
     * @param listener
     * @param rootFolder
     * @param projectToken
     */
    public GenericOssInfoExtractor(String includes,
                                   String excludes,
                                   BuildListener listener,
                                   FilePath rootFolder,
                                   String projectToken) {
        super(includes, excludes, listener);
        this.rootFolder = rootFolder;
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
            projectInfo.setProjectToken(projectToken);
            projectInfo.getDependencies().addAll(rootFolder.act(libScanner));
            projectInfos.add(projectInfo);
        }

        return projectInfos;
    }
}
