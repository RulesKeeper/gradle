/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.toolchain.ConfigurableToolChain;
import org.gradle.nativebinaries.toolchain.CommandLineToolConfiguration;
import org.gradle.nativebinaries.toolchain.internal.tools.*;

import java.util.List;
import java.util.Map;

public class DefaultConfigurableToolChain<T extends CommandLineToolConfiguration> extends DefaultNamedDomainObjectSet<T> implements ConfigurableToolChain<T> {
    private final String name;
    private final String displayName;

    public DefaultConfigurableToolChain(Class<? extends T> type, Map<String, T> asMap, Instantiator instantiator, String name, String displayName) {
        super(type, instantiator);
        this.name = name;
        this.displayName = displayName;
        for (T tool : asMap.values()) {
            add(newConfiguredGccTool(tool));
        }
    }

    private T newConfiguredGccTool(T defaultTool) {
        CommandLineToolConfigurationInternal gccToolInternal = (CommandLineToolConfigurationInternal) defaultTool;
        DefaultCommandLineToolConfiguration platformTool = new DefaultCommandLineToolConfiguration(defaultTool.getName(), gccToolInternal.getToolType(), defaultTool.getExecutable());
        Action<List<String>> argAction = gccToolInternal.getArgAction();
        platformTool.withArguments(argAction);
        return (T) platformTool;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return name;
    }
}