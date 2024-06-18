# scala-master

A generic scheduler for large scale cpg static analysis runs with a build in creation/provision of CPG for a provided set of php projects.
The goal is to ensure that if the analysis or cpg creation of a single project fails the other projects are still analyzed.
Furthermore, Scala-Master can start multiple parallel analysis/creation workers at the same resolving the timing and scheduling challenges of large runs.


# How-To Scala-Master

  Scala-Master is agnostic concerning the consumer, the program used to analyzed/process the CPG of a php project.
  However, it is important that the path to the consumer executable **does start the consumer process**, i.e., if you are
  using a bash script make use of [exec](https://www.putorius.net/exec-command.html), otherwise proper management of timeouts
  cannot be guaranteed.

  The overall idea is that the scala master, takes each (php) project of the input and first processes them into a CGP.
  If a `.cpg` file of the project already exists in the input folder, the already existing CPG is used to save on the creation time.
  The next step is to start the consumer with the CPG file as well as a path to the output **FILE** and then wait until it is done.
  Each step (CPG creation, consumer) can be limited via a timeout.

  Scala-Master expects the consumer to have two variable input parameter.
  One parameter is the `.cpg` file containing the to be analyzed CPG.
  The other parameter is a **FILE** path where the consumer is storing its results.
  Either parameter is to be provided in the configuration file at `scama.consumer.parameter` using `{cpg}` and `{out}` respectively.
  `{cpg}` is replaced by the path to the CPG file.
  `{out}` is replaced by the path to the output file.

  Scala-Master has a build in Telegram bot to share vegan cooking recipes and give status updates concerning the current execution state.

  Even though the Scala-Master can be used outside of docker it is designed around docker usage and expandability.
  Consequently, every configuration besides the path to the config file itself is done inside the configuration file.

  Use `--list` to provide a file containing the folder names of allow listed repos. 
## Configuration

See [example.conf](example.conf).

### Modes of operation
1. Default: Input: PHP-src -> php-cpg -> consumer result
   - Might reuse existing cpgs
2. If `noGeneration: true`: Input: php-cpg files -> consumer result

# How-To Docker

   1. we require/build the `multilayer-php-cpg` docker image (build configurations are provided by the corresponding repository)
   2. we now need to create the template docker file using `cd /resources/docker/template/ && ./create.sh`
       - this creates the docker image `scala-master` based on `multilayer-cpg-php:latest`
       - the created container has the directories `/in/`,`/out/scama/`, and `/out/cpg/` and the installed config file `main.conf` accounts for this and has those folders appropriately pre-set as well as the cpg dependencies
   3. the scala-master container can be tested using `./resources/docker/template/run.sh <projectFolder> <outFolderCpg> <outFolderConsumer> run`. This will create a php cpg for every subfolder in `<projectFolder>` and store them at `<outFolderCpg>`. The default action for the container is `true` which does not do anything and always returns properly, consequently there will be no output in `<outFolderConsumer>`. Every folder can be different or the same, depending on personal preferences.
      1. the paths have to be absolute
        
## Custom Scala-Master

   To expand/use the scala-master via docker you need to create your own custom derived docker image `from scala-master:latest ...`. You then clone, build, install the consumer you want to use and update the consumer and telegram configuration at `/scala-master/main.conf` in the container accordingly (ref. `Configuration`). Do not update the `ENTRYPOINT`. You can now `./run.sh` the container using the `./resources/docker/template/run.sh` script as described initially in this chapter.
