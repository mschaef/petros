(ns petros.core
  (:use petros.util)
  (:require [clojure.tools.logging :as log]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as credentials]
            [hiccup.core :as hiccup]
            [hiccup.page :as page]
            [petros.data :as data]))

(def app-name "Petros")

(defn authenticated-username []
  (if-let [cauth (friend/current-authentication)]
    (cauth :identity)
    nil))

(defn report-unauthorized []
  (friend/throw-unauthorized (friend/current-authentication) {}))

(defn current-user-id []
  ((data/get-user-by-email (authenticated-username)) :user_id))

(defn db-credential-fn [ creds ]
  (if-let [user-record (data/get-user-by-email (creds :username))]
    (and (credentials/bcrypt-verify (creds :password)
                                    (user-record :password))
         {:identity (creds :username)
          :roles (data/get-user-roles (:user_id user-record))})
    nil))

(defn password-matches? [ password ]
  (not (nil? (db-credential-fn {:username (authenticated-username)
                                :password password}))))

(defn logout-button []
  [:span#logout
   [:a { :href "/logout"} "[logout]"]])

(defn standard-includes [ include-js ]
  (list
   (page/include-css "/petros-desktop.css"
                     "/font-awesome.min.css")

   (page/include-js "/jquery-1.10.1.js")

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
  (let [username (authenticated-username)]
    (hiccup/html
     [:html
      (standard-header page-title include-js)
      [:body
       [:div#header 
        (list [:a { :href "/" } app-name] " - ")
        page-title
        (unless (nil? username)
          [:div.right
           [:span [:a {:href "/user/password"} username] " - " (logout-button)]])]
       (when sidebar
         [:div#sidebar sidebar])
       [:div#contents {:class (class-set {"with-sidebar" sidebar})}
        contents]
       (render-footer username)]])))

(defn render-printable [ page-title  & contents]
  (hiccup/html
   [:html
    (standard-header page-title nil)
    [:body
     [:div#contents 
      contents]]]))



