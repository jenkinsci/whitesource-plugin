package org.whitesource.jenkins.model;

import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.ViaComponents;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.jenkins.Constants;
import org.whitesource.jenkins.PolicyCheckReportAction;
import org.whitesource.jenkins.WhiteSourcePublisher;
import org.whitesource.jenkins.extractor.generic.GenericOssInfoExtractor;
import org.whitesource.jenkins.extractor.maven.MavenOssInfoExtractor;
import org.whitesource.jenkins.pipeline.WhiteSourcePipelineStep;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.whitesource.jenkins.Constants.*;

/**
 * Holds job related configuration
 *
 * @author artiom.petrov
 */
public class WhiteSourceStep {

    /* --- Static members --- */

    public static final String SPACE = " ";
    private static final String PLUGIN_AGENTS_VERSION = "2.7.6";
    private static final String PLUGIN_VERSION = "18.10.1";
    public static final String WITH_MAVEN = "withMaven";
    public static final String GENERIC_GLOB_PATTERN = "**/*.";
    public static final String COMMA = ",";
    public static final String SLASH = "/";
    public static final String AGENT_KEYWORD = "agent";
    /* --- Members --- */

    private WhiteSourceDescriptor globalConfig;

    private String jobApiToken;
    private String jobUserKey;
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

    public WhiteSourceStep(WhiteSourceDescriptor globalConfig, String jobApiToken, String jobForceUpdate, String jobCheckPolicies, String jobUserKey) {
        this.globalConfig = globalConfig;
        setApiToken(jobApiToken);
        setUserKey(jobUserKey);
        isForceUpdate(jobForceUpdate);
        isCheckPolicies(jobCheckPolicies);
    }

    public WhiteSourceStep(WhiteSourcePublisher publisher, WhiteSourceDescriptor globalConfig) {
        this(globalConfig, publisher.getJobApiToken(), publisher.getJobForceUpdate(), publisher.getJobCheckPolicies(), publisher.getJobUserKey());
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
        this(globalConfig, step.getJobApiToken(), step.getJobForceUpdate(), step.getJobCheckPolicies(), step.getJobUserKey());
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
        WhitesourceService service = createServiceClient(logger);
        try {
            if (shouldCheckPolicies) {
                logger.println("Checking policies");
                CheckPolicyComplianceResult result = service.checkPolicyCompliance(jobApiToken, productNameOrToken,
                        productVersion, projectInfos, checkAllLibraries, jobUserKey);
                policyCheckReport(result, run, listener);
                boolean hasRejections = result.hasRejections();
                String message;
                if (hasRejections && !isForceUpdate) {
                    message = "Open source rejected by organization policies.";
                    if (globalConfig.isFailOnError()) {
                        stopBuild(run, listener, message);
                    } else {
                        logger.println(message);
                    }
                } else {
                    message = hasRejections ? "Some dependencies violate open source policies, however all" +
                            " were force updated to organization inventory." :
                            "All dependencies conform with open source policies.";
                    logger.println(message);
                    sendUpdate(jobApiToken, requesterEmail, productNameOrToken, projectInfos, service, logger, productVersion, jobUserKey);
                    if (globalConfig.isFailOnError() && hasRejections) {
                        stopBuild(run, listener, "White Source Publisher failure");
                    }
                }
            } else {
                sendUpdate(jobApiToken, requesterEmail, productNameOrToken, projectInfos, service, logger, productVersion, jobUserKey);
            }
        } catch (WssServiceException | IOException | RuntimeException e) {
            stopBuildOnError(run, globalConfig.isFailOnError(), listener, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            service.shutdown();
        }
    }

    public Collection<AgentProjectInfo> getProjectInfos(Run<?, ?> run, TaskListener listener, FilePath workspace, boolean isFreeStyleStep) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();

        // collect OSS usage information
        logger.println("Collecting OSS usage information");
        Collection<AgentProjectInfo> projectInfos = new LinkedList<>();

        productNameOrToken = product;
        if (run instanceof MavenModuleSetBuild) {
            // maven job - support Remote dependency JEP-200
            projectInfos = getMavenProjectInfos((MavenModuleSetBuild) run, listener, workspace, logger);
        } else if (run instanceof FreeStyleBuild || isFreeStyleStep) {
            if (run instanceof WorkflowRun) {
                FlowExecution exec = ((WorkflowRun) run).getExecution();
                String script = "";
                if (exec != null) {
                    script = ((CpsFlowExecution) exec).getScript();
                }
                //WSE-886 remove comments, withMaven can be commented out and still show in pipeline script
                String withMavenChecker=script.replaceAll("(?sm)(^(?:\\s*)?((?:/\\*(?:\\*)?).*?(?<=\\*/))|(?://).*?(?<=$))", "");

                if (StringUtils.isNotBlank(script) && withMavenChecker.contains(WITH_MAVEN)) {
                    // maven pipeline job
                    // todo: check JEP-200 compatibility

                    projectInfos = getFSAProjects(logger, workspace);
                } else {
                    // pipeline job - support Remote dependency JEP-200
                    projectInfos = getGenericProjectInfos(run, listener, workspace, logger);
                }
            } else {
                // freestyle job (same as pipeline) - support Remote dependency JEP-200
                projectInfos = getGenericProjectInfos(run, listener, workspace, logger);
            }
        }
        logger.println("Job finished.");
        return projectInfos;
    }

