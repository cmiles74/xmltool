(defproject windsorsolutions/xmltool "1.0"
  :description "A GUI tool for managing and visualizing XML data"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.1.587"]
                 [org.clojure/tools.cli "1.0.194"]
                 [com.taoensso/timbre "4.10.0"]
                 [slingshot "0.12.2"]
                 [org.fxmisc.richtext/richtextfx "0.10.5"]]
  :plugins [[lein-jlink "0.3.0"]
            [lein-shell "0.5.0"]]
  :middleware [leiningen.jlink/middleware]
  :jlink-module-paths ["C:\\Program Files\\AdoptOpenJDK\\javafx-jmods-11.0.2"]
  :jlink-modules ["javafx.base" "javafx.controls" "javafx.fxml" "javafx.graphics"
                  "javafx.media""javafx.swing" "javafx.web" "java.sql"]
  :main windsorsolutions.xmltool.main
  :resource-paths ["resources"]
  :dist-target "dist"
  :dist-version "1.0"
  :aliases {"run" ["do" "jlink" "init," "run"]
            "clean" ["do" "jlink" "clean," "clean"]
            "repl" ["do" "jlink" "init," "repl"]
            "update-win-exe" ["shell" "bin/rcedit-x64.exe"
                                    "${:dist-target}/${:dist-platform}/xmltool.exe"
                                    "--set-icon" "resources/rocket.ico"]
            "build-image" ["do" "clean," "jlink" "assemble"]
            "build-mac-exe" ["shell" "${:java-cmd}" "-jar" "bin/Packr.jar"
                         "--platform" "${:dist-platform}"
                         "--jdk" "${:jlink-image-path}"
                         "--icon" "resources/rocket.icns"
                         "--bundle" "com.windsorsolutions.xmltool"
                         "--executable" "xmltool"
                         "--classpath" "target/xmltool-${:dist-version}-standalone.jar"
                         "--mainclass" "${:main}"
                         "--output" "${:dist-target}/${:dist-platform}/xmltool.app"]
            "build-exe" ["shell" "${:java-cmd}" "-jar" "bin/Packr.jar"
                         "--platform" "${:dist-platform}"
                         "--jdk" "${:jlink-image-path}"
                         "--executable" "xmltool"
                         "--classpath" "target/xmltool-${:dist-version}-standalone.jar"
                         "--mainclass" "${:main}"
                         "--output" "${:dist-target}/${:dist-platform}"]
            "build-dist" ["do" "build-image," "build-exe"]}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]
                   :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]}
             :linux64 {:dist-platform "linux64"
                       :jlink-image-path "dist/image/linux64"}
             :mac {:dist-platform "mac"
                   :jlink-image-path "dist/image/mac"
                   :aliases {"build-dist" ["do" "build-image,"
                                           "build-mac-exe"]}}
             :windows64 {:dist-platform "windows64"
                         :jlink-image-path "dist/image/windows64"
                         :aliases {"build-dist" ["do" "build-image,"
                                                 "build-exe,"
                                                 "update-win-exe"]}}})
