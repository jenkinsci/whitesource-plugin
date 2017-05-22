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

/**
 * Constants used by the plugin. 
 * 
 * @author Edo.Shor
 */
public final class Constants {
	
	/* --- Agent settings --- */
	
	public static final String AGENT_TYPE = "jenkins";
	public static final String AGENT_VERSION = "2.3.0";

	/* --- Global settings --- */

	public static final String SERVICE_URL = "serviceUrl";
	public static final String API_TOKEN = "apiToken";
	public static final String CHECK_POLICIES = "checkPolicies";
	public static final String PIPELINE_CHECK_POLICIES = "pipelineCheckPolicies";
	public static final String FAIL_ON_ERROR = "failOnError";
	public static final String GLOBAL_FORCE_UPDATE = "globalForceUpdate";
	public static final String PROXY_SETTINGS = "proxySettings";
	public static final String USER_NAME = "userName";
	public static final String PASSWORD = "password";
	public static final String SERVER = "server";
	public static final String PORT = "port";
	public static final String CONNECTION_TIMEOUT = "connectionTimeout";
	public static final int DEFAULT_TIMEOUT = 60;

	/* --- Job settings --- */

	public static final String GLOBAL = "global";
	public static final String ENABLE_NEW = "enableNew";
	public static final String ENABLE_ALL = "enableAll";
	public static final String JOB_FORCE_UPDATE = "forceUpdate";

	/* --- Messages --- */

	public static final String UPDATING_WHITESOURCE = "Updating White Source.";
	public static final String UNSUPPORTED_FREESTYLE_MAVEN_JOB = "Free style maven jobs are not supported in this version. See plugin documentation.";
	public static final String INVALID_API_TOKEN = "No API token configured. Skipping update.";
	public static final String OSS_INFO_NOT_FOUND = "No open source information found.";

	/* --- Constructor --- */
	
	/**
	 * Private default constructor
	 */
	private Constants() {
		// avoid instantiation
	}

}