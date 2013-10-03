/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

public class DefaultModuleVersionArtifactMetaData implements ModuleVersionArtifactMetaData {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final Artifact artifact;

    public DefaultModuleVersionArtifactMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.artifact = artifact;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public ModuleVersionIdentifier getModuleVersion() {
        return moduleVersionIdentifier;
    }

    public String getClassifier() {
        return (String) artifact.getQualifiedExtraAttributes().get(Dependency.CLASSIFIER);
    }

    public String getName() {
        return artifact.getName();
    }

    public String getType() {
        return artifact.getType();
    }

    public String getExtension() {
        return artifact.getExt();
    }
}