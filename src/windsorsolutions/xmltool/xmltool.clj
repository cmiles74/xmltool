(ns windsorsolutions.xmltool.xmltool
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :as sling]
   [clojure.string :as cstring]
   [clojure.java.io :as io]
   [clojure.core.async :as async]
   [windsorsolutions.xmltool.xml :as xml]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.editor :as editor]
   [windsorsolutions.xmltool.ui :as ui])
  (:import
   [windsorsolutions.xmltool.data TreeNode]
   [java.io File]
   [javafx.scene.layout StackPane]))

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

(defn queue-tree-node-message
  [msg-q parent-node child-node]
  (queue-message msg-q {:parent parent-node :node child-node}))

;;
;; Functions for building the tree nodes
;;

(defn build-tree-attr
  "Builds a sub-tree of attribute values and adds them to the tree node."
  [parent attrs]
  (if (not (nil? attrs))
    (let [attr-node (jfx/tree-item (TreeNode. "Attributes" nil) parent)]
      (jfx/add-leaves
       attr-node
       (map #(jfx/tree-item (TreeNode. (name (key %1)) (val %1))) attrs)))
    parent))

(defn build-tree-item
  "Builds a new tree item with the provided key and value We use the 'xml-node' to
  populate the 'Attributes' sub-tree for the new item."
  [keyname value xml-node]
  (let [content (if (nil? value) nil (cstring/trim value))
        tree-item (jfx/tree-item (TreeNode. (name keyname) content))]
    (build-tree-attr tree-item (:attrs xml-node))
    tree-item))

(defn build-tree-node
  "Builds a tree node for the provided node of XML data. If a message
  queue ('msg-q') is provided, progress messages will be provided while the tree
  is being constructed. If a tree queue
  ('tree-q') is provided, messages with new nodes for a tree/table view will
  be provided."
  [parent xml-node & {:keys [msg-q initial tree-q]}]
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
        (queue-tree-node-message tree-q
                                 parent
                                 (build-tree-item (:tag xml-node)
                                                  content
                                                  xml-node)))

      ;; node content is a single map
      (map? (:content xml-node))
      (let [tree-node (build-tree-item (:tag xml-node) nil xml-node)]
        (queue-tree-node-message tree-q
                                 parent
                                 tree-node)
        (if msg-q (queue-tree-message msg-q 1 1))
        (build-tree-node tree-node
                         (:content xml-node)
                         :msg-q msg-q
                         :tree-q tree-q))

      ;; node content is a vector
      (vector? (:content xml-node))
      (cond

        ;; if it's not a map, concatenate the content
        (not (map? (first (:content xml-node))))
        (let [content (apply str (interpose ", " (:content xml-node)))]
          (queue-tree-node-message tree-q
                                   parent
                                   (build-tree-item (:tag xml-node) content xml-node)))


        ;; build nodes for the children
        :else
        (let [tree-node (build-tree-item (:tag xml-node) nil xml-node)]
          (queue-tree-node-message tree-q
                                   parent
                                   tree-node)
          (if msg-q (queue-tree-message msg-q 0 (count (:content xml-node))))
          (doall (map
                  #(build-tree-node tree-node %1 :msg-q msg-q :tree-q tree-q)
                  (:content xml-node))))))

    ;; we don't know what this node is, add a "junk" node
    (queue-tree-node-message tree-q
                             parent
                             (build-tree-item :recovered-content xml-node
                                              {:attrs {:error
                                                       (str "Content not attached "
                                                            "to XML element")}}))))

;;
;; Functions for parsing the XML data
;;

(defn catch-non-fatal-xml-parse-errors

  "Wraps a function that may through XML parsing errors in an error handler that
  will catch and log all non-fatal exceptions to the provided message queue."
  [msg-q work-fn]
  (sling/try+ (work-fn)
              (catch #(not= :fatal (:type %1)) exception
                (warn exception)
                (queue-error-message msg-q
                                     (str "Error of type \"" (name (:type exception))
                                          "\" encountered while parsing line "
                                          (:line exception) " column " (:column exception) ": "
                                          (.getMessage (:exception exception)))))
              (catch Exception exception
                (queue-error-message msg-q (.getMessage exception)))))

