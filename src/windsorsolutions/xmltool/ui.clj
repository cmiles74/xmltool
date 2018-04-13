(ns windsorsolutions.xmltool.ui
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :as sling]
   [windsorsolutions.xmltool.jfx :as jfx]
   [windsorsolutions.xmltool.editor :as editor]
   [windsorsolutions.xmltool.data :as data])
  (:import
   [windsorsolutions.xmltool.data TreeNode]))

(defn tree-node-cell-renderer
  "Provides a renderer for a tree table column that will apply the provided
  'value-fn' to the backing data object of the row's cell."
  [value-fn]
  (partial jfx/string-wrapper-ro
     #(if (instance? TreeNode %1)
        (value-fn %1)
        (str %1))))

(defn source-panel
  "Returns a new source panel that includes a text editor component."
  []
  (let [xml-editor (editor/editor)
        scroll-pane-text (:component xml-editor)]
    {:component scroll-pane-text
     :editor (:editor xml-editor)}))

(defn window-panel
  "Creates a new panel for the main window and returns a map containing the
  components with the following keys: :info-panel, :tree-table, :split-pane
  and :component, which is a component containing them all."
  []
  (let [tree-table (jfx/tree-table (TreeNode. "Root" nil)
                                   (list
                                    (jfx/tree-table-column
                                     "Name" 250 (tree-node-cell-renderer #(.getName %1)))
                                    (jfx/wrappable-tree-table-column
                                     "Value"428 (tree-node-cell-renderer #(.getValue %1))))
                                   :root-visible false
                                   :root-expanded true)
        text-pane (source-panel)
        console-pane (jfx/text-pane :insets (jfx/insets 5 5 5 5))
        tab-pane (jfx/tab-pane
                  (list (jfx/tab "Tree" (:component tree-table))
                        (jfx/tab "Source" (:component text-pane))
                        (jfx/tab "Console" (jfx/scroll-pane console-pane :fit-to-width true))))
        progress-bar (jfx/progress-bar)
        progress-text (jfx/label "Welcome to XML Tool!")
        open-item (jfx/menu-item "Open...")
        quit-item (jfx/menu-item "Quit")
        menu (jfx/menu-bar [(jfx/menu "File" [open-item quit-item])])
        content-pane (jfx/border-pane :center tab-pane
                                      :top menu
                                      :bottom (jfx/hbox [progress-bar progress-text]
                                                        :spacing 8 :insets (jfx/insets 5 5 5 5)))]
    (jfx/install-copy-handler (:component tree-table))
    (jfx/set-progress progress-bar 0)
    (.setEditable (:editor text-pane) false)
    {:tree-table tree-table
     :editor text-pane
     :console console-pane
     :progress-bar progress-bar
     :progress-text progress-text
     :component content-pane
     :open-menu-item open-item
     :quit-menu-item quit-item}))


(defn prompt-for-file
  "Presents a the file chooser window, parented to the provided window. Once a
  file is selected, that file is provided to the start-processing-fn."
  [window start-processing-fn]
  (jfx/open-file window
                 #(if %1
                    (start-processing-fn %1))
                 :title "Select an XML File to Inspect"
                 :filters (jfx/file-chooser-extension-filter "XML Files" "*.xml")))
