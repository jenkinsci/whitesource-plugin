package org.whitesource.jenkins.extractor;

import hudson.model.Run;
import hudson.model.TaskListener;
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

    protected final Run<?, ?> run;

    protected final TaskListener listener;


    /* --- Constructors --- */

    protected BaseOssInfoExtractor(String includes, String excludes, Run<?, ?> run, TaskListener listener) {
        this.includes = WssUtils.splitParameters(includes);
        this.excludes = WssUtils.splitParameters(excludes);
        this.run = run;
        this.listener = listener;
    }

    /* --- Abstract methods --- */

    public abstract Collection<AgentProjectInfo> extract() throws InterruptedException, IOException;
}