(defn parse-xml-data
  "Begins parsing the data from the File ('file') and populates the provided
  'tree-table' with nodes by posting messages with the nodes to the tree-q and
  then reading those messages and adding the nodes to the tree/table view
  created from that data. As parsing progresses, messages are posted to the
  provided message queue ('info-q')."
  [tree-table xml-editor file info-q count-q tree-q]
  (sling/try+
   (queue-info-message info-q (str "Loading the file at " (.getAbsolutePath file) "..."))
   (catch-non-fatal-xml-parse-errors
    info-q #(let [xml-tree (xml/parse-xml file)]
              (build-tree-node (:root tree-table)
                               xml-tree
                               :msg-q count-q
                               :tree-q tree-q)
              (jfx/run (editor/set-text (:editor xml-editor)
                                        @(future (xml/pretty-xml-out xml-tree (slurp file))))
                (editor/scroll-to-top (:component xml-editor)))))
    (catch #(= :fatal (:type %1)) exception
      (queue-error-message info-q
                           (str "Fatal error encountered while parsing line "
                                (:line exception) " column " (:column exception) ": "
                                (.getMessage (:exception exception))))

      ;; strip out the bad characters
      (sling/try+
       (queue-info-message info-q
                           (str "Attempting to scrub bad characters from the XML data"))
       (catch-non-fatal-xml-parse-errors info-q
        #(let [xml-tree (xml/parse-xml (xml/clean-xml file))]
           (build-tree-node (:root tree-table)
                            xml-tree
                            :msg-q count-q
                            :tree-q tree-q
                            :initial true)
                  (jfx/run (editor/set-text (:editor xml-editor)
                                            @(future (xml/pretty-xml-out xml-tree
                                                                         (slurp file))))
                    (editor/scroll-to-top (:component xml-editor)))))

       ;; well, now we know we really can't parse this file :-(
       (catch #(= :fatal (:type %1)) exception
         (queue-error-message info-q
                              (str "Fatal error encountered while parsing line "
                                   (:line exception) " column " (:column exception) ": "
                                   (.getMessage (:exception exception)))))))
    (catch Exception exception
      (do (info exception)
          (queue-error-message info-q
                               (str "Couldn't open the file at " file ": "
                                    (.getMessage exception)))
          (queue-complete-message info-q
                                  "Couldn't open the file, check the \"Console\" "
                                  "tab for more details")))))

;;
;; Functions for handling the message queues
;;

