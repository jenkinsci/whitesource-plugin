package org.whitesource.jenkins.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.*;
import hudson.model.*;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.jenkins.Constants;
import org.whitesource.jenkins.PolicyCheckReportAction;
import org.whitesource.jenkins.WhiteSourcePublisher;
import org.whitesource.jenkins.extractor.generic.GenericOssInfoExtractor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Set;

/**
 * @author Itai Marko
 */
public class WhiteSourceUpdateStep extends Step {
//public class WhiteSourceUpdateStep extends AbstractStepImpl {

    private String product;
    private String productVersion;
    private String jobCheckPolicies;
    private String jobForceUpdate;
    private String jobApiToken;
    private String projectToken;
    private String requesterEmail;
    private String libIncludes;
    private String libExcludes;

    @DataBoundConstructor
    public WhiteSourceUpdateStep() {
        // TODO: add mandatory fields
        // TODO: add setter for optional fields annotated with @DataBoundSetter.
        // TODO: add public getters for these fields
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return null; // TODO: impl
    }

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

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

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

        /**
         * Default constructor
         */
        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
//            return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
            return ImmutableSet.of(FilePath.class, Run.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "whitesource";
        }

        @Override
        public String getDisplayName() {
            return "Scan dependencies and update WhiteSource";
        }

        // TODO: much of the code here is copied from WhiteSourcePublisher.DescriptorImpl. DRYify

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

            serviceUrl  = json.getString("serviceUrl");
            apiToken = json.getString("apiToken");
            checkPolicies = json.getString("checkPolicies");
            failOnError = json.getBoolean("failOnError");
            globalForceUpdate = json.getBoolean("globalForceUpdate");

            JSONObject proxySettings = (JSONObject) json.get("proxySettings");
            if (proxySettings == null) {
                overrideProxySettings = false;
            }
            else {
                overrideProxySettings = true;
                userName = proxySettings.getString("userName");
                password = proxySettings.getString("password");
                server = proxySettings.getString("server");
                port = proxySettings.getString("port");
            }
            connectionTimeout =json.getString("connectionTimeout");
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


    private static class Execution extends SynchronousNonBlockingStepExecution<Void> { // TODO: can also extend SynchronousStepExecution for trivial cases. see if it would be better

        private transient final WhiteSourceUpdateStep step;

        protected Execution(WhiteSourceUpdateStep step, @Nonnull StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
 // TODO: most of the code here is copied from WhiteSourcePublisher. DRYify
            TaskListener listener = getContext().get(TaskListener.class);
            assert listener != null;
            PrintStream logger = listener.getLogger();
            logger.println("Updating White Source");

            Run run = getContext().get(Run.class);
            Result buildResult = run.getResult();
            if (buildResult == null) {
                throw new RuntimeException("Failed to acquire build result");
            }
            if (buildResult.isWorseThan(Result.SUCCESS)) {
                logger.println("Build failed. Skipping update.");
                return null;
            }

            DescriptorImpl globalConfig = (DescriptorImpl) step.getDescriptor();


            // make sure we have an organization token
            String apiToken = globalConfig.apiToken;
            if (StringUtils.isNotBlank(step.jobApiToken)) {
                apiToken = step.jobApiToken;
            }
            if (StringUtils.isBlank(apiToken)) {
                logger.println("No API token configured. Skipping update.");
                return null;
            }


            // should we check policies ?
            boolean shouldCheckPolicies;
            boolean checkAllLibraries;
            if (StringUtils.isBlank(step.jobCheckPolicies) || WhiteSourcePublisher.GLOBAL.equals(step.jobCheckPolicies)) {
                String checkPolicies = globalConfig.checkPolicies;
                shouldCheckPolicies = WhiteSourcePublisher.ENABLE_NEW.equals(checkPolicies) || WhiteSourcePublisher.ENABLE_ALL.equals(checkPolicies);
                checkAllLibraries = WhiteSourcePublisher.ENABLE_ALL.equals(checkPolicies);
            } else {
                shouldCheckPolicies = WhiteSourcePublisher.ENABLE_NEW.equals(step.jobCheckPolicies) || WhiteSourcePublisher.ENABLE_ALL.equals(step.jobCheckPolicies);
                checkAllLibraries = WhiteSourcePublisher.ENABLE_ALL.equals(step.jobCheckPolicies);
            }

            boolean isForceUpdate;
            if (StringUtils.isBlank(step.jobForceUpdate) || WhiteSourcePublisher.GLOBAL.equals(step.jobForceUpdate)) {
                isForceUpdate = globalConfig.globalForceUpdate;
            } else {
                isForceUpdate = WhiteSourcePublisher.JOB_FORCE_UPDATE.equals(step.jobForceUpdate);
            }

            // collect OSS usage information
            logger.println("Collecting OSS usage information");
            Collection<AgentProjectInfo> projectInfos;
            String productNameOrToken = step.product;

            FilePath workspace = getContext().get(FilePath.class);
            GenericOssInfoExtractor extractor = new GenericOssInfoExtractor(step.libIncludes,
                    step.libExcludes, run, listener, step.projectToken, workspace);
            projectInfos = extractor.extract();


            // send to white source
            if (CollectionUtils.isEmpty(projectInfos)) {
                logger.println("No open source information found.");
            } else {
                WhitesourceService service = createServiceClient(globalConfig);
                try {
                    if (shouldCheckPolicies) {
                        logger.println("Checking policies");
                        CheckPolicyComplianceResult result = service.checkPolicyCompliance(apiToken, productNameOrToken ,step.productVersion, projectInfos, checkAllLibraries);
                        policyCheckReport(result, run, listener);
                        boolean hasRejections = result.hasRejections();
                        if (hasRejections && !isForceUpdate) {
                            stopBuild(run, listener, "Open source rejected by organization policies.");
                        } else {
                            String message = hasRejections ? "Some dependencies violate open source policies, however all" +
                                    " were force updated to organization inventory." :
                                    "All dependencies conform with open source policies.";
                            logger.println(message);
                            sendUpdate(apiToken, step.requesterEmail, productNameOrToken, projectInfos, service, logger);
                        }
                    } else {
                        sendUpdate(apiToken, step.requesterEmail, productNameOrToken, projectInfos, service, logger);
                    }
                } catch (WssServiceException e) {
                    stopBuildOnError(run, globalConfig.failOnError, listener, e);
                } catch (IOException e) {
                    stopBuildOnError(run, globalConfig.failOnError, listener, e);
                } catch (RuntimeException e) {
                    stopBuildOnError(run, globalConfig.failOnError, listener, e);
                } finally {
                    service.shutdown();
                }
            }

            return null;
        }


