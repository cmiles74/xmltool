(ns windsorsolutions.xmltool.editor
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :only [throw+ try+]]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [windsorsolutions.xmltool.jfx :as jfx]
   [throttler.core :refer [throttle-chan throttle-fn]])
  (:import
   [java.io File]
   [java.util Collections Scanner]
   [java.util.regex Matcher Pattern]
   [javafx.beans.value ChangeListener]
   [javafx.scene.control ListView ScrollPane]
   [org.fxmisc.flowless VirtualizedScrollPane]
   [org.fxmisc.richtext CodeArea LineNumberFactory]
   [org.fxmisc.richtext.model StyleSpans StyleSpansBuilder]))

;; regular expressions for parsing data
(def XML-TAG (Pattern/compile "(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))|(?<COMMENT><!--[^<>]+-->)"))
(def ATTRIBUTES (Pattern/compile "(\\w+\\h*)(=)(\\h*\"[^\"]+\")"))

;; attribute symbols
(def GROUP_OPEN_BRACKET 2)
(def GROUP_ELEMENT_NAME 3)
(def GROUP_ATTRIBUTES_SECTION 4)
(def GROUP_CLOSE_BRACKET 5)
(def GROUP_ATTRIBUTE_NAME 1)
(def GROUP_EQUAL_SYMBOL 2)
(def GROUP_ATTRIBUTE_VALUE 3)

;; test data
(def SAMPLE ["<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
             "<!-- Sample XML -->",
             "< orders >",
             "	<Order number=\"1\" table=\"center\">",
             "		<items>",
             "			<Item>",
             "				<type>ESPRESSO</type>",
             "				<shots>2</shots>",
             "				<iced>false</iced>",
             "				<orderNumber>1</orderNumber>",
             "			</Item>",
             "			<Item>",
             "				<type>CAPPUCCINO</type>",
             "				<shots>1</shots>",
             "				<iced>false</iced>",
             "				<orderNumber>1</orderNumber>",
             "			</Item>",
             "			<Item>",
             "			<type>LATTE</type>",
             "				<shots>2</shots>",
             "				<iced>false</iced>",
             "				<orderNumber>1</orderNumber>",
             "			</Item>",
             "			<Item>",
             "				<type>MOCHA</type>",
             "				<shots>3</shots>",
             "				<iced>true</iced>",
             "				<orderNumber>1</orderNumber>",
             "			</Item>",
             "		</items>",
             "	</Order>",
             "</orders>"])

(defn editor
  "Returns a new list view that is embedded in a scroll pane."
  []
  (let [list-view (ListView.)
        scroll-pane (ScrollPane. list-view)]
    (.set (.fitToWidthProperty scroll-pane) true)
    (.set (.fitToHeightProperty scroll-pane) true)
    (.bind (.prefWidthProperty scroll-pane) (.widthProperty list-view))
    (.bind (.prefHeightProperty scroll-pane) (.heightProperty list-view))
    {:component scroll-pane
     :editor list-view}))

(defn lazy-reader [file]
  "Returns a lazy reader that will read the contents of a file line-by-line"
  (let [reader (Scanner. file)]
    (letfn [(helper [rdr]
              (lazy-seq
               (if-let [line-next (.hasNextLine rdr)]
                 (let [line-in (.nextLine rdr)]
                   (if (not= -1 line-in)
                     (cons (str line-in "\n") (helper rdr))))
                 (do
                   (info (str "Closing reader on " file))
                   (.close rdr) nil))))]
      (helper reader))))

(defn clear-text
  "Clears the text in the editor."
  [editor]
  (info "Clearing editor window")
  (jfx/run (.remove (.getItems editor) 0 (.size (.getItems editor)))))

(defn scroll-to-top
  [component]
  (.scrollToPixel component 0.0 0.0))

(defn append-text
  [editor text]
  (jfx/run
    (.add (.getItems editor) text)))

(defn set-text
  "Sets the provided text for the editor."
  [info-fn info-q editor file]
  (let [lines-read (atom 0)
        update-fn #(info-fn info-q %1)
        line-q (async/chan (async/buffer 1000) nil #(warn %1))
        append-fn (throttle-fn append-text 10000 :second)
        reader (lazy-reader file)]

    (async/go-loop [line (async/<! line-q)]
      (append-fn editor line)
      (recur (async/<! line-q)))

    (doseq [lines reader]
      (async/>!! line-q (apply str lines))
      (swap! lines-read #(+ (count lines) %1)))))

(defn add-stylesheet
  "Adds our XML CSS stylesheet to the provided scene."
  [scene]
  (.add (.getStylesheets scene) (.toExternalForm (io/resource "xml-highlighting.css"))))
