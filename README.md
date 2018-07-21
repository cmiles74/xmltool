# XML Tools

We were looking for a graphical tool that would let you browse over the contents
of an XML file at the office and the only one that anyone new by name was called
["XMLSpy" from Altova][0]. People who used it liked it, but the [cost was way too
expensive][1] for the casual XML consumer. From that need, "XML Tool" was born.

## What Does it Do?

When you launch the application, XML Tool will provide a file choosing dialog
that you can use to browse to your XML file. Once you have selected a file it
will parse through it and display a nifty tree that you may used to browse
through your data.

![Screenshot](http://git-east.windsor.com/cmiles/xmltool/raw/master/documentation/screenshot.png)

It will also try to strip out any invalid characters should it come across them.
If it does, it will log that fact in the output panel at the bottom of the
window. 

This application isn't really finished, you still can't search for text in your
document, for instance. When it scans for bad characters, there's really only
the one character that it's checking for right now. But I'll continue to work on
it as I find time and as people report bugs or requests for improvements.

## Where Can I Get It?

The most recent build is [available
here](http://git-east.windsor.com/cmiles/xmltool/-/jobs/artifacts/master/download?job=build).
`:-D`

## Development

This project is written in [Clojure][2] and leverages [Java][3] and uses
[JavaFX][4] to provide the user interface components. If you're not fond of
Clojure or Java, you can blame [Miles][5]: he seems to actually _enjoy_ using
Clojure and no matter times he says "never again" to Java, he just keeps coming
back for more punishment.

If you are going to work on the project, you will need to have Java 1.8 or
greater installed along with the matching version of JavaFX. Most Linux
distributions will have packages for both but the majority only install Java by
default, you often need to install the JavaFX package separately.

### Building the Application

This project used [Leiningen][6] to manage the project, the installation
instructions are super easy and listed on their web page. If you want to build
the Windows executable file, you'll also need to install [Launch4J][7].

Once you have the pre-requisites installed building the project is very easy.
Navigate to the project folder and then tell Leiningen to build you an
"uberjar", that's one JAR archive that includes all of the dependencies.

    $ lein uberjar
    
Leiningen will download all of the dependencies, compile the code and then
bundle everything up into one executable JAR archive. Running the application is
just as easy.

    $ java -jar target/*standalone.jar
    
### Building the Windows Executable

The Windows executable is handy because it include that uberjar and wraps it in
a launcher that anyone on Windows can use. It's important to remember, however,
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
[5]: http://git-east.windsor.com/cmiles
[6]: https://leiningen.org/
[7]: http://launch4j.sourceforge.net/
[8]: https://bokehlicia.deviantart.com
[9]: https://en.wikipedia.org/wiki/GNU_General_Public_License
