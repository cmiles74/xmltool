(ns windsorsolutions.xmltool.editor
  (:require
   [taoensso.timbre :as timbre
    :refer (info  warn)]
   [clojure.core.async :as async]
   [windsorsolutions.xmltool.jfx :as jfx]
   [throttler.core :refer [throttle-fn]])
  (:import
   [java.util Scanner]
   [javafx.scene.control ListView ScrollPane]))

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

(defn lazy-reader
  "Returns a lazy reader that will read the contents of a file line-by-line"
  [file]
  (let [reader (Scanner. file)]
    (letfn [(helper [rdr]
              (lazy-seq
               (if-let [_ (.hasNextLine rdr)]
                 (let [line-in (.nextLine rdr)]
                   (when (not= -1 line-in)
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
  [editor file]
  (let [lines-read (atom 0)
        line-q (async/chan (async/buffer 1000) nil #(warn %1))
        append-fn (throttle-fn append-text 10000 :second)
        reader (lazy-reader file)]

    (async/go-loop [line (async/<! line-q)]
      (append-fn editor line)
      (recur (async/<! line-q)))

    (doseq [lines reader]
      (async/>!! line-q (apply str lines))
      (swap! lines-read #(+ (count lines) %1)))))
