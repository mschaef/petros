(ns petros.app
  (:use petros.util
        compojure.core)
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


(defn category-selector [ attrs id val ]
  [:select (merge attrs { :name id })
   (map (fn [ info ]
          [:option { :value (:category_id info)}
           (:name info)])
        (data/all-categories))])

(defn render-sheet [ id error-msg init-vals ]
  (view/render-page {:page-title "Count Sheet"}
                    (form/form-to { } [:post (str "/sheet/" id)]
                                  [:table
                                   [:tr
                                    [:td "Contributor"]
                                    [:td "Category"]
                                    [:td "Amount"]
                                    [:td "Check Number"]
                                    [:td "Notes"]
                                    [:td]]
                                   [:tr
                                    [:td (form/text-field { } "contributor" (:contributor init-vals))]
                                    [:td (category-selector { } "category-id" (:category-id init-vals))]
                                    [:td (form/text-field { } "amount" (:amount init-vals))]
                                    [:td (form/text-field { } "check-number" (:check-number init-vals))]
                                    [:td (form/text-field { } "notes" (:notes init-vals))]
                                    [:td (form/submit-button { } "Add Item")]]
                                   [:tr [:td {:colspan "6"} error-msg]]
                                   (map (fn [ dep ]
                                          [:tr
                                           [:td (:name dep)]
                                           [:td (:category dep)]
                                           [:td (:amount dep)]
                                           [:td (or (:check_number dep) "Cash")]
                                           [:td (:notes dep)]
                                           [:td]])
                                        (data/all-count-sheet-deposits id))])))


(defn accept-integer [ obj message ]
  (or (parsable-integer? obj)
      (fail-validation message)))

(defn accept-double [ obj message ]
  (or (parsable-double? obj)
      (fail-validation message)))

(defn accept-check-number [ obj message ]
  (if (string-empty? obj) 
    nil
    (or (parsable-integer? obj) 
        (fail-validation message))))

(defn accept-notes [ obj message ] 
  obj)

(defroutes app-routes
  (GET "/" []
    (render-home-page))

  (POST "/" []
    (data/add-count-sheet (core/current-user-id))
    (ring/redirect "/"))

  (GET "/sheet/:sheet-id" [ sheet-id ]
    (render-sheet sheet-id nil {}))

  (POST "/sheet/:sheet-id" { params :params }
    (let [ {sheet-id :sheet-id 
            category-id :category-id
            contributor :contributor
            amount :amount
            check-number :check-number
            notes :notes} params ]
      (log/info "add line item:" params) 
      (with-validation #(render-sheet sheet-id % params)
        (data/add-deposit (accept-integer sheet-id          "Invalid sheet-id")
                          contributor
                          (accept-integer category-id       "Invalid category")
                          (accept-double amount             "Invalid sheet-id")
                          (accept-check-number check-number "Invalid check number")
                          (accept-notes notes               "Invalid notes")) 
        (ring/redirect (sheet-url sheet-id))))))


