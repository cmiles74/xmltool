(ns ^{:doc "Provides functions for interacting with JavaFX"}
    windsorsolutions.xmltool.jfx
  (:require
   [taoensso.timbre :as timbre
    :refer (debug info error warn)])
  (:import
   [javafx.application Platform]
   [javafx.beans.property ReadOnlyStringWrapper]
   [javafx.event EventHandler]
   [javafx.geometry Insets Orientation]
   [javafx.scene Group Scene]
   [javafx.scene.control Label ListView Menu MenuBar MenuItem ProgressBar ScrollPane
    ScrollPane$ScrollBarPolicy SplitPane Tab TabPane TabPane$TabClosingPolicy
    TreeTableCell TreeTableView TreeTableColumn TreeItem]
   [javafx.scene.image Image]
   [javafx.scene.layout BorderPane HBox VBox Priority]
   [javafx.scene.text Text TextFlow]
   [javafx.stage FileChooser FileChooser$ExtensionFilter StageStyle Stage]
   [javafx.util Callback]
   [javafx.scene.input Clipboard ClipboardContent KeyCode KeyCodeCombination KeyCombination]
   [javafx.scene.input KeyCombination$Modifier]))

;; key code representing copy, Control+C
(def KEY-COPY (KeyCodeCombination.
               KeyCode/C
               (into-array KeyCombination$Modifier [KeyCombination/CONTROL_ANY])))

