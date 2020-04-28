(defproject windsorsolutions/xmltool "1.0"
  :description "A GUI tool for managing and visualizing XML data"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.1.587"]
                 [org.clojure/tools.cli "1.0.194"]
                 [com.taoensso/timbre "4.10.0"]
                 [slingshot "0.12.2"]
                 [org.fxmisc.richtext/richtextfx "0.10.5"]]
  :plugins [[cmiles74/lein-jlink "0.2.2-SNAPSHOT"]
            [lein-shell "0.5.0"]]
  :middleware [leiningen.jlink/middleware]
  :jlink-jre-image true
  :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
  :jlink-modules ["javafx.base" "javafx.controls" "javafx.fxml" "javafx.graphics"
                  "javafx.media""javafx.swing" "javafx.web" "java.sql"]
  :main windsorsolutions.xmltool.main
  :resource-paths ["resources"]
  :aliases {"run" ["do" "jlink" "init," "run"]
            "clean" ["do" "jlink" "clean," "clean"]
            "repl" ["do" "jlink" "init," "repl"]
            "build-windows64-exe" ["shell" "${:java-cmd}" "-jar" "bin/Packr.jar"
                                   "--platform" "windows64"
                                   "--jdk" "${:jlink-jre-image-path}"
                                   "--executable" "xmltool"
                                   "--classpath" "target/xmltool-*-standalone.jar"
                                   "--mainclass" "${:main}"
                                   "--output" "dist/windows64"]
            "update-windows64-exe" ["shell" "bin/rcedit-x64.exe"
                                    "dist/windows64/xmltool.exe"
                                    "--set-icon" "resources/rocket.ico"]
            "build-windows64-image" ["do" "clean," "jlink" "assemble"]
            "build-windows64" ["with-profile" "windows64" "do"
                               "build-windows64-image,"
                               "build-windows64-exe,"
                               "update-windows64-exe"]}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]}
             :windows64 {:jlink-jre-image-path "dist/image/windows64"
                         :jlink-jdk-path "C:\\Program Files\\Java\\jdk-14.0.1"}})
