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
  :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
  :jlink-modules ["javafx.base" "javafx.controls" "javafx.fxml" "javafx.graphics"
                  "javafx.media""javafx.swing" "javafx.web" "java.sql"]
  :main windsorsolutions.xmltool.main
  :resource-paths ["resources"]
  :dist-target "dist"
  :aliases {"run" ["do" "jlink" "init," "run"]
            "clean" ["do" "jlink" "clean," "clean"]
            "repl" ["do" "jlink" "init," "repl"]
            "update-windows64-exe" ["shell" "bin/rcedit-x64.exe"
                                    "${:dist-target}/${:dist-platform}/xmltool.exe"
                                    "--set-icon" "resources/rocket.ico"]
            "build-exe" ["shell" "${:java-cmd}" "-jar" "bin/Packr.jar"
                         "--platform" "${:dist-platform}"
                         "--jdk" "${:jlink-image-path}"
                         "--executable" "xmltool"
                         "--classpath" "target/xmltool-*-standalone.jar"
                         "--mainclass" "${:main}"
                         "--output" "${:dist-target}/${:dist-platform}"]
            "build-image" ["do" "clean," "jlink" "assemble"]
            "build-dist" ["with-profile" "windows64" "do"
                          "build-image,"
                          "build-exe,"
                          "update-windows64-exe"]}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]
                   :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]}
             :macos  {:dist-platform "macos64"
                      :jlink-image-path "dist/image/macos64"
                      :jlink-archive-name "dist/macos-xmltool"}
             :linux64 {:dist-platform "linux64"
                       :jlink-image-path "dist/image/linux64"
                       :jlink-archive-name "dist/linux64-xmltool"}
             :windows64 {:dist-platform "windows64"
                         :jlink-image-path "dist/image/windows64"
                         :jlink-archive-name "dist/windows64-xmltool"
                         :jlink-archive "zip"}})
