(defproject windsorsolutions/xmltool "1.0"
  :description "A GUI tool for managing and visualizing XML data"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.1.587"]
                 [org.clojure/tools.cli "1.0.194"]
                 [com.taoensso/timbre "4.10.0"]
                 [slingshot "0.12.2"]
                 [org.fxmisc.richtext/richtextfx "0.10.5"]]
  :plugins [[cmiles74/lein-jlink "0.2.2-SNAPSHOT"]]
  :middleware [leiningen.jlink/middleware]
  :jlink-jdk-path "C:\\Program Files\\Java\\jdk-14.0.1"
  :jlink-jre-image true
  :jlink-jre-image-path "dist/windows64"
  :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
  :jlink-modules ["javafx.base" "javafx.controls" "javafx.fxml" "javafx.graphics"
                  "javafx.media""javafx.swing" "javafx.web" "java.sql"]
  :main windsorsolutions.xmltool.main
  :resource-paths ["resources"]
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]}})
