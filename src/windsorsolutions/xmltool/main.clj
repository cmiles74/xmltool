(ns ^{:doc "Bootstraps the XMLTool application"}
    windsorsolutions.xmltool.main
  (:gen-class)
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [taoensso.timbre.appenders.core :as appenders]
   [slingshot.slingshot :only [throw+ try+]]
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
