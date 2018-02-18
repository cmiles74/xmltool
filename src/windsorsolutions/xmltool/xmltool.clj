(ns windsorsolutions.xmltool.xmltool
  (:require
   [clojure.java.io :as io]
   [clojure.xml :as xml]
   [clojure.walk :as walk]
   [clojure.string :as cstring]
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :only [throw+ try+]]
   [windsorsolutions.xmltool.jfx :as jfx])
  (:import
   [javax.xml.parsers SAXParserFactory SAXParser]
   [org.xml.sax ErrorHandler]
   [clojure.lang XMLHandler]))

(defn sax-parser-content-handler
  "Returns a content handler for a SAXParser that will log errors."
  [content-handler]
  (proxy [XMLHandler ErrorHandler][content-handler]
    (error [exception] (warn exception))
    (fatalError [exception] (warn exception))
    (warning [exception] (warn exception))))

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

(defprotocol tree-node-protocol
  "Protocol all of our tree nodes must implement."
  (getName [node])
  (getValue [node]))

;; provides a record representing a tree node
(defrecord tree-node [keyname value]
  tree-node-protocol
  (getName [this] keyname)
  (getValue [this] value))

(defn build-tree-attr
  "Builds a sub-tree of attribute values and adds them to the tree node."
  [parent attrs]
  (if (not (nil? attrs))
    (let [attr-node (jfx/tree-item (tree-node. "Attributes" nil) parent)]
      (jfx/add-leaves
       attr-node
       (map #(jfx/tree-item (tree-node. (name (key %1)) (val %1))) attrs)))))

(defn build-tree-item
  "Builds a new tree item with the provided key and value and then adds it to
  the provided parent tree item. We use the 'xml-node' to populate the
  'Attributes' sub-tree for the new item."
  [parent keyname value xml-node]
  (let [content (if (nil? value) nil (cstring/trim value))
        tree-item (jfx/tree-item (tree-node. (name keyname) content)
                                 parent)]
    (build-tree-attr tree-item (:attrs xml-node))
    tree-item))

(defn build-tree-node
  "Builds a tree node for the provided node of XML data and adds it to the
  parent tree item."
  [parent xml-node]
  (if

    ;; our xml-node is in fact an xml-node
    (and (contains? xml-node :tag) (contains? xml-node :attrs) (contains? xml-node :content))
    (cond

      ;; node has nil or one string of content
      (or (nil? (:content xml-node)) (and (= 1 (count (:content xml-node)))
                                          (string? (first (:content xml-node)))))
      (let [content (if (nil? (:content xml-node)) nil (first (:content xml-node)))]
        (build-tree-item parent (:tag xml-node) content xml-node))

      ;; node content is a single map
      (map? (:content xml-node))
      (let [tree-node (build-tree-item parent (:tag xml-node) nil xml-node)]
        (build-tree-node tree-node (:content xml-node)))

      ;; node content is a vector
      (vector? (:content xml-node))
      (let [tree-node (build-tree-item parent (:tag xml-node) nil xml-node)]
        (future (doall (map #(build-tree-node tree-node %1) (:content xml-node))))))

    ;; we don't know what this node is!
    (warn "Can't build node from " (class xml-node))))

(defn tree-node-cell-renderer
  "Provides a renderer for a tree table column that will apply the provided
  'value-fn' to the backing data object of the row's cell."
  [value-fn]
  (partial jfx/string-wrapper-ro
     #(if (instance? tree-node %1)
        (value-fn %1)
        (str %1))))

(defn new-window
  "Creates a new XMLTool window, begins parsing the provided XML file and makes
  the window visible. Returns a map with information about the window."
  [xml-file-path]

  ;; don't exit when the window closes
  (jfx/implicit-exit false)

  ;; create our table and reference to hold our window
  (let [tree-table (jfx/tree-table
                    (tree-node. "Root" nil)
                    (list
                     (jfx/tree-table-column
                      "Name" 250 (tree-node-cell-renderer #(.getName %1)))
                     (jfx/tree-table-column
                      "Value"428 (tree-node-cell-renderer #(.getValue %1))))
                    :root-visible false
                    :root-expanded true)
        window-ref (ref nil)]

    ;; build the tree of XML data
    (future
      (build-tree-node (:root tree-table) (parse-xml xml-file-path)))

    ;; show our window and set our reference
    (jfx/run
      (let [window (jfx/window
                    :title "XMLTool" :width 700 :height 900
                    :scene (jfx/scene
                            (jfx/border-pane :center (:object tree-table)
                                             :insets (jfx/insets 10 10 10 10 ))))]
            (jfx/show-window window)
            (dosync (ref-set window-ref window))))

    window-ref))

(defn close-window
  "Closes the provided window."
  [window-ref]
  (jfx/close-window @window-ref))

(defn test-window
  "Creates a new test window."
  []
  (new-window "sample-data/ProcessingReport.xml"))

(defn test-window-big
  []
  (new-window "sample-data/mondial-3.0.xml"))

(defn test-window-really-big
  []
  (new-window "sample-data/sample.xml"))

(defn test-window-bad
  []
  (new-window "sample-data/bad-char-file.xml"))

