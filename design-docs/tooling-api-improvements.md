This specification defines a number of improvements to the tooling API.

# Use cases

## Tooling can be developed for Gradle plugins

Many plugins have a model of some kind that declares something about the project. In order to build tooling for these
plugins, such as IDE integration, it is necessary to make these models available outside the build process.

This must be done in a way such that the tooling and the build logic can be versioned separately, and that a given
version of the tooling can work with more than one version of the build logic, and vice versa.

## Replace or override the built-in Gradle IDE plugins

The Gradle IDE plugins currently implement a certain mapping from a Gradle model to a particular IDE model. It should be
possible for a plugin to completely replace the built-in plugins and assemble their own mapping to the IDE model.

Note that this is not the same thing as making the IDE plugins more flexible (although there is a good case to be made there
too). It is about allowing a completely different implementation of the Java domain - such as the Android plugins - to provide
their own opinion of how the project should be represented in the IDE.

## Built-in Gradle plugins should be less privileged

Currently, the built-in Gradle plugins are privileged in several ways, such that only built-in plugins can use
certain features. One such feature is exposing their models to tooling. The IDE and build comparison models
are baked into the tooling API. This means that these plugins, and only these plugins, can contribute models to tooling.

This hard-coding has a number of downsides beyond the obvious lack of flexibility:

* Causes awkward cycles in the Gradle source tree.
* Responsibilities live in the wrong projects, so that, for example, the IDE project knows how to assemble the
  build comparison project. This means that the IDE plugin must be available for the tooling API provider to work.
* Cannot replace the implementation of a model, as discussed above.
* A tooling model cannot be 'private' to a plugin. For example, the build comparison model is really a private cross-version
  model that allows different versions of the build comparison plugin to communicate with each other.
* A tooling model must be distributed as part of the core Gradle runtime. This makes it difficult to bust up the
  Gradle distribution.

Over time, we want to make the built-in plugins less privileged so that the difference between a 'built-in' plugin and
a 'custom' is gradually reduced. Allowing custom plugins to contribute tooling models and changing the build-in plugins
to use this same mechanism is one step in this direction.

# Implementation plan

## Story: Expose the build script of a project

This story exposes via the tooling API some basic information about the build script of a project.

1. Add a `GradleScript` type with the following properties:
    1. A `file` property with type `File`.
2. Add a `buildScript` property to `GradleProject` with type `GradleScript`.
3. Include an `@since` javadoc tag and an `@Incubating` annotation on the new types and methods.
4. Change `GradleProjectBuilder` to populate the model.

An example usage:

    GradleProject project = connection.getModel(GradleProject.class);
    System.out.println("project " + project.getPath() + " uses script " + project.getBuildScript().getFile());

### Test coverage

- Add a new `ToolingApiSpecification` integration test class that covers:
    - A project with standard build script location
    - A project with customized build script location
- Verify that a decent error message is received when using a Gradle version that does not expose the build scripts.
    - Request `GradleProject` directly.
    - Using `GradleProject` via an `EclipseProject` or `IdeaModule`.

## Story: Expose the compile details of a build script

This story exposes some information about how a build script will be compiled. This information can be used by an
IDE to provide some basic content assistance for a build script.

1. Introduce a new hierarchy to represent a classpath element. Retrofit the IDEA and Eclipse models to use this.
    - Should expose a set of files, a set of source archives and a set of API docs.
2. Add `compileClasspath` property to `GradleScript` to expose the build script classpath.
3. Script classpath includes the Gradle API and core plugins
    - Should include the source and Javadoc
4. Script classpath includes the libraries declared in the `buildscript { }` block.
5. Script classpath includes the plugins declared in the `plugins { }` block.
6. Script classpath includes the libraries inherited from parent project.
7. Add a `groovyVersion` property to `GradleScript` to expose the Groovy version that is used.

### Open issues

- Need to flesh out the classpath types.
- Will need to use Eclipse and IDEA specific classpath models

### Test coverage

- Add a new `ToolingApiSpecification` integration test class that covers:
    - Gradle API is included in the classpath.
    - buildSrc output is included in the classpath, if present.
    - Classpath declared in script is included in the classpath.
    - Classpath declared in script of ancestor project is included in the classpath.
    - Source and Javadoc artifacts for the above are included in the classpath.
- Verify that a decent error message is received when using a Gradle version that does not expose the build script classpath.

## Story: Gradle plugin provides a custom tooling model to the tooling API client

This story allows a custom plugin to expose a tooling model to any tooling API client that shares compatible model classes.

