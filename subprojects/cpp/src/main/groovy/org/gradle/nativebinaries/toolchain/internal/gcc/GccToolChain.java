/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.internal.ToolChainAvailability;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionResult;
import org.gradle.nativebinaries.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativebinaries.toolchain.internal.tools.DefaultCommandLineToolConfiguration;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


/**
 * Compiler adapter for GCC.
 */
public class GccToolChain extends AbstractGccCompatibleToolChain implements Gcc {

    private static final Logger LOGGER = LoggerFactory.getLogger(GccToolChain.class);

    public static final String DEFAULT_NAME = "gcc";

    private final Transformer<GccVersionResult, File> versionDeterminer;

    private GccVersionResult versionResult;

    public GccToolChain(Instantiator instantiator, String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory) {
        super(name, operatingSystem, fileResolver, execActionFactory, new GccToolSearchPath(operatingSystem), instantiator);
        this.versionDeterminer = new GccVersionDeterminer(execActionFactory);

        add(new DefaultCommandLineToolConfiguration("cCompiler", ToolType.C_COMPILER, "gcc"));
        add(new DefaultCommandLineToolConfiguration("cppCompiler", ToolType.CPP_COMPILER, "g++"));
        add(new DefaultCommandLineToolConfiguration("linker", ToolType.LINKER, "g++"));
        add(new DefaultCommandLineToolConfiguration("staticLibArchiver", ToolType.STATIC_LIB_ARCHIVER, "ar"));
    }

    @Override
    protected String getTypeName() {
        return "GNU GCC";
    }

    @Override
    protected void initTools(ToolChainAvailability availability) {
        if (versionResult == null) {
            CommandLineToolSearchResult compiler = locate((CommandLineToolConfigurationInternal) getByName("cCompiler"));
            if (!compiler.isAvailable()) {
                compiler = locate((CommandLineToolConfigurationInternal) getByName("cppCompiler"));
            }
            availability.mustBeAvailable(compiler);
            if (!compiler.isAvailable()) {
                return;
            }
            versionResult = versionDeterminer.transform(compiler.getTool());
            LOGGER.debug("Found {} with version {}", ToolType.C_COMPILER.getToolName(), versionResult);
        }
        availability.mustBeAvailable(versionResult);
    }

    protected boolean canUseCommandFile() {
        String[] components = versionResult.getVersion().split("\\.");
        int majorVersion;
        try {
            majorVersion = Integer.valueOf(components[0]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("Unable to determine major g++ version from version number %s.", versionResult), e);
        }
        return majorVersion >= 4;
    }
}