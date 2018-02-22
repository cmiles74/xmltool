(ns windsorsolutions.xmltool.xmltool
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :as sling]
   [clojure.walk :as walk]
   [clojure.string :as cstring]
   [clojure.core.async :as async]
   [windsorsolutions.xmltool.xml :as xml]
   [windsorsolutions.xmltool.jfx :as jfx]
   [clojure.java.io :as io]))


(defprotocol tree-node-protocol
  "Protocol all of our tree nodes must implement."
  (getName [node])
  (getValue [node]))

;; provides a record representing a tree node
(defrecord tree-node [keyname value]
  tree-node-protocol
  (getName [this] keyname)
  (getValue [this] value))

(defn queue-message
  "Adds a message map to the provided message queue."
  [msg-q msg-map]
  (async/>!! msg-q msg-map))

(defn queue-info-message
  "Adds an info message to the provided queue, the message will contain the
  provided text."
  [msg-q text]
  (queue-message msg-q {:text text}))

(defn queue-error-message
  "Adds an error message to the provided queue, the message will contain the
  provided text."
  [msg-q text]
  (queue-message msg-q {:text text :type :error}))

(defn queue-tree-message
  "Adds a tree building progress message to the provided queue."
  [msg-q nodes-added children-count]
  (queue-message msg-q {:type :tree-progress
                        :nodes-added nodes-added
                        :children-count children-count}))