1. Add a public API to allow a plugin to register a tooling model to make available to the tooling API clients.
2. Move core tooling model implementations to live with their plugin implementations.
3. Custom plugin classpath travels with serialized model object back to the provider.

### Test cases

- Client requests a tooling model provided by a custom plugin.
- Client receives a reasonable error message when:
    - Target Gradle version does not support custom tooling models. Should receive an `UnknownModelException`
    - No plugin in the target build provides the requested model. Should receive an `UnknownModelException`.
    - Failure occurs when the plugin attempts to build the requested model.
    - Failure to serialize or deserialize the requested model.
- Generalise `UnsupportedModelFeedbackCrossVersionSpec`.
- Plugin attempts to register a model that some other plugin already has registered.

## Story: Tooling API client builds a complex tooling model in a single batch operation

This story adds support for a tooling API to query portions of the Gradle model in an efficient way.

Introduce the concept of a `BuildAction`. A build action is provided by the tooling API client and is serialized across to the process where
the build is running. It can then query the models as required and return a result. This result is serialized back to the tooling API client.

This approach allows the action to make decisions about which models to request and to request multiple models efficiently within a single
build invocation. It can also later provide the action with access to features which cannot be serialized to the tooling API client.

    public interface BuildAction<T> extends Serializable {
        T execute(BuildController controller);
    }

    public interface BuildController {
        <T> T getModel(Class<T> modelType);
        // Later stories will add more here
    }

    public interface BuildActionExecutor<T> extends LongRunningOperation {
        T get();
        void get(ResultHandler<? super T> handler);
    }

    <T> ConsumerActionExecutor<T> execute(ConsumerAction<T> action);

The `BuildController` interface provides a single method `getModel()` which returns the tooling model of the given type for the default project
associated with the `ProjectConnection`. This method will return the same result as `ProjectConnect.getModel()` would.

For this story, the build action is executed before the build has been configured. If any model is requested, the build is fully configured. Later stories
will introduce a more efficient implementation, where the work done by the build is driven on demand by what the action asks for, and some caching of
models is used.

### Test cases

- Action builds a result and this result is received by the client.
    - Returns a custom result type
    - Returns null
    - Returns a built-in tooling model (such as `IdeaProject`)
    - Returns a custom result type that references a built-in tooling model
    - Returns a custom result type that references multiple tooling models
    - Returns a custom result type that references a custom tooling model whose implementation uses classes that conflict with the client
    - Client changes action implementation to return a different result
- Verify environment that the action executes in:
    - Logging is received by the client as part of the build output.
    - Context ClassLoader is set appropriately.
    - No Gradle-version specific classes are visible to the action.
- Client receives a reasonable error message when:
    - Target Gradle version does not support consumer actions.
    - The action throws an exception.
    - A failure occurs configuring the build.
    - A failure occurs building a requested model.
    - The action cannot be serialized or deserialized.
    - The action result cannot be serialized or deserialized.
    - The action throws an exception that cannot be serialized or deserialized.
    - The Gradle process is killed before the action completes.
    - The action requests an unknown model.
    - The action is compiled for Java 6 but the build runs with Java 5.

## Story: Tooling API build action requests a tooling model for a Gradle project

1. Add a new `GradleBuild` model which contains information about which projects are included in the build.
2. Add a new `BasicGradleProject` type to provide basic structural information about a project.
3. Extend `BuildController` to add methods to query a model for a given project.
4. Change `ProjectConnection.getModel(type)` and `BuildController.getModel(type)` to return only build-level models.
5. Change `BuildController.getModel(project, type)` to return only project-level models.

Here are the above types:

    interface BasicGradleProject { }

    interface GradleBuild {
        BasicGradleProject getRootProject();
        Set<? extends BasicGradleProject> getProjects();
    }

    interface BuildController {
        <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;
    }

Note: there is a breaking change here.

### Test cases

- Can request the `GradleBuild` model via `ProjectConnection`.
- Can request the `GradleBuild` model via a build action.
- Can request a model for a given project:
    - A `BasicGradleProject` for the specified project, not the root.
    - A `GradleProject` for the specified project, not the root.
    - An `IdeaModule` for the specified project, not the root.
    - An `EclipseProject` for the specified project, not the root.
- Cannot request a build model for a project:
    - Cannot request a `BuildModel`.
    - Cannot request a `BuildEnvironment`.
- Client receives decent feedback when
    - Requests a model from an unknown project.
    - Requests an unknown model.

## Story: Gradle provider builds build model efficiently

