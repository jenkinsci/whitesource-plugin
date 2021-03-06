package org.whitesource.jenkins.model;

import hudson.util.Secret;
import org.whitesource.jenkins.WhiteSourcePublisher;
import org.whitesource.jenkins.pipeline.WhiteSourcePipelineStep;

/**
 * Holds global configuration of the plugin
 *
 * @author artiom.petrov
 */
public class WhiteSourceDescriptor {

    /* --- Members --- */

    private String serviceUrl;
    private Secret apiToken;
    private Secret userKey;
    private String checkPolicies;
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

    /* --- Constructors --- */

    public WhiteSourceDescriptor(WhiteSourcePublisher.DescriptorImpl descriptor) {
        this.serviceUrl = descriptor.getServiceUrl();
        this.apiToken = descriptor.getApiToken();
        this.userKey = descriptor.getUserKey();
        this.checkPolicies = descriptor.getCheckPolicies();
        this.globalForceUpdate = descriptor.isGlobalForceUpdate();
        this.failOnError = descriptor.isFailOnError();
        this.overrideProxySettings = descriptor.isOverrideProxySettings();
        this.server = descriptor.getServer();
        this.port = descriptor.getPort();
        this.userName = descriptor.getUserName();
        this.password = descriptor.getPassword();
        this.connectionTimeout = descriptor.getConnectionTimeout();
        this.connectionRetries = descriptor.getConnectionRetries() ==  null ? "1" : descriptor.getConnectionRetries();
        this.connectionRetriesInterval = descriptor.getConnectionRetriesInterval() ==  null ? "30" : descriptor.getConnectionRetries();
    }

    public WhiteSourceDescriptor(WhiteSourcePipelineStep.DescriptorImpl descriptor) {
        this.serviceUrl = descriptor.getServiceUrl();
        this.apiToken = descriptor.getApiToken();
        this.userKey = descriptor.getUserKey();
        this.checkPolicies = descriptor.getCheckPolicies();
        this.globalForceUpdate = descriptor.isGlobalForceUpdate();
        this.failOnError = descriptor.isFailOnError();
        this.overrideProxySettings = descriptor.isOverrideProxySettings();
        this.server = descriptor.getServer();
        this.port = descriptor.getPort();
        this.userName = descriptor.getUserName();
        this.password = descriptor.getPassword();
        this.connectionTimeout = descriptor.getConnectionTimeout();
        this.connectionRetries = descriptor.getConnectionRetries() ==  null ? "1" : descriptor.getConnectionRetries();
        this.connectionRetriesInterval = descriptor.getConnectionRetriesInterval() ==  null ? "30" : descriptor.getConnectionRetries();
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

    public void setApiToken(Secret apiToken) { this.apiToken = apiToken; }

    public Secret getUserKey() { return userKey; }

    public void setUserKey(Secret userKey) { this.userKey = userKey; }

    public String getCheckPolicies() {
        return checkPolicies;
    }

    public void setCheckPolicies(String checkPolicies) {
        this.checkPolicies = checkPolicies;
    }

    public boolean isGlobalForceUpdate() {
        return globalForceUpdate;
    }

    public void setGlobalForceUpdate(boolean globalForceUpdate) {
        this.globalForceUpdate = globalForceUpdate;
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
