(ns petros.view
  (:use petros.util
        clojure.set
        hiccup.core)
  (:require [petros.core :as core]
            [hiccup.form :as form]
            [hiccup.page :as page]))

(def app-name "Petros")

(def img-show-list
     [:img { :src "/list_24x21.png" :class "show-list"
            :width 24 :height 21
            :alt "Show List"}])

(defn logout-button []
  [:span#logout
   [:a { :href "/logout"} "[logout]"]])

(defn standard-includes [ include-js ]
  (list
   (page/include-css "/reset.css"
                     "/petros-desktop.css")

   (page/include-js "/jquery-1.10.1.js"
                    "/jquery-ui.js")

   (apply page/include-js (cons "/petros.js" include-js))))

(defn standard-header [ page-title include-js ]
  [:head
   [:title app-name (unless (nil? page-title) (str " - " page-title))]
   [:link { :rel "shortcut icon" :href "/favicon.ico"}]
   (standard-includes include-js)])

(defn render-footer [ username ]
  [:div#footer
   "All Rights Reserved, Copyright 2015 East Coast Toolworks."])

(defn render-page [{ :keys [ page-title include-js sidebar ] }  & contents]
  (let [username (core/authenticated-username)]
    (html [:html
           (standard-header page-title include-js)

           [:body

            [:div#header 
             (list [:a { :href "/" } app-name] " - ")

             page-title

             (unless (nil? username)
               [:div.right
                [:span username " - " (logout-button)]])]

            (if sidebar
              (list
               [:div#overlay
                [:div#sidebar sidebar]]
               [:div.wrapper
                [:div#contents
                 contents
                 (render-footer username)]])
              [:div#page-contents
               contents
               (render-footer username)])]])))






