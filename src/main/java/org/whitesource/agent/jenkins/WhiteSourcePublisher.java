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

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.jenkins.maven.MavenDependenciesRecord;
import org.whitesource.api.client.WssServiceException;

/**
 * @author ramakrishna
 * @author Edo.Shor
 */
@SuppressWarnings("unchecked")
public class WhiteSourcePublisher extends Recorder {

	/* --- Members --- */
	
	private final String orgToken;
	
	private final String projectToken;
	
	private final String libIncludes;
	
	private final String libExcludes;
	
	/* --- Constructors --- */

	/**
	 * Constructor
	 * 
	 * @param orgToken
	 * @param libIncludes
	 * @param libExcludes
	 */
	@DataBoundConstructor
	public WhiteSourcePublisher(String orgToken, String projectToken, String libIncludes, String libExcludes) {
		this.orgToken = orgToken;
		this.projectToken = projectToken;
		this.libIncludes = libIncludes;
		this.libExcludes = libExcludes;
	}

	/* --- Interface implementation methods --- */
	
	@SuppressWarnings("rawtypes")
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) 
			throws InterruptedException, IOException {
		listener.getLogger().println("Starting White Source publisher");
		
		if (build.getResult().isWorseThan(Result.SUCCESS)) {
			listener.getLogger().println("Build failed. Skipping  update.");
            return true;
        }
		
		if (WssUtils.isFreeStyleMaven(build.getProject())) {
			listener.getLogger().println("Free style maven jobs are not supported in this version. See plugin documentation.");
            return true;
		}

		// prepare all OSS information to send
		listener.getLogger().println("\nPreparing OSS information");
		Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();
		if ((build instanceof MavenModuleSetBuild)) {
			projectInfos = doMaven((MavenModuleSetBuild) build, listener);
		} else if ((build instanceof FreeStyleBuild)) {
			AgentProjectInfo projectInfo = doFreeStyle((FreeStyleBuild) build, listener);
			projectInfo.setProjectToken(projectToken);
			projectInfos.add(projectInfo);
		} else {
			listener.getLogger().println("Unrecognized build type " + build.getClass().getName());
		}

		// update White Source
		listener.getLogger().println("\nSending OSS information to White Source service");
		WssService service = new WssService();
		try {
			UpdateInventoryResult result = service.update(orgToken, projectInfos);
			logUpdateResult(result, listener);
			listener.getLogger().println("Update successful");
			return true;
		} catch (WssServiceException e) {
			listener.getLogger().println("Update fail: " + e.getMessage());
		} finally {
			service.shutdown();
		}
		
		// failed
        build.setResult(Result.FAILURE);
		
		return true;
	}

	/* --- Public methods --- */
	
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}
	
	/* --- Private methods --- */
	
	private  Collection<AgentProjectInfo> doMaven(MavenModuleSetBuild build, BuildListener listener) {
		Collection<AgentProjectInfo> projectInfos = new ArrayList<AgentProjectInfo>();
		
		for (Entry<MavenModule, MavenBuild> entry : build.getModuleLastBuilds().entrySet()) {
			AgentProjectInfo projectInfo = new AgentProjectInfo();
			MavenBuild moduleBuild = entry.getValue();
			
			// module information
			MavenArtifactRecord action = moduleBuild.getAction(MavenArtifactRecord.class);
			if (action != null) {
				listener.getLogger().println(action.pomArtifact.canonicalName);
				projectInfo.setCoordinates(new Coordinates(action.pomArtifact.groupId, 
						action.pomArtifact.artifactId, action.pomArtifact.version));
			}

			// dependencies
			Collection<DependencyInfo> dependencyInfos = projectInfo.getDependencies();
			MavenDependenciesRecord dependenciesAction = moduleBuild.getAction(MavenDependenciesRecord.class);
			if (dependenciesAction == null) {
				listener.getLogger().println("No dependncies found !");
			} else {
				dependencyInfos.addAll(dependenciesAction.getDependencies());
				listener.getLogger().println("Found " + dependenciesAction.getDependencies().size() + " dependencies (transitive included)");
			}
			
			projectInfos.add(projectInfo);
		}
		
		return projectInfos;
	}
	
	private AgentProjectInfo doFreeStyle(FreeStyleBuild freeStyleBuild, BuildListener listener)
			throws IOException, InterruptedException {
		AgentProjectInfo projectInfo = null;
		
		if (StringUtils.isBlank(libIncludes)) {
			listener.getLogger().println("No include pattern defined. Skipping update.");
		} else {
			LibFolderScanner libScanner = new LibFolderScanner(libIncludes, libExcludes, listener.getLogger());
			Collection<DependencyInfo> folderDependencies = freeStyleBuild.getWorkspace().act(libScanner);
			projectInfo = new AgentProjectInfo();
			projectInfo.getDependencies().addAll(folderDependencies);
		}
		
		return projectInfo;
	}
	
	private void logUpdateResult(UpdateInventoryResult result, BuildListener listener) {
		listener.getLogger().println("White Source update results: ");
		listener.getLogger().println("White Source organization: " + result.getOrganization());
		listener.getLogger().println(result.getCreatedProjects().size() + " Newly created projects:");
		StringUtils.join(result.getCreatedProjects(), ",");
		listener.getLogger().println(result.getUpdatedProjects().size() + " existing projects were updated:");
		StringUtils.join(result.getUpdatedProjects(), ",");
	}

	/* --- Nested classes --- */

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		/* --- Members--- */
		
		private String orgToken;
		
		/* --- Constructors--- */

		/**
		 * Default constructor
		 */
		public DescriptorImpl() {
			super(WhiteSourcePublisher.class);
			load();
		}

		/* --- Overridden methods --- */

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "White Source Publisher";
		}

		@Override
		public String getHelpFile() {
			return "/plugin/whitesource/help/help.html";
		}

		@Override
		public WhiteSourcePublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			String projectToken = formData.optString("projectToken");
			String libIncludes = formData.optString("libIncludes");
			String libExcludes = formData.optString("libExcludes");
			
			JSONObject overridingTokenJSON = formData.getJSONObject("overridingOrgToken");
			if (!overridingTokenJSON.isNullObject()) {
				String overridingOrgToken = overridingTokenJSON.getString("overridingOrgToken");
				orgToken = overridingOrgToken;
			}
			
			return new WhiteSourcePublisher(orgToken, projectToken, libIncludes, libExcludes);
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			orgToken = json.getString("orgToken");
			save();
			return super.configure(req, json);
		}

		/* --- Public methods --- */

		public FormValidation doCheckOrgToken(@QueryParameter String orgToken) {
			return FormValidation.validateRequired(orgToken);
		}

		public FormValidation doCheckOverridingOrgToken(@QueryParameter String overridingOrgToken) {
			return FormValidation.validateRequired(overridingOrgToken);
		}

		public FormValidation doCheckLibIncludes(@QueryParameter String libIncludes) {
			return FormValidation.validateRequired(libIncludes);
		}
		
		public FormValidation doCheckProjectToken(@QueryParameter String projectToken) {
			return FormValidation.validateRequired(projectToken);
		}

		/* --- Getters / Setters --- */
		
		public String getOrgToken() {
			return orgToken;
		}

		public void setOrgToken(String orgToken) {
			this.orgToken = orgToken;
		}
		
	}
	
	/* --- Getters --- */

	public String getOrgToken() {
		return orgToken;
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

}
