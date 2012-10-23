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

package org.whitesource.jenkins.extractor.generic;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the interface for scanning the workspace for all OSS libraries. 
 * 
 * @author Edo.Shor
 */
public class LibFolderScanner implements FilePath.FileCallable<Collection<DependencyInfo>> {

	/* --- Static members --- */
	
	private static final long serialVersionUID = 6773794529916357187L;
	
	/* --- Members --- */
	
	private List<String> libIncludes;
	
	private List<String> libExcludes;
	
	private BuildListener listener;
	
	private Collection<DependencyInfo> dependencies;
	
	/* --- Constructors --- */

    /**
     * Constructor
     *
     * @param libIncludes Ant style pattern for files to include.
     * @param libExcludes Ant style pattern for files to exclude.
     * @param listener
     */
    public LibFolderScanner(List<String> libIncludes, List<String> libExcludes, BuildListener listener) {
        this.libIncludes = libIncludes;
        this.libExcludes = libExcludes;
        this.listener = listener;
        dependencies = new ArrayList<DependencyInfo>();
    }

	/* --- Interface implementation methods --- */

	public Collection<DependencyInfo> invoke(File f, VirtualChannel channel)
			throws IOException, InterruptedException {
        listener.getLogger().println("Scanning folder " + f.getName());

        String includes = StringUtils.join(libIncludes, ",");
        String excludes = StringUtils.join(libExcludes, ",");
		FilePath[] libraries = new FilePath(f).list(includes, excludes);
		for (FilePath file : libraries) {
			try {
                dependencies.add(collectDependencyInfo(file));
            } catch (IOException e) {
                listener.getLogger().println("Error extracting library details");
            }
		}
		
        listener.getLogger().println("Found " + dependencies.size() + " dependencies matching inclulde / exclude pattern in folder.");
		
		return dependencies;
	}
	
	/* --- Private methods --- */

	private DependencyInfo collectDependencyInfo(FilePath file) throws IOException, InterruptedException {
		DependencyInfo info = new DependencyInfo();
		info.setSystemPath(file.getRemote());
		info.setArtifactId(file.getName());
		info.setSha1(file.act(new CalcSha1FileCallable()));

		return info;
	}
	
	/* --- Nested classes --- */
	
	/**
	 * Implementation of the interface to calculate SHA-1 hash code for location abstracted files. 
	 * 
	 * @author Edo.Shor
	 */
	static class CalcSha1FileCallable implements FilePath.FileCallable<String> {

		/* --- Static members --- */
		
		private static final long serialVersionUID = 2959979211787869074L;
		
		/* --- Interface implementation methods --- */

		public String invoke(File f, VirtualChannel channel)
				throws IOException, InterruptedException {
			return ChecksumUtils.calculateSHA1(f);
		}
		
	}
}