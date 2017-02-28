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

import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility methods used throughout the plugin.
 * 
 * @author c_rsharv
 * @author Edo.Shor
 */
public final class WssUtils {

    /* --- Static members --- */

        private static final Pattern PARAM_LIST_SPLIT_PATTERN = Pattern.compile(",|$| ", Pattern.MULTILINE);
        private static final Pattern KEY_VALUE_SPLIT_PATTERN = Pattern.compile("=");

	
	/* --- Static methods --- */

    /**
     * <b>Important: </b> do not remove since it is used in jelly config files to determine job type.
     *
     * @param project
     * @return True if this is a maven project invoking top maven target.
     */
    public static boolean isMaven(AbstractProject<?,?> project) {
        return project instanceof MavenModuleSet;
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
            Iterator<Builder> it = ((FreeStyleProject) project).getBuilders().iterator();
            while(it.hasNext() && !freeStyle) {
                freeStyle = it.next() instanceof Maven;
            }
		}
		
		return freeStyle;
	}

    /**
     * <b>Important: </b> do not remove since it is used in jelly config files to determine job type.
     *
     * @param value
     * @return
     */
    public static String selectedCheckPolicies(String value) {
        String selectedCheckPolicies = "global";
        if (StringUtils.isNotBlank(value)) {
            selectedCheckPolicies = value;
        }

        return selectedCheckPolicies;
    }

    /**
     * <b>Important: </b> do not remove since it is used in jelly config files to determine job type.
     *
     * @param value
     * @return
     */
    public static String selectedForceUpdate(String value) {
        String selectedForceUpdate = "global";
        if (StringUtils.isNotBlank(value)) {
            selectedForceUpdate = value;
        }
        return selectedForceUpdate;
    }

    /**
     * <b>Important: </b> do not remove since it is used in jelly global files to determine job type.
     *
     * @param value
     * @return
     */
    public static String selectedGlobalCheckPolicies(String value) {
        String globalCheckedPolicies = "disable";
        if (StringUtils.isNotBlank(value)) {
            globalCheckedPolicies = value;
        }

        return globalCheckedPolicies;
    }

    public static List<String> splitParameters(String paramList) {
            List<String> params = new ArrayList<String>();

            if (paramList != null) {
                String[] split = PARAM_LIST_SPLIT_PATTERN.split(paramList);
                if (split != null) {
                    for (String param : split) {
                        if (StringUtils.isNotBlank(param)) {
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
