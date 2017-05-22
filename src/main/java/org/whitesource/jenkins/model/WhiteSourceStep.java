package org.whitesource.jenkins.model;

import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
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
import org.whitesource.jenkins.extractor.maven.MavenOssInfoExtractor;
import org.whitesource.jenkins.pipeline.WhiteSourcePipelineStep;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import static org.whitesource.jenkins.Constants.*;

/**
 * Holds job related configuration
 * @author artiom.petrov
 */
public class WhiteSourceStep {

    /* --- Members --- */

    private WhiteSourceDescriptor globalConfig;

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
    private String productNameOrToken;

    private boolean shouldCheckPolicies;
    private boolean checkAllLibraries;
    private boolean isForceUpdate;

    /* --- Constructor --- */

    public WhiteSourceStep(WhiteSourceDescriptor globalConfig, String jobApiToken, String jobForceUpdate, String jobCheckPolicies) {
        this.globalConfig = globalConfig;
        setApiToken(jobApiToken);
        isForceUpdate(jobForceUpdate);
        isCheckPolicies(jobCheckPolicies);
    }

    public WhiteSourceStep(WhiteSourcePublisher publisher, WhiteSourceDescriptor globalConfig) {
        this(globalConfig, publisher.getJobApiToken(), publisher.getJobForceUpdate(), publisher.getJobCheckPolicies());
        this.jobApiToken = publisher.getJobApiToken();
        this.product = publisher.getProduct();
        this.productVersion = publisher.getProductVersion();
        this.projectToken = publisher.getProjectToken();
        this.libIncludes = publisher.getLibIncludes();
        this.libExcludes = publisher.getLibExcludes();
        this.mavenProjectToken = publisher.getMavenProjectToken();
        this.requesterEmail = publisher.getRequesterEmail();
        this.moduleTokens = publisher.getModuleTokens();
        this.modulesToInclude = publisher.getModulesToInclude();
        this.modulesToExclude = publisher.getModulesToExclude();
        this.ignorePomModules = publisher.isIgnorePomModules();
    }

    public WhiteSourceStep(WhiteSourcePipelineStep step, WhiteSourceDescriptor globalConfig) {
        this(globalConfig, step.getJobApiToken(), step.getJobForceUpdate(), step.getJobCheckPolicies());
        this.product = step.getProduct();
        this.productVersion = step.getProductVersion();
        this.projectToken = step.getProjectToken();
        this.libIncludes = step.getLibIncludes();
        this.libExcludes = step.getLibExcludes();
        this.requesterEmail = step.getRequesterEmail();
    }

    /* --- Public methods --- */

    public void update(Run<?, ?> run, TaskListener listener, Collection<AgentProjectInfo> projectInfos) {
        PrintStream logger = listener.getLogger();
        WhitesourceService service = createServiceClient();
        try {
            if (shouldCheckPolicies) {
                logger.println("Checking policies");
                CheckPolicyComplianceResult result = service.checkPolicyCompliance(jobApiToken, productNameOrToken,
                        productVersion, projectInfos, checkAllLibraries);
                policyCheckReport(result, run, listener);
                boolean hasRejections = result.hasRejections();
                String message;
                if (hasRejections && !isForceUpdate) {
                    message = "Open source rejected by organization policies.";
                    if (globalConfig.isFailOnError()) {
                        stopBuild(run, listener, "Open source rejected by organization policies.");
                    } else {
                        logger.println(message);
                    }
                } else {
                    message = hasRejections ? "Some dependencies violate open source policies, however all" +
                            " were force updated to organization inventory." :
                            "All dependencies conform with open source policies.";
                    logger.println(message);
                    sendUpdate(jobApiToken, requesterEmail, productNameOrToken, projectInfos, service, logger, productVersion);
                    if (globalConfig.isFailOnError() && hasRejections) {
                        stopBuild(run, listener, "White Source Publisher failure");
                    }
                }
            } else {
                sendUpdate(jobApiToken, requesterEmail, productNameOrToken, projectInfos, service, logger, productVersion);
            }
        } catch (WssServiceException | IOException | RuntimeException e) {
            stopBuildOnError(run, globalConfig.isFailOnError(), listener, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            service.shutdown();
        }
    }

    public Collection<AgentProjectInfo> getProjectInfos(Run<?, ?> run, TaskListener listener, FilePath workspace, boolean isPipelineJob) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();

        // collect OSS usage information
        logger.println("Collecting OSS usage information");
        Collection<AgentProjectInfo> projectInfos = null;

        productNameOrToken = product;
        if (run instanceof MavenModuleSetBuild) {
            MavenOssInfoExtractor extractor = new MavenOssInfoExtractor(modulesToInclude,
                    modulesToExclude, (MavenModuleSetBuild) run, listener, mavenProjectToken, moduleTokens, ignorePomModules);
            projectInfos = extractor.extract();
            if (StringUtils.isBlank(product)) {
                productNameOrToken = extractor.getTopMostProjectName();
            }
        } else if (run instanceof FreeStyleBuild || isPipelineJob) {
            GenericOssInfoExtractor extractor = new GenericOssInfoExtractor(libIncludes, libExcludes, run, listener, projectToken, workspace);
            projectInfos = extractor.extract();
        }
        return projectInfos;
    }

