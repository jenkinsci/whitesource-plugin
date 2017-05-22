package org.whitesource.jenkins.model;

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

    /* --- Constructors --- */

    public WhiteSourceDescriptor(WhiteSourcePublisher.DescriptorImpl descriptor) {
        this.serviceUrl = descriptor.getServiceUrl();
        this.apiToken = descriptor.getApiToken();
        this.checkPolicies = descriptor.getCheckPolicies();
        this.globalForceUpdate = descriptor.isGlobalForceUpdate();
        this.failOnError = descriptor.isFailOnError();
        this.overrideProxySettings = descriptor.isOverrideProxySettings();
        this.server = descriptor.getServer();
        this.port = descriptor.getPort();
        this.userName = descriptor.getUserName();
        this.password = descriptor.getPassword();
        this.connectionTimeout = descriptor.getConnectionTimeout();
    }

    public WhiteSourceDescriptor(WhiteSourcePipelineStep.DescriptorImpl descriptor) {
        this.serviceUrl = descriptor.getServiceUrl();
        this.apiToken = descriptor.getApiToken();
        this.checkPolicies = descriptor.getCheckPolicies();
        this.globalForceUpdate = descriptor.isGlobalForceUpdate();
        this.failOnError = descriptor.isFailOnError();
        this.overrideProxySettings = descriptor.isOverrideProxySettings();
        this.server = descriptor.getServer();
        this.port = descriptor.getPort();
        this.userName = descriptor.getUserName();
        this.password = descriptor.getPassword();
        this.connectionTimeout = descriptor.getConnectionTimeout();
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
}
