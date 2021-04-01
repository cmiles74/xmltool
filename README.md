# XML Tools

![Continuous Integration](https://github.com/cmiles74/xmltool/workflows/Continuous%20Integration/badge.svg)

We were looking for a graphical tool that would let you browse over the contents
of an XML file at the office and the only one that anyone knew by name was called
["XMLSpy" from Altova][0]. People who used it liked it, but the [cost was way too
expensive][1] for the casual XML consumer. From that need, "XML Tool" was born.

If you find this code useful in any way, please feel free to...

<a href="https://www.buymeacoffee.com/cmiles74" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>

## What Does it Do?

When you launch the application, the XML Tool window will appear. From there,
clock on the "File" menu and choose "Open..." to browse to the XML file you'd
like to inspect. XML Tool will parse through your file and display a nifty tree
that you may use to browse through your data.

![Screenshot](https://raw.githubusercontent.com/cmiles74/xmltool/release/documentation/screenshot.png)

It will also try to strip out any invalid characters should it come across them.
If it does, it will log that fact in the "Console" tab, accessible from the top
of the window. 

This application isn't really finished: you still can't search for text in your
document, for instance. When it scans for bad characters, there's really only
the one character that it's checking for right now. But I'll continue to work on
it as I find time and as people report bugs or requests for improvements.

## Where Can I Get It?

The most recent build is [available on our release page](https://github.com/cmiles74/xmltool/releases). `:-D` It's
distributed as platform-specific bundles, mostly because of the way the Java
module system works and our dependency on JavaFX. In any case, you can download
the package for your platform and then run it by double-clicking the provided
launcher.

## Development

This project is written in [Clojure][2] (which leverages [Java][3]) and uses
[JavaFX][4] to provide the user interface components. If you're not fond of
Clojure or Java, you can blame [Miles][5]: he seems to actually _enjoy_ using
Clojure and no matter how many times he says "never again" to Java, he just
keeps coming back for more punishment.

At this time this project requires you to be running on a recent version of Java
to do any development work on the project. So, if you have that set, download
the "jmods" file for your platform and version of Java from the [Gluon][12]
website. You will want to unpack that archive and place it somewhere permanent
on your machine and note the path. Edit the `project.clj` file provided with
this project and set the `:jlink-modules-paths` key to point to the path were
you unpacked the archive.

### The Development Environment

Things will vary from tool to tool, but the simplest development environment is
simply asking Leiningen build a custom Java environment that includes JavaFX...

    $ lein build-image

The you can have Lein gather the dependencies and present you with an
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

### Building the Application

This project uses [Leiningen][6] to manage the project, the installation
instructions are super easy and are listed on their web page. You man also use a
Docker image, the "docker" directory contains script to start up a new image.
`;-)`

Navigate to the project folder and then tell Leiningen to build you a custom
Java runtime and an "uberjar", that's a Java environment with all of the
required modules and platform specific code and a launch-able JAR archive that
includes all of the library dependencies.

    $ lein with-profile YOUR_PLATFORM build-dist

This will build the distribution for your platform. Right now your options are
"linux64", "macos" or "windows64". The "64" indicates that a 64-bit native
executable will be built.

In the "dist" folder there will be a folder named after your platform, inside of
that directory will be the custom JRE for the project, the standalone "uberjar"
for the project, a native launcher and a small configuration file for that
native launcher.

If you're just looking to run the application you can always just have Leiningen
start it for you with...

     $ lein run
    
### Building for Other Platforms

The way the build works is that we take the provided JDK (usually the one in
your `JAVA_HOME` directory) and that's used to build the custom Java runtime
image. This works great, but if you think about it a minute, you'll see the
problem. That's right: the Java runtime comes with a bunch of platform specific
code!

There's not really any good solution for this, the `jlink` tool for another
platform won't run and when you run it on your platform it assumes that your
platform is the target. To build for other platforms you'd need to run under a
virtual machine or actually on the other platform. This project uses GitHub
actions for building the project, when you push to a branch GitHub will build
distributions for each supported platform.

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
[10]: https://github.com/electron/rcedit
[11]: https://github.com/electron/rcedit/blob/master/LICENSE
[12]: https://gluonhq.com/products/javafx/

