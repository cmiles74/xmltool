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
   [slingshot.slingshot :only [throw+ try+]]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.xmltool :as xmltool]))

(defn main
  "Bootstraps the application"
  [& args]

  ;; ensure that the JavaFX environment has been initialized
  (jfx/init)

  (println "Hello from XMLTool!")
  (xmltool/xml-tool (first args)))

(defn -main
  "Bootstraps the application"
  [& args]
  (apply main args))
