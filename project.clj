(defproject windsorsolutions/xmltool "1.0"
  :description "A GUI tool for managing and visualizing XML data"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/core.async "0.4.474"]
                 [com.taoensso/timbre "4.10.0"]
                 [slingshot "0.12.2"]
                 [org.fxmisc.richtext/richtextfx "0.8.2"]]
  :main windsorsolutions.xmltool.main
  :launch4j-config-file "dev-resources/launch4j-config.xml"
  :resource-paths ["resources"]
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]}})