(defn queue-complete-message
  "Adds an 'processing complete' message to the provided queue, the message will
  contain the provided text."
  [msg-q text]
  (queue-message msg-q {:text text :type :complete}))

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
  parent tree item. If a message queue ('msg-q') is provided, progress messages
  will be provided while the tree is being constructed."
  [parent xml-node  & {:keys [msg-q]}]

  ;; increment our count of tree nodes
  (if msg-q (queue-tree-message msg-q 1 0))

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
        (build-tree-node tree-node (:content xml-node) :msg-q msg-q))

      ;; node content is a vector
      (vector? (:content xml-node))
      (let [tree-node (build-tree-item parent (:tag xml-node) nil xml-node)]
        (if msg-q (queue-tree-message msg-q 0 (count (:content xml-node))))
        (future (doall (map #(build-tree-node tree-node %1 :msg-q msg-q) (:content xml-node))))))

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

(defn catch-non-fatal-xml-parse-errors
  "Wraps a function that may through XML parsing errors in an error handler that
  will catch and log all non-fatal exceptions to the provided message queue."
  [msg-q work-fn]
  (sling/try+ (work-fn)
              (catch #(not= :fatal (:type %1)) exception
                (queue-error-message msg-q
                                     (str "Error of type \"" (name (:type exception))
                                          "\" encountered while parsing line "
                                          (:line exception) " column " (:column exception) ": "
                                          (.getMessage (:exception exception)))))
              (catch Exception exception
                (queue-error-message msg-q (.getMessage exception)))))

(defn parse-xml-data
  "Begins parsing the data from the File ('file') and populates the provided
  'tree-table' with nodes created from that data. As parsing progresses,
  messages are posted to the provided message queue ('info-q')."
  [tree-table file info-q]
  (sling/try+
   (queue-info-message info-q (str "Loading the file at " (.getAbsolutePath file) "..."))
   (catch-non-fatal-xml-parse-errors
    info-q #(build-tree-node (:root tree-table) (xml/parse-xml file) :msg-q info-q))
   (catch #(= :fatal (:type %1)) exception
       (warn (str (:exception exception)))
       (queue-error-message info-q
                            (str "Fatal error encountered while parsing line "
                                 (:line exception) " column " (:column exception) ": "
                                 (.getMessage (:exception exception))))

       ;; strip out the bad characters
       (sling/try+
        (queue-info-message info-q (str "Attempting to scrub bad characters from the XML data"))
        (catch-non-fatal-xml-parse-errors
         info-q #(build-tree-node (:root tree-table) (xml/parse-xml (xml/clean-xml file)) :msg-q info-q))

        ;; well, now we know we really can't parse this file :-(
        (catch #(= :fatal (:type %1)) exception
          (warn (str (:exception exception)))
          (queue-error-message info-q
                               (str "Fatal error encountered while parsing line "
                                    (:line exception) " column " (:column exception) ": "
                                    (.getMessage (:exception exception)))))))))

(defn start-xml-parsing
  "Begins parsing the XML data provided by 'xml-file-path' and, as that data is
  processed, creates nodes and adds them to the provided
  TreeTable ('tree-table') instance. As the nodes are created, a count is kept
  via the 'node-count-atom' as well as an estimation of the nodes that will need
  to be created ('children-count-atom'). As the XML file is parsed and the tree
  created, messages about the progress of this process are posted to the
  provided message queue ('msg-q'). Once all of the data has been processed, a
  final messages with the type :complete is posted to the message queue."
  [tree-table xml-file-path node-count-atom children-count-atom msg-q]

  ;; start parsing our data and building the tree
  (parse-xml-data tree-table xml-file-path msg-q)

  ;; loop the see if we've completed building our tree
  (async/go
    (loop [last-incoming-count -1]

      ;; we don't need to in a tight loop
      (async/<! (async/timeout 500))

      ;; when our incoming node count stops changing, processing is complete
      (if (not= last-incoming-count (- (inc @children-count-atom) @node-count-atom))
        (recur (- (inc @children-count-atom) @node-count-atom))))

    ;; post completion message to the queue
    (if (= 0 @children-count-atom)
      (queue-complete-message msg-q (str "Document could not be parsed!"))
      (queue-complete-message msg-q
                              (str "Document parsed with " (inc @children-count-atom) " nodes")))))

(defn new-window
  "Creates a new XMLTool window, begins parsing the provided XML file and makes
  the window visible. Returns a map with information about the window."
  [xml-file-path]

  ;; create our table and reference to hold our window
  (let [info-q (async/chan (async/buffer 500) nil #(warn %1))
        tree-table (jfx/tree-table
                    (tree-node. "Root" nil)
                    (list
                     (jfx/tree-table-column
                      "Name" 250 (tree-node-cell-renderer #(.getName %1)))
                     (jfx/tree-table-column
                      "Value"428 (tree-node-cell-renderer #(.getValue %1))))
                    :root-visible false
                    :root-expanded true)
        window-atom (atom nil)
        node-count (atom 0)
        children-count (atom 0)]

    ;; show our window and set our reference
    (jfx/run
      (let [info-panel (jfx/text-pane)
            split-pane (jfx/split-pane
                        [(:object tree-table) (jfx/scroll-pane info-panel :fit-to-width true)]
                        :orientation :vertical)
            window (jfx/window
                    :title "XMLTool" :width 700 :height 900
                    :icon (jfx/image "rocket-32.png")
                    :exit-on-close true
                    :scene (jfx/scene
                            (jfx/border-pane :center split-pane
                                             :insets (jfx/insets 10 10 10 10 ))))]

        ;; loop to handle update messages
        (async/go-loop [message (async/<! info-q)]
          (when message
            (cond

              ;; display the text message
              (:text message)
              (jfx/run (jfx/add-text info-panel message))

              ;; update our progress counts
              (= :tree-progress (:type message))
              (do
                (swap! node-count #(+ %1 (:nodes-added message)))
                (swap! children-count #(+ %1 (:children-count message)))))
            (recur (async/<! info-q))))

        ;; display our window
        (jfx/show-window window)
        (jfx/set-split-pane-divider-positions split-pane [0 0.9])

        ;; update our reference with our items
        (reset! window-atom window)))

    (if xml-file-path

      ;; build the tree of XML data
      (future
        (start-xml-parsing tree-table xml-file-path node-count children-count info-q))

      ;; prompt for a file
      (jfx/run
        (jfx/open-file @window-atom
                       #(if %1
                          (future (start-xml-parsing tree-table %1 node-count children-count info-q))
                          (jfx/close-window @window-atom))
                       :title "Select an XML File to Inspect"
                       :filters (jfx/file-chooser-extension-filter "XML Files" "*.xml"))))

     window-atom))

(defn xml-tool
  "Creates a new XMLTool instance. This is the main entry point for starting the
  application user interface."
  ([]
   (new-window nil))
  ([file-path]
   (new-window file-path)))
