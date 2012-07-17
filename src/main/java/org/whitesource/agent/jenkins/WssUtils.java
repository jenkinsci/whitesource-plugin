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

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.Maven;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;

import org.sonatype.aether.util.ChecksumUtils;

/**
 * Utility methods used throughout the plugin.
 * 
 * @author c_rsharv
 * @author Edo.Shor
 */
public final class WssUtils {
	
	/* --- Static methods --- */
	
	/**
	 * Calculates SHA-1 for the given file.
	 * 
	 * @param file Physical file to calculate SHA-1 for.
	 * @param logger Logger to use for errors. May be null.
	 * 
	 * @return SHA-1 code for the given file.
	 */
	public static String calculateSha1(File file, PrintStream logger) {
		String sha1 = Constants.ERROR_SHA1;
		
		if (file != null) {
			try {
				Map<String, Object> calcMap = ChecksumUtils.calc(file, Arrays.asList(Constants.SHA1));
				sha1 = (String) calcMap.get(Constants.SHA1);
			} catch (IOException e) {
				if (logger != null) {
					logger.println(Constants.ERROR_SHA1 + " " + e.getMessage());
				}
			}
		}
		
		return sha1;
	}
	
	/**
	 * <b>Important: </b> do not remove since it is used in jelly config files to determine job type.
	 * 
	 * @param project
	 * @return True if this is a freestyle project not invoking top maven target.
	 */
	public static boolean isFreeStyle(AbstractProject<?,?> project) {
		return project instanceof FreeStyleProject && !isFreeStyleMaven(project);
	}		
	
	/**
	 * <b>Important: </b> do not remove since it is used in jelly config files to determine job type.
	 * 
	 * @param project
	 * @return True if this is a freestyle project that invoke a top maven target.
	 */
	public static boolean isFreeStyleMaven(AbstractProject<?,?> project) {
		boolean freeStyle = false;
		
		if (project instanceof FreeStyleProject) {
			for (Builder builder : ((FreeStyleProject) project).getBuilders()) {
				if (builder instanceof Maven) {
					freeStyle = true;
					break;
				}
			}
		}
		
		return freeStyle;
	}

	/* --- Constructors --- */
	
	/**
	 * Private constructor
	 */
	private WssUtils() {
		// avoid instantiation
	}
	
	
	
}
