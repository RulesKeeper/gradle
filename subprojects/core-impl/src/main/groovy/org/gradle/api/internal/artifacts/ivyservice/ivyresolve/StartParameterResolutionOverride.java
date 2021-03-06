/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

import java.util.concurrent.TimeUnit;

public class StartParameterResolutionOverride {
    private final StartParameter startParameter;

    public StartParameterResolutionOverride(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public void addResolutionRules(ResolutionRules resolutionRules) {
        if (startParameter.isOffline()) {
            resolutionRules.eachDependency(new Action<DependencyResolutionControl>() {
                public void execute(DependencyResolutionControl dependencyResolutionControl) {
                    dependencyResolutionControl.useCachedResult();
                }
            });
            resolutionRules.eachModule(new Action<ModuleResolutionControl>() {
                public void execute(ModuleResolutionControl moduleResolutionControl) {
                    moduleResolutionControl.useCachedResult();
                }
            });
            resolutionRules.eachArtifact(new Action<ArtifactResolutionControl>() {
                public void execute(ArtifactResolutionControl artifactResolutionControl) {
                    artifactResolutionControl.useCachedResult();
                }
            });
        } else if (startParameter.isRefreshDependencies()) {
            resolutionRules.eachDependency(new Action<DependencyResolutionControl>() {
                public void execute(DependencyResolutionControl dependencyResolutionControl) {
                    dependencyResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
            resolutionRules.eachModule(new Action<ModuleResolutionControl>() {
                public void execute(ModuleResolutionControl moduleResolutionControl) {
                    moduleResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
            resolutionRules.eachArtifact(new Action<ArtifactResolutionControl>() {
                public void execute(ArtifactResolutionControl artifactResolutionControl) {
                    artifactResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
        }
    }

    public ModuleComponentRepository overrideModuleVersionRepository(ModuleComponentRepository original) {
        if (startParameter.isOffline()) {
            return new OfflineModuleComponentRepository(original);
        }
        return original;
    }

    private static class OfflineModuleComponentRepository extends BaseModuleComponentRepository {

        private final FailedRemoteAccess failedRemoteAccess = new FailedRemoteAccess();

        public OfflineModuleComponentRepository(ModuleComponentRepository original) {
            super(original);
        }

        @Override
        public ModuleComponentRepositoryAccess getRemoteAccess() {
            return failedRemoteAccess;
        }

        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            result.failed(new ArtifactResolveException(artifact.getId(), "No cached version available for offline mode"));
        }
    }

    private static class FailedRemoteAccess implements ModuleComponentRepositoryAccess {
        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            result.failed(new ModuleVersionResolveException(dependency.getRequested(), "No cached version listing for %s available for offline mode."));
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            result.failed(new ModuleVersionResolveException(moduleComponentIdentifier, "No cached version of %s available for offline mode."));
        }

        public void resolveModuleArtifacts(ComponentMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            result.failed(new ArtifactResolveException(component.getComponentId(), "No cached version available for offline mode"));
        }

        public void resolveModuleArtifacts(ComponentMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
            result.failed(new ArtifactResolveException(component.getComponentId(), "No cached version available for offline mode"));
        }
    }
}
