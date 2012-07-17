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

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import org.whitesource.agent.api.model.DependencyInfo;

/**
 * Implementation of the interface for scanning the workspace for all OSS libraries. 
 * 
 * @author Edo.Shor
 */
class LibFolderScanner implements FilePath.FileCallable<Collection<DependencyInfo>> {

	/* --- Static members --- */
	
	private static final long serialVersionUID = 6773794529916357187L;
	
	/* --- Members --- */
	
	private String libIncludes;
	
	private String libExcludes;
	
	private PrintStream logger;
	
	private Collection<DependencyInfo> dependencies;
	
	/* --- Constructors --- */
	
	/**
	 * Constructor
	 * 
	 * @param libIncludes Ant style pattern for files to include.
	 * @param libExcludes Ant style pattern for files to exclude.
	 * @param logger Logger to use when required.
	 */
	public LibFolderScanner(String libIncludes, String libExcludes, PrintStream logger) {
		this.libIncludes = libIncludes;
		this.libExcludes = libExcludes;
		this.logger = logger;
		dependencies = new ArrayList<DependencyInfo>();
	}

	/* --- Interface implementation methods --- */

	public Collection<DependencyInfo> invoke(File f, VirtualChannel channel)
			throws IOException, InterruptedException {
		logger.println("Scanning folder " + f.getName());
		
		FilePath[] libraries = new FilePath(f).list(libIncludes, libExcludes);
		for (FilePath file : libraries) {
			dependencies.add(collectDependencyInfo(file));
		}
		
		logger.println("Found " + dependencies.size() + " dependencies matching inclulde / exclude pattern in folder.");
		
		return dependencies;
	}
	
	/* --- Private methods --- */

	private DependencyInfo collectDependencyInfo(FilePath file) throws IOException, InterruptedException {
		DependencyInfo info = new DependencyInfo();
		
		info.setSystemPath(file.getRemote());
		info.setArtifactId(file.getName());
		
		String sha1 = file.act(new CalcSha1FileCallable(logger));
		if (Constants.ERROR_SHA1.equals(sha1)) {
			logger.print("Error calculating SHA-1 for " + file.getRemote());
		} else {
			info.setSha1(sha1);
		}
		
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
		
		/* --- Members --- */
		
		private PrintStream logger;
		
		/* --- Constructors --- */
		
		/**
		 * Constructor
		 * 
		 * @param logger
		 */
		public CalcSha1FileCallable(PrintStream logger) {
			this.logger = logger;
		}

		/* --- Interface implementation methods --- */

		public String invoke(File f, VirtualChannel channel)
				throws IOException, InterruptedException {
			return WssUtils.calculateSha1(f, logger);
		}
		
	}
}