        private WhitesourceService createServiceClient(DescriptorImpl globalConfig) {
            String url = globalConfig.serviceUrl;
            if (StringUtils.isNotBlank(url)){
                if (!url.endsWith("/")) {
                    url += "/";
                }
                url += "agent";
            }
            int connectionTimeout = WhiteSourcePublisher.DEFAULT_TIMEOUT;
            if (NumberUtils.isNumber(globalConfig.connectionTimeout)) {
                int connectionTimeoutInteger = Integer.parseInt(globalConfig.connectionTimeout);
                connectionTimeout = connectionTimeoutInteger > 0 ? connectionTimeoutInteger : connectionTimeout;
            }
            boolean proxyConfigured = isProxyConfigured(globalConfig);
            WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, url,
                    proxyConfigured, connectionTimeout);

            if (proxyConfigured) {
                String host, userName, password;
                int port;
                if (globalConfig.overrideProxySettings) {
                    host = globalConfig.server;
                    port = StringUtils.isBlank(globalConfig.port) ? 0 : Integer.parseInt(globalConfig.port);
                    userName = globalConfig.userName;
                    password = globalConfig.password;
                }
                else { // proxy is configured in jenkins global settings
                    Hudson hudsonInstance = Hudson.getInstance();
                    if (hudsonInstance == null) {
                        throw new RuntimeException("Failed to acquire Hudson Instance");
                    }
                    final ProxyConfiguration proxy = hudsonInstance.proxy;
                    host = proxy.name;
                    port = proxy.port;
                    userName = proxy.getUserName();
                    password = proxy.getPassword();
                }
                // ditch protocol if present
                try {
                    URL tmpUrl = new URL(host);
                    host = tmpUrl.getHost();
                } catch (MalformedURLException e) {
                    // nothing to do here
                }
                service.getClient().setProxy(host, port, userName, password);
            }

            return service;
        }

        private boolean isProxyConfigured(DescriptorImpl globalConfig) {
            Hudson hudsonInstance = Hudson.getInstance();
            return globalConfig.overrideProxySettings ||
                    (hudsonInstance != null && hudsonInstance.proxy != null);
        }

        private void policyCheckReport(CheckPolicyComplianceResult result, Run<?, ?> run, TaskListener listener) //CheckPoliciesResult
                throws IOException, InterruptedException {
            listener.getLogger().println("Generating policy check report");

            PolicyCheckReport report = new PolicyCheckReport(result,
                    run.getParent().getName(),
                    Integer.toString(run.getNumber()));
            report.generate(run.getRootDir(), false);

            run.addAction(new PolicyCheckReportAction(run));
        }

        private void stopBuild(Run<?, ?> run, TaskListener listener, String message) {
            listener.error(message);
            run.setResult(Result.FAILURE);
        }

        private void sendUpdate(String orgToken,
                                String requesterEmail,
                                String productNameOrToken,
                                Collection<AgentProjectInfo> projectInfos,
                                WhitesourceService service,
                                PrintStream logger) throws WssServiceException {
            logger.println("Sending to White Source");
            UpdateInventoryResult updateResult = service.update(orgToken, requesterEmail, productNameOrToken, step.productVersion, projectInfos);
            logUpdateResult(updateResult, logger);
        }

        private void logUpdateResult(UpdateInventoryResult result, PrintStream logger) {
            logger.println("White Source update results: ");
            logger.println("White Source organization: " + result.getOrganization());
            logger.println(result.getCreatedProjects().size() + " Newly created projects:");
            logger.println(StringUtils.join(result.getCreatedProjects(), ","));
            logger.println(result.getUpdatedProjects().size() + " existing projects were updated:");
            logger.println(StringUtils.join(result.getUpdatedProjects(), ","));
        }

        private void stopBuildOnError(Run<?, ?> run, boolean failOnError, TaskListener listener, Exception e) {
            if (e instanceof IOException) {
                Util.displayIOException((IOException) e, listener);
            }
            e.printStackTrace(listener.fatalError("White Source Publisher failure"));
            if (failOnError) {
                run.setResult(Result.FAILURE);
            }
        }

    }
}
