package org.whitesource.jenkins.extractor;

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

    protected List<String> includes;

    protected List<String> excludes;

    protected BuildListener listener;


    /* --- Constructors --- */

    /**
     * Constructor
     *
     * @param listener
     */
    protected BaseOssInfoExtractor(String includes, String excludes, BuildListener listener) {
        this.includes = WssUtils.splitParameters(includes);
        this.excludes = WssUtils.splitParameters(excludes);
        this.listener = listener;
    }

    /* --- Abstract methods --- */

    public abstract Collection<AgentProjectInfo> extract() throws InterruptedException, IOException;
}
