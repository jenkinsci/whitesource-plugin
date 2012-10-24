package org.whitesource.jenkins;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * @author Edo.Shor
 */
public class PolicyCheckReportAction implements ProminentProjectAction {

    /* --- Static members --- */

    public static final String ICON_PATH = "/plugin/whitesource/images/whitesource-icon.png";
    public static final String DISPLAY_NAME = "White Source - policy check report";

    /* --- Members --- */

    private final AbstractBuild<?, ?> build;

    /* --- Constructors --- */

    /**
     * Constrcutor
     *
     * @param build
     */
    public PolicyCheckReportAction(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    /* --- Interface implementation methods --- */

    public String getIconFileName() {
        return dir().exists() ? ICON_PATH : null;
    }

    public String getDisplayName() {
        return dir().exists() ? DISPLAY_NAME : null;
    }

    public String getUrlName() {
        return "whitesource";
    }

    /* --- Public methods --- */

    public File getBuildArchiveDir(Run run) {
        return new File(run.getRootDir(), "whitesource");
    }

    public FilePath getArchiveTarget(AbstractBuild build) {
            return new FilePath(getBuildArchiveDir(build));
        }

    /**
     * Serves HTML reports.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        DirectoryBrowserSupport dbs = new DirectoryBrowserSupport(this, new FilePath(this.dir()), DISPLAY_NAME, ICON_PATH, false);
        dbs.setIndexFileName("index.html");
        dbs.generateResponse(req, rsp, this);
    }

    /* --- Private methods --- */

    private File dir() {
        return getBuildArchiveDir(this.build);
    }
}