    private Collection<AgentProjectInfo> getMavenProjectInfos(MavenModuleSetBuild run, TaskListener listener, FilePath workspace, PrintStream logger) throws InterruptedException, IOException {
        Collection<AgentProjectInfo> projectInfos;
        logger.println("Starting Maven job on " + workspace.getRemote());
        MavenOssInfoExtractor extractor = new MavenOssInfoExtractor(modulesToInclude,
                modulesToExclude, run, listener, mavenProjectToken, moduleTokens, ignorePomModules);
        projectInfos = extractor.extract();
        if (StringUtils.isBlank(product)) {
            productNameOrToken = extractor.getTopMostProjectName();
        }
        return projectInfos;
    }

    private Collection<AgentProjectInfo> getGenericProjectInfos(Run<?, ?> run, TaskListener listener, FilePath workspace, PrintStream logger) throws InterruptedException, IOException {
        Collection<AgentProjectInfo> projectInfos;
        logger.println("Starting generic job on " + workspace.getRemote());
        GenericOssInfoExtractor extractor = new GenericOssInfoExtractor(libIncludes, libExcludes, run, listener, projectToken, workspace);
        projectInfos = extractor.extract();
        return projectInfos;
    }

    public void stopBuild(Run<?, ?> run, TaskListener listener, String message) {
        listener.error(message);
        run.setResult(Result.FAILURE);
    }

    /* --- Private methods --- */

