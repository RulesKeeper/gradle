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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.result.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DownloadedIvyModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.result.metadata.IvyDescriptorArtifact;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;

import java.net.URI;

public class IvyResolver extends ExternalResourceResolver implements PatternBasedResolver {

    private final RepositoryTransport transport;
    private final boolean dynamicResolve;
    private final MetaDataParser metaDataParser;

    public IvyResolver(String name, RepositoryTransport transport,
                       LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                       boolean dynamicResolve, ResolverStrategy resolverStrategy) {
        super(name, transport.getRepository(), new ResourceVersionLister(transport.getRepository()), locallyAvailableResourceFinder, resolverStrategy);
        this.metaDataParser = new DownloadedIvyModuleDescriptorParser(resolverStrategy);
        this.transport = transport;
        this.transport.configureCacheManager(this);
        this.dynamicResolve = dynamicResolve;
    }

    @Override
    public boolean isDynamicResolveMode() {
        return dynamicResolve;
    }

    protected boolean isMetaDataArtifact(Class<? extends Artifact> artifactType) {
        return artifactType == IvyDescriptorArtifact.class;
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        return new DefaultIvyArtifactName("ivy", "ivy", "xml");
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        String artifactPattern = transport.convertToPath(baseUri) + pattern;
        addArtifactPattern(artifactPattern);
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        String descriptorPattern = transport.convertToPath(baseUri) + pattern;
        addIvyPattern(descriptorPattern);
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return new IvyLocalRepositoryAccess();
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new IvyRemoteRepositoryAccess();
    }

    protected MutableModuleVersionMetaData createMetaDataForDependency(DependencyMetaData dependency) {
        return new DefaultIvyModuleVersionMetaData(dependency);
    }

    protected MutableModuleVersionMetaData parseMetaDataFromResource(LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        return metaDataParser.parseMetaData(context, cachedResource);
    }

    private class IvyLocalRepositoryAccess extends LocalRepositoryAccess {

        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            result.resolved(configuration.getArtifacts());
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetaData configuration = module.getConfiguration("javadoc");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            ConfigurationMetaData configuration = module.getConfiguration("sources");
            if (configuration != null) {
                result.resolved(configuration.getArtifacts());
            }
        }
    }

    private class IvyRemoteRepositoryAccess extends RemoteRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            // Configuration artifacts are determined locally
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }
}
