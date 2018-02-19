(ns ^{:doc "Provides functions for manipulating XML data."}
    windsorsolutions.xmltool.xml
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :as sling]
   [clojure.java.io :as io]
   [clojure.xml :as xml]
   [clojure.walk :as walk]
   [clojure.string :as cstring])
  (:import
   [javax.xml.parsers SAXParserFactory SAXParser]
   [org.xml.sax ErrorHandler]
   [clojure.lang XMLHandler]))

(defn sax-parser-content-handler
  "Returns a content handler for a SAXParser that will log errors."
  [content-handler]
  (proxy [XMLHandler ErrorHandler][content-handler]
    (error [exception]
      (error exception)
      (info (class exception))
      (sling/throw+ {:exception exception
                     :type :error
                     :id (.getSystemId exception)
                     :column (.getColumnNumber exception)
                     :line (.getLineNumber exception)}))
    (fatalError [exception]
      (fatal exception)
      (info (class exception))
      (sling/throw+ {:exception exception
                     :type :fatal
                     :id (.getSystemId exception)
                     :column (.getColumnNumber exception)
                     :line (.getLineNumber exception)}))
    (warning [exception]
      (warn exception)
      (info (class exception))
      (sling/throw+ {:exception exception
                     :type :warn
                     :id (.getSystemId exception)
                     :column (.getColumnNumber exception)
                     :line (.getLineNumber exception)}))))

(defn startparse-sax-non-validating
  "Provides a SAX parser that will not attempt to validate the DTDs and will log
  any parsing errors."
  [source content-handler]
  (let [factory (SAXParserFactory/newInstance)]
    (.setFeature factory "http://apache.org/xml/features/nonvalidating/load-external-dtd" false)
    (let [parser (.newSAXParser factory)
          error-content-handler (sax-parser-content-handler content-handler)]
      (.parse parser source error-content-handler))))

(defn parse-xml
  "Returns a map of XML data derived from the XML file at the provided path"
  [file-path]
  (xml/parse (io/input-stream file-path) startparse-sax-non-validating))

(defn parse-xml-str
  "Returns a map of XML data derived from the provided String of XML data."
  [xml-str]
  (xml/parse (io/input-stream (.getBytes xml-str)) startparse-sax-non-validating))

(defn clean-xml
  "Attempts to clean the XML data at the provided 'file-path' and returns a
  string containing that cleaned data."
  [file-path]
  (let [cleaned-xml (cstring/replace
                     (slurp file-path)
                     #"[\u0010]+"
                     "")]
    (info (str "Text cleaned with " (count cleaned-xml) " characters"))
    cleaned-xml))
