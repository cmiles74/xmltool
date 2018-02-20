(ns user
  (:use
   [clojure.repl])
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :only [throw+ try+]]
   [clojure.java.io :as io]
   [windsorsolutions.xmltool.xml :as xml]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.xmltool :as xmltool]
   [windsorsolutions.xmltool.main :as boot])
  (:import
   [java.io File]))

(defn init
  "Initializes the development environment."
  []

  ;; set our environment
  (defonce ENVIRONMENT :development)

  ;; initialize the JavaFX runtime
  (jfx/init))

