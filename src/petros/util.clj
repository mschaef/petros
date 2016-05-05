(ns petros.util
  (:use [slingshot.slingshot :only (throw+ try+)])
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]))

(defmacro unless [ condition & body ]
  `(when (not ~condition)
     ~@body))

(defn string-empty? [ str ]
  (or (nil? str)
      (= 0 (count (.trim str)))))

(defn in? 
  "true if seq contains elm"
  [seq elm]  
  (some #(= elm %) seq))

(defn assoc-if [ map assoc? k v ]
  (if assoc?
    (assoc map k v)
    map))

(defn string-leftmost
  ( [ string count ellipsis ]
      (let [length (.length string)
            leftmost (min count length)]
        (if (< leftmost length)
          (str (.substring string 0 leftmost) ellipsis)
          string)))

  ( [ string count ]
      (string-leftmost string count "")))

(defn shorten-url-text [ url-text target-length ]
  (let [url (java.net.URL. url-text)
        base (str (.getProtocol url)
                  ":"
                  (if-let [authority (.getAuthority url)]
                    (str "//" authority)))]
    (str base
         (string-leftmost (.getPath url)
                          (max 0 (- (- target-length 3) (.length base)))
                          "..."))))

(defn parsable-integer? [ str ]
  (if (integer? str)
    str
    (try
      (Integer/parseInt str)
      (catch Exception ex
        false))))

(defn parsable-double? [ str ]
  (if (number? str)
    str
    (try
      (Double/parseDouble str)
      (catch Exception ex
        false))))

(defn config-property 
  ( [ name ] (config-property name nil))
  ( [ name default ]
      (let [prop-binding (System/getProperty name)]
        (if (nil? prop-binding)
          default
          (if-let [ int (parsable-integer? prop-binding) ]
            int
            prop-binding)))))

(defn add-shutdown-hook [ shutdown-fn ]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (shutdown-fn)))))

(defmacro with-validation [ fail-markup-fn & body ]
  `(try+
    ~@body
    (catch [ :type :form-error ] details#
      (~fail-markup-fn (:message details#)))))

(defn fail-validation [ message ]
  (throw+ { :type :form-error :message message }))


(defn class-set [ classes ]
  (clojure.string/join " " (map str (filter #(classes %) (keys classes)))))

(defmacro lwatch [ expr ]
  `(log/error :watch '~expr ~expr))

(defn wrap-authorize-fn [ authorized? handler ]
  (fn [request]
    (when (authorized?)
      (handler request))))
