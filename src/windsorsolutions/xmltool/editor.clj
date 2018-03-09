(ns windsorsolutions.xmltool.editor
  (:require
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
   [taoensso.timbre.profiling :as profiling
    :refer (pspy pspy* profile defnp p p*)]
   [slingshot.slingshot :only [throw+ try+]]
   [clojure.java.io :as io])
  (:import
   [java.util Collections]
   [java.util.regex Matcher Pattern]
   [javafx.beans.value ChangeListener]
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

(defn compute-highlighting
  "Returns a set of StyleSpans that indicate how the content of the provided
  'text' should be highlighted."
  [text]
  (let [matcher (.matcher XML-TAG text)
        spans-builder (StyleSpansBuilder.)]
    (loop [last-end 0
           found (.find matcher)]
      (if found
        (do
          (.add spans-builder [], (- (.start matcher) last-end))
          (if (.group matcher "COMMENT")
            (.add spans-builder #{"comment"} (- (inc (.end matcher)) (.start matcher)))
            (if (.group matcher "ELEMENT")
              (let [attr-text (.group matcher GROUP_ATTRIBUTES_SECTION)]
                (.add spans-builder #{"tagmark"} (- (.end matcher GROUP_OPEN_BRACKET) (.start matcher GROUP_OPEN_BRACKET)))
                (.add spans-builder #{"anytag"} (- (.end matcher GROUP_ELEMENT_NAME) (.end matcher GROUP_OPEN_BRACKET)))

                (if (not (.isEmpty attr-text))
                  (let [attr-matcher (.matcher ATTRIBUTES attr-text)]

                    (loop [attr-last-end 0
                           attr-found (.find attr-matcher)]
                      (if attr-found
                        (do (.add spans-builder [] (- (.start attr-matcher) attr-last-end))
                            (.add spans-builder #{"attribute"}
                                  (- (.end attr-matcher GROUP_ATTRIBUTE_NAME) (.start attr-matcher GROUP_ATTRIBUTE_NAME)))
                            (.add spans-builder #{"tagmark"}
                                  (- (.end attr-matcher GROUP_EQUAL_SYMBOL) (.end attr-matcher GROUP_ATTRIBUTE_NAME)))
                            (.add spans-builder #{"avalue"}
                                  (- (.end attr-matcher GROUP_ATTRIBUTE_VALUE) (.end attr-matcher GROUP_EQUAL_SYMBOL)))))

                      (if attr-found (recur (.end attr-matcher) (.find attr-matcher))
                          (if (> (.length attr-text) attr-last-end)
                            (.add spans-builder [] (- (.length attr-text) attr-last-end))))))
                  (.add spans-builder #{"tagmark"}
                        (- (.end matcher GROUP_CLOSE_BRACKET) (.end matcher GROUP_ATTRIBUTES_SECTION)))))))))

      (if found (recur (.end matcher) (.find matcher))
          (.add spans-builder [] (- (.length text) last-end))))
    (.create spans-builder)))

(defn editor
  "Returns a new CodeArea that is embedded in a VirtualizedScrollPane."
  []
  (let [code-area (CodeArea.)]
    (.setParagraphGraphicFactory code-area (LineNumberFactory/get code-area))
    (.addListener (.textProperty code-area)
                  (reify ChangeListener
                    (changed [this observable old-value new-value]
                      (.setStyleSpans code-area 0 (compute-highlighting new-value)))))
    {:component (VirtualizedScrollPane. code-area)
     :editor code-area}))

(defn set-text
  "Sets the provided text for the editor."
  [editor text]
  (.replaceText editor 0 (.length (.getText editor)) text))

(defn scroll-to-top
  [component]
  (.scrollToPixel component 0.0 0.0))

(defn add-stylesheet
  "Adds our XML CSS stylesheet to the provided scene."
  [scene]
  (.add (.getStylesheets scene) (.toExternalForm (io/resource "xml-highlighting.css"))))
