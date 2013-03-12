/*
 * Copyright (C) 2010 JFrog Ltd.
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
 * 
 * This file contains modifications to the original work made by White Source Ltd. 2012. 
 */

package org.whitesource.jenkins.extractor.maven;

import hudson.Extension;
import hudson.maven.*;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.model.BuildListener;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Records dependencies used during the build.
 *
 * @author Yossi Shaul (Original)
 * @author Edo.Shor (White Source)
 */
public class MavenDependenciesRecorder extends MavenReporter {

    /* --- Static members --- */
	
	private static final long serialVersionUID = 9107918530513865446L;
	
	/* --- Members --- */
	
	/**
     * All dependencies this module used, including transitive ones.
     */
    private transient Set<DependencyInfo> dependencies;

    /* --- Concrete implementation methods --- */

    @Override
    public boolean preBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) {
        listener.getLogger().println("[Jenkins] Collecting dependencies info");
        dependencies = new HashSet<DependencyInfo>();
        return true;
    }

    /**
     * Mojos perform different dependency resolution, so we add dependencies for each mojo.
     */
    @Override
    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener,
            Throwable error) {
        recordMavenDependencies(pom.getArtifacts());
        return true;
    }

    /**
     * Sends the collected dependencies over to the master and record them.
     */
    @Override
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener)
            throws InterruptedException, IOException {
        build.executeAsync(new BuildCallable<Void, IOException>() {
            
        	/* --- Static members --- */
			private static final long serialVersionUID = -3923086337535368565L;
			
			/* --- Members --- */
			
			// record is transient, so needs to make a copy first
            private final Set<DependencyInfo> d = dependencies;
            
            /* --- Interface implementation methods --- */

            public Void call(MavenBuild build) throws IOException, InterruptedException {
                // add the action
                //These actions are persisted into the build.xml of each build run - we need another
                //context to store these actions
                build.getActions().add(new MavenDependenciesRecord(d));
                return null;
            }
        });
        return true;
    }
    
    /* --- Private methods --- */

    private void recordMavenDependencies(Set<Artifact> artifacts) {
        if (artifacts != null) {
            for (Artifact dependency : artifacts) {
                File dependencyFile = dependency.getFile();
                if (dependency.isResolved() && dependencyFile != null) {
                	DependencyInfo info = new DependencyInfo();
                	info.setGroupId(dependency.getGroupId());
					info.setArtifactId(dependency.getArtifactId());
					info.setVersion(dependency.getVersion());
					info.setType(dependency.getType());
					info.setClassifier(dependency.getClassifier());
					info.setScope(dependency.getScope());
					info.setSystemPath(dependencyFile.getName());
					
					if (dependencyFile.exists()) {
						try {
                            info.setSha1(ChecksumUtils.calculateSHA1(dependencyFile));
                        } catch (IOException e) {
                            // ignore
                        }
					}
                	
                    dependencies.add(info);
                }
            }
        }
    }
    
    /* --- Nested classes --- */

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
    	
        @Override
        public String getDisplayName() {
            return "Record Maven Dependencies";
        }

        @Override
        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenDependenciesRecorder();
        }
    }
    
}
