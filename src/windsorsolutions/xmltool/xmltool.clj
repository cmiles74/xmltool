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
   [slingshot.slingshot :only [throw+ try+]])
  (:import
   [javax.xml.parsers SAXParserFactory SAXParser]
   [org.xml.sax ErrorHandler]
   [clojure.lang XMLHandler]
   [javafx.beans.property ReadOnlyStringWrapper]
   [javafx.application Application Platform]
   [javafx.scene Group Scene]
   [javafx.scene.control Label TreeTableView TreeTableColumn TreeView TreeItem]
   [javafx.scene.layout BorderPane VBox]
   [javafx.scene.text Font]
   [javafx.stage StageBuilder Stage]
   [javafx.geometry Insets]
   [javafx.util Callback]))

(defn sax-parser-content-handler
  [content-handler]
  (proxy [XMLHandler ErrorHandler][content-handler]
    (error [exception] (warn exception))
    (fatalError [exception] (warn exception))
    (warning [exception] (warn exception))))

(defn startparse-sax-non-validating
  "Provides a SAX parser that will not attempt to validate the DTDs."
  [source content-handler]
  (let [factory (SAXParserFactory/newInstance)]
    (.setFeature factory "http://apache.org/xml/features/nonvalidating/load-external-dtd" false)
    (let [parser (.newSAXParser factory)
          error-content-handler (sax-parser-content-handler content-handler)]
      (.parse parser source error-content-handler))))

(defn parse-xml
  "Returns a map of XML data at the provided path"
  [file-path]
  (xml/parse (io/input-stream file-path) startparse-sax-non-validating))

(defmacro jfx-run
  "Invokes the provided body in the context of the JavaFX application thread."
  [& body]
  `(if (Platform/isFxApplicationThread)
     (try ~@body
          (catch Exception exception#
            (timbre/warn "Exception in JFX Application thread: " exception#)
            (timbre/debug exception#)))
     (Platform/runLater (fn []
                          (try ~@body
                               (catch Exception exception#
                                 (timbre/warn "Exception in JFX Application thread: " exception#)
                                 (timbre/debug exception#)))))))

(defprotocol tree-node-protocol
  "Protocol all of our tree nodes must implement"
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
    (let [attr-node (TreeItem. (tree-node. "Attributes" nil))]
      (jfx-run
       (.add (.getChildren parent) attr-node))
      (jfx-run
       (.addAll (.getChildren attr-node)
                (map #(TreeItem. (tree-node. (name (key %1)) (val %1))) attrs))))))

(defn build-tree-item
  "Builds a new tree item with the provided key and value and then adds it to
  the provided parent tree item. We use the 'xml-node' to populate the
  'Attributes' sub-tree for the new item."
  [parent keyname value xml-node]
  (let [tree-item (TreeItem. (tree-node. (name keyname)
                                         (if (nil? value) nil (cstring/trim value))))]
    (jfx-run (.add (.getChildren parent) tree-item))
    (build-tree-attr tree-item (:attrs xml-node))
    tree-item))

(defn build-tree-node
  "Builds a tree node for the provided node of XML data and adds it to the
  parent tree item."
  [parent xml-node]
  (cond

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
    :else
    (warn "Can't build node from " (class xml-node))))

(defn tree-table-column
  "Creates a new table column with the provided name, whose width matches that
  provided and whose value will be populated by using the provided 'value-fn'."
  [name value-fn width]
  (let [column (TreeTableColumn. name)]
    (.setCellValueFactory
     column
     (reify Callback
       (call [this node]
         (let [node-item (.getValue (.getValue node))]
           (cond
             (instance? tree-node node-item)
             (ReadOnlyStringWrapper. (str (value-fn (.getValue (.getValue node)))))

             :else
             (ReadOnlyStringWrapper. (str tree-node)))))))
    (.setPrefWidth column width)
    column))

(defn new-window
  "Creates a new XMLTool window, begins parsing the provided XML file and makes
  the window visible. Returns a map with information about the window."
  [xml-file-path]
  (let [stage-ref (ref nil)]
    (jfx-run
     (Platform/setImplicitExit false)
     (let [stage (.build (StageBuilder/create))
           border-pane (new BorderPane)
           scene (new Scene border-pane)
           tree-root (new TreeItem (tree-node. "Root" nil))
           tree-table (new TreeTableView tree-root)]

       (.setAll (.getColumns tree-table)
                (list
                 (tree-table-column "Name" #(.getName %1) 250)
                 (tree-table-column "Value" #(.getValue %1) 700)))
       (.setShowRoot tree-table false)

       (.setExpanded tree-root true)

       (.setPadding border-pane (new Insets 10 10 10 10))
       (.setCenter border-pane tree-table)

       (.setScene stage scene)
       (.show stage)

       (dosync (ref-set stage-ref
                        {:stage stage
                         :root tree-root
                         :table tree-table}))

       (future
         (build-tree-node tree-root (parse-xml xml-file-path)))))
    stage-ref))

(defn test-window
  "Creates a new test window."
  []
  (new-window "ProcessingReport.xml"))

(defn test-window-bad
  "Creates a new test window."
  []
  (new-window "bad-char-file.xml"))

(defn close-window
  [stage-ref]
  (jfx-run
   (.close (:stage @stage-ref))))

