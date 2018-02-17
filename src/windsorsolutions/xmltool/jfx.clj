(ns ^{:doc "Provides functions for interacting with JavaFX"}
    windsorsolutions.xmltool.jfx
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :only [throw+ try+]])
  (:import
   [javafx.beans.property ReadOnlyStringWrapper]
   [javafx.application Application Platform]
   [javafx.scene Group Scene]
   [javafx.scene.control Label TreeTableView TreeTableColumn TreeView TreeItem]
   [javafx.scene.layout BorderPane VBox]
   [javafx.scene.text Font]
   [javafx.stage StageBuilder StageStyle Stage]
   [javafx.geometry Insets]
   [javafx.util Callback]))

(defn init
  "Initializes the JavaFX environment."
  []
  (defonce force-toolkit-init (javafx.embed.swing.JFXPanel.)))

(defmacro run
  "Invokes the provided body in the context of the JavaFX application thread."
  [& body]
  `(if (Platform/isFxApplicationThread)
     (try ~@body
          (catch Exception exception#
            (timbre/warn "Exception in JFX Application thread: " exception#)
            (timbre/debug exception#)))
     (Platform/runLater
      (fn []
        (try ~@body
             (catch Exception exception#
               (timbre/warn "Exception in JFX Application thread: " exception#)
               (timbre/debug exception#)))))))

(defn add-leaves
  "Adds the provided leaf or leaves TreeItem to the provided parent TreeItem."
  [parent leaves]
  (let [children (.getChildren parent)]
    (run
     (if (seq? leaves)
       (.addAll children leaves)
       (.add children leaves)))))

(defn tree-item
  "Returns a new TreeItem instance that wraps the provided data object. If a
  parent TreeItem is provided, the new item is added to that parent."
  ([data-object]
   (tree-item data-object nil))

  ([data-object parent]
   (let [item (TreeItem. data-object)]
     (if parent (add-leaves parent item))
     item)))

(defn string-wrapper-ro
  "Creates a StringProperty instance (a java.fx.beans.ObjectProperty instance)
  that is read only and is populated with the provided 'value-fn' function. The
  'value-fn' should take one argument, that will be a Java data object (i.e. a
  bean)."
  [value-fn data-object]
  (ReadOnlyStringWrapper. (value-fn data-object)))

(defn tree-table-column-callback
  "Creates a Callback that may be provided to a TreeTableColumn to render the
  cell values for that column. The Callback will apply the provided 'value-fn'
  to the backing data object for the TreeTableRow's TreeItem instance."
  [value-fn]
  (reify Callback
    (call [this tree-table-row]
      (let [data-object (.getValue (.getValue tree-table-row))]
        (value-fn data-object)))))

(defn tree-table-column
  "Creates a TreeTableColumn with the provided 'name' and set to the supplied
  'width' that will render the cell values of the column with the provided
  'render-fn'. The 'render-fn' must accept the backing data object for the
  TreeTableRow's TreeItem instance and return a javafx.beans.ObjectProperty
  instance that can be placed in the table cell."
  [name width render-fn]
  (let [column (TreeTableColumn. name)]
    (.setCellValueFactory column (tree-table-column-callback render-fn))
    (.setPrefWidth column width)
    column))

(defn tree-table
  "Creates a new tree table with the provided 'data-object' as the backing data
  object for the root of the tree. The 'root-visible' and 'root-expanded' values
  will be applied to the root and the tree table view respectively, if present.
  This function will return a map with the TreeTableView available under
  the :object key and the root TreeTableItem under the :root key."
  [data-object columns & {:keys [root-visible root-expanded]}]
  (let [root (TreeItem. data-object)
        tree-table (TreeTableView. root)]
    (.setAll (.getColumns tree-table)
             (if (seq? columns) columns (list columns)))
    (.setShowRoot tree-table root-visible)
    (if root-expanded (.setExpanded root root-expanded))
    {:root root :object tree-table}))

(defn insets
  "Returns a set of Insets (inside offsets) for the four sides of a rectangular
  area."
  [top right bottom left]
  (Insets. top right bottom left))

(defn border-pane
  "Returns a BorderPane with the provided components ('top', 'right', 'bottom'
  and 'left') placed in their respective locations in the panel. If 'insets' are
  provided then they are used for the margins of the panel."
  [& {:keys [top right bottom left center insets]}]
  (let [pane (BorderPane.)]
    (if top (.setTop pane top))
    (if right (.setRight pane right))
    (if bottom (.setBottom pane bottom))
    (if left (.setLeft pane left))
    (if center (.setCenter pane center))
    (if insets (.setPadding pane insets))
    pane))

(defn scene
  "Returns a new scene with the provided 'root' component."
  [root]
  (Scene. root))

(defn window
  "Creates and returns a new Stage instance around the provided Scene. If no
  'style' is provided, the Stage will be 'decorated'. The 'width' and 'height'
  values will be used to set the Stage's minimum width and height."
  [& {:keys [title scene style width height]}]
  (let [stage (Stage. (if style style StageStyle/DECORATED))]
    (if title (.setTitle stage title))
    (if scene (.setScene stage scene))
    (if width (.setMinWidth stage width))
    (if height (.setMinHeight stage height))
    stage))

(defn show-window
  "Shows the provided window (Stage) instance."
  [stage]
  (run (.show stage))
  stage)

(defn close-window
  "Closes the provided window (Stage) instance."
  [stage]
  (run (.close stage)))

(defn implicit-exit
  "If this is set to true, the JavaFX runtime will exit when the last
  window (Stage) instence is closed."
  [implicit-exit]
  (Platform/setImplicitExit implicit-exit))
