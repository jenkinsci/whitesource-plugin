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
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.model.ChecksumType;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.hash.HashCalculator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Implementation of the interface for scanning the workspace for all OSS libraries. 
 * 
 * @author Edo.Shor
 */
public class LibFolderScanner extends MasterToSlaveFileCallable<Collection<DependencyInfo>> {

	/* --- Static members --- */
	
	private static final long serialVersionUID = 6773794529916357187L;

	private static final String JAVA_SCRIPT_REGEX = ".*\\.js";

	/* --- Members --- */
	
	private List<String> libIncludes;
	
	private List<String> libExcludes;
	
	private TaskListener listener;
	
	private Collection<DependencyInfo> dependencies;
	
	/* --- Constructors --- */

    /**
     * Constructor
     *  @param libIncludes Ant style pattern for files to include.
     * @param libExcludes Ant style pattern for files to exclude.
	 * @param listener
	 */
    public LibFolderScanner(List<String> libIncludes, List<String> libExcludes, TaskListener listener) {
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
		
        listener.getLogger().println("Found " + dependencies.size() + " dependencies matching include / exclude pattern in folder.");
		
		return dependencies;
	}
	
	/* --- Private methods --- */

	private DependencyInfo collectDependencyInfo(FilePath file) throws IOException, InterruptedException {
		DependencyInfo info = new DependencyInfo();
		info.setSystemPath(file.getRemote());
		info.setArtifactId(file.getName());
		info.setSha1(file.act(new CalcSha1FileCallable()));
		// handle JavaScript files
		calculateHashes(new File(file.getRemote()), info);

		return info;
	}

	private void calculateHashes(File file, DependencyInfo info) {
		if (file.getName().toLowerCase().matches(JAVA_SCRIPT_REGEX)) {
			Map<ChecksumType, String> javaScriptChecksums = new HashMap<>();
			try {
				javaScriptChecksums = new HashCalculator().calculateJavaScriptHashes(file);
			} catch (Exception e) {
				listener.getLogger().println("Failed to calculate javaScript file hash for :" + file.getName());
//				logger.debug("Failed to calculate javaScript hash for file: {}, error: {}", dependencyFile.getPath(), e);
			}
			for (Map.Entry<ChecksumType, String> entry : javaScriptChecksums.entrySet()) {
				info.addChecksum(entry.getKey(), entry.getValue());
			}
		}

		// other platform SHA1
		ChecksumUtils.calculateOtherPlatformSha1(info, file);

		// super hash
		ChecksumUtils.calculateSuperHash(info, file);
	}

	/* --- Nested classes --- */

	/**
	 * Implementation of the interface to calculate SHA-1 hash code for location abstracted files.
	 *
	 * @author Edo.Shor
	 */
	static class CalcSha1FileCallable extends MasterToSlaveFileCallable<String> {

		/* --- Static members --- */

		private static final long serialVersionUID = 2959979211787869074L;

		/* --- Interface implementation methods --- */

		public String invoke(File f, VirtualChannel channel)
				throws IOException, InterruptedException {
			return ChecksumUtils.calculateSHA1(f);
		}

	}

}