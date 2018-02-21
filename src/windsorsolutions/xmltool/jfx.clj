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
   [javafx.application Application Platform]
   [javafx.beans.property ReadOnlyStringWrapper]
   [javafx.event EventHandler]
   [javafx.geometry Insets Orientation]
   [javafx.scene Group Scene]
   [javafx.scene.control Label ScrollPane ScrollPane$ScrollBarPolicy SplitPane
    TreeTableView TreeTableColumn TreeView TreeItem]
   [javafx.scene.image Image]
   [javafx.scene.layout BorderPane VBox]
   [javafx.scene.text Font Text TextFlow]
   [javafx.stage FileChooser StageBuilder StageStyle Stage]
   [javafx.util Callback]))

(defn development?
  "Returns true if we are running in the development environment."
  []
  (if (and (resolve 'user/ENVIRONMENT)
           (= :development (var-get (resolve 'user/ENVIRONMENT))))
    true))

(defn exit
  "Exits the JavaFX Platform and the Java runtime."
  []
  (if (not (development?))
    (do (Platform/exit)
        (System/exit 0))))

(defn implicit-exit
  "If this is set to true, the JavaFX runtime will exit when the last
  window (Stage) instance is closed."
  [implicit-exit]
  (Platform/setImplicitExit implicit-exit))

(defn init
  "Initializes the JavaFX environment."
  []

  ;; force the JavaFX runtime to initialize
  (defonce force-toolkit-init (javafx.embed.swing.JFXPanel.))

  ;; don't exit on window close during development
  (if (development?) (implicit-exit false)))

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
      (run (if (seq? leaves)
             (.addAll children leaves)
             (.add children leaves))))))

(defn remove-leaves
  "Removes all of the leaves from the provided parent."
  [parent]
  (if (seq (.getChildren parent))
    (.removeAll (.getChildren parent))))

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
  cell valuesq for that column. The Callback will apply the provided 'value-fn'
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

(defn translate-scrollbar-policy
  [policy-key]
  (cond
    (= :always policy-key)
    ScrollPane$ScrollBarPolicy/ALWAYS

    (= :never policy-key)
    ScrollPane$ScrollBarPolicy/NEVER

    :else
    ScrollPane$ScrollBarPolicy/AS_NEEDED))

(defn scroll-pane
  "Returns a new ScrollPane and populates it with the supplied component."
  [component & {:keys [hbar-policy vbar-policy fit-to-width]}]
  (let [scroll-pane (ScrollPane. component)]
    (if hbar-policy (.setHbarPolicy scroll-pane
                                    (translate-scrollbar-policy hbar-policy)))
    (if vbar-policy (.setVbarPolicy scroll-pane
                                    (translate-scrollbar-policy vbar-policy)))
    (if fit-to-width (.setFitToWidth scroll-pane fit-to-width))
    scroll-pane))

(defn text-pane
  "Returns a new TextFlow instance."
  [& {:keys [height width min-height min-width]}]
  (let [text-pane (TextFlow.)]
    (if min-height (.setMinHeight text-pane min-height))
    (if min-width (.setMinWidth text-pane min-width))
    (if height (.setMaxHeight text-pane height))
    (if width (.setMaxWidth text-pane width))
    text-pane))

(defn group
  "Returns a new Group containing the provided components."
  [components]
  (let [group (Group.)]
    (if (seq? components)
      (.addAll (.getChildrent group) components)
      (.add (.getChildren group) components))
    group))

(defn add-text
  "Adds the provides Text instances to the panel."
  [text-pane text-seq]
  (if (seq? text-seq)
    (.addAll (.getChildren text-pane)
             (map #(Text. (str (:text %1) "\n")) text-seq))
    (.add (.getChildren text-pane)
          (Text. (str (:text text-seq) "\n")))))

(defn set-split-pane-divider-positions
  "Sets the divider positions for the provided SplitPane instance. The divider positions should be a sequence consisting of a sequence with two items, the first being the divider index and the second the position for that divider (from 0 to 1.0). For instance...

  (set-split-pane-divider-positions split-pane [[0 0.85]])"
  [split-pane div-positions]
  (if (seq? div-positions)
    #(.setDividerPosition split-pane (first %1) (second %1))
    (.setDividerPosition split-pane
                         (first div-positions) (second div-positions))))

(defn split-pane
  "Returns a new SplitPane instance. The :orientation may be :horizontal
  or :vertical. The :div-positions should be a sequence, the first item in each
  should be the divider index and the second it's requested position."
  [children-seq & {:keys [orientation div-positions]}]
  (let [split-pane (SplitPane.)]
    (if div-positions (set-split-pane-divider-positions split-pane div-positions))
    (if (= :vertical orientation)
      (.setOrientation split-pane Orientation/VERTICAL))
    (if (seq children-seq)
      (.addAll (.getItems split-pane) children-seq))
    split-pane))

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

(defn exit-on-close-handler
  "Returns an EventHandler that may be attached to a window (Stage) instance in
  order to exit the JavaFX and Java runtime when that window is closed."
  []
  (reify
    EventHandler
    (handle [this event]
      (if (not (development?))
        (exit)))))

(defn image
  "Returns a new image with the content at the provided path."
  [file-path]
  (Image. file-path))

(defn window
  "Creates and returns a new Stage instance around the provided Scene. If no
  'style' is provided, the Stage will be 'decorated'. The 'width' and 'height'
  values will be used to set the Stage's minimum width and height. If an Image
  is provided under the :icon key, it will be used as the icon in the title bar
  window if the host operating system does that sort of thing."
  [& {:keys [title scene style width height exit-on-close icon]}]
  (let [stage (Stage. (if style style StageStyle/DECORATED))]
    (if title (.setTitle stage title))
    (if scene (.setScene stage scene))
    (if width (.setMinWidth stage width))
    (if height (.setMinHeight stage height))
    (if exit-on-close (.setOnCloseRequest stage (exit-on-close-handler)))
    (if icon (.add (.getIcons stage) icon))
    stage))

(defn show-window
  "Shows the provided window (Stage) instance."
  [stage]
  (run (.show stage))
  stage)

(defn close-window
  "Closes the provided window (Stage) instance."
  [stage]
  (run
    (.close stage)
    (exit)))

(defn open-file
  [window handler-fn]
  (let [file-chooser (FileChooser.)]
    (run (let [file (.showOpenDialog file-chooser window)]
           (handler-fn file)))))