When the `GradleBuild` model is requested, execute only the settings script, and don't configure any of the projects.

## Story: Tooling API client determines whether model is available

This story adds support for conditionally requesting a model, if it is available

    interface BuildController {
        <T> T findModel(Class<T> type); // returns null when model not present
        <T> T findModel(Model target, Class<T> type); // returns null when model not present
    }

### Test cases

- Client receives null for unknown model, for all target Gradle versions.
- Build action receives null for unknown model, for all target Gradle versions >= 1.8

## Story: Tooling API client changes implementation of a build action

Fix the `ClassLoader` caching in the tooling API so that it can deal with changing implementations.

## Story: GRADLE-2434 - Expose the aggregate tasks for a project

This story allows an IDE to implement a way to select the tasks to execute based on their name, similar to the Gradle command-line.

1. Add an `EntryPoint` model interface, which represents some arbitrary entry point to the build.
2. Add a `TaskSelector` model interface, which represents an entry point that uses a task name to select the tasks to execute.
3. Change `GradleTask` to extend `EntryPoint`, so that each task can be used as an entry point.
4. Add a method to `GradleProject` to expose the task selectors for the project.
    - For new target Gradle versions, delegate to the provider.
    - For older target Gradle versions, use a client-side mix-in that assembles the task selectors using the information available in `GradleProject`.
5. Add methods to `BuildLauncher` to allow a sequence of entry points to be used to specify what the build should execute.
6. Add `@since` and `@Incubating` to the new types and methods.

Here are the above types:

    interface EntryPoint {
    }

    interface TaskSelector extends EntryPoint {
        String getName(); // A display name
    }

    interface GradleTask extends EntryPoint {
        ...
    }

    interface GradleProject {
        DomainObjectSet<? extends TaskSelector> getTaskSelectors();
        ...
    }

    interface BuildLauncher {
        BuildLauncher forTasks(Iterable<? extends EntryPoint> tasks);
        BuildLauncher forTasks(EntryPoint... tasks);
        ...
    }

TBD - maybe don't change `forTasks()` but instead add an `execute(Iterable<? extends EntryPoint> tasks)` method.

### Test cases

- Can request the entry points for a given project hierarchy
    - Task is present in some subprojects but not the target project
    - Task is present in target project but no subprojects
    - Task is present in target project and some subprojects
- Executing a task selector when task is also present in subprojects runs all the matching tasks, for the above cases.
- Can execute a task selector from a child project. Verify the tasks from the child project are executed.
- Executing a task (as an `EntryPoint`) when task is also present in subprojects run the specified task only and nothing from subprojects.
- Can request the entry points for all target Gradle versions.

## Story: Tooling API client launches a build using task selectors from different projects

TBD

### Test cases

- Can execute task selectors from multiple projects, for all target Gradle versions
- Can execute overlapping task selectors.

## Story: Tooling API exposes project's implicit tasks as launchable

TBD

### Test cases

