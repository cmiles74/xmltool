(ns ^{:doc "Bootstraps the XMLTool application"}
    windsorsolutions.xmltool.main
  (:gen-class)
  (:require
   [taoensso.timbre :as timbre
    :refer (info)]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.xmltool :as xmltool]))

(defn main
  "Bootstraps the application"
  [& args]

  ;; set up logging
  ;; (timbre/merge-config!
  ;;  {:appenders {:spit (appenders/spit-appender {:fname "xmltool.log"})}})
  (info "Called with arguments" args)

  ;; ensure that the JavaFX environment has been initialized
  (jfx/init)

  (info "Opening file" (last args))
  (xmltool/xml-tool (last args)))

(defn -main
  "Bootstraps the application"
  [& args]
  (apply main args))
