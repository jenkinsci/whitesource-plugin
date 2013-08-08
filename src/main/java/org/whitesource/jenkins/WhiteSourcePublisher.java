/*
 * Copyright (C) 2010 White Source Ltd.
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

package org.whitesource.jenkins;

import hudson.*;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.dispatch.CheckPoliciesResult;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.jenkins.extractor.BaseOssInfoExtractor;
import org.whitesource.jenkins.extractor.generic.GenericOssInfoExtractor;
import org.whitesource.jenkins.extractor.maven.MavenOssInfoExtractor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

/**
 * @author ramakrishna
 * @author Edo.Shor
 */
public class WhiteSourcePublisher extends Recorder {

    /* --- Members --- */

    private final String jobCheckPolicies;

    private final String jobApiToken;

    private final String product;

    private final String productVersion;

    private final String projectToken;

    private final String libIncludes;

    private final String libExcludes;

    private final String mavenProjectToken;

    private final String moduleTokens;

    private final String modulesToInclude;

    private final String modulesToExclude;

    private final boolean ignorePomModules;

    /* --- Constructors --- */

    @DataBoundConstructor
    public WhiteSourcePublisher(String jobCheckPolicies,
                                String jobApiToken,
                                String product,
                                String productVersion,
                                String projectToken,
                                String libIncludes,
                                String libExcludes,
                                String mavenProjectToken,
                                String moduleTokens,
                                String modulesToInclude,
                                String modulesToExclude,
                                boolean ignorePomModules) {
        super();
        this.jobCheckPolicies = jobCheckPolicies;
        this.jobApiToken = jobApiToken;
        this.product = product;
        this.productVersion = productVersion;
        this.projectToken = projectToken;
        this.libIncludes = libIncludes;
        this.libExcludes = libExcludes;
        this.mavenProjectToken = mavenProjectToken;
        this.moduleTokens = moduleTokens;
        this.modulesToInclude = modulesToInclude;
        this.modulesToExclude = modulesToExclude;
        this.ignorePomModules = ignorePomModules;
    }

    /* --- Interface implementation methods --- */

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();

