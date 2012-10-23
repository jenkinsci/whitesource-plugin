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

import hudson.model.Action;

import java.util.Set;

import org.whitesource.agent.api.model.DependencyInfo;

/**
 * Records dependencies (including transitive) of a maven module.
 *
 * @author Yossi Shaul (Original)
 * @author Edo.Shor (White Source)
 */
public class MavenDependenciesRecord implements Action {
    private final Set<DependencyInfo> dependencies;

    public MavenDependenciesRecord(Set<DependencyInfo> dependencies) {
        this.dependencies = dependencies;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public Set<DependencyInfo> getDependencies() {
        return dependencies;
    }
}