(defn handle-count-queue
  "Starts an asynchronous loop that monitors the provided channel (count-q) and
  updates the provided atoms (node-count-atom and children-count-atom) to
  reflect the number of nodes created and the number we expect to construct."
  [panel node-count-atom children-count-atom count-q info-q]
  (async/go-loop [message (async/<! count-q) last-count -1]

    ;; update our progress counts
    (if (= :tree-progress (:type message))
      (do (swap! node-count-atom #(+ %1 (:nodes-added message)))
          (swap! children-count-atom #(+ %1 (:children-count message)))))

    ;; post completion message to the queue
    (if (and (not= -1 last-count)
             (= 0 (- @node-count-atom (inc @children-count-atom))))
      (queue-complete-message
       info-q (str "Document parsed with " (inc @children-count-atom) " nodes")))

    ;; update the progress bar + message every 1000 nodes
    (jfx/set-progress-indeterminate (:progress-bar panel))
    (if (= 0 (rem @node-count-atom 1000))
      (do 
          (jfx/set-progress (:progress-bar panel)
                            (float (/ @node-count-atom (inc @children-count-atom))))
          (jfx/set-text (:progress-text panel)
                        (str "Processing document, added " @node-count-atom
                             " of ~" (inc @children-count-atom) " nodes..."))))
    (recur (async/<! count-q) (:children-count message))))

(defn handle-info-queue
  "Starts an asynchronous loop that monitors the provided channel (info-q) and
  updates the provided information panel (info-q) of UI components."
  [panel info-q]
  (async/go-loop [message (async/<! info-q)]
    (cond

      ;; processing complete, update the progress bar
      (= :complete (:type message))
      (do 
          (jfx/set-progress (:progress-bar panel) 1)
          ;; (jfx/add-text (:console panel) message)
          (jfx/set-text (:progress-text panel)
                        (if (:text message) (:text message) "Document processed!")))

      ;; display the text message in the console area
      (:text message)
      (do (jfx/add-text (:console panel) message))

      ;; update the text next to the progress bar
      (= :status (:type message))
      (jfx/set-text (:progress-text panel) (:content message)))
    (recur (async/<! info-q))))

  (defn handle-tree-node-queue
    [tree-table tree-q]
    (async/go-loop [message (async/<! tree-q)]
      (if (:parent message)
        (jfx/add-leaves (:parent message) (:node message))
        (jfx/add-leaves (:root tree-table) (:node message)))
      (recur (async/<! tree-q))))

;;
;; Functions to create the main window
;;

(defn start-monitoring
  [node-count children-count count-q info-q tree-q tree-table panel]

  ;; loop to handle the node counting messages
  (handle-count-queue panel node-count children-count count-q info-q)

  ;; loop to handle update messages
  (handle-info-queue panel info-q)

  ;; loop to handle adding tree nodes to the tree/table
  (handle-tree-node-queue tree-table tree-q))

(defn new-window
  "Creates a new XMLTool window, begins parsing the provided XML file and makes
  the window visible. If no file path is provided, then a file chooser will be
  displayed. Returns a map with information about the window."
  [xml-file-path]

  (let [
        ;; get a handle on the incoming xml file
        xml-file (if xml-file-path (File. xml-file-path))

        ;; we're going to track nodes and children as we add them
        count-q (async/chan (async/buffer 500) nil #(warn %1))
        node-count (atom 0)
        children-count (atom 0)

        ;; we're going to send messages to update the progress UI
        info-q (async/chan (async/buffer 500) nil #(warn %1))

        ;; we're going to send messages with new nodes for the tree/table view
        tree-q (async/chan (async/buffer 500) nil #(warn %1))

        ;; main window panel
        panel (ui/window-panel)

        ;; our scene
        scene (jfx/scene (:component panel))

        ;; create our window
        window (jfx/window
                :title "XMLTool" :width 700 :height 900
                :icon (jfx/image "rocket-32.png")
                :exit-on-close true
                :scene scene)

        ;; function to start processing an XML file and monitoring queues
        start-fn (fn [file]
                   (jfx/set-text (:progress-text panel) (str "Reading XML Document " file))
                   (future (parse-xml-data (:tree-table panel)
                                           (:editor panel)
                                           file
                                           info-q
                                           count-q
                                           tree-q))
                   (future (start-monitoring node-count
                                             children-count
                                             count-q
                                             info-q
                                             tree-q
                                             (:tree-table panel)
                                             panel)))

        ;; function to start processing input or prompt for a file
        acquire-file-fn (fn []
                          (if xml-file
                            (start-fn xml-file)))]

    ;; add our stylesheet for the editor
    ;;(jfx/run (editor/add-stylesheet scene))

    ;; add handlers for opening an new file
    (jfx/selection-handler (:open-menu-item panel)
                           (fn [event]
                             (ui/prompt-for-file @window
                                              (fn [file-in]
                                                (jfx/remove-leaves (:root (:tree-table panel)))
                                                (editor/set-text (:editor (:editor panel)) " ")
                                                (start-fn file-in)))))

    ;; add a handler for quitting the application
    (jfx/selection-handler (:quit-menu-item panel)
                           (fn [event] (jfx/close-window @window)))

    ;; display our window
    (jfx/show-window @window
                     :pack false
                     :after-fn #(acquire-file-fn))


    ;; workaround janky layout issue
    (future
      (Thread/sleep 500)
      (jfx/run (.setHeight @window (dec (.getHeight @window)))))

    window))

(defn xml-tool
  "Creates a new XMLTool instance. This is the main entry point for starting the
  application user interface."
  ([]
   (new-window nil))
  ([file-path]
   (new-window file-path)))