        logger.println("Updating White Source");

        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            logger.println("Build failed. Skipping update.");
            return true;
        }

        if (WssUtils.isFreeStyleMaven(build.getProject())) {
            logger.println("Free style maven jobs are not supported in this version. See plugin documentation.");
            return true;
        }

        DescriptorImpl globalConfig = (DescriptorImpl) getDescriptor();

        // make sure we have an organization token
        String apiToken = globalConfig.apiToken;
        if (StringUtils.isNotBlank(jobApiToken)) {
            apiToken = jobApiToken;
        }
        if (StringUtils.isBlank(apiToken)) {
            logger.println("No API token configured. Skipping update.");
            return true;
        }

        // should we check policies ?
        boolean shouldCheckPolicies;
        if (StringUtils.isBlank(jobCheckPolicies) || "global".equals(jobCheckPolicies)) {
            shouldCheckPolicies = globalConfig.checkPolicies;
        } else {
            shouldCheckPolicies = "enable".equals(jobCheckPolicies);
        }

        // collect OSS usage information
        logger.println("Collecting OSS usage information");
        Collection<AgentProjectInfo> projectInfos;
        String productNameOrToken = product;
        if ((build instanceof MavenModuleSetBuild)) {
            MavenOssInfoExtractor extractor = new MavenOssInfoExtractor(modulesToInclude,
                    modulesToExclude, (MavenModuleSetBuild) build, listener, mavenProjectToken, moduleTokens, ignorePomModules);
            projectInfos = extractor.extract();
            if (StringUtils.isBlank(product)) {
                productNameOrToken = extractor.getTopMostProjectName();
            }
        } else if ((build instanceof FreeStyleBuild)) {
            GenericOssInfoExtractor extractor = new GenericOssInfoExtractor(libIncludes,
                    libExcludes, build, listener, projectToken);
            projectInfos = extractor.extract();
        } else {
            stopBuild(build, listener, "Unrecognized build type " + build.getClass().getName());
            return true;
        }

        // send to white source
        if (CollectionUtils.isEmpty(projectInfos)) {
            logger.println("No open source information found.");
        } else {
            WhitesourceService service = createServiceClient(globalConfig.serviceUrl);
            try {
                if (shouldCheckPolicies) {
                    logger.println("Checking policies");
                    CheckPoliciesResult result = service.checkPolicies(apiToken, productNameOrToken, productVersion, projectInfos);
                    policyCheckReport(result, build, listener);
                    if (result.hasRejections()) {
                        stopBuild(build, listener, "Open source rejected by organization policies.");
                    } else {
                        logger.println("All dependencies conform with open source policies.");
                        sendUpdate(apiToken, productNameOrToken, projectInfos, service, logger);
                    }
                } else {
                    sendUpdate(apiToken, productNameOrToken, projectInfos, service, logger);
                }
            } catch (WssServiceException e) {
                stopBuildOnError(build, listener, e);
            } catch (IOException e) {
                stopBuildOnError(build, listener, e);
            } catch (RuntimeException e) {
                stopBuildOnError(build, listener, e);
            } finally {
                service.shutdown();
            }
        }

        return true;
    }

    /* --- Public methods --- */

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /* --- Private methods --- */

    private WhitesourceService createServiceClient(String serviceUrl) {
        String url = serviceUrl;
        if (StringUtils.isNotBlank(url)){
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "agent";
        }

        WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, url);

        if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
            final ProxyConfiguration proxy = Hudson.getInstance().proxy;

            // ditch protocol if present
            String host = proxy.name;
            try {
                URL tmpUrl = new URL(proxy.name);
                host = tmpUrl.getHost();
            } catch (MalformedURLException e) {
                // nothing to do here
            }

            service.getClient().setProxy(host, proxy.port, proxy.getUserName(), proxy.getPassword());
        }

        return service;
    }

    private void policyCheckReport(CheckPoliciesResult result, AbstractBuild build, BuildListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().println("Generating policy check report");

        PolicyCheckReport report = new PolicyCheckReport(result,
                build.getProject().getName(),
                Integer.toString(build.getNumber()));
        report.generate(build.getRootDir(), false);

        build.addAction(new PolicyCheckReportAction(build));
    }

    private void sendUpdate(String orgToken, String productNameOrToken,
                            Collection<AgentProjectInfo> projectInfos,
                            WhitesourceService service, PrintStream logger) throws WssServiceException {
        logger.println("Sending to White Source");
        UpdateInventoryResult updateResult = service.update(orgToken, productNameOrToken, productVersion, projectInfos);
        logUpdateResult(updateResult, logger);
    }

    private void stopBuild(AbstractBuild build, BuildListener listener, String message) {
        listener.error(message);
        build.setResult(Result.FAILURE);
    }

    private void stopBuildOnError(AbstractBuild build, BuildListener listener, Exception e) {
        if (e instanceof IOException) {
            Util.displayIOException((IOException) e, listener);
        }
        e.printStackTrace(listener.fatalError("White Source Publisher failure"));
        build.setResult(Result.FAILURE);
    }

    private void logUpdateResult(UpdateInventoryResult result, PrintStream logger) {
        logger.println("White Source update results: ");
        logger.println("White Source organization: " + result.getOrganization());
        logger.println(result.getCreatedProjects().size() + " Newly created projects:");
        logger.println(StringUtils.join(result.getCreatedProjects(), ","));
        logger.println(result.getUpdatedProjects().size() + " existing projects were updated:");
        logger.println(StringUtils.join(result.getUpdatedProjects(), ","));
    }

    /* --- Nested classes --- */

    /**
     * Implementation of the interface for generating the policy check report in a machine agnostic manner.
     */
    static final class PolicyCheckReportFileCallable implements FilePath.FileCallable<FilePath> {

        /* --- Static members--- */

        private static final long serialVersionUID = -1560305874205317068L;

        /* --- Members--- */

        private final PolicyCheckReport report;

        /* --- Constructors--- */

        PolicyCheckReportFileCallable(PolicyCheckReport report) {
            this.report = report;
        }

        /* --- Interface implementation methods --- */

        public FilePath invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return new FilePath(report.generate(f, false));
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /* --- Members--- */

        private String serviceUrl;

        private String apiToken;

        private boolean checkPolicies;

        /* --- Constructors--- */

        /**
         * Default constructor
         */
        public DescriptorImpl() {
            super();
            load();
        }

        /* --- Overridden methods --- */

        @Override
        public String getDisplayName() {
            return "White Source Publisher";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/whitesource/help/help.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            apiToken = json.getString("apiToken");
            serviceUrl  = json.getString("serviceUrl");
            checkPolicies = json.getBoolean("checkPolicies");

            save();

            return super.configure(req, json);
        }

        /* --- Public methods --- */

        public FormValidation doCheckApiToken(@QueryParameter String apiToken) {
            return FormValidation.validateRequired(apiToken);
        }

        /* --- Getters / Setters --- */

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public boolean isCheckPolicies() {
            return checkPolicies;
        }

        public void setCheckPolicies(boolean checkPolicies) {
            this.checkPolicies = checkPolicies;
        }
    }

    /* --- Getters --- */

    public String getJobCheckPolicies() {
        return jobCheckPolicies;
    }

    public String getJobApiToken() {
        return jobApiToken;
    }

    public String getProduct() {
        return product;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public String getProjectToken() {
        return projectToken;
    }

    public String getLibIncludes() {
        return libIncludes;
    }

    public String getLibExcludes() {
        return libExcludes;
    }

    public String getMavenProjectToken() {
        return mavenProjectToken;
    }

    public String getModuleTokens() {
        return moduleTokens;
    }

    public String getModulesToInclude() {
        return modulesToInclude;
    }

    public String getModulesToExclude() {
        return modulesToExclude;
    }

    public boolean isIgnorePomModules() {
        return ignorePomModules;
    }
}
