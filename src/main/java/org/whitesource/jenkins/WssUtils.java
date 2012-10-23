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

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.Maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility methods used throughout the plugin.
 * 
 * @author c_rsharv
 * @author Edo.Shor
 */
public final class WssUtils {

    /* --- Static members --- */

        private static final Pattern PARAM_LIST_SPLIT_PATTERN = Pattern.compile(",|$", Pattern.MULTILINE);
        private static final Pattern KEY_VALUE_SPLIT_PATTERN = Pattern.compile("=");

	
	/* --- Static methods --- */
	
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

    public static List<String> splitParameters(String paramList) {
            List<String> params = new ArrayList<String>();

            if (paramList != null) {
                String[] split = PARAM_LIST_SPLIT_PATTERN.split(paramList);
                if (split != null) {
                    for (String param : split) {
                        if (!(param == null || param.trim().length() == 0)) {
                            params.add(param.trim());
                        }
                    }
                }
            }

            return params;
        }

        public static Map<String, String> splitParametersMap(String paramList) {
            Map<String, String> params = new HashMap<String, String>();

            List<String> kvps = splitParameters(paramList);
            if (kvps != null) {
                for (String kvp : kvps) {
                    String[] split = KEY_VALUE_SPLIT_PATTERN.split(kvp);
                    if (split.length == 2) {
                        params.put(split[0], split[1]);
                    }
                }
            }

            return params;
        }


	/* --- Constructors --- */
	
	/**
	 * Private constructor
	 */
	private WssUtils() {
		// avoid instantiation
	}
	
	
	
}