    private WhitesourceService createServiceClient(PrintStream logger) {
        String url = globalConfig.getServiceUrl();
        logger.println("WhiteSource Service URL:" + url);
        if (StringUtils.isNotBlank(url)) {

            if (!url.endsWith(AGENT_KEYWORD)) {
                if (!url.endsWith(SLASH)) {
                    url += SLASH;
                }
                url += AGENT_KEYWORD;
            }
        }
        int connectionTimeout = DEFAULT_TIMEOUT;
        if (NumberUtils.isNumber(globalConfig.getConnectionTimeout())) {
            int connectionTimeoutInteger = Integer.parseInt(globalConfig.getConnectionTimeout());
            connectionTimeout = connectionTimeoutInteger > 0 ? connectionTimeoutInteger : connectionTimeout;
        }
        boolean proxyConfigured = isProxyConfigured(globalConfig);
        WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, PLUGIN_AGENTS_VERSION,
                PLUGIN_VERSION, url, proxyConfigured, connectionTimeout);


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
                            PrintStream logger, String productVersion, String userKey) throws WssServiceException {
        logger.println("Sending to White Source");

        int retries =  Integer.parseInt(globalConfig.getConnectionRetries());
        int interval = Integer.parseInt(globalConfig.getConnectionRetriesInterval());


        UpdateInventoryResult updateResult = null;
        while (retries-- > -1) {
            try {
                updateResult = service.update(orgToken, requesterEmail, productNameOrToken, productVersion, projectInfos, userKey);
                if(updateResult != null) {
                    break;
                }
            } catch (WssServiceException e) {
                logger.println("Failed to send request to WhiteSource server: " + e.getMessage());
                if (e.getCause() != null &&
                        e.getCause().getClass().getCanonicalName().substring(0, e.getCause().getClass().getCanonicalName().lastIndexOf(Constants.DOT)).equals(Constants.JAVA_NETWORKING)) {
                    //statusCode = StatusCode.CONNECTION_FAILURE;
                    logger.println("Trying " + (retries + 1) + " more time" + (retries != 0 ? "s" : Constants.EMPTY_STRING));
                } else {
                    //statusCode = StatusCode.SERVER_FAILURE;
                    retries = -1;
                }

                if (retries > -1) {
                    try {
                        Thread.sleep(interval*1000);
                    } catch (InterruptedException e1) {
                        logger.println("Failed to sleep while retrying to connect to server " + e1.getMessage());
                    }
                }
            }
        }
        if (updateResult != null) {
            logUpdateResult(updateResult, logger);
        } else {
            throw new WssServiceException("Connection Failed");
        }
    }

    private void stopBuildOnError(Run<?, ?> run, boolean failOnError, TaskListener listener, Exception e) {
        if (e instanceof IOException) {
            Util.displayIOException((IOException) e, listener);
        }
        listener.error("White Source Publisher failure " + e.getMessage());
        if (failOnError) {
            run.setResult(Result.FAILURE);
        }
        if (e instanceof WssServiceException) {
            String requestToken = ((WssServiceException) e).getRequestToken();
            if (StringUtils.isNotBlank(requestToken)) {
                listener.getLogger().println("Support Token: " + requestToken);
            }
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

    private void setUserKey(String jobUerKey) {
        this.jobUserKey = StringUtils.isNotBlank(jobUerKey) ? jobUerKey : globalConfig.getUserKey();
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
        logger.println("WhiteSource update results: ");
        logger.println("WhiteSource organization: " + result.getOrganization());
        logger.println(result.getCreatedProjects().size() + " Newly created projects:");
        logger.println(StringUtils.join(result.getCreatedProjects(), ","));
        logger.println(result.getUpdatedProjects().size() + " existing projects were updated:");
        logger.println(StringUtils.join(result.getUpdatedProjects(), ","));
        // support token
        String requestToken = result.getRequestToken();
        if (StringUtils.isNotBlank(requestToken)) {
            logger.println("WhiteSource Support Token: " + requestToken);
        }
    }

    private String getResource(String propertyName, PrintStream logger) {
        Properties properties = getProperties(logger);
        String val = (properties.getProperty(propertyName));
        if (StringUtils.isNotBlank(val)) {
            return val;
        }
        return "";
    }

    private Properties getProperties(PrintStream logger) {
        Properties properties = new Properties();
        try (InputStream stream = WhiteSourceStep.class.getResourceAsStream("/project.properties")) {
            properties.load(stream);
        } catch (IOException e) {
            logger.println("White Source update results: ");
        }
        return properties;
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

    public String getJobUserKey() {
        return jobUserKey;
    }

    public void setJobUserKey(String jobUserKey) {
        this.jobUserKey = jobUserKey;
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

    public void initializeIncludes() {
        Collection<String> includes = new LinkedList<>();
        if (CollectionUtils.isEmpty(includes)) {
            for (String extension : GenericOssInfoExtractor.DEFAULT_SCAN_EXTENSIONS) {
                includes.add(GENERIC_GLOB_PATTERN + extension);
            }
        }
        libIncludes = String.join(",", includes);
    }

    public Collection<AgentProjectInfo> getFSAProjects(PrintStream logger, FilePath workspace) {
        List<AgentProjectInfo> projects = new ArrayList<>();
        Map<AgentProjectInfo, LinkedList<ViaComponents>> fsaProjects = Collections.emptyMap();
        logger.println("Starting Pipeline-FSA job on " + workspace.getRemote());
        Properties props = new Properties();
        List<String> paths = new ArrayList<>();
        paths.add(workspace.getRemote());

        props.put(ConfigPropertyKeys.MAVEN_AGGREGATE_MODULES, "false");
        FSAConfiguration fsaConfiguration = new FSAConfiguration(props);
        // initialize libIncludes if the user didn't insert any extensions
        if (StringUtils.isEmpty(libIncludes)) {
            initializeIncludes();
            libIncludes = libIncludes.replaceAll(COMMA, SPACE);
        }
        Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
        Set<String> set = new HashSet<>();
        appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, set);
        appPathsToDependencyDirs.get(FSAConfiguration.DEFAULT_KEY).add(workspace.getRemote());

        try {
            FileSystemScanner fileSystemScanner = new FileSystemScanner(fsaConfiguration.getResolver(), fsaConfiguration.getAgent(), false);
            fsaProjects = fileSystemScanner.createProjects(paths, appPathsToDependencyDirs, false, libIncludes.split(SPACE), libExcludes.split(COMMA),
                    false, 0, null, null, false,
                    false, null, false, false, false);
            int dependencyCount = 0;
            for (AgentProjectInfo agentProjectInfo : fsaProjects.keySet()) {
                dependencyCount += agentProjectInfo.getDependencies().size();
            }
            logger.println("Found " + dependencyCount + " dependencies .");
        } catch (Exception ex) {
            for (StackTraceElement stackTraceElement : ex.getStackTrace()) {
                logger.println("Error getting FSA dependencies " + stackTraceElement.toString()) ;
            }
        }
        for (AgentProjectInfo agentProjectInfo : fsaProjects.keySet()) {
            if (projectToken != null && (StringUtils.isNotBlank(projectToken))) {
                agentProjectInfo.setProjectToken(projectToken); // WSE-494 fix
            } else {
                if (agentProjectInfo.getCoordinates() != null) {
                    agentProjectInfo.setCoordinates(new Coordinates(null, agentProjectInfo.getCoordinates().getArtifactId(), productVersion));
                } else {
                    agentProjectInfo.setCoordinates(new Coordinates(null, productNameOrToken, productVersion));
                }
            }
            projects.add(agentProjectInfo);
        }

        return projects;
    }
}
