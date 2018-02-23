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

;;
;; Tree node to back our tree nodes
;;

(defprotocol tree-node-protocol
  "Protocol all of our tree nodes must implement."
  (getName [node])
  (getValue [node]))

;; provides a record representing a tree node
(defrecord tree-node [keyname value]
  tree-node-protocol
  (getName [this] keyname)
  (getValue [this] value))

(defn tree-node-cell-renderer
  "Provides a renderer for a tree table column that will apply the provided
  'value-fn' to the backing data object of the row's cell."
  [value-fn]
  (partial jfx/string-wrapper-ro
     #(if (instance? tree-node %1)
        (value-fn %1)
        (str %1))))

;;
;; Functions for posting messages to our queues
;;

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

;;
;; Functions for building the tree nodes
;;

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
  [parent xml-node & {:keys [msg-q]}]

  ;; add our node to the count
  (if msg-q (queue-tree-message msg-q 1 0))

  ;; our xml-node is in fact an xml-node
  (if (and (map? xml-node) (contains? xml-node :tag) (contains? xml-node :attrs)
           (contains? xml-node :content))
    (cond

      ;; node has nil or one string of content
      (or (nil? (:content xml-node)) (and (= 1 (count (:content xml-node)))
                                          (string? (first (:content xml-node)))))
      (let [content (if (nil? (:content xml-node)) nil (first (:content xml-node)))]
        (build-tree-item parent (:tag xml-node) content xml-node))

      ;; node content is a single map
      (map? (:content xml-node))
      (let [tree-node (build-tree-item parent (:tag xml-node) nil xml-node)]
        (if msg-q (queue-tree-message msg-q 1 1))
        (build-tree-node tree-node (:content xml-node) :msg-q msg-q))

      ;; node content is a vector
      (vector? (:content xml-node))
      (cond

        ;; if it's not a map, concatenate the content
        (not (map? (first (:content xml-node))))
        (let [content (apply str (interpose ", " (:content xml-node)))]
          (build-tree-item parent (:tag xml-node) content xml-node))


        ;; build nodes for the children
        :else
        (let [tree-node (build-tree-item parent (:tag xml-node) nil xml-node)]
          (if msg-q (queue-tree-message msg-q 0 (count (:content xml-node))))
          (doall (map #(build-tree-node tree-node %1 :msg-q msg-q) (:content xml-node))))))

    ;; we don't know what this node is, add a "junk" node
    (build-tree-item parent :recovered-content xml-node
                     {:attrs {:error "Content not attached to XML element"}})))

;;
;; Functions for parsing the XML data
;;

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
  [tree-table file info-q count-q]
  (sling/try+
   (queue-info-message info-q (str "Loading the file at " (.getAbsolutePath file) "..."))
   (catch-non-fatal-xml-parse-errors
    info-q #(build-tree-node (:root tree-table) (xml/parse-xml file) :msg-q count-q))
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
         info-q #(build-tree-node (:root tree-table) (xml/parse-xml (xml/clean-xml file)) :msg-q count-q))

        ;; well, now we know we really can't parse this file :-(
        (catch #(= :fatal (:type %1)) exception
          (warn (str (:exception exception)))
          (queue-error-message info-q
                               (str "Fatal error encountered while parsing line "
                                    (:line exception) " column " (:column exception) ": "
                                    (.getMessage (:exception exception)))))))))

;;
;; Functions to build UI components
;;

(defn status-panel
  "Creates a new status panel and returns a map containing the components with
  the following keys: :component, :progress-bar :progress-text :text-pane."
  []
  (let [text-pane (jfx/text-pane :insets (jfx/insets 5 5 5 5))
        progress-bar (jfx/progress-bar)
        progress-text (jfx/label)]
    {:component (jfx/vbox
                 [(jfx/vgrow-component
                   (jfx/scroll-pane text-pane :fit-to-width true :fit-to-height true)
                   :priority :always)
                  (jfx/hbox [progress-bar progress-text]
                            :spacing 8 :insets (jfx/insets 5 5 5 5))])
     :progress-bar progress-bar
     :progress-text progress-text
     :text-pane text-pane}))

(defn window-panel
  "Creates a new panel for the main window and returns a map containing the
  components with the following keys: :info-panel, :tree-table, :split-pane
  and :component, which is a component containing them all."
  []
  (let [info-panel (status-panel)
        tree-table (jfx/tree-table
                    (tree-node. "Root" nil)
                    (list
                     (jfx/tree-table-column
                      "Name" 250 (tree-node-cell-renderer #(.getName %1)))
                     (jfx/tree-table-column
                      "Value"428 (tree-node-cell-renderer #(.getValue %1))))
                    :root-visible false
                    :root-expanded true)
        split-pane (jfx/split-pane
                    [(:component tree-table) (:component info-panel)]
                    :orientation :vertical)]
    {:info-panel info-panel
     :tree-table tree-table
     :split-pane split-pane
     :component (jfx/border-pane :center split-pane
                                 :insets (jfx/insets 10 10 10 10 ))}))

;;
;; Functions for handling the message queues
;;

(defn handle-count-queue
  "Starts an asynchronous loop that monitors the provided channel (count-q) and
  updates the provided atoms (node-count-atom and children-count-atom) to
  reflect the number of nodes created and the number we expect to construct."
  [node-count-atom children-count-atom count-q]
  (async/go-loop [message (async/<! count-q)]

    ;; update our progress counts
    (if (= :tree-progress (:type message))
      (do (swap! node-count-atom #(+ %1 (:nodes-added message)))
          (swap! children-count-atom #(+ %1 (:children-count message)))))
    (recur (async/<! count-q))))

(defn handle-info-queue
  "Starts an asynchronous loop that monitors the provided channel (info-q) and
  updates the provided information panel (info-q) of UI components."
  [info-panel info-q]
  (async/go-loop [message (async/<! info-q)]
    (cond

      ;; display the text message in the console area
      (:text message)
      (do (jfx/run (jfx/add-text (:text-pane info-panel) message))

          ;; processing complete, update the progress bar
          (if (= :complete (:type message))
            (jfx/run
              (.setProgress (:progress-bar info-panel) 1)
              (.setText (:progress-text info-panel) "Document processed"))))

      ;; update the text next to the progress bar
      (= :status (:type message))
      (do
        (info (:content message))
        (jfx/run (.setText (:progress-text info-panel) (:content message)))))
    (recur (async/<! info-q))))

(defn monitor-for-completion
  "Starts an asynchronous loop that monitors the provided atoms until they
  indicate that all of the nodes of XML data have been processed. As nodes are
  processed, the UI components in the provided info-panel will be updated. Once
  processing has been completed, an update message is posted to the provided
  info-q channel."
  [info-panel node-count-atom children-count-atom info-q]
  (async/go
    (loop [last-incoming-count -1]

      ;; we don't need to in a tight loop
      (async/<! (async/timeout 100))

      ;; update the progress bar status
      (if (not= -1 last-incoming-count)
        (jfx/run
          (.setProgress (:progress-bar info-panel)
                        (float (/ @node-count-atom (inc @children-count-atom))))
          (.setText (:progress-text info-panel)
                    (str "Processing document, added " @node-count-atom
                         " of ~" (inc @children-count-atom) " nodes..."))))

      ;; if we haven't started parsing nodes out, continue to loop
      (if (and (= -1 last-incoming-count) (= 0 @node-count-atom))
        (recur -1)

        ;; when we have built all nodes or our incoming node count stops
        ;; changing, processing is complete
        (if (or (not= 0 (- (inc @children-count-atom) @node-count-atom))
                (not= last-incoming-count (- (inc @children-count-atom) @node-count-atom)))
          ;;(not= last-incoming-count (- (inc @children-count-atom) @node-count-atom))
          (recur (- (inc @children-count-atom) @node-count-atom)))))

    ;; post completion message to the queue
    (if (= 0 @children-count-atom)
      (queue-complete-message info-q (str "Document could not be parsed!"))
      (queue-complete-message
       info-q (str "Document parsed with " (inc @children-count-atom) " nodes")))))

;;
;; Functions to create the main window
;;

(defn new-window
  "Creates a new XMLTool window, begins parsing the provided XML file and makes
  the window visible. If no file path is provided, then a file chooser will be
  displayed. Returns a map with information about the window."
  [xml-file-path]

  ;; create our table and reference to hold our window
  (let [
        ;; we're going to track nodes and children as we add them
        count-q (async/chan (async/buffer 500) nil #(warn %1))
        node-count (atom 0)
        children-count (atom 0)

        ;; we're going to send messages to update the progress UI
        info-q (async/chan (async/buffer 500) nil #(warn %1))

        ;; main window panel
        panel (window-panel)

        ;; we'll return a handle on our main window
        window-atom (atom nil)

        ;; function to start processing an XML file
        start-fn (fn [file]
                   (jfx/run (.setText (:progress-text (:info-panel panel))
                                      "Reading XML Document"))
                   (future (parse-xml-data (:tree-table panel) file info-q count-q)))]

    ;; create the main window
    (jfx/run
      (let [window (jfx/window
                    :title "XMLTool" :width 700 :height 900
                    :icon (jfx/image "rocket-32.png")
                    :exit-on-close true
                    :scene (jfx/scene (:component panel)))]

        ;; display our window and set the divider location
        (jfx/show-window window)
        (jfx/set-split-pane-divider-positions (:split-pane panel) [0 0.9])

        ;; update our reference with our items
        (reset! window-atom window)))

    ;; if we have a file, start building that tree!
    (if xml-file-path
      (start-fn xml-file-path)

      ;; prompt for a file
      (jfx/run
        (jfx/open-file @window-atom
                       #(if %1 (start-fn %1) (jfx/close-window @window-atom))
         :title "Select an XML File to Inspect"
         :filters (jfx/file-chooser-extension-filter "XML Files" "*.xml"))))

    ;; loop to handle the node counting messages
    (handle-count-queue node-count children-count count-q)

    ;; loop to handle update messages
    (handle-info-queue (:info-panel panel) info-q)

    ;; loop to monitor for completion
    (monitor-for-completion (:info-panel panel) node-count children-count info-q)

    window-atom))

(defn xml-tool
  "Creates a new XMLTool instance. This is the main entry point for starting the
  application user interface."
  ([]
   (new-window nil))
  ([file-path]
   (new-window file-path)))
