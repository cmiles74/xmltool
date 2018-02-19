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

## Where Can I Get It?

The most recent build is [available
here](http://git-east.windsor.com/cmiles/xmltool/-/jobs/5/artifacts/download?job=deploy).
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
