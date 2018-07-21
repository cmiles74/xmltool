# XML Tools

We were looking for a graphical tool that would let you browse over the contents
of an XML file at the office and the only one that anyone knew by name was called
["XMLSpy" from Altova][0]. People who used it liked it, but the [cost was way too
expensive][1] for the casual XML consumer. From that need, "XML Tool" was born.

## What Does it Do?

When you launch the application, the XML Tool window will appear. From there,
clock on the "File" menu and choose "Open..." to browse to the XML file you'd
like to inspect. XML Tool will parse through your file and display a nifty tree
that you may use to browse through your data.

![Screenshot](https://raw.githubusercontent.com/cmiles74/xmltool/master/documentation/screenshot.png)

It will also try to strip out any invalid characters should it come across them.
If it does, it will log that fact in the "Console" tab, accessible from the top
of the window. 

This application isn't really finished: you still can't search for text in your
document, for instance. When it scans for bad characters, there's really only
the one character that it's checking for right now. But I'll continue to work on
it as I find time and as people report bugs or requests for improvements.

## Where Can I Get It?

The most recent build is [available
here](https://github.com/cmiles74/xmltool/releases/tag/1.0). `:-D` It includes a
Java JAR that you may run as well as a double-clickable Windows executable. On
Windows you can double-click the "xmltool.exe" file. On MacOS or Linux, you
might be able to double click the JAR file. Everyone else will have to create
their own launcher, to start XML Tool from the command line you can type the
following:

    $ java -jar xmltool-1.0-standalone.jar

## Development

This project is written in [Clojure][2] (which leverages [Java][3]) and uses
[JavaFX][4] to provide the user interface components. If you're not fond of
Clojure or Java, you can blame [Miles][5]: he seems to actually _enjoy_ using
Clojure and no matter times he says "never again" to Java, he just keeps coming
back for more punishment.

If you are going to work on the project, you will need to have Java 1.8 or
greater installed along with the matching version of JavaFX. Most Linux
distributions will have packages for both but the majority only install Java by
default, you often need to install the JavaFX package separately.

### Building the Application

This project uses [Leiningen][6] to manage the project, the installation
instructions are super easy and are listed on their web page. If you want to
build the Windows executable file, you'll also need to install [Launch4J][7]. Or
you can use a Docker image, the "docker" directory contains script to start up a
new image. `;-)`

Once you have the pre-requisites installed building the project is very easy.
Navigate to the project folder and then tell Leiningen to build you an
"uberjar", that's one launch-able JAR archive that includes all of the
dependencies.

    $ lein uberjar
    
Leiningen will download all of the dependencies, compile the code and then
bundle everything up into one executable JAR archive. Running the application is
just as easy.

    $ java -jar target/*standalone.jar
    
### Building the Windows Executable

The Windows executable is handy because it takes that uberjar and wraps it in a
launcher that anyone on Windows can use. It's important to remember, however,
that Windows customers _need to have Java 1.8 installed_ to run the application.
In the Windows environment, JavaFX is bundled along with the Java installer.

You will need to add the Launch4J plugin to your personal Leiningen
configuration. This can be found on your machine in the `~/.lein/profiles.clj`
file. This file contains a Clojure map, add the following map under the `:user`
key in that file.

    {:plugins [[com.nervestaple/lein-launch4j "0.1.2"]]
     :launch4j-install-dir "/opt/launch4j"}
     
Note that the `:launch4j-install-dir` should point to wherever you have
installed Launch4J. `;-)`

With that complete, you can instruct Leiningen to build the Windows executable.

    $ lein launch4j

The executable will be in the `target` folder.

### The Development Environment

Things will vary from tool to tool, but the simplest development environment is
simply asking Leiningen to gather the dependencies and present you with an
interactive session.

    $ lein repl
    
From there, you can switch to the `user` namespace and initialize the
environment.

    windsorsolutions.xmltool.main=> (ns user)
    user=> (init)
    
This will initialize the JavaFX runtime and set some flags indicating that you
are in development mode. This will prevent your interactive session from exiting
every time you close the XML Tool window. From here you can work on the project.
To test your work, you may startup the application.

    user=> (xmltool/xml-tool)
    
That will start up a new instance and prompt you for an XML file to parse.

## The Icon

The icon for the application was created by [bokehlicia][8] is licensed under
the [GNU General Public License][9].

[0]: https://en.wikipedia.org/wiki/XMLSpy
[1]: https://shop.altova.com/XMLSpy
[2]: https://en.wikipedia.org/wiki/Clojure
[3]: https://en.wikipedia.org/wiki/Java_(programming_language)
[4]: https://en.wikipedia.org/wiki/JavaFX
[5]: https://github.com/cmiles74
[6]: https://leiningen.org/
[7]: http://launch4j.sourceforge.net/
[8]: https://bokehlicia.deviantart.com
[9]: https://en.wikipedia.org/wiki/GNU_General_Public_License
