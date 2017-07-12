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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.jenkins.model.WhiteSourceDescriptor;
import org.whitesource.jenkins.model.WhiteSourceStep;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

import static org.whitesource.jenkins.Constants.*;

/**
 * @author ramakrishna
 * @author Edo.Shor
 */
public class WhiteSourcePublisher extends Publisher implements SimpleBuildStep {

    /* --- Members --- */

    private String jobCheckPolicies;

    private String jobForceUpdate;

    private String jobApiToken;

    private String product;

    private String productVersion;

    private String projectToken;

    private String libIncludes;

    private String libExcludes;

    private String mavenProjectToken;

    private String requesterEmail;

    private String moduleTokens;

    private String modulesToInclude;

    private String modulesToExclude;

    private boolean ignorePomModules;

    /* --- Constructors --- */

    @DataBoundConstructor
    public WhiteSourcePublisher(String jobCheckPolicies,
                                String jobForceUpdate,
                                String jobApiToken,
                                String product,
                                String productVersion,
                                String projectToken,
                                String libIncludes,
                                String libExcludes,
                                String mavenProjectToken,
                                String requesterEmail,
                                String moduleTokens,
                                String modulesToInclude,
                                String modulesToExclude,
                                boolean ignorePomModules) {
        super();
        this.jobCheckPolicies = jobCheckPolicies;
        this.jobForceUpdate = jobForceUpdate;
        this.jobApiToken = jobApiToken;
        this.product = product;
        this.productVersion = productVersion;
        this.projectToken = projectToken;
        this.libIncludes = libIncludes;
        this.libExcludes = libExcludes;
        this.mavenProjectToken = mavenProjectToken;
        this.requesterEmail = requesterEmail;
        this.moduleTokens = moduleTokens;
        this.modulesToInclude = modulesToInclude;
        this.modulesToExclude = modulesToExclude;
        this.ignorePomModules = ignorePomModules;
    }

    /* --- Interface implementation methods --- */

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        checkEnvironmentVariables(run, listener);
        PrintStream logger = listener.getLogger();
        Result buildResult = run.getResult();

        if (buildResult == null) {
            throw new RuntimeException("Failed to acquire build result");
        }
        if (buildResult.isWorseThan(Result.SUCCESS)) {
            logger.println("Build failed. Skipping update.");
            return;
        }

        if (WssUtils.isFreeStyleMaven(run.getParent())) {
            logger.println(UNSUPPORTED_FREESTYLE_MAVEN_JOB);
            return;
        }

        logger.println(UPDATING_WHITESOURCE);
        WhiteSourceStep whiteSourceStep = new WhiteSourceStep(this, new WhiteSourceDescriptor((DescriptorImpl) getDescriptor()));

        // make sure we have an organization token
        if (StringUtils.isBlank(whiteSourceStep.getJobApiToken())) {
            logger.println(INVALID_API_TOKEN);
            return;
        }

        Collection<AgentProjectInfo> projectInfos = whiteSourceStep.getProjectInfos(run, listener, workspace, false);
        if (projectInfos == null) {
            whiteSourceStep.stopBuild(run, listener, "Unrecognized build type " + run.getClass().getName());
            return;
        } else if (projectInfos.isEmpty()) {
            logger.println(OSS_INFO_NOT_FOUND);
        } else {
            whiteSourceStep.update(run, listener, projectInfos);
        }
    }

    /* --- Public methods --- */

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /* --- Nested classes --- */

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /* --- Members--- */

        private String serviceUrl;

        private String apiToken;

        private String checkPolicies;

        private boolean globalForceUpdate;

        private boolean failOnError;

        private boolean overrideProxySettings;

        private String server;

        private String port;

        private String userName;

        private String password;

        private String connectionTimeout;

        /* --- Constructor --- */

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
            apiToken = json.getString(API_TOKEN);
            serviceUrl  = json.getString(SERVICE_URL);
            checkPolicies = json.getString(CHECK_POLICIES);
            failOnError = json.getBoolean(FAIL_ON_ERROR);
            globalForceUpdate = json.getBoolean(GLOBAL_FORCE_UPDATE);

            JSONObject proxySettings = (JSONObject) json.get(PROXY_SETTINGS);
            if (proxySettings == null) {
                overrideProxySettings = false;
            }
            else {
                overrideProxySettings = true;
                server = proxySettings.getString(SERVER);
                port = proxySettings.getString(PORT);
                userName = proxySettings.getString(USER_NAME);
                password = proxySettings.getString(PASSWORD);
            }
            connectionTimeout = json.getString(CONNECTION_TIMEOUT);
            save();

            return super.configure(req, json);
        }

        /* --- Public methods --- */

        public FormValidation doCheckApiToken(@QueryParameter String apiToken) {
            return FormValidation.validateRequired(apiToken);
        }

        public FormValidation doCheckConnectionTimeout(@QueryParameter String connectionTimeout) {
            FormValidation formValidation = FormValidation.validatePositiveInteger(connectionTimeout);
            return formValidation;
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

        public String getCheckPolicies() {
            return checkPolicies;
        }

        public void setCheckPolicies(String checkPolicies) {
            this.checkPolicies = checkPolicies;
        }

        public boolean isFailOnError() { return failOnError; }

        public void setFailOnError(boolean failOnError) { this.failOnError = failOnError; }

        public boolean isOverrideProxySettings() {
            return overrideProxySettings;
        }

        public void setOverrideProxySettings(boolean overrideProxySettings) {
            this.overrideProxySettings = overrideProxySettings;
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(String connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public boolean isGlobalForceUpdate() {
            return globalForceUpdate;
        }

        public void setGlobalForceUpdate(boolean globalForceUpdate) {
            this.globalForceUpdate = globalForceUpdate;
        }

    }

    /* --- Private methods --- */

    private void checkEnvironmentVariables(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener) {
        this.jobApiToken = extractEnvironmentVariables(run, listener, this.jobApiToken);
        this.product = extractEnvironmentVariables(run, listener, this.product);
        this.productVersion = extractEnvironmentVariables(run, listener, this.productVersion);
        this.projectToken = extractEnvironmentVariables(run, listener, this.projectToken);
        this.libIncludes = extractEnvironmentVariables(run, listener, this.libIncludes);
        this.libExcludes = extractEnvironmentVariables(run, listener, this.libExcludes);
        this.requesterEmail = extractEnvironmentVariables(run, listener, this.requesterEmail);
    }

    private String extractEnvironmentVariables(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, String variable) {
        EnvVars envVars = new EnvVars();
        PrintStream logger = listener.getLogger();
        String result = variable;
        try {
            envVars = run.getEnvironment(listener);
            if (variable.startsWith("$")) {
                if (variable.startsWith("${")) {
                    result = envVars.get("$" + variable.substring(2, variable.length() - 1));
                }
                result = envVars.get(variable);
            }
            if (result == null) {
                logger.println("Environment variable \"" + variable + "\" was not found");
                run.setResult(Result.ABORTED);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    /* --- Getters --- */

    public String getJobCheckPolicies() {
        return jobCheckPolicies;
    }

    public String getJobForceUpdate() {
        return jobForceUpdate;
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

    public String getRequesterEmail() {
        return requesterEmail;
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
