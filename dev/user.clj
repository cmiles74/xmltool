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
   [clojure.core.async :as async]
   [windsorsolutions.xmltool.xml :as xml]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.xmltool :as xmltool]
   [windsorsolutions.xmltool.main :as boot]
   [throttler.core :refer [throttle-chan throttle-fn]])
  (:import
   [java.io File]
   [java.util Scanner]))

(defn init
  "Initializes the development environment."
  []

  ;; set our environment
  (defonce ENVIRONMENT :development)

  ;; initialize the JavaFX runtime
  (jfx/init))

(defn lazy-reader [file]
  "Returns a lazy reader that will read the contents of a file line-by-line"
  (let [reader (Scanner. file)]
    (letfn [(helper [rdr]
              (lazy-seq
               (if-let [line-next (.hasNextLine rdr)]
                 (let [line-in (.nextLine rdr)]
                   (if (not= -1 line-in)
                     (cons (str line-in "\n") (helper rdr))))
                 (do
                   (info (str "Closing reader on " file))
                   (.close rdr) nil))))]
      (helper reader))))


-
