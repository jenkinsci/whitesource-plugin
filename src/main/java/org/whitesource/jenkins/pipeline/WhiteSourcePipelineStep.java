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

package org.whitesource.jenkins.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.jenkins.model.WhiteSourceDescriptor;
import org.whitesource.jenkins.model.WhiteSourceStep;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.whitesource.jenkins.Constants.*;

/**
 * @author Itai Marko
 * @author artiom.petrov
 */
public class WhiteSourcePipelineStep extends Step {

    /* --- Members --- */

    private String product;

    private String productVersion;

    private String jobCheckPolicies;

    private String jobForceUpdate;

    private String jobApiToken;

    private String jobUserKey;

    private String projectToken;

    private String requesterEmail;

    private String libIncludes;

    private String libExcludes;

    /* --- Constructor --- */

    @DataBoundConstructor
    public WhiteSourcePipelineStep(String jobCheckPolicies,
                                   String jobForceUpdate,
                                   String jobApiToken,
                                   String jobUserKey,
                                   String product,
                                   String productVersion,
                                   String projectToken,
                                   String libIncludes,
                                   String libExcludes,
                                   String requesterEmail) {
        super();
        this.jobCheckPolicies = jobCheckPolicies;
        this.jobForceUpdate = jobForceUpdate;
        this.jobApiToken = jobApiToken;
        this.jobUserKey = jobUserKey;
        this.product = product;
        this.productVersion = productVersion;
        this.projectToken = projectToken;
        this.libIncludes = libIncludes;
        this.libExcludes = libExcludes;
        this.requesterEmail = requesterEmail;
    }

    /* --- Overridden methods --- */

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    /* --- Getters / Setters --- */

    public String getProduct() {
        return product;
    }

    @DataBoundSetter
    public void setProduct(String product) {
        this.product = product;
    }

    public String getProductVersion() {
        return productVersion;
    }

    @DataBoundSetter
    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getJobCheckPolicies() {
        return jobCheckPolicies;
    }

    @DataBoundSetter
    public void setJobCheckPolicies(String jobCheckPolicies) {
        this.jobCheckPolicies = jobCheckPolicies;
    }

    public String getJobForceUpdate() {
        return jobForceUpdate;
    }

    @DataBoundSetter
    public void setJobForceUpdate(String jobForceUpdate) {
        this.jobForceUpdate = jobForceUpdate;
    }

    public String getJobApiToken() {
        return jobApiToken;
    }

    @DataBoundSetter
    public void setJobApiToken(String jobApiToken) {
        this.jobApiToken = jobApiToken;
    }

    public String getJobUserKey() { return jobUserKey; }

    @DataBoundSetter
    public void setJobUserKey(String jobUserKey) { this.jobUserKey = jobUserKey; }

    public String getProjectToken() {
        return projectToken;
    }

    @DataBoundSetter
    public void setProjectToken(String projectToken) {
        this.projectToken = projectToken;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    @DataBoundSetter
    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getLibIncludes() {
        return libIncludes;
    }

    @DataBoundSetter
    public void setLibIncludes(String libIncludes) {
        this.libIncludes = libIncludes;
    }

    public String getLibExcludes() {
        return libExcludes;
    }

    @DataBoundSetter
    public void setLibExcludes(String libExcludes) {
        this.libExcludes = libExcludes;
    }

    /* --- Nested classes --- */

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        /* --- Members --- */

        private String serviceUrl;
        private String apiToken;
        private String userKey;
        private String pipelineCheckPolicies;
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
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(FilePath.class, Run.class, TaskListener.class));
        }

        @Override
        public String getFunctionName() {
            return "whitesource";
        }

        @Override
        public String getDisplayName() {
            return "Scan dependencies and update WhiteSource";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            serviceUrl = json.getString(SERVICE_URL);
            apiToken = json.getString(API_TOKEN);
            userKey = json.getString(USER_KEY);
            pipelineCheckPolicies = json.getString(PIPELINE_CHECK_POLICIES);
            failOnError = json.getBoolean(FAIL_ON_ERROR);
            globalForceUpdate = json.getBoolean(GLOBAL_FORCE_UPDATE);

            JSONObject proxySettings = (JSONObject) json.get(PROXY_SETTINGS);
            if (proxySettings == null) {
                overrideProxySettings = false;
            } else {
                overrideProxySettings = true;
                userName = proxySettings.getString(USER_NAME);
                password = proxySettings.getString(PASSWORD);
                server = proxySettings.getString(SERVER);
                port = proxySettings.getString(PORT);
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

        public String getUserKey() { return userKey; }

        public void setUserKey(String userKey) { this.userKey = userKey; }

        public String getCheckPolicies() {
            return pipelineCheckPolicies;
        }

        public void setCheckPolicies(String checkPolicies) {
            this.pipelineCheckPolicies = checkPolicies;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }

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

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        /* --- Members --- */

        private transient final WhiteSourcePipelineStep step;

        /* --- Constructor --- */

        protected Execution(@Nonnull StepContext context, WhiteSourcePipelineStep step) {
            super(context);
            this.step = step;
        }

        /* --- Overridden methods --- */

        /**
         * Fires step right after build job ends
         */
        @Override
        public Void run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            assert listener != null;
            PrintStream logger = listener.getLogger();
            logger.println(UPDATING_WHITESOURCE);

            Run run = getContext().get(Run.class);

            WhiteSourceStep whiteSourceStep = new WhiteSourceStep(step, new WhiteSourceDescriptor((DescriptorImpl) step.getDescriptor()));

            // make sure we have an organization token
            if (StringUtils.isBlank(whiteSourceStep.getJobApiToken())) {
                logger.println(INVALID_API_TOKEN);
                return null;
            }

            FilePath workspace = getContext().get(FilePath.class);
            Collection<AgentProjectInfo> projectInfos = whiteSourceStep.getProjectInfos(run, listener, workspace, true);
            if (projectInfos == null) {
                whiteSourceStep.stopBuild(run, listener, "Unrecognized build type " + run.getClass().getName());
                return null;
            } else if (projectInfos.isEmpty()) {
                logger.println(OSS_INFO_NOT_FOUND);
            } else {
                whiteSourceStep.update(run, listener, projectInfos);
            }
            return null;
        }
    }
}
