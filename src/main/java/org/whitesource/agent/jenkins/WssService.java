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

package org.whitesource.agent.jenkins;

import hudson.EnvVars;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.dispatch.RequestFactory;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.api.client.WssServiceClient;
import org.whitesource.api.client.WssServiceClientImpl;
import org.whitesource.api.client.WssServiceException;

/**
 * A facade to the communication with the White Source service.
 * 
 * @author c_rsharv
 * @author Edo.Shor
 */
public class WssService {

	/* --- Members --- */
	
	private WssServiceClient client;
	
	private RequestFactory requestFactory;

	/* --- Constructors --- */
	
	/**
	 * Default constructor.
	 */
	public WssService() {
		requestFactory = new RequestFactory(Constants.AGENT_TYPE, Constants.AGENT_VERSION);
		
		String serviceUrl = EnvVars.masterEnvVars.get(Constants.SERVICE_URL_KEYWORD);
		if (StringUtils.isBlank(serviceUrl)) {
			serviceUrl = System.getProperty(Constants.SERVICE_URL_KEYWORD, Constants.DEFAULT_SERVICE_URL);
		}
		client = new WssServiceClientImpl(serviceUrl);
	}
	
	/* --- Public methods --- */
	
	/**
	 * The method update the White Source organization account with the given OSS information.
	 * 
	 * @param orgToken 
	 * @param projectInfos
	 * @return
	 * @throws WssServiceException
	 */
	public UpdateInventoryResult update(String orgToken, Collection<AgentProjectInfo> projectInfos) 
			throws WssServiceException {
		return client.updateInventory(requestFactory.newUpdateInventoryRequest(orgToken, projectInfos));
	}
	
	/**
	 * The method close the underlying client to the White Source service.
	 */
	public void shutdown() {
		client.shutdown();
	}
	
	/* --- Getters / Setters --- */

	public WssServiceClient getClient() {
		return client;
	}

	public void setClient(WssServiceClient client) {
		this.client = client;
	}

	public RequestFactory getRequestFactory() {
		return requestFactory;
	}

	public void setRequestFactory(RequestFactory requestFactory) {
		this.requestFactory = requestFactory;
	}
	
}