(defn development?
  "Returns true if we are running in the development environment."
  []
  (when (and (resolve 'user/ENVIRONMENT)
           (= :development (var-get (resolve 'user/ENVIRONMENT))))
    true))

(defn exit
  "Exits the JavaFX Platform and the Java runtime."
  []
  (when (not (development?))
    (Platform/exit)
    (System/exit 0)))

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
  (when (development?) (implicit-exit false)))

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

(defn label
  "Returns a new label with the provided content."
  ([]
   (label nil))
  ([content]
   (Label. content)))

(defn add-leaves
  "Adds the provided leaf or leaves TreeItem to the provided parent TreeItem."
  [parent leaves]
  (let [children (.getChildren parent)]
    (if (sequential? leaves)
      (run (.addAll children leaves))
      (run (.add children leaves)))))

(defn remove-leaves
  "Removes all of the leaves from the provided parent."
  [parent]
  (when (seq (.getChildren parent))
    (run (.clear (.getChildren parent)))))

(defn tree-item
  "Returns a new TreeItem instance that wraps the provided data object. If a
  parent TreeItem is provided, the new item is added to that parent."
  ([data-object]
   (tree-item data-object nil))

  ([data-object parent]
   (let [item (TreeItem. data-object)]
     (when parent (add-leaves parent item))
     item)))

(defn string-wrapper-ro
  "Creates a StringProperty instance (a java.fx.beans.ObjectProperty instance)
  that is read only and is populated with the provided 'value-fn' function. The
  'value-fn' should take one argument, that will be a Java data object (i.e. a
  bean)."
  [value-fn data-object]
  (ReadOnlyStringWrapper. (value-fn data-object)))

(defn wrappable-cell-value-factory
  "Provides a cell value factory that will create cells that wrap their text
  when the contents doesn't fit the cell width."
  []
  (reify Callback
    (call [this table-column]
      (let [cell (TreeTableCell.)
            contents (Text.)]
        (.setGraphic cell contents)
        (.bind (.wrappingWidthProperty contents) (.widthProperty cell))
        (.bind (.textProperty contents) (.itemProperty cell))
        cell))))

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

(defn wrappable-tree-table-column
  "Creates a TreeTableColumn with the provided 'name' and set to the supplied
  'width' that will render the cell values of the column with the provided
  'render-fn' with contents that will wrap at the edge of the cell. The
  'render-fn' must accept the backing data object for the TreeTableRow's
  TreeItem instance and return a javafx.beans.ObjectProperty instance that can
  be placed in the table cell."
  [name width render-fn]
  (let [column (tree-table-column name width render-fn)]
    (.setCellFactory column (wrappable-cell-value-factory))
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
             (if (sequential? columns) columns (list columns)))
    (.setShowRoot tree-table root-visible)
    (.setPlaceholder tree-table (label (str "Select \"Open\" from under the \"File\" "
                                            "menu to select an XML file to view.")))
    (when root-expanded (.setExpanded root root-expanded))
    {:root root :component tree-table}))

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
  [component & {:keys [hbar-policy vbar-policy fit-to-width fit-to-height insets]}]
  (let [scroll-pane (ScrollPane. component)]
    (when hbar-policy (.setHbarPolicy scroll-pane
                                    (translate-scrollbar-policy hbar-policy)))
    (when vbar-policy (.setVbarPolicy scroll-pane
                                    (translate-scrollbar-policy vbar-policy)))
    (when fit-to-width (.setFitToWidth scroll-pane fit-to-width))
    (when fit-to-height (.setFitToHeight scroll-pane fit-to-height))
    (when insets (.setPadding scroll-pane insets))
    scroll-pane))

(defn text-pane
  "Returns a new TextFlow instance."
  [& {:keys [height width min-height min-width insets]}]
  (let [text-pane (TextFlow.)]
    (when min-height (.setMinHeight text-pane min-height))
    (when min-width (.setMinWidth text-pane min-width))
    (when height (.setMaxHeight text-pane height))
    (when width (.setMaxWidth text-pane width))
    (when insets (.setPadding text-pane insets))
    text-pane))

(defn group
  "Returns a new Group containing the provided components."
  [components]
  (let [group (Group.)]
    (if (sequential? components)
      (run (.addAll (.getChildrent group) components))
      (run (.add (.getChildren group) components)))
    group))

(defn text
  "Creates a new text node and populates with the value under :text, if
  provided."
  ([]
   (text nil))
  ([content]
   (let [text-node (Text.)]
     (if content (.setText text-node content))
     text-node)))

(defn add-text
  "Adds the provides Text instances to the panel."
  [text-pane text-seq]
  (if (sequential? text-seq)
    (run (.addAll (.getChildren text-pane)
                 (map #(text (str (:text %1) "\n")) text-seq)))
    (run (.add (.getChildren text-pane)
               (text (str (:text text-seq) "\n"))))))

(defn set-text
  [component text]
  (run (.setText component text)))

(defn set-split-pane-divider-positions
  "Sets the divider positions for the provided SplitPane instance. The divider
  positions should be a sequence consisting of a sequence with two items, the
  first being the divider index and the second the position for that divider
  (from 0 to 1.0). For instance...

  (set-split-pane-divider-positions split-pane [[0 0.85]])"
  [split-pane div-positions]
  (if (sequential? (first div-positions))
    (doall (map #(set-split-pane-divider-positions split-pane [(first %1) (second %1)])
                div-positions))
    (run (.setDividerPosition split-pane
                              (first div-positions) (second div-positions)))))

(defn split-pane
  "Returns a new SplitPane instance. The :orientation may be :horizontal
  or :vertical. The :div-positions should be a sequence, the first item in each
  should be the divider index and the second it's requested position."
  [children-seq & {:keys [orientation div-positions]}]
  (let [split-pane (SplitPane.)]
    (when div-positions (set-split-pane-divider-positions split-pane div-positions))
    (when (= :vertical orientation)
      (.setOrientation split-pane Orientation/VERTICAL))
    (when (seq children-seq)
      (run (.addAll (.getItems split-pane) children-seq)))
    split-pane))

(defn border-pane
  "Returns a BorderPane with the provided components ('top', 'right', 'bottom'
  and 'left') placed in their respective locations in the panel. If 'insets' are
  provided then they are used for the margins of the panel."
  [& {:keys [top right bottom left center insets]}]
  (let [pane (BorderPane.)]
    (when top (.setTop pane top))
    (when right (.setRight pane right))
    (when bottom (.setBottom pane bottom))
    (when left (.setLeft pane left))
    (when center (.setCenter pane center))
    (when insets (.setPadding pane insets))
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
      (when (not (development?))
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
  window if the host operating system does that sort of thing.

  This function returns a promise that may be de-referenced to get a handle on
  the new window instance."
  [& {:keys [title scene style width height exit-on-close icon]}]
  (let [handle (promise)]
    (run (let [stage (Stage. (if style style StageStyle/DECORATED))]
           (when title (.setTitle stage title))
           (when scene (.setScene stage scene))
           (when width (.setMinWidth stage width))
           (when height (.setMinHeight stage height))
           (when exit-on-close (.setOnCloseRequest stage (exit-on-close-handler)))
           (when icon (.add (.getIcons stage) icon))
           (deliver handle stage)))
    handle))

(defn show-window
  "Shows the provided window (Stage) instance. If the :pack key is set, the
  \"sizeToScene\" will be called on the window before it is displayed. If
  function is on the :after-fn key, it will be called after the window is
  shown."
  [stage & {:keys [pack after-fn]}]
  (run
    (when pack (.sizeToScene stage))
    (.show stage))
  (when after-fn (after-fn))
  stage)

(defn close-window
  "Closes the provided window (Stage) instance."
  [stage]
  (run
    (.close stage)
    (exit)))

(defn file-chooser-extension-filter
  "Returns a new ExtensionFilter for a FileChooser with the provided description
  and supplied list of file extension description (i.e. \"*.txt\")."
  [description extensions]
  (if (sequential? extensions)
    (FileChooser$ExtensionFilter. description extensions)
    (FileChooser$ExtensionFilter. description [extensions])))

(defn file-chooser
  "Returns a new FileChooser. If the :title key is set, it's used as the title
  for the window. If a sequence of filters is provided under the :filters key,
  they will be used to create and add ExtensionFilter instances to the chooser."
  [& {:keys [title filters]}]
  (let [chooser (FileChooser. )]
    (when title (.setTitle chooser title))
    (when filters
      (if (sequential? filters)
        (run (.addAll (.getExtensionFilters chooser) filters))
        (run (.add (.getExtensionFilters chooser) filters))))
    chooser))

(defn open-file
  "Creates a new FileChooser (with the optional :title and :filters values) and
  owns it to the provided Window. The chooser is then displayed, prompting for
  the selection of a file to open."
  [window handler-fn & {:keys [title filters]}]
  (let [file-chooser (file-chooser :title title :filters filters)]
    (run (let [file (.showOpenDialog file-chooser window)]
           (handler-fn file)))))

(defn progress-bar
  [& {:keys [progress]}]
  (let [bar (ProgressBar. )]
    (when progress (run (.setProgress bar progress)))
    bar))

(defn set-progress
  [bar progress]
  (run (.setProgress bar progress)))

(defn set-progress-indeterminate
  [bar]
  (run (.setProgress bar ProgressBar/INDETERMINATE_PROGRESS)))

(defn priority-for-key
  "Returns a Priority for the provided key. Valid keys are :always, :never
  and :sometimes."
  [priority-key]
  (cond
    (= :always priority-key) Priority/ALWAYS
    (= :never priority-key) Priority/NEVER
    (= :sometimes priority-key) Priority/SOMETIMES))

(defn hgrow-component
  "Sets the vertical grow priority for the component. If no :priority is
  provided then ALWAYS is used."
  [component & {:keys [priority]}]
  (let [priority (priority-for-key priority)]
    (HBox/setHgrow component (if priority priority Priority/ALWAYS))
    component))

(defn vgrow-component
  "Sets the vertical grow priority for the component. If no :priority is
  provided then ALWAYS is used."
  [component & {:keys [priority]}]
  (let [priority (priority-for-key priority)]
    (VBox/setVgrow component (if priority priority Priority/ALWAYS))
    component))

(defn hbox
  "Returns a new HBox containing the provided components."
  [components & {:keys [spacing insets]}]
  (let [box (HBox.)]
    (when spacing (.setSpacing box spacing))
    (when insets (.setPadding box insets))
    (when components
      (if (sequential? components)
        (run (.addAll (.getChildren box) components))
        (run (.add (.getChildren box) components))))
    box))

(defn vbox
  "Returns a new VBox containing the provided components."
  [components & {:keys [spacing insets]}]
  (let [box (VBox.)]
    (when spacing (.setSpacing box spacing))
    (when insets (.setPadding box insets))
    (when components
      (if (sequential? components)
        (run (.addAll (.getChildren box) components))
        (run (.add (.getChildren box) components))))
    box))

(defn set-pref-size
  "Sets the preferred width and height of the component."
  [component & {:keys [width height]}]
  (run
    (when width (.setPrefWidth component width))
    (when height (.setPrefHeight component height)))
  component)

(defn tab
  "Returns a new tab with the given name and component as its content."
  [name component]
  (Tab. name component))

(defn tab-closing-policy
  "Returns a closing policy for a keyword."
  [key-name]
  (cond
    (= :all key-name)
    TabPane$TabClosingPolicy/ALL_TABS

    (= :selected key-name)
    TabPane$TabClosingPolicy/SELECTED_TAB

    :else
    TabPane$TabClosingPolicy/UNAVAILABLE))

(defn tab-pane
  "Returns a new TabPane containing the provided tabs and applying the supplied
  closing policy to all of those tabs."
  [tabs & {:keys [closing-policy]}]
  (let [tab-panel (TabPane.)
        policy (tab-closing-policy closing-policy)]
    (.setTabClosingPolicy tab-panel policy)
    (if (sequential? tabs)
      (run (.addAll (.getTabs tab-panel) tabs))
      (run (.add (.getTabs tab-panel) tabs)))
    tab-panel))

(defn table-key-event-handler
  "Returns a new TableKeyEventHandler that may be attached to a table and will
  copy selected rows to the clipboard when 'Control+C' are pressed."
  []
  (reify
    EventHandler
    (handle [thix event]
      (when (.match KEY-COPY event)
        (let [table-view (.getSource event)
              clipboard (ClipboardContent.)
              selected-text (interpose
                             "/t"
                             (for [position (.getSelectedCells (.getSelectionModel table-view))]
                               (.get (.getCellObservableValue
                                      (.get (.getColumns table-view) (.getColumn position))
                                      (.getRow position)))))]
          (.consume event)
          (.putString clipboard
                      (apply str
                             selected-text))
          (.setContent (Clipboard/getSystemClipboard) clipboard))))))

(defn list-view-key-event-handler
  "Returns a new KeyEventHandler that may be attached to a table and will
  copy selected rows to the clipboard when 'Control+C' are pressed."
  []
  (reify
    EventHandler
    (handle [thix event]
      (when (.match KEY-COPY event)
        (let [list-view (.getSource event)
              clipboard (ClipboardContent.)
              selected-text (interpose
                             "/t"
                             (for [item (.getSelectedItems (.getSelectionModel list-view))]
                               item))]
          (.consume event)
          (.putString clipboard
                      (apply str
                             selected-text))
          (.setContent (Clipboard/getSystemClipboard) clipboard))))))

(defn install-copy-handler
  "Installs a copy handler to copy text from a component to the clipboard"
  [component]
  (when (instance? TreeTableView component)
    (.setOnKeyPressed component (table-key-event-handler)))
  (when (instance? ListView component)
    (.setOnKeyPressed component (list-view-key-event-handler))))

(defn menu-item
  ([name]
   (menu-item name nil))
  ([name item-seq]
   (let [menu (MenuItem. name)]
     (when item-seq
       (if (sequential? item-seq)
         (.addAll (.getItems menu) item-seq)
         (.add (.getItems menu) item-seq)))
     menu)))

(defn menu
  ([name]
   (menu-item name nil))
  ([name item-seq]
   (let [menu (Menu. name)]
     (when item-seq
       (if (sequential? item-seq)
         (.addAll (.getItems menu) item-seq)
         (.add (.getItems menu) item-seq)))
     menu)))

(defn menu-bar
  [item-seq]
  (let [menu-bar (MenuBar.)]
    (if (sequential? item-seq)
      (.addAll (.getMenus menu-bar) item-seq)
      (.add (.getMenus menu-bar) item-seq))
    menu-bar))

(defn selection-handler
  [menu-item handler-fn]
  (.setOnAction
   menu-item
   (reify EventHandler
     (handle [this action-event]
       (handler-fn action-event)))))

