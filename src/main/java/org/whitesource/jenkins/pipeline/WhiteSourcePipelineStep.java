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
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.jenkins.Constants;
import org.whitesource.jenkins.model.WhiteSourceDescriptor;
import org.whitesource.jenkins.model.WhiteSourceStep;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


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

    private Secret jobApiToken;

    private Secret jobUserKey;

    private Secret projectToken;

    private String requesterEmail;

    private String libIncludes;

    private String libExcludes;

    /* --- Constructor --- */

    @DataBoundConstructor
    public WhiteSourcePipelineStep(String jobCheckPolicies,
                                   String jobForceUpdate,
                                   Secret jobApiToken,
                                   Secret jobUserKey,
                                   String product,
                                   String productVersion,
                                   Secret projectToken,
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

    public Secret getJobApiToken() {
        return jobApiToken;
    }

    @DataBoundSetter
    public void setJobApiToken(Secret jobApiToken) {
        this.jobApiToken = jobApiToken;
    }

    public Secret getJobUserKey() { return jobUserKey; }

    @DataBoundSetter
    public void setJobUserKey(Secret jobUserKey) { this.jobUserKey = jobUserKey; }

    public Secret getProjectToken() {
        return projectToken;
    }

    @DataBoundSetter
    public void setProjectToken(Secret projectToken) {
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
        private Secret apiToken;
        private Secret userKey;
        private String pipelineCheckPolicies;
        private boolean globalForceUpdate;
        private boolean failOnError;
        private boolean overrideProxySettings;
        private String server;
        private String port;
        private String userName;
        private Secret password;
        private String connectionTimeout;
        private String connectionRetries;
        private String connectionRetriesInterval;

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
            serviceUrl = json.getString(Constants.SERVICE_URL);
            apiToken = Secret.fromString(json.getString(Constants.API_TOKEN));
            userKey = Secret.fromString(json.getString(Constants.USER_KEY));
            pipelineCheckPolicies = json.getString(Constants.PIPELINE_CHECK_POLICIES);
            failOnError = json.getBoolean(Constants.FAIL_ON_ERROR);
            globalForceUpdate = json.getBoolean(Constants.GLOBAL_FORCE_UPDATE);

            JSONObject proxySettings = (JSONObject) json.get(Constants.PROXY_SETTINGS);
            if (proxySettings == null) {
                overrideProxySettings = false;
            } else {
                overrideProxySettings = true;
                userName = proxySettings.getString(Constants.USER_NAME);
                password = Secret.fromString(proxySettings.getString(Constants.PASSWORD));
                server = proxySettings.getString(Constants.SERVER);
                port = proxySettings.getString(Constants.PORT);
            }
            connectionTimeout = json.getString(Constants.CONNECTION_TIMEOUT);
            connectionRetries = json.getString(Constants.CONNECTION_RETRIES);
            connectionRetriesInterval = json.getString(Constants.CONNECTION_RETRIES_INTERVAL);

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

        public FormValidation doCheckConnectionRetries(@QueryParameter String connectionRetries) {
            FormValidation formValidation = FormValidation.validateNonNegativeInteger(connectionRetries);
            return formValidation;
        }

        public FormValidation doCheckConnectionRetriesInterval(@QueryParameter String connectionRetriesInterval) {
            FormValidation formValidation = FormValidation.validateNonNegativeInteger(connectionRetriesInterval);
            return formValidation;
        }

        /* --- Getters / Setters --- */

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public Secret getApiToken() {
            return apiToken;
        }

        public void setApiToken(Secret apiToken) {
            this.apiToken = apiToken;
        }

        public Secret getUserKey() { return userKey; }

        public void setUserKey(Secret userKey) { this.userKey = userKey; }

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

        public Secret getPassword() {
            return password;
        }

        public void setPassword(Secret password) {
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

        public String getConnectionRetries() {
            return connectionRetries;
        }

        public void setConnectionRetries(String connectionRetries) {
            this.connectionRetries = connectionRetries;
        }

        public String getConnectionRetriesInterval() {
            return connectionRetriesInterval;
        }

        public void setConnectionRetriesInterval(String connectionRetriesInterval) {
            this.connectionRetriesInterval = connectionRetriesInterval;
        }

    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 8178356851772162243L;

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
            logger.println(Constants.UPDATING_WHITESOURCE);

            Run run = getContext().get(Run.class);

            WhiteSourceStep whiteSourceStep = new WhiteSourceStep(step, new WhiteSourceDescriptor((DescriptorImpl) step.getDescriptor()));

            // make sure we have an organization token
            if (StringUtils.isBlank(Secret.toString(whiteSourceStep.getJobApiToken()))) {
                logger.println(Constants.INVALID_API_TOKEN);
                return null;
            }

            FilePath workspace = getContext().get(FilePath.class);
            Collection<AgentProjectInfo> projectInfos = whiteSourceStep.getProjectInfos(run, listener, workspace, true);
//            if (projectInfos == null) {
//                whiteSourceStep.stopBuild(run, listener, "Unrecognized build type " + run.getClass().getName());
//                return null;
//            } else
            if (projectInfos.isEmpty()) {
                logger.println(Constants.OSS_INFO_NOT_FOUND);
            } else {
                whiteSourceStep.update(run, listener, projectInfos);
            }
            return null;
        }
    }
}
