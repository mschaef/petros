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

(defn sheet-url [ sheet-id ]
  (str "/sheet/" sheet-id))

(defn render-home-page []
  (view/render-page {:page-title "Petros Count Sheets"}
                    (form/form-to [:post "/"]
                                  [:input {:type "submit"
                                           :value "Create Sheet"}])
                    [:table
                     (map (fn [ cs ]
                            [:tr
                             [:td 
                              [:a {:href (sheet-url (:count_sheet_id cs))}
                               (:created_on cs)]]])
                          (data/all-count-sheets))]))



(defn render-sheet [ id ]
  (view/render-page {:page-title "Count Sheet"}
                    (form/form-to { } [:post (str "/sheet/" id)]
                                  [:table
                                   [:tr
                                    [:td "Contributor"]
                                    [:td "Amount"]
                                    [:td "Notes"]]
                                   [:tr
                                    [:td (form/text-field {  } "contributor")]
                                    [:td (form/text-field {  } "amount")]
                                    [:td
                                     (form/text-field {  } "notes")
                                     (form/submit-button {} "Add Item")]]
                                   (map (fn [ dep ]
                                          [:tr
                                           [:td (:name dep)]
                                           [:td (:amount dep)]
                                           [:td (:notes dep)]])
                                        (data/all-count-sheet-deposits id))])))

(defroutes app-routes
  (GET "/" []
    (render-home-page))

  (POST "/" []
    (data/add-count-sheet (core/current-user-id))
    (ring/redirect "/"))

  (GET "/sheet/:sheet-id" [ sheet-id ]
    (render-sheet sheet-id))

  (POST "/sheet/:sheet-id"  { {sheet-id :sheet-id 
                               contributor :contributor
                               amount :amount
                               notes :notes} :params }
    (log/info "add line item:" [ sheet-id contributor amount notes ])
    (data/add-deposit sheet-id contributor amount notes)
    (ring/redirect (sheet-url sheet-id))))
