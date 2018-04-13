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
   [java.io ByteArrayInputStream ByteArrayOutputStream DataOutputStream File
    InputStream PipedInputStream PipedOutputStream]
   [javax.xml.parsers DocumentBuilderFactory SAXParserFactory SAXParser]
   [javax.xml.transform OutputKeys TransformerFactory]
   [javax.xml.transform.dom DOMSource]
   [javax.xml.transform.stream StreamResult]
   [javax.xml.xpath XPath XPathFactory]
   [java.io OutputStreamWriter StringWriter]
   [org.xml.sax ErrorHandler]
   [clojure.lang XMLHandler]))

;; set of invalid XML characters that prevent parsing
(def BAD-CHARACTERS #{16})

(defn sax-parser-content-handler
  "Returns a content handler for a SAXParser that will log errors."
  [content-handler]
  (proxy [XMLHandler ErrorHandler][content-handler]
    (error [exception]
      (error exception)
      (sling/throw+ {:exception exception
                     :type :error
                     :id (.getSystemId exception)
                     :column (.getColumnNumber exception)
                     :line (.getLineNumber exception)}))
    (fatalError [exception]
      (fatal exception)
      (sling/throw+ {:exception exception
                     :type :fatal
                     :id (.getSystemId exception)
                     :column (.getColumnNumber exception)
                     :line (.getLineNumber exception)}))
    (warning [exception]
      (warn exception)
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

(defn clean-char-seq
  "Provides a sequence of characters from the provide File ('file') of text.
  Characters that aren't valid XML are filtered out of the sequence. The
  'char-codes' should be a set of int values that denote characters that should
  be filtered out."
  [file char-codes]
  (let [stream (io/input-stream file)
        step (fn step []
               (let [char-in (.read stream)]
                 (cond

                   ;; drop "data link escape" characters
                   ;;(= 16 (int char-in))
                   (some #{char-in} char-codes)
                   (lazy-seq (step))

                   ;; add all other characters to our sequence
                   (not= -1 char-in)
                   (cons (char char-in) (lazy-seq (step)))

                   ;; no more data, close the stream and exit
                   :else
                   (.close stream))))]
    (lazy-seq (step))))

(defn clean-xml
  "Returns an InputStream that has filtered out invalid XML characters."
  [file]
  (let [chars-in (clean-char-seq file BAD-CHARACTERS)
        piped-in (PipedInputStream.)
        piped-out (PipedOutputStream. piped-in)]
    (future
      (try
        (doseq [char-in chars-in] (.write piped-out (int char-in)))
        (catch Exception e (info e))
        (finally (try (.close piped-out)))))
    piped-in))

(defn strip-whitespace-str
  "Removes extraneous whitespace from the end of each line in the supplied
  'text' String."
  [text]
  (let [output (StringWriter.)
        reader (io/reader (ByteArrayInputStream. (.getBytes text)))]
    (try
      (doseq [line (line-seq reader)]
        (if line (.write output (.trim line))))
      (catch Exception e (info e))
      (finally (try
                 (.close output)
                 (.flush output))))
    (.toString output)))

(defn write-xml-str
  "Accepts a tree of parsed XML data and returns a string representation."
  [xml-data]
  (with-out-str (xml/emit xml-data)))

(defn transformer-factory
  "Returns a TransformerFactory that will indent by two spaces."
  []
  (let [factory (TransformerFactory/newInstance)]
    (.setAttribute factory "indent-number" (Integer. 2))
    factory))

(defn pretty-xml-out
  [xml-data raw-data]
  (try
    (let [xml-str (strip-whitespace-str (write-xml-str xml-data))
          factory (DocumentBuilderFactory/newInstance)
          builder (.newDocumentBuilder factory)
          document (.parse builder (ByteArrayInputStream. (.getBytes xml-str)))
          transformer (.newTransformer (transformer-factory))
          output-stream (ByteArrayOutputStream.)]

      ;; format the document
      (.setOutputProperty transformer OutputKeys/INDENT "yes")
      (.setOutputProperty transformer OutputKeys/ENCODING "UTF-8")
      (.transform transformer
                  (DOMSource. document)
                  (StreamResult. (OutputStreamWriter. output-stream "utf-8")))
      (.toString output-stream))
    (catch Exception exception
      (timbre/warn "Couldn't parse the parsed XML data! " exception
                   "... Falling back to raw data")
      raw-data)))

(defmulti parse-xml
  "Parses an XML file and returns a map of it's data."
  class)

(defmethod parse-xml InputStream [stream]
  (xml/parse stream startparse-sax-non-validating))

(defmethod parse-xml File [file]
  (xml/parse (io/input-stream file) startparse-sax-non-validating))

(defmethod parse-xml String [text]
  (xml/parse (io/input-stream (.getBytes text)) startparse-sax-non-validating))