- `BuildInvocations.getTasks()` includes the `help` and other implicit tasks. Running the task instance runs the appropriate task.
- `BuildInvocations.getTaskSelectors()` includes the `help` and other implicit tasks. Running the selector instance runs the appropriate task only (e.g. `help` at the
root of a multi-project build runs `help` in the root project only.

## Story: Expose information about the visibility of a task

This story allows the IDE to hide those tasks that are part of the implementation details of a build.

## Story: Allow options to be specified for tasks

For example, allow something similar to `gradle test --tests SomePattern`

## Story: Tooling API build action requests a tooling model for a Gradle build

This story adds support to build models that have a scope of a whole Gradle build (not just a project)

1. Add a new `GradleBuildToolingModelBuilder` similar to `ToolingModelBuilder`. Possibly an abstract class since this is an SPI.
2. Extend `ToolingModelBuilderRegistry` to allow registration of such a builder.
3. Change the way how models are queried from `ProjectConnection` to use this new builders (there is no project context passed).
   The only special case is the EclipseModel, which is actually built from the default project instead of the root project, so we'd need a specific implementation for that.
4. Extend `BuildController.getModel()` to support `GradleBuild` as model parameter or add `BuildController.getBuildModel(Class)`.
   Those would be using gradle model builder rather than project model builders

### Test cases

- Can register new model builder and
  - query it from client via `ProjectConnection`.
  - query it from client via build action.
- Can request a model via build action:
  - And get result from `GradleBuildToolingModelBuilder` for gradle build scope if passing `GradleBuild` as target.
  - And get result from `ToolingModelBuilder` for project scope if passing one of parameters describing project.
- Client receives decent feedback when requests an unknown model.

## Story: Tooling API client cancels a long running operation

Represent the execution of a long running operation using a `Future`. This `Future` can be used to cancel the operation.

    interface BuildFuture<T> extends Future<T> {
        void onSuccess(Action<? super T> action); // called immediately if the operation has completed successfully
        void onFailure(Action<? super GradleConnectionException> action); // called immediately if the operation has failed
        void onComplete(ResultHandler<? super T> handler); // called immediately if the operation has completed successfully
    }

    interface ModelBuilder<T> {
        BuildInvocation<T> fetch(); // starts building the model, does not block
        ...
    }

    interface BuildLauncher {
        BuildInvocation<Void> start(); // starts running the build, does not block
        ...
    }

    interface BuildActionExecuter<T> {
        BuildInvocation<T> start(); // starts running the build, does not block
        ...
    }

    BuildFuture<GradleProject> model = connection.model(GradleProject.class).fetch();
    model.cancel(true);

    BuildFuture<Void> build = connection.newBuild().forTasks('a').start();
    build.get();

    BuildFuture<CustomModel> action = connection.action(new MyAction()).start();
    CustomModel m = action.get();

### Implementation

The overall plan is to start close to the client and gradually move the asynchronous execution and cancellation closer
to the build:

Use futures to represent the existing asynchronous behaviour:

1. Change internal class `BlockingResultHandler` to implement `BuildInvocation` and reuse this type to implement the futures.
2. Implementation should discard handlers once they have been notified, so they can be garbage collected.

Push asynchronous execution down to the provider:

1. Change `AsyncConsumerActionExecutor.run()` to return a future (not necessarily a `Future`) instead of accepting a
   result handler.
2. Rework `DefaultAsyncConsumerActionExecutor` and `LazyConsumerActionExecutor` into a lazy `AsyncConsumerActionExecutor`
   implementation that composes two asynchronous operations into a single operation, represented by a composite future. The
   first operation creates the real `AsyncConsumerActionExecutor` (possibly downloading the distribution) and the second dispatches
   the client action to this executor once it is available.
3. Add a new asynchronous protocol interface similar to `AsyncConsumerActionExecutor` that allows actions to be queued up for
   execution, returning a future representing the result.
4. For provider connections that implement this protocol interface, delegate to the provider to execute the action. For
   connections that do not, adapt the connection in the client.

Forward cancellation requests to the daemon:

1. When `Future.cancel()` is called by the client, close the daemon connection. The daemon will stop the build and exit.

For target versions that do not support cancellation, `Future.cancel()` always returns false.

### Test cases

- Client blocks until results available when using `get()`
- Client receives failure when using `get()` and operation fails
- Client receives timeout exception when blocking with timeout
- Client can cancel operation
    - Stops the operation for all target versions that support cancellation
    - Returns `false` for all older target versions
- Client is notified when result is available
- Client is notified when operation fails

## Story: Expose the IDE output directories

Add the appropriate properties to the IDEA and Eclipse models.

## Story: Expose the project root directory

Add a `projectDir` property to `GradleProject`

### Test coverage

- Verify that a decent error message is received when using a Gradle version that does not expose the project directory

## Story: Expose the Java language level

Split out a `GradleJavaProject` model from `GradleProject` and expose this for Java projects.

Add the appropriate properties to the IDEA, Eclipse and GradleJavaProject models. For Eclipse, need to expose the appropriate
container and nature. For IDEA, need to choose between setting level on all modules vs setting level on project and inheriting.

## Story: Expose the Groovy language level

Split out a `GradleGroovyProject` model from `GradleProject` and expose this for Groovy projects.

Add the appropriate properties to the IDEA, Eclipse and GradleGroovyProject models.

## Story: Expose generated directories

It is useful for IDEs to know which directories are generated by the build. An initial approximation can be to expose
just the build directory and the `.gradle` directory. This can be improved later.

## Story: Add a convenience dependency for obtaining the tooling API JARs

Similar to `gradleApi()`

## Story: Add ability to launch tests in debug mode

Need to allow a debug port to be specified, as hard-coded port 5005 can conflict with IDEA.

# Open issues

* Replace `LongRunningOperation.standardOutput` and `standardError` with overloads that take a `Writer`, and (later) deprecate the `OutputStream` variants.
* Handle cancellation during the Gradle distribution download.
* Daemon cleanly stops the build when cancellation is requested.
* Test fixtures should stop daemons at end of test when custom user home dir is used.
* Introduce a `GradleExecutor` implementation backed by the tooling API.