    public void stopBuild(Run<?, ?> run, TaskListener listener, String message) {
        listener.error(message);
        run.setResult(Result.FAILURE);
    }

    /* --- Private methods --- */

    private WhitesourceService createServiceClient() {
        String url = globalConfig.getServiceUrl();
        if (StringUtils.isNotBlank(url)) {
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "agent";
        }
        int connectionTimeout = DEFAULT_TIMEOUT;
        if (NumberUtils.isNumber(globalConfig.getConnectionTimeout())) {
            int connectionTimeoutInteger = Integer.parseInt(globalConfig.getConnectionTimeout());
            connectionTimeout = connectionTimeoutInteger > 0 ? connectionTimeoutInteger : connectionTimeout;
        }
        boolean proxyConfigured = isProxyConfigured(globalConfig);
        WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, url,
                proxyConfigured, connectionTimeout);

        if (proxyConfigured) {
            String host, userName, password;
            int port;
            if (globalConfig.isOverrideProxySettings()) {
                host = globalConfig.getServer();
                port = StringUtils.isBlank(globalConfig.getPort()) ? 0 : Integer.parseInt(globalConfig.getPort());
                userName = globalConfig.getUserName();
                password = globalConfig.getPassword();
            } else { // proxy is configured in jenkins global settings
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

    private void sendUpdate(String orgToken,
                            String requesterEmail,
                            String productNameOrToken,
                            Collection<AgentProjectInfo> projectInfos,
                            WhitesourceService service,
                            PrintStream logger, String productVersion) throws WssServiceException {
        logger.println("Sending to White Source");
        UpdateInventoryResult updateResult = service.update(orgToken, requesterEmail, productNameOrToken, productVersion, projectInfos);
        logUpdateResult(updateResult, logger);
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

    private void policyCheckReport(CheckPolicyComplianceResult result, Run<?, ?> run, TaskListener listener) //CheckPoliciesResult
            throws IOException, InterruptedException {
        listener.getLogger().println("Generating policy check report");

        PolicyCheckReport report = new PolicyCheckReport(result,
                run.getParent().getName(),
                Integer.toString(run.getNumber()));
        report.generate(run.getRootDir(), false);

        run.addAction(new PolicyCheckReportAction(run));
    }

    private void setApiToken(String jobApiToken) {
        this.jobApiToken = StringUtils.isNotBlank(jobApiToken) ? jobApiToken : globalConfig.getApiToken();
    }

    private void isCheckPolicies(String jobCheckPolicies) {
        if (StringUtils.isBlank(jobCheckPolicies) || Constants.GLOBAL.equals(jobCheckPolicies)) {
            String checkPolicies = globalConfig.getCheckPolicies();
            shouldCheckPolicies = ENABLE_NEW.equals(checkPolicies) || ENABLE_ALL.equals(checkPolicies);
            checkAllLibraries = ENABLE_ALL.equals(checkPolicies);
        } else {
            shouldCheckPolicies = ENABLE_NEW.equals(jobCheckPolicies) || ENABLE_ALL.equals(jobCheckPolicies);
            checkAllLibraries = ENABLE_ALL.equals(jobCheckPolicies);
        }
    }

    private void isForceUpdate(String jobForceUpdate) {
        if (StringUtils.isBlank(jobForceUpdate) || Constants.GLOBAL.equals(jobForceUpdate)) {
            isForceUpdate = globalConfig.isGlobalForceUpdate();
        } else {
            isForceUpdate = JOB_FORCE_UPDATE.equals(jobForceUpdate);
        }
    }

    private boolean isProxyConfigured(WhiteSourceDescriptor globalConfig) {
        Hudson hudsonInstance = Hudson.getInstance();
        return globalConfig.isOverrideProxySettings() ||
                (hudsonInstance != null && hudsonInstance.proxy != null);
    }

    private void logUpdateResult(UpdateInventoryResult result, PrintStream logger) {
        logger.println("White Source update results: ");
        logger.println("White Source organization: " + result.getOrganization());
        logger.println(result.getCreatedProjects().size() + " Newly created projects:");
        logger.println(StringUtils.join(result.getCreatedProjects(), ","));
        logger.println(result.getUpdatedProjects().size() + " existing projects were updated:");
        logger.println(StringUtils.join(result.getUpdatedProjects(), ","));
    }

    /* --- Getters / Setters --- */

    public WhiteSourceDescriptor getGlobalConfig() {
        return globalConfig;
    }

    public void setGlobalConfig(WhiteSourceDescriptor globalConfig) {
        this.globalConfig = globalConfig;
    }

    public String getJobApiToken() {
        return jobApiToken;
    }

    public void setJobApiToken(String jobApiToken) {
        this.jobApiToken = jobApiToken;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getProjectToken() {
        return projectToken;
    }

    public void setProjectToken(String projectToken) {
        this.projectToken = projectToken;
    }

    public String getLibIncludes() {
        return libIncludes;
    }

    public void setLibIncludes(String libIncludes) {
        this.libIncludes = libIncludes;
    }

    public String getLibExcludes() {
        return libExcludes;
    }

    public void setLibExcludes(String libExcludes) {
        this.libExcludes = libExcludes;
    }

    public String getMavenProjectToken() {
        return mavenProjectToken;
    }

    public void setMavenProjectToken(String mavenProjectToken) {
        this.mavenProjectToken = mavenProjectToken;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getModuleTokens() {
        return moduleTokens;
    }

    public void setModuleTokens(String moduleTokens) {
        this.moduleTokens = moduleTokens;
    }

    public String getModulesToInclude() {
        return modulesToInclude;
    }

    public void setModulesToInclude(String modulesToInclude) {
        this.modulesToInclude = modulesToInclude;
    }

    public String getModulesToExclude() {
        return modulesToExclude;
    }

    public void setModulesToExclude(String modulesToExclude) {
        this.modulesToExclude = modulesToExclude;
    }

    public boolean isIgnorePomModules() {
        return ignorePomModules;
    }

    public void setIgnorePomModules(boolean ignorePomModules) {
        this.ignorePomModules = ignorePomModules;
    }

    public String getProductNameOrToken() {
        return productNameOrToken;
    }

    public void setProductNameOrToken(String productNameOrToken) {
        this.productNameOrToken = productNameOrToken;
    }

    public boolean isShouldCheckPolicies() {
        return shouldCheckPolicies;
    }

    public void setShouldCheckPolicies(boolean shouldCheckPolicies) {
        this.shouldCheckPolicies = shouldCheckPolicies;
    }

    public boolean isCheckAllLibraries() {
        return checkAllLibraries;
    }

    public void setCheckAllLibraries(boolean checkAllLibraries) {
        this.checkAllLibraries = checkAllLibraries;
    }

    public void setForceUpdate(boolean forceUpdate) {
        isForceUpdate = forceUpdate;
    }

    public boolean isForceUpdate() {
        return isForceUpdate;
    }
}
