(defproject windsorsolutions/xmltool "1.0"
  :description "A GUI tool for managing and visualizing XML data"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.cli "0.3.5"]
                 [com.taoensso/timbre "4.10.0"]
                 [slingshot "0.12.2"]
                 [com.nervestaple/javafx "14.0.1"]
                 ;; [com.nervestaple/javafx-windows-x86-64-native-deps "14.0.1"]
                 [org.fxmisc.richtext/richtextfx "0.8.2"]]
  :main windsorsolutions.xmltool.main
  :launch4j-config-file "dev-resources/launch4j-config.xml"
  :middleware [leiningen.jlink/middleware]
  :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
  :jlink-modules ["javafx.base" "javafx.controls" "javafx.fxml" "javafx.graphics"
                  "javafx.media""javafx.swing" "javafx.web" "java.sql"]
  :resource-paths ["resources"]
  :plugins [[com.nervestaple/lein-jlink "0.2.2-SNAPSHOT"]]
  :alias {"build" ["do" ["jlink init"] ]}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]}})
