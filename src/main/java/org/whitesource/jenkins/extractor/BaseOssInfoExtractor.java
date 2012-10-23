package org.whitesource.jenkins.extractor;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.jenkins.WssUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author Edo.Shor
 */
public abstract class BaseOssInfoExtractor {

    /* --- Members --- */

    protected final List<String> includes;

    protected final List<String> excludes;

    protected final AbstractBuild build;

    protected final BuildListener listener;


    /* --- Constructors --- */

    protected BaseOssInfoExtractor(String includes, String excludes, AbstractBuild build, BuildListener listener) {
        this.includes = WssUtils.splitParameters(includes);
        this.excludes = WssUtils.splitParameters(excludes);
        this.build = build;
        this.listener = listener;
    }

    /* --- Abstract methods --- */

    public abstract Collection<AgentProjectInfo> extract() throws InterruptedException, IOException;
}
