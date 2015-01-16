(ns petros.app
  (:use petros.util
        compojure.core
        [slingshot.slingshot :only (throw+ try+)])
  (:require [clojure.tools.logging :as log]            
            [petros.data :as data]
            [petros.core :as core]
            [petros.user :as user]
            [petros.view :as view]
            [compojure.route :as route]
            [hiccup.form :as form]
            [compojure.handler :as handler]
            [ring.util.response :as ring]))


(defroutes app-routes
  (GET "/" []
    (view/render-page {:page-title "Petros Count Sheets"}
                      (form/form-to [:post "/"]
                                    [:input {:type "submit"
                                             :value "Create Sheet"}])
                      [:table
                       (map (fn [ cs ]
                              [:tr
                               [:td (:created_on cs)]
                               [:td (:email_addr cs)]])

                            (data/all-count-sheets))]))

  (POST "/" []
    (data/add-count-sheet (core/current-user-id))
    (ring/redirect "/")))
